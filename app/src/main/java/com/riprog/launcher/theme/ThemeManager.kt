package com.riprog.launcher.theme

import com.riprog.launcher.data.model.LauncherSettings
import com.riprog.launcher.R

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            var nightMode = UiModeManager.MODE_NIGHT_AUTO
            if ("light" == mode) nightMode = UiModeManager.MODE_NIGHT_NO
            else if ("dark" == mode) nightMode = UiModeManager.MODE_NIGHT_YES

            if (uiModeManager.nightMode != nightMode) {
                uiModeManager.setApplicationNightMode(nightMode)
            }
        }
    }

    fun applyThemeToContext(base: Context, mode: String?): Context {
        if ("system" == mode || mode == null) return base

        val config = Configuration(base.resources.configuration)
        if ("light" == mode) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        } else if ("dark" == mode) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }

        return base.createConfigurationContext(config)
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

    fun applySettingItemStyle(context: Context, item: LinearLayout, settings: LauncherSettings) {
        item.isClickable = true
        item.isFocusable = true

        val radius = dpToPx(context, 12).toFloat()
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val shape = GradientDrawable()
        val baseColor = if (settings.isLiquidGlass) {
            if (isNight) 0x26FFFFFF.toInt() else 0x1A000000.toInt()
        } else {
            if (isNight) 0x1AFFFFFF.toInt() else 0x0D000000.toInt()
        }
        shape.setColor(baseColor)
        shape.cornerRadius = radius
        if (settings.isLiquidGlass) {
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
