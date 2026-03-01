package com.riprog.launcher.logic.receivers

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
            // Intelligent Invalidation: only clear affected app's cache
            model.invalidateIcon(packageName)
        }
        onPackageChanged()
    }
}
