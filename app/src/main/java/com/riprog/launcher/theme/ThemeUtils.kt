package com.riprog.launcher.theme

import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.R
import com.google.android.material.color.MaterialColors

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


    fun getThemedSurface(context: Context, settingsManager: SettingsManager): Drawable {
        return getThemedSurface(context, settingsManager, 28f)
    }


    fun getThemedSurface(context: Context, settingsManager: SettingsManager, cornerRadiusDp: Float): Drawable {
        val themeStyle = settingsManager.themeStyle
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val gd = GradientDrawable()
        val backgroundColor: Int = when (themeStyle) {
            ThemeStyle.LIQUID_GLASS -> context.getColor(R.color.background_glass)
            ThemeStyle.MATERIAL -> getSurfaceContainerColor(context)
            else -> context.getColor(R.color.surface)
        }

        val cornerRadiusPx = dpToPx(context, cornerRadiusDp).toFloat()
        gd.setColor(backgroundColor)
        gd.cornerRadius = cornerRadiusPx

        return when (themeStyle) {
            ThemeStyle.LIQUID_GLASS -> {
                gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke))
                val reflectionDrawable = GlassReflectionDrawable(gd, isNight)
                reflectionDrawable.setCornerRadius(cornerRadiusPx)
                reflectionDrawable
            }
            ThemeStyle.MATERIAL -> {
                MaterialSurfaceDrawable(gd, isNight)
            }
            else -> {
                // Pure mode: Solid background with subtle outline where appropriate.
                if (cornerRadiusDp > 0) {
                    gd.setStroke(dpToPx(context, 1.2f), context.getColor(R.color.surface_stroke))
                } else {
                    gd.setStroke(0, 0)
                }
                gd
            }
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
        val themeStyle = settingsManager.themeStyle

        if (themeStyle == ThemeStyle.MATERIAL) {
            return getOnSurfaceColor(context)
        }

        if (isOnGlass) {
            return if (themeStyle == ThemeStyle.LIQUID_GLASS) {
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


    fun getBackgroundColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#141218") else Color.parseColor("#FEF7FF")
        return MaterialColors.getColor(context, android.R.attr.colorBackground, fallback)
    }

    fun getOnBackgroundColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#E6E1E9") else Color.parseColor("#1D1B20")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnBackground, fallback)
    }

    fun getSurfaceColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#141218") else Color.parseColor("#FEF7FF")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, fallback)
    }

    fun getOnSurfaceColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#E6E1E9") else Color.parseColor("#1D1B20")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, fallback)
    }

    fun getSurfaceVariantColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#49454F") else Color.parseColor("#E7E0EC")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, fallback)
    }

    fun getOnSurfaceVariantColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, fallback)
    }

    fun getSurfaceContainerColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#211F26") else Color.parseColor("#F3EDF7")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, fallback)
    }

    fun getPrimaryColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, fallback)
    }

    fun getOnPrimaryColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#381E72") else Color.parseColor("#FFFFFF")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, fallback)
    }

    fun getSecondaryContainerColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#332D41") else Color.parseColor("#E8DEF8")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, fallback)
    }

    fun getOnSecondaryContainerColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#E8DEF8") else Color.parseColor("#1D192B")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer, fallback)
    }

    fun getOutlineColor(context: Context): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fallback = if (isNight) Color.parseColor("#938F99") else Color.parseColor("#79747E")
        return MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, fallback)
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
