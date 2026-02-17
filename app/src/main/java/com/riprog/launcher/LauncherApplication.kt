package com.riprog.launcher

import android.app.Application
import com.riprog.launcher.di.appModule
import com.riprog.launcher.model.LauncherModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LauncherApplication : Application() {
    var model: LauncherModel? = null
        private set

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@LauncherApplication)
            modules(appModule)
        }
        model = LauncherModel(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        model?.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        model?.shutdown()
    }
}
