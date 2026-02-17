package com.riprog.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riprog.launcher.data.repository.AppLoader

class AppInstallReceiver(private val callback: Callback) : BroadcastReceiver() {
    interface Callback {
        fun getAppLoader(): AppLoader?
        fun removePackageItems(packageName: String)
        fun loadApps()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val data = intent.data ?: return
        val packageName = data.schemeSpecificPart ?: return

        if (Intent.ACTION_PACKAGE_REMOVED == action) {
            val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (!isReplacing) {
                callback.removePackageItems(packageName)
            }
        }
        callback.loadApps()
    }
}
