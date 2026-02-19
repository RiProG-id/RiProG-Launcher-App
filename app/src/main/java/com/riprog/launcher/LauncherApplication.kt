package com.riprog.launcher

import com.riprog.launcher.data.local.db.AppDatabase
import com.riprog.launcher.data.repository.AppRepository
import com.riprog.launcher.data.repository.HomeRepository
import com.riprog.launcher.data.repository.SettingsRepository
import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherApplication : Application() {
    lateinit var model: AppRepository
        private set
    lateinit var homeRepository: HomeRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        model = AppRepository(this)
        val database = AppDatabase.getInstance(this)
        homeRepository = HomeRepository(this, database.homeItemDao())
        settingsRepository = SettingsRepository(this)

        applicationScope.launch {
            homeRepository.checkAndMigrate()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        model.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        model.shutdown()
    }
}
