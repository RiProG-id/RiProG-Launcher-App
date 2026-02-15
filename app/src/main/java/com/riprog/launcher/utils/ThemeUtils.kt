package com.riprog.launcher.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import com.riprog.launcher.R

object ThemeUtils {
    @JvmStatic
    @JvmOverloads
    fun getGlassDrawable(context: Context, settingsManager: SettingsManager, cornerRadiusDp: Float = 28f): Drawable {
        val isLiquidGlass = settingsManager.isLiquidGlass
        val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val gd = GradientDrawable()
        val backgroundColor: Int = if (isLiquidGlass) {
            context.getColor(R.color.background)
        } else {
            if (isNight) Color.BLACK else Color.WHITE
        }

        gd.setColor(backgroundColor)
        gd.cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()

        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke))
        }

        return gd
    }

    @JvmStatic
    fun getAdaptiveColor(context: Context, backgroundColor: Int): Int {
        val luminance = (0.2126 * Color.red(backgroundColor) +
                0.7152 * Color.green(backgroundColor) +
                0.0722 * Color.blue(backgroundColor)) / 255.0
        return if (luminance > 0.5) context.getColor(R.color.foreground) else Color.WHITE
    }

    @JvmStatic
    fun getAdaptiveColor(context: Context, settingsManager: SettingsManager, isOnGlass: Boolean): Int {
        val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        return if (isOnGlass) {
            if (settingsManager.isLiquidGlass) {
                getAdaptiveColor(context, context.getColor(R.color.background))
            } else {
                if (isNight) Color.WHITE else Color.BLACK
            }
        } else {
            if (isNight && settingsManager.isDarkenWallpaper) {
                Color.WHITE
            } else if (isNight) Color.WHITE else Color.BLACK
        }
    }

    @JvmStatic
    fun applyBlurIfSupported(view: View, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                view.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.CLAMP))
            } else {
                view.setRenderEffect(null)
            }
        }
    }

    @JvmStatic
    fun applyWindowBlur(window: Window, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 60
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 0
            }
        }
    }

    @JvmStatic
    fun updateStatusBarContrast(activity: android.app.Activity) {
        val window = activity.window
        val isNight = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isNight
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }
}
