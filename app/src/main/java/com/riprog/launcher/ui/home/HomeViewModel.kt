package com.riprog.launcher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riprog.launcher.data.repository.HomeRepository
import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.model.HomeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    val homeItems = repository.topLevelItems
    val settings = repository.settings

    init {
        viewModelScope.launch {
            repository.checkAndMigrate()
            loadApps()
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _installedApps.value = repository.loadApps()
        }
    }

    fun saveHomeItems(items: List<HomeItem>) {
        viewModelScope.launch {
            repository.saveHomeItems(items)
        }
    }

    fun saveHomeItem(item: HomeItem) {
        viewModelScope.launch {
            repository.saveHomeItem(item)
        }
    }

    fun deleteHomeItem(item: HomeItem) {
        viewModelScope.launch {
            repository.deleteHomeItem(item)
        }
    }
}
