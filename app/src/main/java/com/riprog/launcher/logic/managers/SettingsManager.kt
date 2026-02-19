package com.riprog.launcher.logic.managers

import android.content.Context
import com.riprog.launcher.LauncherApplication
import com.riprog.launcher.data.model.HomeItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsManager(private val context: Context) {
    private val repo = (context.applicationContext as LauncherApplication).settingsRepository
    private val homeRepo = (context.applicationContext as LauncherApplication).homeItemRepository

    var columns: Int
        get() = runBlocking { repo.columns.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setColumns(value) } }

    var widgetId: Int
        get() = runBlocking { repo.widgetId.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setWidgetId(value) } }

    var isFreeformHome: Boolean
        get() = runBlocking { repo.isFreeformHome.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setFreeformHome(value) } }

    var iconScale: Float
        get() = runBlocking { repo.iconScale.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setIconScale(value) } }

    var isHideLabels: Boolean
        get() = runBlocking { repo.isHideLabels.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setHideLabels(value) } }

    var themeMode: String?
        get() = runBlocking { repo.themeMode.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setThemeMode(value ?: "system") } }

    var isLiquidGlass: Boolean
        get() = runBlocking { repo.isLiquidGlass.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setLiquidGlass(value) } }

    var isDarkenWallpaper: Boolean
        get() = runBlocking { repo.isDarkenWallpaper.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setDarkenWallpaper(value) } }

    fun incrementUsage(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch { repo.incrementUsage(packageName) }
    }

    fun getUsage(packageName: String): Int {
        return runBlocking { repo.getUsage(packageName).first() }
    }

    var drawerOpenCount: Int
        get() = runBlocking { repo.drawerOpenCount.first() }
        set(value) { /* Not directly set in old code except via increment */ }

    fun incrementDrawerOpenCount() {
        CoroutineScope(Dispatchers.IO).launch { repo.incrementDrawerOpenCount() }
    }

    var lastDefaultPromptTimestamp: Long
        get() = runBlocking { repo.lastDefaultPromptTimestamp.first() }
        set(value) { CoroutineScope(Dispatchers.IO).launch { repo.setLastDefaultPromptTimestamp(value) } }

    var defaultPromptCount: Int
        get() = runBlocking { repo.defaultPromptCount.first() }
        set(value) { /* Not directly set in old code */ }

    fun incrementDefaultPromptCount() {
        CoroutineScope(Dispatchers.IO).launch { repo.incrementDefaultPromptCount() }
    }

    fun saveHomeItems(items: List<HomeItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            homeRepo.saveHomeItems(items)
        }
    }

    fun getHomeItems(): MutableList<HomeItem> {
        return runBlocking { homeRepo.getHomeItems().toMutableList() }
    }
}
