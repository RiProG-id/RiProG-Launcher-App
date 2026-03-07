package com.riprog.launcher.data.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class AppItem(
    val label: String,
    val packageName: String,
    val className: String
) {

    fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }

    companion object {

        fun fromPackage(context: Context, packageName: String): AppItem {
            return try {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(packageName, 0)
                AppItem(pm.getApplicationLabel(ai).toString(), packageName, "")
            } catch (e: Exception) {
                AppItem("...", packageName, "")
            }
        }
    }
}
