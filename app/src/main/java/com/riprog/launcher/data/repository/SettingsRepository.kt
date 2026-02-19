package com.riprog.launcher.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.riprog.launcher.data.model.LauncherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val USER_PREFERENCES_NAME = "launcher_settings"
private const val OLD_PREFS_NAME = "riprog_launcher_prefs"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, OLD_PREFS_NAME))
    }
)

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
