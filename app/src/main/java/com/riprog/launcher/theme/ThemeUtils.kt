package com.riprog.launcher.theme

import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.R

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

object ThemeUtils {


    fun getGlassDrawable(context: Context, settingsManager: SettingsManager): Drawable {
        return getGlassDrawable(context, settingsManager, 28f)
    }


    fun getGlassDrawable(context: Context, settingsManager: SettingsManager, cornerRadiusDp: Float): Drawable {
        val isLiquidGlass = settingsManager.isLiquidGlass
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val gd = GradientDrawable()
        val backgroundColor: Int
        if (isLiquidGlass) {
            backgroundColor = context.getColor(R.color.background_glass)
        } else {
            backgroundColor = context.getColor(R.color.background)
        }

        val cornerRadiusPx = dpToPx(context, cornerRadiusDp).toFloat()
        gd.setColor(backgroundColor)
        gd.cornerRadius = cornerRadiusPx

        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke))
            val reflectionDrawable = GlassReflectionDrawable(gd, isNight)
            reflectionDrawable.setCornerRadius(cornerRadiusPx)
            return reflectionDrawable
        } else {
            gd.setStroke(0, 0)
            return gd
        }
    }


    fun getAdaptiveColor(context: Context, backgroundColor: Int): Int {
        val luminance = (0.2126 * Color.red(backgroundColor) +
                0.7152 * Color.green(backgroundColor) +
                0.0722 * Color.blue(backgroundColor)) / 255.0
        return if (luminance > 0.5) context.getColor(R.color.foreground) else Color.WHITE
    }


    fun getAdaptiveColor(context: Context, settingsManager: SettingsManager, isOnGlass: Boolean): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isOnGlass) {
            return if (settingsManager.isLiquidGlass) {
                getAdaptiveColor(context, context.getColor(R.color.background_glass))
            } else {
                if (isNight) Color.WHITE else Color.BLACK
            }
        } else {
            return if (isNight) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }
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
        val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = !isNight
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }
}
