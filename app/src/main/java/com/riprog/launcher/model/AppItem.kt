package com.riprog.launcher.model

import android.content.ComponentName

class AppItem(
    val label: String,
    val packageName: String,
    val className: String
) {
    fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }
}
