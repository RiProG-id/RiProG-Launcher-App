package com.riprog.launcher.theme

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

class MaterialSurfaceDrawable(
    private val baseDrawable: GradientDrawable,
    private val isNight: Boolean
) : Drawable() {

    private var cornerRadius: Float = 0f

    fun setCornerRadius(radius: Float) {
        this.cornerRadius = radius
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        baseDrawable.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        // 1. Draw base Material surface
        // The baseDrawable color is already set to M3 Surface Container in ThemeUtils
        baseDrawable.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        baseDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
