package com.riprog.launcher.data.model

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle

class AppItem(
    val label: String,
    val packageName: String,
    val className: String,
    val userHandle: UserHandle? = null
) {
    fun getUserHandleOrDefault(): UserHandle {
        return userHandle ?: Process.myUserHandle()
    }

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