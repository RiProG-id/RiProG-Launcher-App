package com.riprog.launcher.theme

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.animation.LinearInterpolator

class GlassReflectionDrawable(
    private val baseDrawable: GradientDrawable,
    private val isNight: Boolean
) : Drawable(), Animatable {

    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    private var cornerRadius: Float = 0f

    fun setCornerRadius(radius: Float) {
        this.cornerRadius = radius
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        baseDrawable.bounds = bounds
        updateShader(bounds)
    }

    private fun updateShader(bounds: Rect) {
        if (bounds.isEmpty) return
        val width = bounds.width().toFloat()

        // Intensity adapts to theme brightness - subtle and elegant
        val colors = if (isNight) {
            intArrayOf(0x00FFFFFF, 0x0AFFFFFF, 0x00FFFFFF) // ~4% alpha for dark
        } else {
            intArrayOf(0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF) // ~8% alpha for light
        }

        // Horizontal shimmer that will be rotated
        val shader = LinearGradient(
            0f, 0f, width * 0.6f, 0f,
            colors, null, Shader.TileMode.CLAMP
        )
        reflectionPaint.shader = shader
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        // 1. Draw base glass background
        baseDrawable.draw(canvas)

        // Reflection shimmer (Step 2) is now fully removed to ensure a static UI.
    }

    override fun setAlpha(alpha: Int) {
        baseDrawable.alpha = alpha
        reflectionPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable.colorFilter = colorFilter
        reflectionPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart)
    }

    override fun start() {
    }

    override fun stop() {
    }

    override fun isRunning(): Boolean = false
}
