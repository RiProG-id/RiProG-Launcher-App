package com.riprog.launcher.model

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LauncherModel(context: Context) {
    interface OnAppsLoadedListener {
        fun onAppsLoaded(apps: List<AppItem>)
    }

    private val context: Context = context.applicationContext
    private val pm: PackageManager = context.packageManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun onTrimMemory(level: Int) {
    }

    fun shutdown() {
        executor.shutdown()
    }

    @JvmOverloads
    fun loadApps(listener: OnAppsLoadedListener, forceRefresh: Boolean = false) {
        executor.execute {
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val infos = pm.queryIntentActivities(mainIntent, 0)

            val apps = mutableListOf<AppItem>()
            val selfPackage = context.packageName

            for (info in infos) {
                if (info.activityInfo.packageName != selfPackage) {
                    val item = AppItem(
                        info.loadLabel(pm).toString(),
                        info.activityInfo.packageName,
                        info.activityInfo.name
                    )
                    apps.add(item)
                }
            }

            apps.sortWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }

            mainHandler.post { listener.onAppsLoaded(apps) }
        }
    }

    fun loadIcon(item: AppItem, listener: OnIconLoadedListener) {
        executor.execute {
            var bitmap: Bitmap? = null
            try {
                val drawable = pm.getApplicationIcon(item.packageName)
                bitmap = drawableToBitmap(drawable)
            } catch (ignored: PackageManager.NameNotFoundException) {
            }

            val finalBitmap = bitmap
            mainHandler.post {
                listener.onIconLoaded(finalBitmap)
            }
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
