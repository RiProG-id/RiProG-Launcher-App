package com.riprog.launcher.logic.managers

import com.riprog.launcher.data.model.HomeItem

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_FIRST_RUN)) {
            prefs.edit().apply {
                putString(KEY_THEME_MODE, "light")
                putBoolean(KEY_LIQUID_GLASS, true)
                putBoolean(KEY_DARKEN_WALLPAPER, true)
                putBoolean(KEY_FREEFORM_HOME, false)
                putBoolean(KEY_HIDE_LABELS, false)
                putInt(KEY_PAGE_COUNT, 1)
                putBoolean(KEY_FIRST_RUN, true)
                apply()
            }
        }
    }

    var columns: Int
        get() = prefs.getInt(KEY_COLUMNS, 4)
        set(columns) {
            prefs.edit().putInt(KEY_COLUMNS, columns).apply()
        }

    var isFreeformHome: Boolean
        get() = prefs.getBoolean(KEY_FREEFORM_HOME, false)
        set(freeform) {
            prefs.edit().putBoolean(KEY_FREEFORM_HOME, freeform).apply()
        }

    val iconScale: Float
        get() = 1.12f

    var isHideLabels: Boolean
        get() = prefs.getBoolean(KEY_HIDE_LABELS, false)
        set(hide) {
            prefs.edit().putBoolean(KEY_HIDE_LABELS, hide).apply()
        }

    var themeMode: String?
        get() = prefs.getString(KEY_THEME_MODE, "system")
        set(mode) {
            prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        }

    var isLiquidGlass: Boolean
        get() = prefs.getBoolean(KEY_LIQUID_GLASS, false)
        set(enabled) {
            prefs.edit().putBoolean(KEY_LIQUID_GLASS, enabled).apply()
        }

    var isDarkenWallpaper: Boolean
        get() = prefs.getBoolean(KEY_DARKEN_WALLPAPER, false)
        set(enabled) {
            prefs.edit().putBoolean(KEY_DARKEN_WALLPAPER, enabled).apply()
        }

    var pageCount: Int
        get() = prefs.getInt(KEY_PAGE_COUNT, 2)
        set(count) {
            prefs.edit().putInt(KEY_PAGE_COUNT, count).apply()
        }

    fun incrementUsage(packageName: String) {
        val current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + 1).apply()
    }

    fun getUsage(packageName: String): Int {
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
    }

    var drawerOpenCount: Int
        get() = prefs.getInt(KEY_DRAWER_OPEN_COUNT, 0)
        set(count) {
            prefs.edit().putInt(KEY_DRAWER_OPEN_COUNT, count).apply()
        }

    fun incrementDrawerOpenCount() {
        drawerOpenCount = drawerOpenCount + 1
    }

    var lastDefaultPromptTimestamp: Long
        get() = prefs.getLong(KEY_DEFAULT_PROMPT_TIMESTAMP, 0)
        set(ts) {
            prefs.edit().putLong(KEY_DEFAULT_PROMPT_TIMESTAMP, ts).apply()
        }

    var defaultPromptCount: Int
        get() = prefs.getInt(KEY_DEFAULT_PROMPT_COUNT, 0)
        set(count) {
            prefs.edit().putInt(KEY_DEFAULT_PROMPT_COUNT, count).apply()
        }

    fun incrementDefaultPromptCount() {
        defaultPromptCount = defaultPromptCount + 1
    }

    fun saveHomeItems(items: List<HomeItem>) {
        try {
            val array = JSONArray()
            for (item in items) {
                array.put(serializeItem(item))
            }
            val json = array.toString()
            if (json.isNotEmpty() && json != "[]") {
                prefs.edit().putString(KEY_HOME_ITEMS, json).apply()
            } else if (items.isEmpty()) {
                prefs.edit().putString(KEY_HOME_ITEMS, "[]").apply()
            }
        } catch (e: Exception) {
            // Prevent partial save if serialization fails
        }
    }

    private fun serializeItem(item: HomeItem): JSONObject {
        val obj = JSONObject()
        try {
            if (item.type == null) return obj
            obj.put("type", item.type?.name)
            obj.put("packageName", item.packageName)
            obj.put("className", item.className)
            obj.put("folderName", item.folderName)
            obj.put("col", item.col.toDouble())
            obj.put("row", item.row.toDouble())
            obj.put("spanX", item.spanX.toDouble())
            obj.put("spanY", item.spanY.toDouble())
            obj.put("page", item.page)
            obj.put("originalCol", item.originalCol.toDouble())
            obj.put("originalRow", item.originalRow.toDouble())
            obj.put("originalSpanX", item.originalSpanX.toDouble())
            obj.put("originalSpanY", item.originalSpanY.toDouble())
            obj.put("originalPage", item.originalPage)
            obj.put("widgetId", item.widgetId)
            obj.put("rotation", item.rotation.toDouble())
            obj.put("scale", item.scale.toDouble())
            obj.put("tiltX", item.tiltX.toDouble())
            obj.put("tiltY", item.tiltY.toDouble())
            obj.put("visualOffsetX", item.visualOffsetX.toDouble())
            obj.put("visualOffsetY", item.visualOffsetY.toDouble())

            if (item.folderItems.isNotEmpty()) {
                val folderArray = JSONArray()
                for (subItem in item.folderItems) {
                    val serializedSub = serializeItem(subItem)
                    if (serializedSub.has("type")) {
                        folderArray.put(serializedSub)
                    }
                }
                obj.put("folderItems", folderArray)
            }
        } catch (ignored: Exception) {
        }
        return obj
    }

    fun getHomeItems(): MutableList<HomeItem> {
        val items: MutableList<HomeItem> = ArrayList()
        val json = prefs.getString(KEY_HOME_ITEMS, null) ?: return items
        if (json.isEmpty() || json == "[]") return items

        try {
            val array = JSONArray(json)
            val tempItems = ArrayList<HomeItem>()
            for (i in 0 until array.length()) {
                val item = deserializeItem(array.getJSONObject(i))
                if (item != null) {
                    tempItems.add(item)
                } else {
                    // Critical failure in one item, but we should try to recover others
                }
            }
            items.addAll(tempItems)
        } catch (e: Exception) {
            // Return empty list only if the entire JSON is corrupt
        }
        return items
    }

    private fun deserializeItem(obj: JSONObject): HomeItem? {
        if (!obj.has("type")) return null
        val item = HomeItem()
        try {
            item.type = HomeItem.Type.valueOf(obj.getString("type"))
        } catch (e: Exception) {
            return null
        }
        item.packageName = if (obj.has("packageName")) obj.getString("packageName") else null
        item.className = if (obj.has("className")) obj.getString("className") else null
        item.folderName = if (obj.has("folderName")) obj.getString("folderName") else null

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

        item.spanX = obj.optDouble("spanX", (obj.optInt("width", 100) / 100).toDouble()).toFloat()
        item.spanY = obj.optDouble("spanY", (obj.optInt("height", 100) / 100).toDouble()).toFloat()
        if (item.spanX <= 0) item.spanX = 1f
        if (item.spanY <= 0) item.spanY = 1f
        item.page = obj.optInt("page", 0)

        // Recovery: ensure items are not placed on impossible pages/positions
        if (item.page < 0) item.page = 0
        if (item.col < -1) item.col = 0f
        if (item.row < -1) item.row = 0f

        item.originalCol = obj.optDouble("originalCol", item.col.toDouble()).toFloat()
        item.originalRow = obj.optDouble("originalRow", item.row.toDouble()).toFloat()
        item.originalSpanX = obj.optDouble("originalSpanX", item.spanX.toDouble()).toFloat()
        item.originalSpanY = obj.optDouble("originalSpanY", item.spanY.toDouble()).toFloat()
        item.originalPage = obj.optInt("originalPage", item.page)

        item.widgetId = obj.optInt("widgetId", -1)
        item.rotation = obj.optDouble("rotation", 0.0).toFloat()
        item.scale = obj.optDouble("scale", 1.0).toFloat()
        item.tiltX = obj.optDouble("tiltX", 0.0).toFloat()
        item.tiltY = obj.optDouble("tiltY", 0.0).toFloat()
        item.visualOffsetX = obj.optDouble("visualOffsetX", -1.0).toFloat()
        item.visualOffsetY = obj.optDouble("visualOffsetY", -1.0).toFloat()

        if (obj.has("folderItems")) {
            val folderArray = obj.getJSONArray("folderItems")
            for (i in 0 until folderArray.length()) {
                val subItem = deserializeItem(folderArray.getJSONObject(i))
                if (subItem != null) item.folderItems.add(subItem)
            }
        }

        // Final validation for specific types
        return when (item.type) {
            HomeItem.Type.APP -> if (item.packageName == null) null else item
            HomeItem.Type.WIDGET -> if (item.widgetId == -1) null else item
            HomeItem.Type.CLOCK -> item
            HomeItem.Type.FOLDER -> if (item.folderItems.isEmpty()) null else item
            null -> null
        }
    }

    companion object {
        private const val PREFS_NAME = "riprog_launcher_prefs"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_USAGE_PREFIX = "usage_"
        private const val KEY_HOME_ITEMS = "home_items"
        private const val KEY_FREEFORM_HOME = "freeform_home"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_DARKEN_WALLPAPER = "darken_wallpaper"
        private const val KEY_HIDE_LABELS = "hide_labels"
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
        private const val KEY_FIRST_RUN = "first_run_init"
        private const val KEY_PAGE_COUNT = "page_count"
    }
}
