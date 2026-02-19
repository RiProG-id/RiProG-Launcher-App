package com.riprog.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.repository.HomeItemRepository
import com.riprog.launcher.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.graphics.Bitmap

class MainViewModel(
    private val appRepository: AppRepository,
    private val homeItemRepository: HomeItemRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppItem>>(emptyList())
    val allApps: StateFlow<List<AppItem>> = _allApps.asStateFlow()

    val homeItems: StateFlow<List<HomeItem>> = homeItemRepository.getHomeItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode = settingsRepository.themeMode
    val iconScale = settingsRepository.iconScale
    val isHideLabels = settingsRepository.isHideLabels
    val columns = settingsRepository.columns
    val widgetId = settingsRepository.widgetId
    val isFreeformHome = settingsRepository.isFreeformHome
    val isLiquidGlass = settingsRepository.isLiquidGlass
    val isDarkenWallpaper = settingsRepository.isDarkenWallpaper
    val drawerOpenCount = settingsRepository.drawerOpenCount
    val lastDefaultPromptTimestamp = settingsRepository.lastDefaultPromptTimestamp
    val defaultPromptCount = settingsRepository.defaultPromptCount

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _allApps.value = appRepository.loadApps()
        }
    }

    fun saveHomeState(items: List<HomeItem>) {
        viewModelScope.launch {
            homeItemRepository.saveHomeItems(items)
        }
    }

    suspend fun loadIcon(app: AppItem): Bitmap? {
        return appRepository.loadIcon(app)
    }

    fun incrementUsage(packageName: String) {
        viewModelScope.launch {
            settingsRepository.incrementUsage(packageName)
        }
    }

    fun setColumns(count: Int) {
        viewModelScope.launch { settingsRepository.setColumns(count) }
    }

    fun setWidgetId(id: Int) {
        viewModelScope.launch { settingsRepository.setWidgetId(id) }
    }

    fun setFreeformHome(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFreeformHome(enabled) }
    }

    fun setIconScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setIconScale(scale) }
    }

    fun setHideLabels(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideLabels(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setLiquidGlass(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLiquidGlass(enabled) }
    }

    fun setDarkenWallpaper(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkenWallpaper(enabled) }
    }

    fun incrementDrawerOpenCount() {
        viewModelScope.launch { settingsRepository.incrementDrawerOpenCount() }
    }

    fun setLastDefaultPromptTimestamp(ts: Long) {
        viewModelScope.launch { settingsRepository.setLastDefaultPromptTimestamp(ts) }
    }

    fun incrementDefaultPromptCount() {
        viewModelScope.launch { settingsRepository.incrementDefaultPromptCount() }
    }

    class Factory(
        private val appRepository: AppRepository,
        private val homeItemRepository: HomeItemRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(appRepository, homeItemRepository, settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
