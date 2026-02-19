package com.riprog.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.riprog.launcher.data.model.LauncherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val USER_PREFERENCES_NAME = "launcher_settings"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = USER_PREFERENCES_NAME)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
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
        val MIGRATED = booleanPreferencesKey("settings_migrated")
    }

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            LauncherSettings(
                columns = preferences[PreferencesKeys.COLUMNS] ?: 4,
                widgetId = preferences[PreferencesKeys.WIDGET_ID] ?: -1,
                isFreeformHome = preferences[PreferencesKeys.FREEFORM_HOME] ?: false,
                iconScale = preferences[PreferencesKeys.ICON_SCALE] ?: 1.0f,
                isHideLabels = preferences[PreferencesKeys.HIDE_LABELS] ?: false,
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "system",
                isLiquidGlass = preferences[PreferencesKeys.LIQUID_GLASS] ?: false,
                isDarkenWallpaper = preferences[PreferencesKeys.DARKEN_WALLPAPER] ?: false,
                drawerOpenCount = preferences[PreferencesKeys.DRAWER_OPEN_COUNT] ?: 0,
                lastDefaultPromptTimestamp = preferences[PreferencesKeys.DEFAULT_PROMPT_TIMESTAMP] ?: 0L,
                defaultPromptCount = preferences[PreferencesKeys.DEFAULT_PROMPT_COUNT] ?: 0
            )
        }

    suspend fun checkAndMigrate() {
        val prefs = context.dataStore.data.first()
        if (prefs[PreferencesKeys.MIGRATED] == true) return

        val oldPrefs = context.getSharedPreferences("riprog_launcher_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) return

        context.dataStore.edit { it ->
            if (oldPrefs.contains("columns")) it[PreferencesKeys.COLUMNS] = oldPrefs.getInt("columns", 4)
            if (oldPrefs.contains("widget_id")) it[PreferencesKeys.WIDGET_ID] = oldPrefs.getInt("widget_id", -1)
            if (oldPrefs.contains("freeform_home")) it[PreferencesKeys.FREEFORM_HOME] = oldPrefs.getBoolean("freeform_home", false)
            if (oldPrefs.contains("icon_scale")) it[PreferencesKeys.ICON_SCALE] = oldPrefs.getFloat("icon_scale", 1.0f)
            if (oldPrefs.contains("hide_labels")) it[PreferencesKeys.HIDE_LABELS] = oldPrefs.getBoolean("hide_labels", false)
            if (oldPrefs.contains("theme_mode")) it[PreferencesKeys.THEME_MODE] = oldPrefs.getString("theme_mode", "system") ?: "system"
            if (oldPrefs.contains("liquid_glass")) it[PreferencesKeys.LIQUID_GLASS] = oldPrefs.getBoolean("liquid_glass", false)
            if (oldPrefs.contains("darken_wallpaper")) it[PreferencesKeys.DARKEN_WALLPAPER] = oldPrefs.getBoolean("darken_wallpaper", false)
            if (oldPrefs.contains("drawer_open_count")) it[PreferencesKeys.DRAWER_OPEN_COUNT] = oldPrefs.getInt("drawer_open_count", 0)
            if (oldPrefs.contains("default_prompt_ts")) it[PreferencesKeys.DEFAULT_PROMPT_TIMESTAMP] = oldPrefs.getLong("default_prompt_ts", 0L)
            if (oldPrefs.contains("default_prompt_count")) it[PreferencesKeys.DEFAULT_PROMPT_COUNT] = oldPrefs.getInt("default_prompt_count", 0)

            it[PreferencesKeys.MIGRATED] = true
        }
    }

    suspend fun updateColumns(columns: Int) {
        context.dataStore.edit { it[PreferencesKeys.COLUMNS] = columns }
    }

    suspend fun updateWidgetId(id: Int) {
        context.dataStore.edit { it[PreferencesKeys.WIDGET_ID] = id }
    }

    suspend fun updateFreeformHome(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FREEFORM_HOME] = enabled }
    }

    suspend fun updateIconScale(scale: Float) {
        context.dataStore.edit { it[PreferencesKeys.ICON_SCALE] = scale }
    }

    suspend fun updateHideLabels(hide: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIDE_LABELS] = hide }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    suspend fun updateLiquidGlass(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.LIQUID_GLASS] = enabled }
    }

    suspend fun updateDarkenWallpaper(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DARKEN_WALLPAPER] = enabled }
    }

    suspend fun incrementDrawerOpenCount() {
        context.dataStore.edit {
            val current = it[PreferencesKeys.DRAWER_OPEN_COUNT] ?: 0
            it[PreferencesKeys.DRAWER_OPEN_COUNT] = current + 1
        }
    }

    suspend fun updateDefaultPrompt(timestamp: Long, count: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.DEFAULT_PROMPT_TIMESTAMP] = timestamp
            it[PreferencesKeys.DEFAULT_PROMPT_COUNT] = count
        }
    }

    suspend fun incrementUsage(packageName: String) {
        val key = intPreferencesKey("usage_$packageName")
        context.dataStore.edit {
            val current = it[key] ?: 0
            it[key] = current + 1
        }
    }

    fun getUsage(packageName: String): Flow<Int> {
        val key = intPreferencesKey("usage_$packageName")
        return context.dataStore.data.map { it[key] ?: 0 }
    }
}
