package com.riprog.launcher

import android.app.Application
import com.riprog.launcher.model.LauncherModel

class LauncherApplication : Application() {
    var model: LauncherModel? = null
        private set

    override fun onCreate() {
        super.onCreate()
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
