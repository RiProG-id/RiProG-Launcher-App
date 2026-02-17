package com.riprog.launcher.model

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.*
import java.util.Locale

class LauncherModel(context: Context) {
    interface OnAppsLoadedListener {
        fun onAppsLoaded(apps: List<AppItem>)
    }

    private val context: Context = context.applicationContext
    private val pm: PackageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun onTrimMemory(level: Int) {
    }

    fun shutdown() {
        scope.cancel()
    }

    @JvmOverloads
    fun loadApps(listener: OnAppsLoadedListener, forceRefresh: Boolean = false) {
        scope.launch {
            val apps = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val infos = pm.queryIntentActivities(mainIntent, 0)

                val appsList = mutableListOf<AppItem>()
                val selfPackage = context.packageName

                for (info in infos) {
                    if (info.activityInfo.packageName != selfPackage) {
                        val item = AppItem(
                            info.loadLabel(pm).toString(),
                            info.activityInfo.packageName,
                            info.activityInfo.name
                        )
                        appsList.add(item)
                    }
                }
                appsList.sortedWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
            }
            listener.onAppsLoaded(apps)
        }
    }

    fun loadIcon(item: AppItem, listener: OnIconLoadedListener) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val drawable = pm.getApplicationIcon(item.packageName)
                    drawableToBitmap(drawable)
                } catch (ignored: PackageManager.NameNotFoundException) {
                    null
                }
            }
            listener.onIconLoaded(bitmap)
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        val size = 192
        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: OutOfMemoryError) {
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else null
        }
    }

    interface OnIconLoadedListener {
        fun onIconLoaded(icon: Bitmap?)
    }

    companion object {
        @JvmStatic
        fun filterApps(apps: List<AppItem>, query: String?): List<AppItem> {
            if (query.isNullOrEmpty()) return ArrayList(apps)
            val filtered = mutableListOf<AppItem>()
            val lowerQuery = query.lowercase(Locale.getDefault())
            for (item in apps) {
                if (item.label.lowercase(Locale.getDefault()).contains(lowerQuery)) {
                    filtered.add(item)
                }
            }
            return filtered
        }
    }
}
