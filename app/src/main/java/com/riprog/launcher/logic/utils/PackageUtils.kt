package com.riprog.launcher.logic.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager

object PackageUtils {

    fun isUninstallable(context: Context, packageName: String, userHandle: UserHandle? = null): Boolean {
        if (packageName.isEmpty()) return false

        try {
            val pm = context.packageManager
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

            val applicationInfo = if (userHandle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.getApplicationInfo(packageName, 0, userHandle)
            } else {
                pm.getApplicationInfo(packageName, 0)
            }

            // Check if it's a system app
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // System apps cannot be uninstalled unless they were updated, in which case the update can be uninstalled.
            if (isSystemApp && !isUpdatedSystemApp) {
                return false
            }

            // Check for user restrictions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val targetUser = userHandle ?: android.os.Process.myUserHandle()
                val userRestrictions = userManager.getUserRestrictions(targetUser)
                if (userRestrictions.getBoolean(UserManager.DISALLOW_APPS_CONTROL) ||
                    userRestrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS)) {
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }
}
