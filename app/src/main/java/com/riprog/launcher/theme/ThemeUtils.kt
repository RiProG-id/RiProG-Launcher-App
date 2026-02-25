package com.riprog.launcher.theme

import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.theme.modules.LiquidGlassTheme
import com.riprog.launcher.theme.modules.StandardTheme
import com.riprog.launcher.theme.modules.ThemeModule
import com.riprog.launcher.R

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

object ThemeUtils {

    private fun getModule(settingsManager: SettingsManager): ThemeModule {
        return if (settingsManager.isLiquidGlass) LiquidGlassTheme else StandardTheme
    }


    fun getThemedSurface(context: Context, settingsManager: SettingsManager): Drawable {
        return getThemedSurface(context, settingsManager, 28f)
    }


    fun getThemedSurface(context: Context, settingsManager: SettingsManager, cornerRadiusDp: Float): Drawable {
        return getModule(settingsManager).getThemedSurface(context, cornerRadiusDp)
    }


    fun getAdaptiveColor(context: Context, backgroundColor: Int): Int {
        val luminance = (0.2126 * Color.red(backgroundColor) +
                0.7152 * Color.green(backgroundColor) +
                0.0722 * Color.blue(backgroundColor)) / 255.0
        return if (luminance > 0.5) context.getColor(R.color.foreground) else Color.WHITE
    }


    fun getAdaptiveColor(context: Context, settingsManager: SettingsManager, isOnGlass: Boolean): Int {
        return getModule(settingsManager).getAdaptiveColor(context, isOnGlass)
    }


    fun applyBlurIfSupported(view: View, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP))
            } else {
                view.setRenderEffect(null)
            }
        }
    }


    fun applyWindowBlur(window: Window, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val lp = window.attributes
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                lp.blurBehindRadius = 60
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                lp.blurBehindRadius = 0
            }
            window.attributes = lp
        }
    }


    fun updateStatusBarContrast(activity: Activity) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isNight = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = !isNight
    }
}
