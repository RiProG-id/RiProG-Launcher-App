package com.riprog.launcher.theme.modules

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import com.riprog.launcher.R

object StandardTheme : ThemeModule {
    override fun getThemedSurface(context: Context, cornerRadiusDp: Float): Drawable {
        val gd = GradientDrawable()
        val backgroundColor = context.getColor(R.color.surface)

        val cornerRadiusPx = dpToPx(context, cornerRadiusDp).toFloat()
        gd.setColor(backgroundColor)
        gd.cornerRadius = cornerRadiusPx

        // Pure mode: Solid background with subtle outline where appropriate.
        if (cornerRadiusDp > 0) {
            gd.setStroke(dpToPx(context, 1.2f), context.getColor(R.color.surface_stroke))
        } else {
            gd.setStroke(0, 0)
        }
        return gd
    }

    override fun getAdaptiveColor(context: Context, isOnGlass: Boolean): Int {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        return if (isNight) Color.WHITE else Color.BLACK
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
    }
}
