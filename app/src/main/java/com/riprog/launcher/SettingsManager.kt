package com.riprog.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var columns: Int
        get() = prefs.getInt(KEY_COLUMNS, 4)
        set(value) = prefs.edit().putInt(KEY_COLUMNS, value).apply()

    var widgetId: Int
        get() = prefs.getInt(KEY_WIDGET_ID, -1)
        set(value) = prefs.edit().putInt(KEY_WIDGET_ID, value).apply()

    var isFreeformHome: Boolean
        get() = prefs.getBoolean(KEY_FREEFORM_HOME, false)
        set(value) = prefs.edit().putBoolean(KEY_FREEFORM_HOME, value).apply()

    var iconScale: Float
        get() = prefs.getFloat(KEY_ICON_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ICON_SCALE, value).apply()

    var isHideLabels: Boolean
        get() = prefs.getBoolean(KEY_HIDE_LABELS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_LABELS, value).apply()

    var isLiquidGlass: Boolean
        get() = prefs.getBoolean(KEY_LIQUID_GLASS, true)
        set(value) = prefs.edit().putBoolean(KEY_LIQUID_GLASS, value).apply()

    var themeMode: String?
        get() = prefs.getString(KEY_THEME_MODE, "system")
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    fun incrementUsage(packageName: String) {
        val current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + 1).apply()
    }

    fun getUsage(packageName: String): Int {
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
    }

    var drawerOpenCount: Int
        get() = prefs.getInt(KEY_DRAWER_OPEN_COUNT, 0)
        private set(value) = prefs.edit().putInt(KEY_DRAWER_OPEN_COUNT, value).apply()

    fun incrementDrawerOpenCount() {
        drawerOpenCount++
    }

    var lastDefaultPromptTimestamp: Long
        get() = prefs.getLong(KEY_DEFAULT_PROMPT_TIMESTAMP, 0)
        set(value) = prefs.edit().putLong(KEY_DEFAULT_PROMPT_TIMESTAMP, value).apply()

    var defaultPromptCount: Int
        get() = prefs.getInt(KEY_DEFAULT_PROMPT_COUNT, 0)
        private set(value) = prefs.edit().putInt(KEY_DEFAULT_PROMPT_COUNT, value).apply()

    fun incrementDefaultPromptCount() {
        prefs.edit().putInt(KEY_DEFAULT_PROMPT_COUNT, defaultPromptCount + 1).apply()
    }

    fun saveHomeItems(items: List<HomeItem>) {
        val array = JSONArray()
        for (item in items) {
            val obj = serializeItem(item)
            if (obj != null) array.put(obj)
        }
        prefs.edit().putString(KEY_HOME_ITEMS, array.toString()).apply()
    }

    private fun serializeItem(item: HomeItem): JSONObject? {
        val obj = JSONObject()
        try {
            obj.put("type", item.type?.name)
            obj.put("packageName", item.packageName)
            obj.put("className", item.className)
            obj.put("col", item.col.toDouble())
            obj.put("row", item.row.toDouble())
            obj.put("spanX", item.spanX)
            obj.put("spanY", item.spanY)
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
            return obj
        } catch (e: JSONException) {
            return null
        }
    }

    fun getHomeItems(): MutableList<HomeItem> {
        val items = mutableListOf<HomeItem>()
        val json = prefs.getString(KEY_HOME_ITEMS, null) ?: return items
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = deserializeItem(array.getJSONObject(i))
                if (item != null) items.add(item)
            }
        } catch (ignored: Exception) {
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
        item.spanX = obj.optInt("spanX", obj.optInt("width", 100) / 100)
        item.spanY = obj.optInt("spanY", obj.optInt("height", 100) / 100)
        if (item.spanX <= 0) item.spanX = 1
        if (item.spanY <= 0) item.spanY = 1
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
        item.folderName = obj.optString("folderName", null)
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
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
    }
}
