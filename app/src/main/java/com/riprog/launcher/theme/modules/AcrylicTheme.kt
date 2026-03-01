package com.riprog.launcher.theme.modules

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.riprog.launcher.R
import com.riprog.launcher.theme.AcrylicReflectionDrawable
import com.riprog.launcher.theme.ThemeUtils

object AcrylicTheme : ThemeModule {
    override fun getThemedSurface(context: Context, cornerRadiusDp: Float): Drawable {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val gd = GradientDrawable()
        val backgroundColor = context.getColor(R.color.background_acrylic)

        val cornerRadiusPx = dpToPx(context, cornerRadiusDp).toFloat()
        gd.setColor(backgroundColor)
        gd.cornerRadius = cornerRadiusPx

        gd.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.acrylic_stroke))
        val reflectionDrawable = AcrylicReflectionDrawable(gd, isNight)
        reflectionDrawable.setCornerRadius(cornerRadiusPx)
        return reflectionDrawable
    }

    override fun getAdaptiveColor(context: Context, isOnAcrylic: Boolean): Int {
        if (isOnAcrylic) {
            return ThemeUtils.getAdaptiveColor(context, context.getColor(R.color.background_acrylic))
        } else {
            val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            return if (isNight) Color.WHITE else Color.BLACK
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }
}