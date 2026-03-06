package com.riprog.launcher.data.repository

import com.riprog.launcher.data.model.AppItem

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
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import androidx.core.graphics.createBitmap
import org.json.JSONArray
import org.json.JSONObject
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
    private val diskCache: DiskCacheManager = DiskCacheManager(context)
    private val pendingListeners: MutableMap<String, MutableList<OnIconLoadedListener>> = HashMap()

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

        val cacheSize = Math.min(8 * 1024, maxMemory / 20)
        iconCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        executor.execute {
            diskCache.performCleanup()
        }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            iconCache.trimToSize(iconCache.size() / 2)
        }
        if (level >= 60 ) {
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
            val cachedJson = diskCache.getData("app_list")
            if (cachedJson != null) {
                val cachedApps = deserializeApps(cachedJson)
                if (cachedApps.isNotEmpty()) {
                    mainHandler.post { listener.onAppsLoaded(cachedApps) }
                }
            }

            val apps: MutableList<AppItem> = ArrayList()
            val selfPackage = context.packageName

            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

            for (user in userManager.userProfiles) {
                val activities = launcherApps.getActivityList(null, user)
                for (activity in activities) {
                    if (activity.applicationInfo.packageName != selfPackage) {
                        val item = AppItem(
                            activity.label.toString(),
                            activity.applicationInfo.packageName,
                            activity.name,
                            user
                        )
                        apps.add(item)
                    }
                }
            }

            apps.sortWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }

            val newJson = serializeApps(apps)
            if (newJson != cachedJson) {
                diskCache.saveData("app_list", newJson)
                mainHandler.post { listener.onAppsLoaded(apps) }
            }
        }
    }

    fun loadIcon(item: AppItem, listener: OnIconLoadedListener) {
        val userHandle = item.getUserHandleOrDefault()
        val cacheKey = "${item.packageName}_$userHandle"
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
            var bitmap = diskCache.getBitmap(cacheKey)

            if (bitmap == null) {
                try {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val activities = launcherApps.getActivityList(item.packageName, userHandle)
                    if (activities.isNotEmpty()) {
                        val drawable = activities[0].getIcon(0)
                        bitmap = drawableToBitmap(drawable)
                        if (bitmap != null) {
                            diskCache.saveBitmap(cacheKey, bitmap)
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            if (bitmap != null) {
                iconCache.put(cacheKey, bitmap)
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

    fun invalidateIcon(packageName: String) {
        iconCache.remove(packageName)
        executor.execute {
            diskCache.removeBitmap(packageName)
        }
    }

    private fun serializeApps(apps: List<AppItem>): String {
        val array = JSONArray()
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        for (app in apps) {
            val obj = JSONObject()
            obj.put("label", app.label)
            obj.put("packageName", app.packageName)
            obj.put("className", app.className)
            obj.put("userSerial", userManager.getSerialNumberForUser(app.getUserHandleOrDefault()))
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeApps(json: String): List<AppItem> {
        val apps = mutableListOf<AppItem>()
        try {
            val array = JSONArray(json)
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val userSerial = if (obj.has("userSerial")) obj.getLong("userSerial") else -1L
                val userHandle = if (userSerial != -1L) userManager.getUserForSerialNumber(userSerial) else Process.myUserHandle()

                if (userHandle != null) {
                    apps.add(AppItem(
                        obj.getString("label"),
                        obj.getString("packageName"),
                        obj.getString("className"),
                        userHandle
                    ))
                }
            }
        } catch (ignored: Exception) {}
        return apps
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null

        val size = 192

        return try {
            val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
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
