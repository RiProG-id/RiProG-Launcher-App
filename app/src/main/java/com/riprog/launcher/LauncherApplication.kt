package com.riprog.launcher

import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.repository.SettingsRepository
import com.riprog.launcher.data.repository.HomeItemRepository
import com.riprog.launcher.data.db.AppDatabase

import android.app.Application

class LauncherApplication : Application() {
    lateinit var appRepository: AppRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var homeItemRepository: HomeItemRepository
        private set

    override fun onCreate() {
        super.onCreate()
        appRepository = AppRepository(this)
        settingsRepository = SettingsRepository(this)
        val database = AppDatabase.getDatabase(this)
        homeItemRepository = HomeItemRepository(database.homeItemDao(), settingsRepository)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appRepository.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        appRepository.shutdown()
    }
}
