package com.riprog.launcher.logic.managers

import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.theme.ThemeStyle

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
                putString(KEY_THEME_STYLE, ThemeStyle.STANDARD.name)
                putBoolean(KEY_MATERIAL_YOU_ICONS, false)
                putBoolean(KEY_DARKEN_WALLPAPER, false)
                putBoolean(KEY_FREEFORM_HOME, false)
                putBoolean(KEY_HIDE_LABELS, false)
                putBoolean(KEY_FIRST_RUN, true)
                apply()
            }
        }

        // Migration from old Liquid Glass toggle
        if (!prefs.contains(KEY_THEME_STYLE)) {
            val isLiquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, false)
            val style = if (isLiquidGlass) ThemeStyle.LIQUID_GLASS.name else ThemeStyle.STANDARD.name
            prefs.edit().putString(KEY_THEME_STYLE, style).apply()
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

    var themeStyle: ThemeStyle
        get() {
            val name = prefs.getString(KEY_THEME_STYLE, ThemeStyle.STANDARD.name)
            return try {
                ThemeStyle.valueOf(name!!)
            } catch (e: Exception) {
                ThemeStyle.STANDARD
            }
        }
        set(style) {
            prefs.edit().putString(KEY_THEME_STYLE, style.name).apply()
        }

    var isMaterialYouIcons: Boolean
        get() = prefs.getBoolean(KEY_MATERIAL_YOU_ICONS, false)
        set(enabled) {
            prefs.edit().putBoolean(KEY_MATERIAL_YOU_ICONS, enabled).apply()
        }

    val isLiquidGlass: Boolean
        get() = themeStyle == ThemeStyle.LIQUID_GLASS

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
            obj.put("spanX", item.spanX)
            obj.put("spanY", item.spanY)
            obj.put("page", item.page)
            obj.put("widgetId", item.widgetId)
            obj.put("rotation", item.rotation.toDouble())
            obj.put("scale", item.scale.toDouble())
            obj.put("tiltX", item.tiltX.toDouble())
            obj.put("tiltY", item.tiltY.toDouble())

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
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_MATERIAL_YOU_ICONS = "material_you_icons"
        private const val KEY_LIQUID_GLASS = "liquid_glass"
        private const val KEY_DARKEN_WALLPAPER = "darken_wallpaper"
        private const val KEY_HIDE_LABELS = "hide_labels"
        private const val KEY_DRAWER_OPEN_COUNT = "drawer_open_count"
        private const val KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts"
        private const val KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count"
        private const val KEY_FIRST_RUN = "first_run_init"
    }
}
