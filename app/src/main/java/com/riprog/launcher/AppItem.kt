package com.riprog.launcher

import android.content.ComponentName

data class AppItem(
    @JvmField val label: String,
    @JvmField val packageName: String,
    @JvmField val className: String
) {
    fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }
}
