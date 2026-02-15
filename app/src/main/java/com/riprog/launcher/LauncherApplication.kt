package com.riprog.launcher
import android.app.Application
class LauncherApplication : Application() {
    private var model: LauncherModel? = null
    override fun onCreate() {
        super.onCreate()
        model = LauncherModel(this)
    }
    fun getModel(): LauncherModel? {
        return model
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
