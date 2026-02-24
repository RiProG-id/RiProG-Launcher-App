package com.riprog.launcher.theme

import com.riprog.launcher.logic.managers.SettingsManager
import com.riprog.launcher.R

import androidx.appcompat.app.AppCompatDelegate
import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.LinearLayout

object ThemeManager {


    fun applyThemeMode(context: Context, mode: String?) {
        val nightMode = when (mode) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        // Still apply to UiModeManager on S+ for better system integration if needed,
        // but AppCompatDelegate is the primary driver now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            val systemNightMode = when (mode) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            if (uiModeManager.nightMode != systemNightMode) {
                uiModeManager.setApplicationNightMode(systemNightMode)
            }
        }
    }




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


    fun applySettingItemStyle(context: Context, item: LinearLayout, settingsManager: SettingsManager) {
        item.isClickable = true
        item.isFocusable = true

        val radius = dpToPx(context, 12).toFloat()
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val shape = GradientDrawable()
        val baseColor = if (settingsManager.isLiquidGlass) {
            if (isNight) 0x26FFFFFF.toInt() else 0x1A000000.toInt()
        } else {
            if (isNight) 0x1AFFFFFF.toInt() else 0x0D000000.toInt()
        }
        shape.setColor(baseColor)
        shape.cornerRadius = radius
        if (settingsManager.isLiquidGlass) {
            shape.setStroke(dpToPx(context, 1), 0x20FFFFFF.toInt())
        }

        val mask = GradientDrawable()
        mask.setColor(Color.BLACK)
        mask.cornerRadius = radius

        val rippleColor = try {
            context.getColor(R.color.search_background)
        } catch (e: Exception) {
            0x40FFFFFF.toInt()
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
