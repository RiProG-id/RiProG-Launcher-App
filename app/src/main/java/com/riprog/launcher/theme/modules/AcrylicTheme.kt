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

        val cornerRadiusPx = dpToPx(context, cornerRadiusDp).toFloat()
        val backgroundColor = context.getColor(R.color.background_acrylic)

        val reflectionDrawable = AcrylicReflectionDrawable(isNight)
        reflectionDrawable.setColor(backgroundColor)
        reflectionDrawable.cornerRadius = cornerRadiusPx
        reflectionDrawable.setStroke(dpToPx(context, 1.5f), context.getColor(R.color.acrylic_stroke))
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
