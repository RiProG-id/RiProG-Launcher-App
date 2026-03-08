package com.riprog.launcher

import com.riprog.launcher.data.repository.AppRepository

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle

class LauncherApplication : Application() {
    lateinit var model: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        model = AppRepository(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
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