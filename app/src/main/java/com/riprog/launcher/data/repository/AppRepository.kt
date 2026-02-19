package com.riprog.launcher.data.repository

import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.cache.SmartCacheManager

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.LruCache
import kotlinx.coroutines.*
import java.util.*

class AppRepository(context: Context) {
    fun interface OnAppsLoadedListener {
        fun onAppsLoaded(apps: List<AppItem>)
    }

    private val context: Context = context.applicationContext
    private val pm: PackageManager = context.packageManager
    private val launcherApps: LauncherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val iconCache: LruCache<String, Bitmap>
    private val pendingListeners: MutableMap<String, MutableList<OnIconLoadedListener>> = HashMap()
    private val appsLoadedListeners: MutableList<OnAppsLoadedListener> = ArrayList()

    private val cacheManager = SmartCacheManager(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val launcherCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            onPackageChangedInternal(packageName)
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) {
            onPackageChangedInternal(packageName)
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            onPackageChangedInternal(packageName)
        }

        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            onPackageChangedInternal(null)
        }

        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
            onPackageChangedInternal(null)
        }
    }

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = Math.min(24 * 1024, maxMemory / 10)
        iconCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        launcherApps.registerCallback(launcherCallback)

        // Initial cleanup in background
        scope.launch {
            withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val infos = pm.queryIntentActivities(mainIntent, 0)
                val currentPkgs = infos.map { it.activityInfo.packageName }.toSet()
                cacheManager.cleanup(currentPkgs)
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
        launcherApps.unregisterCallback(launcherCallback)
        scope.cancel()
    }

    private fun onPackageChangedInternal(packageName: String?) {
        scope.launch {
            if (packageName != null) {
                cacheManager.evictApp(packageName)
                synchronized(pendingListeners) {
                    iconCache.remove(packageName)
                }
            }
            refreshApps()
        }
    }

    private fun refreshApps() {
        loadApps { /* Subscribed listeners will be notified automatically */ }
    }

    fun loadApps(listener: OnAppsLoadedListener) {
        synchronized(appsLoadedListeners) {
            if (!appsLoadedListeners.contains(listener)) {
                appsLoadedListeners.add(listener)
            }
        }

        scope.launch {
            // 1. Load from cache first
            val cachedApps = cacheManager.getCachedApps()
            if (cachedApps.isNotEmpty()) {
                notifyListeners(cachedApps)
            }

            // 2. Sync with PackageManager in background
            withContext(Dispatchers.IO) {
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

                // Check if data changed
                val changed = cachedApps.size != apps.size ||
                             cachedApps.zip(apps).any {
                                 it.first.packageName != it.second.packageName ||
                                 it.first.label != it.second.label ||
                                 it.first.className != it.second.className
                             }

                if (changed || cachedApps.isEmpty()) {
                    cacheManager.saveAppsToCache(apps)
                    withContext(Dispatchers.Main) {
                        notifyListeners(apps)
                    }
                }
            }
        }
    }

    private fun notifyListeners(apps: List<AppItem>) {
        val listeners = synchronized(appsLoadedListeners) { ArrayList(appsLoadedListeners) }
        for (l in listeners) {
            l.onAppsLoaded(apps)
        }
    }

    fun loadIcon(item: AppItem, listener: OnIconLoadedListener) {
        // 1. Check memory cache
        val memoryCached = iconCache[item.packageName]
        if (memoryCached != null) {
            listener.onIconLoaded(memoryCached)
            return
        }

        synchronized(pendingListeners) {
            var listeners = pendingListeners[item.packageName]
            if (listeners != null) {
                listeners.add(listener)
                return
            }

            listeners = ArrayList()
            listeners.add(listener)
            pendingListeners[item.packageName] = listeners
        }

        scope.launch {
            // 2. Check disk cache
            var bitmap = cacheManager.getCachedIcon(item.packageName)

            if (bitmap == null) {
                // 3. Load from PackageManager
                bitmap = withContext(Dispatchers.IO) {
                    try {
                        val drawable = pm.getApplicationIcon(item.packageName)
                        drawableToBitmap(drawable)
                    } catch (ignored: PackageManager.NameNotFoundException) {
                        null
                    }
                }
                if (bitmap != null) {
                    cacheManager.saveIconToCache(item.packageName, bitmap)
                }
            }

            if (bitmap != null) {
                iconCache.put(item.packageName, bitmap)
            }

            val finalBitmap = bitmap
            val listeners: MutableList<OnIconLoadedListener>?
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
