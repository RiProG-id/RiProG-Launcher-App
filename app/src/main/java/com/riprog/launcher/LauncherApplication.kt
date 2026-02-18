package com.riprog.launcher

import android.app.Application
import com.riprog.launcher.di.appModule
import com.riprog.launcher.data.repository.AppLoader
import com.riprog.launcher.data.local.prefs.LauncherPreferences
import com.riprog.launcher.ui.common.Logger
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LauncherApplication : Application() {
    var appLoader: AppLoader? = null
        private set

    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        startKoin {
            androidLogger()
            androidContext(this@LauncherApplication)
            modules(appModule)
        }
        appLoader = AppLoader(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appLoader?.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        appLoader?.shutdown()
        val prefs: LauncherPreferences by inject()
        prefs.onDestroy()
    }
}
