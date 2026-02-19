package com.riprog.launcher.logic.managers

import android.content.Context
import com.riprog.launcher.LauncherApplication
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.LauncherSettings
import com.riprog.launcher.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest

class SettingsManager(context: Context) {
    private val settingsRepository: SettingsRepository = (context.applicationContext as LauncherApplication).settingsRepository
    private var cachedSettings = LauncherSettings()
    private var usageCache = mutableMapOf<String, Int>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        scope.launch {
            settingsRepository.settingsFlow.collectLatest {
                cachedSettings = it
            }
        }
    }

    var columns: Int
        get() = cachedSettings.columns
        set(value) {
            scope.launch { settingsRepository.updateColumns(value) }
        }

    var widgetId: Int
        get() = cachedSettings.widgetId
        set(value) {
            scope.launch { settingsRepository.updateWidgetId(value) }
        }

    var isFreeformHome: Boolean
        get() = cachedSettings.isFreeformHome
        set(value) {
            scope.launch { settingsRepository.updateFreeformHome(value) }
        }

    var iconScale: Float
        get() = cachedSettings.iconScale
        set(value) {
            scope.launch { settingsRepository.updateIconScale(scale = value) }
        }

    var isHideLabels: Boolean
        get() = cachedSettings.isHideLabels
        set(value) {
            scope.launch { settingsRepository.updateHideLabels(value) }
        }

    var themeMode: String?
        get() = cachedSettings.themeMode
        set(value) {
            scope.launch { settingsRepository.updateThemeMode(value ?: "system") }
        }

    var isLiquidGlass: Boolean
        get() = cachedSettings.isLiquidGlass
        set(value) {
            scope.launch { settingsRepository.updateLiquidGlass(value) }
        }

    var isDarkenWallpaper: Boolean
        get() = cachedSettings.isDarkenWallpaper
        set(value) {
            scope.launch { settingsRepository.updateDarkenWallpaper(value) }
        }

    fun incrementUsage(packageName: String) {
        val current = usageCache[packageName] ?: 0
        usageCache[packageName] = current + 1
        scope.launch { settingsRepository.incrementUsage(packageName) }
    }

    fun getUsage(packageName: String): Int {
        if (!usageCache.containsKey(packageName)) {
            scope.launch {
                val usage = settingsRepository.getUsage(packageName).first()
                usageCache[packageName] = usage
            }
            return 0
        }
        return usageCache[packageName] ?: 0
    }

    var drawerOpenCount: Int
        get() = cachedSettings.drawerOpenCount
        set(value) {}

    fun incrementDrawerOpenCount() {
        scope.launch { settingsRepository.incrementDrawerOpenCount() }
    }

    var lastDefaultPromptTimestamp: Long
        get() = cachedSettings.lastDefaultPromptTimestamp
        set(value) {
            scope.launch { settingsRepository.updateDefaultPrompt(value, cachedSettings.defaultPromptCount) }
        }

    var defaultPromptCount: Int
        get() = cachedSettings.defaultPromptCount
        set(value) {
            scope.launch { settingsRepository.updateDefaultPrompt(cachedSettings.lastDefaultPromptTimestamp, value) }
        }

    fun incrementDefaultPromptCount() {
        scope.launch { settingsRepository.updateDefaultPrompt(cachedSettings.lastDefaultPromptTimestamp, cachedSettings.defaultPromptCount + 1) }
    }

    fun getSettings(): LauncherSettings {
        return cachedSettings
    }

    fun saveHomeItems(items: List<HomeItem>) {
        // Handled by HomeRepository via ViewModel now
    }

    fun getHomeItems(): MutableList<HomeItem> {
        return mutableListOf()
    }
}
