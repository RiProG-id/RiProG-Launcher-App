package com.riprog.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LauncherModel(context: Context) {
    fun interface OnAppsLoadedListener {
        fun onAppsLoaded(apps: List<AppItem>)
    }

    private val context: Context = context.applicationContext
    private val pm: PackageManager = context.packageManager
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconCache: LruCache<String, Bitmap>
    private val pendingListeners: MutableMap<String, MutableList<OnIconLoadedListener>> = HashMap()

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        iconCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            iconCache.evictAll()
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.trimToSize(iconCache.size() / 2)
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    fun loadApps(listener: OnAppsLoadedListener) {
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
        synchronized(pendingListeners) {
            val cached = iconCache.get(item.packageName)
            if (cached != null) {
                item.icon = cached
                listener.onIconLoaded(cached)
                return
            }

            val listeners = pendingListeners[item.packageName]
            if (listeners != null) {
                listeners.add(listener)
                return
            }

            val newList = mutableListOf<OnIconLoadedListener>()
            newList.add(listener)
            pendingListeners[item.packageName] = newList
        }

        executor.execute {
            var bitmap: Bitmap? = null
            try {
                val drawable = pm.getApplicationIcon(item.packageName)
                bitmap = drawableToBitmap(drawable)
                if (bitmap != null) {
                    iconCache.put(item.packageName, bitmap)
                }
                item.icon = bitmap
            } catch (ignored: PackageManager.NameNotFoundException) {
            }

            val finalBitmap = bitmap
            mainHandler.post {
                val listeners: List<OnIconLoadedListener>?
                synchronized(pendingListeners) {
                    listeners = pendingListeners.remove(item.packageName)
                }
                if (listeners != null) {
                    for (l in listeners) {
                        l.onIconLoaded(finalBitmap)
                    }
                }
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null

        // Use a consistent size for all icons (192x192 for high quality)
        val size = 192

        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: OutOfMemoryError) {
            // Fallback for low-memory situations
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                null
            }
        }
    }

    fun interface OnIconLoadedListener {
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
