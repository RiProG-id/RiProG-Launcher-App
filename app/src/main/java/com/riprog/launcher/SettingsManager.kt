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
                obj.put("rotation", item.rotation.toDouble())
                obj.put("scale", item.scale.toDouble())
                obj.put("tiltX", item.tiltX.toDouble())
                obj.put("tiltY", item.tiltY.toDouble())
                array.put(obj)
            } catch (ignored: JSONException) {
            }
        }
        prefs.edit().putString(KEY_HOME_ITEMS, array.toString()).apply()
    }

    fun getHomeItems(): MutableList<HomeItem> {
        val items: MutableList<HomeItem> = ArrayList()
        val json = prefs.getString(KEY_HOME_ITEMS, null) ?: return items
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (!obj.has("type")) continue
                val item = HomeItem()
                try {
                    item.type = HomeItem.Type.valueOf(obj.getString("type"))
                } catch (e: Exception) {
                    continue
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
                item.scale = obj.optDouble("scale", 1.0).toFloat()
                item.tiltX = obj.optDouble("tiltX", 0.0).toFloat()
                item.tiltY = obj.optDouble("tiltY", 0.0).toFloat()
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
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_DARKEN_WALLPAPER = "darken_wallpaper"
        private const val KEY_HIDE_LABELS = "hide_labels"
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
    }
}
