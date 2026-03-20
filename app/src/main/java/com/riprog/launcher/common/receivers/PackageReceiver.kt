package com.riprog.launcher.common.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riprog.launcher.data.repository.AppRepository

class PackageReceiver(
    private val model: AppRepository,
    private val onPackageChanged: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        if (packageName != null) {

            model.invalidateIcon(packageName)
            model.invalidateAppList()
        }
        onPackageChanged()
    }
}
