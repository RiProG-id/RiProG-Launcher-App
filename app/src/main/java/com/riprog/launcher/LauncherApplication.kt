package com.riprog.launcher

import com.riprog.launcher.data.repository.AppRepository

import android.app.Application

class LauncherApplication : Application() {
    lateinit var model: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        model = AppRepository(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        model.shutdown()
    }
}
