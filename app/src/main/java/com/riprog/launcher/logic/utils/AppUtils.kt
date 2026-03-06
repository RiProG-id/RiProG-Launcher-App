package com.riprog.launcher.logic.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri

object AppUtils {

    fun isUninstallable(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        } catch (e: Exception) {
            false
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
