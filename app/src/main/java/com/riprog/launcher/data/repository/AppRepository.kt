package com.riprog.launcher.data.repository

import com.riprog.launcher.data.model.AppItem

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AppRepository(context: Context) {
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
        executor.shutdown()
    }

    fun loadApps(listener: OnAppsLoadedListener) {
        executor.execute {
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

            mainHandler.post { listener.onAppsLoaded(apps) }
        }
    }

    fun loadIcon(item: AppItem, isMaterialYou: Boolean = false, tintColor: Int = Color.TRANSPARENT, listener: OnIconLoadedListener) {
        val cacheKey = if (isMaterialYou) item.packageName + "_my_" + tintColor else item.packageName

        synchronized(pendingListeners) {
            val cached = iconCache[cacheKey]
            if (cached != null) {
                listener.onIconLoaded(cached)
                return
            }

            var listeners = pendingListeners[cacheKey]
            if (listeners != null) {
                listeners.add(listener)
                return
            }

            listeners = ArrayList()
            listeners.add(listener)
            pendingListeners[cacheKey] = listeners
        }

        executor.execute {
            var bitmap: Bitmap? = null
            try {
                var drawable = pm.getApplicationIcon(item.packageName)

                if (isMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
                    val monochrome = drawable.monochrome
                    if (monochrome != null) {
                        drawable = monochrome.mutate()
                        drawable.setTint(tintColor)

                        // For simplicity, we just tint the monochrome layer.
                        // To make it look like a real themed icon, it usually needs a background.
                        // But many systems just use the monochrome layer on a themed circle.
                    }
                }

                bitmap = drawableToBitmap(drawable)
                if (bitmap != null) {
                    iconCache.put(cacheKey, bitmap)
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
            }

            val finalBitmap = bitmap
            mainHandler.post {
                val listeners: MutableList<OnIconLoadedListener>?
                synchronized(pendingListeners) {
                    listeners = pendingListeners.remove(cacheKey)
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

    fun interface OnIconLoadedListener {
        fun onIconLoaded(icon: Bitmap?)
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
