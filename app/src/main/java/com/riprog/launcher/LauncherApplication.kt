package com.riprog.launcher

import android.app.Application
import com.riprog.launcher.di.appModule
import com.riprog.launcher.data.repository.AppLoader
import com.riprog.launcher.util.Logger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LauncherApplication : Application() {
    var appLoader: AppLoader? = null
        private set

    override fun onCreate() {
        super.onCreate()

        Logger.init(this)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.logError("Uncaught exception on thread ${thread.name}", throwable)
            originalHandler?.uncaughtException(thread, throwable)
        }

        startKoin {
            androidLogger()
            androidContext(this@LauncherApplication)
            modules(appModule)
        }
        appLoader = AppLoader(this)
        Logger.log("Application created")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        appLoader?.onTrimMemory(level)
    }

    override fun onTerminate() {
        super.onTerminate()
        appLoader?.shutdown()
    }
}
