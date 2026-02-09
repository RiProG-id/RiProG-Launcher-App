package com.riprog.launcher

import android.content.ComponentName
import android.graphics.Bitmap

class AppItem(
    @JvmField val label: String,
    @JvmField val packageName: String,
    @JvmField val className: String
) {
    @JvmField
    var icon: Bitmap? = null

    fun getComponentName(): ComponentName {
        return ComponentName(packageName, className)
    }
}
