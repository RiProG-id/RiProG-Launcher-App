package com.riprog.launcher.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.LinearLayout
import com.riprog.launcher.data.local.prefs.LauncherPreferences

/**
 * Theme switching and dynamic color mechanisms.
 */
object ThemeMechanism {

    /**
     * Applies the selected theme mode (system, light, dark).
     */
    fun applyThemeMode(context: Context, mode: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val nightMode = when (mode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }

    /**
     * Extracts dynamic accent color from Android 12+ system colors.
     */
    fun getSystemAccentColor(context: Context): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                context.resources.getColor(android.R.color.system_accent1_400, context.theme)
            } catch (ignored: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Applies the specialized "Liquid Glass" item style to a container.
     */
    fun applySettingItemStyle(context: Context, item: LinearLayout, settingsManager: LauncherPreferences) {
        item.isClickable = true
        item.isFocusable = true

        val radius = dpToPx(context, 12).toFloat()
        val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val shape = GradientDrawable()
        val baseColor = if (settingsManager.isLiquidGlass) {
            if (isNight) 0x26FFFFFF else 0x1A000000
        } else {
            if (isNight) 0x1AFFFFFF else 0x0D000000
        }
        shape.setColor(baseColor)
        shape.cornerRadius = radius
        if (settingsManager.isLiquidGlass) {
            shape.setStroke(dpToPx(context, 1), 0x20FFFFFF)
        }

        val mask = GradientDrawable()
        mask.setColor(Color.BLACK)
        mask.cornerRadius = radius

        // Note: R.color.search_background should be defined in colors.xml
        val rippleColor = try {
            context.getColor(context.resources.getIdentifier("search_background", "color", context.packageName))
        } catch (e: Exception) {
            0x40FFFFFF
        }

        item.background = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            shape,
            mask
        )
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
