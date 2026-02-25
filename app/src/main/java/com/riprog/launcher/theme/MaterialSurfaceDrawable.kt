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
        baseDrawable.draw(canvas)

        // 2. Add subtle Material 3 depth effect (optional, but keep structure)
        // Unlike Liquid Glass, Material You uses color tonal levels.
        // We can add a very faint top-down gradient to simulate M3 elevation.

        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL

        // Subtle elevation overlay
        val overlayAlpha = if (isNight) 0x0D else 0x08 // 5% or 3%
        paint.color = Color.WHITE
        paint.alpha = overlayAlpha

        val rect = RectF(0f, 0f, width, height)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    override fun setAlpha(alpha: Int) {
        baseDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
