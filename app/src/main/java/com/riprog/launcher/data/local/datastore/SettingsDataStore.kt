package com.riprog.launcher.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsDataStore(private val context: Context) {

    private val dataStore = context.dataStore

    val columns: Flow<Int> = dataStore.data.map { it[KEY_COLUMNS] ?: 4 }
    val widgetId: Flow<Int> = dataStore.data.map { it[KEY_WIDGET_ID] ?: -1 }
    val isFreeformHome: Flow<Boolean> = dataStore.data.map { it[KEY_FREEFORM_HOME] ?: false }
    val iconScale: Flow<Float> = dataStore.data.map { it[KEY_ICON_SCALE] ?: 1.0f }
    val isHideLabels: Flow<Boolean> = dataStore.data.map { it[KEY_HIDE_LABELS] ?: false }
    val isLiquidGlass: Flow<Boolean> = dataStore.data.map { it[KEY_LIQUID_GLASS] ?: true }
    val isDarkenWallpaper: Flow<Boolean> = dataStore.data.map { it[KEY_DARKEN_WALLPAPER] ?: true }
    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val drawerOpenCount: Flow<Int> = dataStore.data.map { it[KEY_DRAWER_OPEN_COUNT] ?: 0 }
    val lastDefaultPromptTimestamp: Flow<Long> = dataStore.data.map { it[KEY_DEFAULT_PROMPT_TIMESTAMP] ?: 0L }
    val defaultPromptCount: Flow<Int> = dataStore.data.map { it[KEY_DEFAULT_PROMPT_COUNT] ?: 0 }
    val pageCount: Flow<Int> = dataStore.data.map { it[KEY_PAGE_COUNT] ?: 2 }

    suspend fun setColumns(value: Int) { dataStore.edit { it[KEY_COLUMNS] = value } }
    suspend fun setWidgetId(value: Int) { dataStore.edit { it[KEY_WIDGET_ID] = value } }
    suspend fun setFreeformHome(value: Boolean) { dataStore.edit { it[KEY_FREEFORM_HOME] = value } }
    suspend fun setIconScale(value: Float) { dataStore.edit { it[KEY_ICON_SCALE] = value } }
    suspend fun setHideLabels(value: Boolean) { dataStore.edit { it[KEY_HIDE_LABELS] = value } }
    suspend fun setLiquidGlass(value: Boolean) { dataStore.edit { it[KEY_LIQUID_GLASS] = value } }
    suspend fun setDarkenWallpaper(value: Boolean) { dataStore.edit { it[KEY_DARKEN_WALLPAPER] = value } }
    suspend fun setThemeMode(value: String) { dataStore.edit { it[KEY_THEME_MODE] = value } }
    suspend fun setPageCount(value: Int) { dataStore.edit { it[KEY_PAGE_COUNT] = value } }

    suspend fun incrementDrawerOpenCount() {
        dataStore.edit {
            val current = it[KEY_DRAWER_OPEN_COUNT] ?: 0
            it[KEY_DRAWER_OPEN_COUNT] = current + 1
        }
    }

    suspend fun setLastDefaultPromptTimestamp(value: Long) { dataStore.edit { it[KEY_DEFAULT_PROMPT_TIMESTAMP] = value } }

    suspend fun incrementDefaultPromptCount() {
        dataStore.edit {
            val current = it[KEY_DEFAULT_PROMPT_COUNT] ?: 0
            it[KEY_DEFAULT_PROMPT_COUNT] = current + 1
        }
    }

    fun getUsage(packageName: String): Flow<Int> {
        return dataStore.data.map { it[intPreferencesKey("usage_$packageName")] ?: 0 }
    }

    suspend fun incrementUsage(packageName: String) {
        dataStore.edit {
            val key = intPreferencesKey("usage_$packageName")
            val current = it[key] ?: 0
            it[key] = current + 1
        }
    }

    companion object {
        private val KEY_COLUMNS = intPreferencesKey("columns")
        private val KEY_WIDGET_ID = intPreferencesKey("widget_id")
        private val KEY_FREEFORM_HOME = booleanPreferencesKey("freeform_home")
        private val KEY_ICON_SCALE = floatPreferencesKey("icon_scale")
        private val KEY_HIDE_LABELS = booleanPreferencesKey("hide_labels")
        private val KEY_LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
        private val KEY_DARKEN_WALLPAPER = booleanPreferencesKey("darken_wallpaper")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DRAWER_OPEN_COUNT = intPreferencesKey("drawer_open_count")
        private val KEY_DEFAULT_PROMPT_TIMESTAMP = longPreferencesKey("default_prompt_ts")
        private val KEY_DEFAULT_PROMPT_COUNT = intPreferencesKey("default_prompt_count")
        private val KEY_PAGE_COUNT = intPreferencesKey("page_count")
    }
}
