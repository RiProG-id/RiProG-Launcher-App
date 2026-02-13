package com.riprog.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.WindowInsetsController

object ThemeUtils {
    @JvmStatic
    fun getGlassDrawable(context: Context, settingsManager: SettingsManager): Drawable {
        return getGlassDrawable(context, settingsManager, 28f)
    }

    @JvmStatic
    fun getGlassDrawable(context: Context, settingsManager: SettingsManager, cornerRadiusDp: Float): Drawable {
        val isLiquidGlass = settingsManager.isLiquidGlass
        val gd = GradientDrawable()
        var backgroundColor = context.getColor(R.color.background)
        if (!isLiquidGlass) {
            val uiMode = context.resources.configuration.uiMode
            backgroundColor = if ((uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }
        gd.setColor(backgroundColor)
        gd.cornerRadius = dpToPx(context, cornerRadiusDp).toFloat()
        if (isLiquidGlass) {
            gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.glass_stroke))
        }
        return gd
    }

    @JvmStatic
    fun updateStatusBarContrast(activity: android.app.Activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val window = activity.window
            val controller = window.insetsController
            if (controller != null) {
                val uiMode = activity.resources.configuration.uiMode
                val isNight = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isNight) {
                    controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                } else {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            var flags = activity.window.decorView.systemUiVisibility
            val uiMode = activity.resources.configuration.uiMode
            val isNight = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            flags = if (isNight) {
                flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = flags
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }
}
