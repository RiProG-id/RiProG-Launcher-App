package com.riprog.launcher.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.riprog.launcher.data.local.datastore.SettingsDataStore
import com.riprog.launcher.data.model.HomeItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class LauncherPreferences(private val context: Context, initialDataStore: SettingsDataStore? = null) : KoinComponent {
    private val dataStore: SettingsDataStore by if (initialDataStore != null) lazy { initialDataStore } else inject()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var _columns = 4
    private var _widgetId = -1
    private var _isFreeformHome = false
    private var _iconScale = 1.0f
    private var _isHideLabels = false
    private var _isLiquidGlass = true
    private var _isDarkenWallpaper = true
    private var _themeMode = "system"
    private var _drawerOpenCount = 0
    private var _lastDefaultPromptTimestamp = 0L
    private var _defaultPromptCount = 0
    private var _pageCount = 2

    init {
        scope.launch {
            launch { dataStore.columns.collect { _columns = it } }
            launch { dataStore.widgetId.collect { _widgetId = it } }
            launch { dataStore.isFreeformHome.collect { _isFreeformHome = it } }
            launch { dataStore.iconScale.collect { _iconScale = it } }
            launch { dataStore.isHideLabels.collect { _isHideLabels = it } }
            launch { dataStore.isLiquidGlass.collect { _isLiquidGlass = it } }
            launch { dataStore.isDarkenWallpaper.collect { _isDarkenWallpaper = it } }
            launch { dataStore.themeMode.collect { _themeMode = it } }
            launch { dataStore.drawerOpenCount.collect { _drawerOpenCount = it } }
            launch { dataStore.lastDefaultPromptTimestamp.collect { _lastDefaultPromptTimestamp = it } }
            launch { dataStore.defaultPromptCount.collect { _defaultPromptCount = it } }
            launch { dataStore.pageCount.collect { _pageCount = it } }
        }
    }

    var columns: Int
        get() = _columns
        set(value) {
            _columns = value
            scope.launch { dataStore.setColumns(value) }
            prefs.edit { putInt(KEY_COLUMNS, value) }
        }

    var widgetId: Int
        get() = _widgetId
        set(value) {
            _widgetId = value
            scope.launch { dataStore.setWidgetId(value) }
            prefs.edit { putInt(KEY_WIDGET_ID, value) }
        }

    var isFreeformHome: Boolean
        get() = _isFreeformHome
        set(value) {
            _isFreeformHome = value
            scope.launch { dataStore.setFreeformHome(value) }
            prefs.edit { putBoolean(KEY_FREEFORM_HOME, value) }
        }

    var iconScale: Float
        get() = _iconScale
        set(value) {
            _iconScale = value
            scope.launch { dataStore.setIconScale(value) }
            prefs.edit { putFloat(KEY_ICON_SCALE, value) }
        }

    var isHideLabels: Boolean
        get() = _isHideLabels
        set(value) {
            _isHideLabels = value
            scope.launch { dataStore.setHideLabels(value) }
            prefs.edit { putBoolean(KEY_HIDE_LABELS, value) }
        }

    var isLiquidGlass: Boolean
        get() = _isLiquidGlass
        set(value) {
            _isLiquidGlass = value
            scope.launch { dataStore.setLiquidGlass(value) }
            prefs.edit { putBoolean(KEY_LIQUID_GLASS, value) }
        }

    var isDarkenWallpaper: Boolean
        get() = _isDarkenWallpaper
        set(value) {
            _isDarkenWallpaper = value
            scope.launch { dataStore.setDarkenWallpaper(value) }
            prefs.edit { putBoolean(KEY_DARKEN_WALLPAPER, value) }
        }

    var themeMode: String?
        get() = _themeMode
        set(value) {
            if (value != null) {
                _themeMode = value
                scope.launch { dataStore.setThemeMode(value) }
            }
            prefs.edit { putString(KEY_THEME_MODE, value) }
        }

    fun incrementUsage(packageName: String) {
        scope.launch { dataStore.incrementUsage(packageName) }
        val current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
        prefs.edit { putInt(KEY_USAGE_PREFIX + packageName, current + 1) }
    }

    fun getUsage(packageName: String): Int {
        // This one is harder because it's dynamic.
        // For simplicity and since usage is not critical, we can use the prefs value or a separate cache.
        // But the requirement is to remove runBlocking.
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
    }

    var drawerOpenCount: Int
        get() = _drawerOpenCount
        private set(value) { }

    fun incrementDrawerOpenCount() {
        scope.launch { dataStore.incrementDrawerOpenCount() }
        val current = prefs.getInt(KEY_DRAWER_OPEN_COUNT, 0)
        prefs.edit { putInt(KEY_DRAWER_OPEN_COUNT, current + 1) }
    }

    var lastDefaultPromptTimestamp: Long
        get() = _lastDefaultPromptTimestamp
        set(value) {
            _lastDefaultPromptTimestamp = value
            scope.launch { dataStore.setLastDefaultPromptTimestamp(value) }
            prefs.edit { putLong(KEY_DEFAULT_PROMPT_TIMESTAMP, value) }
        }

    var defaultPromptCount: Int
        get() = _defaultPromptCount
        private set(value) { }

    fun incrementDefaultPromptCount() {
        scope.launch { dataStore.incrementDefaultPromptCount() }
        val current = prefs.getInt(KEY_DEFAULT_PROMPT_COUNT, 0)
        prefs.edit { putInt(KEY_DEFAULT_PROMPT_COUNT, current + 1) }
    }

    private fun getHomeFile(): File {
        val dir = File(context.filesDir, "layout")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "home_layout.json")
    }

    private fun getPageFile(pageIndex: Int): File {
        val dir = File(context.filesDir, "layout")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "home_page_$pageIndex.json")
    }

    private fun writeToFile(file: File, data: String) {
        try {
            FileOutputStream(file).use { out ->
                out.write(data.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (ignored: IOException) {
        }
    }

    private fun readFromFile(file: File): String? {
        if (!file.exists()) return null
        return try {
            FileInputStream(file).use { `in` ->
                val bytes = ByteArray(file.length().toInt())
                `in`.read(bytes)
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        }
    }

    fun savePageCount(count: Int) {
        _pageCount = count
        scope.launch { dataStore.setPageCount(count) }
        prefs.edit { putInt("page_count", count) }
    }

    fun savePageItems(pageIndex: Int, items: List<HomeItem>?) {
        saveHomeItems(items, pageCount)
    }

    fun getPageItems(pageIndex: Int): List<HomeItem> {
        return getHomeItems().filter { it.page == pageIndex }
    }

    fun removePageData(index: Int, oldPageCount: Int) {
        savePageCount(oldPageCount - 1)
        val items = getHomeItems()
        val iterator = items.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().page == index) {
                iterator.remove()
            }
        }
        for (item in items) {
            if (item.page > index) item.page--
        }
        saveHomeItems(items, oldPageCount - 1)
    }

    fun saveHomeItems(items: List<HomeItem>?, pageCount: Int) {
        if (items == null) return
        val array = JSONArray()
        for (item in items) {
            val obj = serializeItem(item)
            if (obj != null) array.put(obj)
        }
        writeToFile(getHomeFile(), array.toString())

        prefs.edit {
            putInt("page_count", pageCount)
            remove(KEY_HOME_ITEMS)
        }
    }

    private fun serializeItem(item: HomeItem): JSONObject? {
        val obj = JSONObject()
        return try {
            obj.put("type", item.type?.name)
            obj.put("packageName", item.packageName)
            obj.put("className", item.className)
            obj.put("col", item.col.toDouble())
            obj.put("row", item.row.toDouble())
            obj.put("spanX", item.spanX.toDouble())
            obj.put("spanY", item.spanY.toDouble())
            obj.put("page", item.page)
            obj.put("widgetId", item.widgetId)
            obj.put("rotation", item.rotation.toDouble())
            obj.put("scaleX", item.scaleX.toDouble())
            obj.put("scaleY", item.scaleY.toDouble())
            obj.put("tiltX", item.tiltX.toDouble())
            obj.put("tiltY", item.tiltY.toDouble())
            obj.put("folderName", item.folderName)
            if (item.folderItems != null) {
                val folderArray = JSONArray()
                for (subItem in item.folderItems!!) {
                    val subObj = serializeItem(subItem)
                    if (subObj != null) folderArray.put(subObj)
                }
                obj.put("folderItems", folderArray)
            }
            obj
        } catch (e: JSONException) {
            null
        }
    }

    fun getHomeItems(): MutableList<HomeItem> {
        val items = mutableListOf<HomeItem>()
        val homeFile = getHomeFile()
        if (homeFile.exists()) {
            val json = readFromFile(homeFile)
            if (json != null) {
                try {
                    val array = JSONArray(json)
                    for (i in 0 until array.length()) {
                        val item = deserializeItem(array.getJSONObject(i))
                        if (item != null) items.add(item)
                    }
                } catch (ignored: Exception) {
                }
            }
            return items
        }

        val pageCount = prefs.getInt("page_count", 0)
        if (pageCount > 0) {
            for (i in 0 until pageCount) {
                val pageFile = getPageFile(i)
                if (pageFile.exists()) {
                    val json = readFromFile(pageFile)
                    if (json != null) {
                        try {
                            val array = JSONArray(json)
                            for (j in 0 until array.length()) {
                                val item = deserializeItem(array.getJSONObject(j))
                                if (item != null) {
                                    item.page = i
                                    items.add(item)
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                saveHomeItems(items, pageCount)
            }
            return items
        }

        val json = prefs.getString(KEY_HOME_ITEMS, null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val item = deserializeItem(array.getJSONObject(i))
                    if (item != null) items.add(item)
                }
                saveHomeItems(items, 1)
            } catch (ignored: Exception) {
            }
        }
        return items
    }

    val pageCount: Int
        get() = _pageCount

    private fun deserializeItem(obj: JSONObject): HomeItem? {
        if (!obj.has("type")) return null
        val item = HomeItem()
        try {
            item.type = HomeItem.Type.valueOf(obj.getString("type"))
        } catch (e: Exception) {
            return null
        }
        item.packageName = obj.optString("packageName", "")
        item.className = obj.optString("className", "")
        if (obj.has("col")) {
            item.col = obj.optDouble("col", 0.0).toFloat()
        } else {
            item.col = (obj.optDouble("x", 0.0) / 100.0).toFloat()
        }
        if (obj.has("row")) {
            item.row = obj.optDouble("row", 0.0).toFloat()
        } else {
            item.row = (obj.optDouble("y", 0.0) / 100.0).toFloat()
        }
        item.spanX = obj.optDouble("spanX", obj.optDouble("width", 100.0) / 100.0).toFloat()
        item.spanY = obj.optDouble("spanY", obj.optDouble("height", 100.0) / 100.0).toFloat()
        if (item.spanX <= 0) item.spanX = 1f
        if (item.spanY <= 0) item.spanY = 1f
        item.page = obj.optInt("page", 0)
        item.widgetId = obj.optInt("widgetId", -1)
        item.rotation = obj.optDouble("rotation", 0.0).toFloat()
        if (obj.has("scaleX")) {
            item.scaleX = obj.optDouble("scaleX", 1.0).toFloat()
            item.scaleY = obj.optDouble("scaleY", 1.0).toFloat()
        } else {
            val s = obj.optDouble("scale", 1.0).toFloat()
            item.scaleX = s
            item.scaleY = s
        }
        item.tiltX = obj.optDouble("tiltX", 0.0).toFloat()
        item.tiltY = obj.optDouble("tiltY", 0.0).toFloat()
        item.folderName = if (obj.has("folderName")) obj.optString("folderName") else null
        if (obj.has("folderItems")) {
            item.folderItems = mutableListOf()
            val folderArray = obj.optJSONArray("folderItems")
            if (folderArray != null) {
                for (i in 0 until folderArray.length()) {
                    val subItem = deserializeItem(folderArray.optJSONObject(i))
                    if (subItem != null) item.folderItems!!.add(subItem)
                }
            }
        }
        return item
    }

    fun onDestroy() {
        scope.cancel()
    }

    companion object {
        private const val PREFS_NAME = "riprog_launcher_prefs"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_WIDGET_ID = "widget_id"
        private const val KEY_USAGE_PREFIX = "usage_"
        private const val KEY_HOME_ITEMS = "home_items"
        private const val KEY_FREEFORM_HOME = "freeform_home"
        private const val KEY_ICON_SCALE = "icon_scale"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_HIDE_LABELS = "hide_labels"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_DARKEN_WALLPAPER = "darken_wallpaper"
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
    }
}
