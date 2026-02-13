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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

class LauncherModel(private val context: Context) {
    interface OnAppsLoadedListener {
        fun onAppsLoaded(apps: List<AppItem>)
    }

    private val pm: PackageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val iconCache: LruCache<String, Bitmap>
    private val pendingListeners = mutableMapOf<String, MutableList<OnIconLoadedListener>>()
    private val diskCache: DiskCache = DiskCache(context)

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = Math.min(16 * 1024, maxMemory / 12)
        iconCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            iconCache.trimToSize(iconCache.size() / 2)
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            iconCache.evictAll()
            scope.launch { diskCache.performCleanup() }
            System.gc()
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.evictAll()
            System.gc()
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    @JvmOverloads
    fun loadApps(listener: OnAppsLoadedListener, forceRefresh: Boolean = false) {
        scope.launch {
            if (!forceRefresh) {
                val cached = withContext(Dispatchers.IO) { diskCache.loadData("app_list") }
                if (cached != null) {
                    val apps = deserializeAppList(cached)
                    if (apps.isNotEmpty()) {
                        listener.onAppsLoaded(apps)
                        return@launch
                    }
                }
            }

            val apps = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val infos = pm.queryIntentActivities(mainIntent, 0)
                val appList = mutableListOf<AppItem>()
                val selfPackage = context.packageName
                for (info in infos) {
                    if (info.activityInfo.packageName != selfPackage) {
                        val item = AppItem(
                            info.loadLabel(pm).toString(),
                            info.activityInfo.packageName,
                            info.activityInfo.name
                        )
                        appList.add(item)
                    }
                }
                appList.sortBy { it.label.lowercase(Locale.getDefault()) }
                diskCache.saveData("app_list", serializeAppList(appList))
                appList
            }
            listener.onAppsLoaded(apps)
        }
    }

    private fun serializeAppList(apps: List<AppItem>): String {
        return try {
            val array = JSONArray()
            for (app in apps) {
                val obj = JSONObject()
                obj.put("l", app.label)
                obj.put("p", app.packageName)
                obj.put("c", app.className)
                array.put(obj)
            }
            array.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun deserializeAppList(json: String): List<AppItem> {
        val apps = mutableListOf<AppItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                apps.add(AppItem(obj.getString("l"), obj.getString("p"), obj.getString("c")))
            }
        } catch (ignored: Exception) {
        }
        return apps
    }

    fun invalidateAppListCache() {
        diskCache.invalidateData("app_list")
    }

    fun clearAppIconCache(packageName: String) {
        iconCache.remove(packageName)
        diskCache.removeIcon(packageName)
    }

    fun loadIcon(item: AppItem, listener: OnIconLoadedListener) {
        synchronized(pendingListeners) {
            val cached = iconCache.get(item.packageName)
            if (cached != null) {
                listener.onIconLoaded(cached)
                return
            }
            val listeners = pendingListeners[item.packageName]
            if (listeners != null) {
                listeners.add(listener)
                return
            }
            val newListeners = mutableListOf<OnIconLoadedListener>()
            newListeners.add(listener)
            pendingListeners[item.packageName] = newListeners
        }

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                var b = diskCache.loadIcon(item.packageName)
                if (b == null) {
                    try {
                        val drawable = pm.getApplicationIcon(item.packageName)
                        b = drawableToBitmap(drawable)
                        if (b != null) {
                            diskCache.saveIcon(item.packageName, b)
                        }
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                }
                b
            }

            if (bitmap != null) {
                iconCache.put(item.packageName, bitmap)
            }

            val listeners = synchronized(pendingListeners) {
                pendingListeners.remove(item.packageName)
            }
            listeners?.forEach { it.onIconLoaded(bitmap) }
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
