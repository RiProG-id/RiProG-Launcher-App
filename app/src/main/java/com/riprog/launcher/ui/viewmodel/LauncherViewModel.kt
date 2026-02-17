package com.riprog.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riprog.launcher.data.repository.LauncherRepository
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LauncherViewModel(private val repository: LauncherRepository) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val apps: StateFlow<List<AppItem>> = _apps.asStateFlow()

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
            _apps.value = repository.loadApps()
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
