package com.riprog.launcher.logic.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riprog.launcher.data.repository.AppRepository

import android.os.Build
import android.os.Process

class PackageReceiver(
    private val model: AppRepository,
    private val onPackageChanged: (String?, android.os.UserHandle?) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        val userHandle = try {
            if (intent.hasExtra(Intent.EXTRA_USER)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_USER, android.os.UserHandle::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.os.UserHandle>(Intent.EXTRA_USER)
                }
            } else {
                Process.myUserHandle()
            }
        } catch (e: RuntimeException) {
            // Fallback for environments where UserHandle or related APIs are not fully mocked/available in tests
            null
        }

        if (packageName != null) {
            model.invalidateIcon(packageName)
        }
        onPackageChanged(packageName, userHandle)
    }
}