package com.riprog.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var columns: Int
        get() = prefs.getInt(KEY_COLUMNS, 4)
        set(columns) {
            prefs.edit().putInt(KEY_COLUMNS, columns).apply()
        }

    var widgetId: Int
        get() = prefs.getInt(KEY_WIDGET_ID, -1)
        set(widgetId) {
            prefs.edit().putInt(KEY_WIDGET_ID, widgetId).apply()
        }

    var isFreeformHome: Boolean
        get() = prefs.getBoolean(KEY_FREEFORM_HOME, false)
        set(freeform) {
            prefs.edit().putBoolean(KEY_FREEFORM_HOME, freeform).apply()
        }

    var iconScale: Float
        get() = prefs.getFloat(KEY_ICON_SCALE, 1.0f)
        set(scale) {
            prefs.edit().putFloat(KEY_ICON_SCALE, scale).apply()
        }

    var themeMode: String?
        get() = prefs.getString(KEY_THEME_MODE, "system")
        set(mode) {
            prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        }

    fun incrementUsage(packageName: String) {
        val current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + 1).apply()
    }

    fun getUsage(packageName: String): Int {
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
    }

    val drawerOpenCount: Int
        get() = prefs.getInt(KEY_DRAWER_OPEN_COUNT, 0)

    fun incrementDrawerOpenCount() {
        prefs.edit().putInt(KEY_DRAWER_OPEN_COUNT, drawerOpenCount + 1).apply()
    }

    var lastDefaultPromptTimestamp: Long
        get() = prefs.getLong(KEY_DEFAULT_PROMPT_TIMESTAMP, 0)
        set(ts) {
            prefs.edit().putLong(KEY_DEFAULT_PROMPT_TIMESTAMP, ts).apply()
        }

    val defaultPromptCount: Int
        get() = prefs.getInt(KEY_DEFAULT_PROMPT_COUNT, 0)

    fun incrementDefaultPromptCount() {
        prefs.edit().putInt(KEY_DEFAULT_PROMPT_COUNT, defaultPromptCount + 1).apply()
    }

    fun saveHomeItems(items: List<HomeItem>) {
        val array = JSONArray()
        for (item in items) {
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
                array.put(obj)
            } catch (ignored: JSONException) {
            }
        }
        prefs.edit().putString(KEY_HOME_ITEMS, array.toString()).apply()
    }

    fun getHomeItems(): List<HomeItem> {
        val items: MutableList<HomeItem> = ArrayList()
        val json = prefs.getString(KEY_HOME_ITEMS, null) ?: return items
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = HomeItem()
                item.type = HomeItem.Type.valueOf(obj.getString("type"))
                item.packageName = if (obj.has("packageName")) obj.getString("packageName") else null
                item.className = if (obj.has("className")) obj.getString("className") else null
                // Fallback to x/y if col/row missing (for migration)
                item.col = obj.optDouble("col", obj.optDouble("x", 0.0) / 100.0).toFloat()
                item.row = obj.optDouble("row", obj.optDouble("y", 0.0) / 100.0).toFloat()
                item.spanX = obj.optInt("spanX", obj.optInt("width", 100) / 100)
                item.spanY = obj.optInt("spanY", obj.optInt("height", 100) / 100)
                if (item.spanX <= 0) item.spanX = 1
                if (item.spanY <= 0) item.spanY = 1
                item.page = obj.getInt("page")
                item.widgetId = obj.getInt("widgetId")
                items.add(item)
            }
        } catch (ignored: Exception) {
        }
        return items
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
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
    }
}
