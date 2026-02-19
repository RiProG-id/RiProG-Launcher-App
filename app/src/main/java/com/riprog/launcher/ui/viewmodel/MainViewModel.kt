package com.riprog.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.LauncherSettings
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.repository.HomeRepository
import com.riprog.launcher.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    private val appRepository: AppRepository,
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _allApps = MutableStateFlow<List<AppItem>>(emptyList())
    val allApps: StateFlow<List<AppItem>> = _allApps.asStateFlow()

    val homeItems: StateFlow<List<HomeItem>> = homeRepository.getHomeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<LauncherSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LauncherSettings())

    init {
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _allApps.value = appRepository.getApps()
        }
    }

    fun saveHomeItems(items: List<HomeItem>) {
        viewModelScope.launch {
            homeRepository.saveHomeItems(items)
        }
    }

    fun updateColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.updateColumns(columns) }
    }

    fun updateFreeformHome(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateFreeformHome(enabled) }
    }

    fun updateIconScale(scale: Float) {
        viewModelScope.launch { settingsRepository.updateIconScale(scale) }
    }

    fun updateHideLabels(hide: Boolean) {
        viewModelScope.launch { settingsRepository.updateHideLabels(hide) }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun updateLiquidGlass(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateLiquidGlass(enabled) }
    }

    fun updateDarkenWallpaper(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateDarkenWallpaper(enabled) }
    }

    fun incrementDrawerOpenCount() {
        viewModelScope.launch { settingsRepository.incrementDrawerOpenCount() }
    }

    fun updateDefaultPrompt(timestamp: Long, count: Int) {
        viewModelScope.launch { settingsRepository.updateDefaultPrompt(timestamp, count) }
    }

    fun incrementUsage(packageName: String) {
        viewModelScope.launch { settingsRepository.incrementUsage(packageName) }
    }
}

class MainViewModelFactory(
    private val appRepository: AppRepository,
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(appRepository, homeRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
