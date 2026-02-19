package com.riprog.launcher.data.repository

import com.riprog.launcher.data.model.AppItem

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.*

class AppRepository(context: Context) {

    private val context: Context = context.applicationContext
    private val pm: PackageManager = context.packageManager
    private val iconCache: LruCache<String, Bitmap>
    private val iconLoadingMutex = Mutex()
    private val pendingIcons: MutableMap<String, Bitmap?> = mutableMapOf()

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = Math.min(24 * 1024, maxMemory / 10)
        iconCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            iconCache.trimToSize(iconCache.size() / 2)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            iconCache.evictAll()
            System.gc()
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.trimToSize(0)
        }
    }

    fun shutdown() {
        // No-op for coroutines
    }

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(mainIntent, 0)

        val apps: MutableList<AppItem> = ArrayList()
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
        apps
    }

    suspend fun loadIcon(item: AppItem): Bitmap? = withContext(Dispatchers.IO) {
        iconCache[item.packageName]?.let { return@withContext it }

        iconLoadingMutex.withLock {
            iconCache[item.packageName]?.let { return@withLock it }

            var bitmap: Bitmap? = null
            try {
                val drawable = pm.getApplicationIcon(item.packageName)
                bitmap = drawableToBitmap(drawable)
                if (bitmap != null) {
                    iconCache.put(item.packageName, bitmap)
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
            bitmap
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

    companion object {

        fun filterApps(apps: List<AppItem>, query: String?): List<AppItem> {
            if (query == null || query.isEmpty()) return ArrayList(apps)
            val filtered: MutableList<AppItem> = ArrayList()
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
