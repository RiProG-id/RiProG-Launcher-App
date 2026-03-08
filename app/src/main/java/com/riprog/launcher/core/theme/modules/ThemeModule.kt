package com.riprog.launcher.core.theme.modules

import android.content.Context
import android.graphics.drawable.Drawable

interface ThemeModule {
    fun getThemedSurface(context: Context, cornerRadiusDp: Float): Drawable
    fun getAdaptiveColor(context: Context, isOnAcrylic: Boolean): Int
}