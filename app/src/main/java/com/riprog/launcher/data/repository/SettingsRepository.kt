package com.riprog.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "riprog_launcher_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "riprog_launcher_prefs"))
    }
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val COLUMNS = intPreferencesKey("columns")
        val WIDGET_ID = intPreferencesKey("widget_id")
        val FREEFORM_HOME = booleanPreferencesKey("freeform_home")
        val ICON_SCALE = floatPreferencesKey("icon_scale")
        val HIDE_LABELS = booleanPreferencesKey("hide_labels")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
        val DARKEN_WALLPAPER = booleanPreferencesKey("darken_wallpaper")
        val DRAWER_OPEN_COUNT = intPreferencesKey("drawer_open_count")
        val DEFAULT_PROMPT_TIMESTAMP = longPreferencesKey("default_prompt_ts")
        val DEFAULT_PROMPT_COUNT = intPreferencesKey("default_prompt_count")
        val HOME_ITEMS_JSON = stringPreferencesKey("home_items")
    }

    val columns: Flow<Int> = context.settingsDataStore.data.map { it[Keys.COLUMNS] ?: 4 }
    val widgetId: Flow<Int> = context.settingsDataStore.data.map { it[Keys.WIDGET_ID] ?: -1 }
    val isFreeformHome: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.FREEFORM_HOME] ?: false }
    val iconScale: Flow<Float> = context.settingsDataStore.data.map { it[Keys.ICON_SCALE] ?: 1.0f }
    val isHideLabels: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.HIDE_LABELS] ?: false }
    val themeMode: Flow<String> = context.settingsDataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val isLiquidGlass: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.LIQUID_GLASS] ?: false }
    val isDarkenWallpaper: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DARKEN_WALLPAPER] ?: false }
    val drawerOpenCount: Flow<Int> = context.settingsDataStore.data.map { it[Keys.DRAWER_OPEN_COUNT] ?: 0 }
    val lastDefaultPromptTimestamp: Flow<Long> = context.settingsDataStore.data.map { it[Keys.DEFAULT_PROMPT_TIMESTAMP] ?: 0L }
    val defaultPromptCount: Flow<Int> = context.settingsDataStore.data.map { it[Keys.DEFAULT_PROMPT_COUNT] ?: 0 }

    suspend fun setColumns(columns: Int) {
        context.settingsDataStore.edit { it[Keys.COLUMNS] = columns }
    }

    suspend fun setWidgetId(widgetId: Int) {
        context.settingsDataStore.edit { it[Keys.WIDGET_ID] = widgetId }
    }

    suspend fun setFreeformHome(freeform: Boolean) {
        context.settingsDataStore.edit { it[Keys.FREEFORM_HOME] = freeform }
    }

    suspend fun setIconScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.ICON_SCALE] = scale }
    }

    suspend fun setHideLabels(hide: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIDE_LABELS] = hide }
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setLiquidGlass(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LIQUID_GLASS] = enabled }
    }

    suspend fun setDarkenWallpaper(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DARKEN_WALLPAPER] = enabled }
    }

    suspend fun incrementDrawerOpenCount() {
        context.settingsDataStore.edit {
            val current = it[Keys.DRAWER_OPEN_COUNT] ?: 0
            it[Keys.DRAWER_OPEN_COUNT] = current + 1
        }
    }

    suspend fun setLastDefaultPromptTimestamp(ts: Long) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_PROMPT_TIMESTAMP] = ts }
    }

    suspend fun incrementDefaultPromptCount() {
        context.settingsDataStore.edit {
            val current = it[Keys.DEFAULT_PROMPT_COUNT] ?: 0
            it[Keys.DEFAULT_PROMPT_COUNT] = current + 1
        }
    }

    fun getUsage(packageName: String): Flow<Int> {
        val key = intPreferencesKey("usage_$packageName")
        return context.settingsDataStore.data.map { it[key] ?: 0 }
    }

    suspend fun incrementUsage(packageName: String) {
        val key = intPreferencesKey("usage_$packageName")
        context.settingsDataStore.edit {
            val current = it[key] ?: 0
            it[key] = current + 1
        }
    }

    // For migration of home items
    suspend fun getHomeItemsJson(): String? {
        return context.settingsDataStore.data.first()[Keys.HOME_ITEMS_JSON]
    }

    suspend fun clearHomeItemsJson() {
        context.settingsDataStore.edit { it.remove(Keys.HOME_ITEMS_JSON) }
    }
}
