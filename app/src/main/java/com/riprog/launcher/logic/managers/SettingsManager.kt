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
                putString(KEY_THEME_MODE, "system")
                putBoolean(KEY_LIQUID_GLASS, false)
                putBoolean(KEY_DARKEN_WALLPAPER, false)
                putBoolean(KEY_FREEFORM_HOME, false)
                putBoolean(KEY_HIDE_LABELS, false)
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
            array.put(serializeItem(item))
        }
        prefs.edit().putString(KEY_HOME_ITEMS, array.toString()).apply()
    }

    private fun serializeItem(item: HomeItem): JSONObject {
        val obj = JSONObject()
        try {
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
            obj.put("lastVisualWidth", item.lastVisualWidth.toDouble())
            obj.put("lastVisualHeight", item.lastVisualHeight.toDouble())
            obj.put("lastSpanX", item.lastSpanX.toDouble())
            obj.put("lastSpanY", item.lastSpanY.toDouble())

            if (item.folderItems.isNotEmpty()) {
                val folderArray = JSONArray()
                for (subItem in item.folderItems) {
                    folderArray.put(serializeItem(subItem))
                }
                obj.put("folderItems", folderArray)
            }
        } catch (ignored: JSONException) {
        }
        return obj
    }

    fun getHomeItems(): MutableList<HomeItem> {
        val items: MutableList<HomeItem> = ArrayList()
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
        item.packageName = if (obj.has("packageName")) obj.optString("packageName") else null
        item.className = if (obj.has("className")) obj.optString("className") else null
        item.folderName = if (obj.has("folderName")) obj.optString("folderName") else null
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
        item.lastVisualWidth = obj.optDouble("lastVisualWidth", -1.0).toFloat()
        item.lastVisualHeight = obj.optDouble("lastVisualHeight", -1.0).toFloat()
        item.lastSpanX = obj.optDouble("lastSpanX", -1.0).toFloat()
        item.lastSpanY = obj.optDouble("lastSpanY", -1.0).toFloat()

        if (obj.has("folderItems")) {
            val folderArray = obj.getJSONArray("folderItems")
            for (i in 0 until folderArray.length()) {
                val subItem = deserializeItem(folderArray.getJSONObject(i))
                if (subItem != null) item.folderItems.add(subItem)
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
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_DARKEN_WALLPAPER = "darken_wallpaper"
        private const val KEY_HIDE_LABELS = "hide_labels"
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
        private const val KEY_FIRST_RUN = "first_run_init"
    }
}
