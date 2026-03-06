package com.riprog.launcher.logic.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.UserManager

object UninstallUtils {

    fun canUninstall(context: Context, packageName: String): Boolean {
        return try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

            // Check device policy / user restrictions
            if (userManager.hasUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS)) {
                return false
            }

            if (userManager.hasUserRestriction(UserManager.DISALLOW_APPS_CONTROL)) {
                return false
            }

            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)

            // System apps cannot be uninstalled (usually)
            // Updated system apps have FLAG_SYSTEM but also FLAG_UPDATED_SYSTEM_APP
            // The requirement says "The Uninstall option must automatically hide or disable when: the selected app is a system application"
            if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                return false
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    fun triggerUninstall(context: Context, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val packageInstaller = context.packageManager.packageInstaller
            val intent = Intent(context, context.javaClass).apply {
                action = "com.riprog.launcher.UNINSTALL_COMPLETE"
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                flags
            )
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
        } else {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
