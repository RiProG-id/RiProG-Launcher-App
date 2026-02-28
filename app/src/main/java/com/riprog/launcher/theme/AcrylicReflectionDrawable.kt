package com.riprog.launcher.theme

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.animation.LinearInterpolator

class AcrylicReflectionDrawable(
    private val baseDrawable: GradientDrawable,
    private val isNight: Boolean
) : AdaptiveIconDrawable(baseDrawable, null), Animatable {

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

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val highlightRect = RectF()
    private val highlightPath = Path()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        // 1. Draw base acrylic background
        baseDrawable.draw(canvas)

        // 2. Draw static reflection highlight for surface depth
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        highlightPaint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(
                if (isNight) 0x33FFFFFF else 0x66FFFFFF,
                0x00FFFFFF,
                0x00FFFFFF
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        highlightRect.set(0f, 0f, width, height)
        highlightPath.reset()
        highlightPath.addRoundRect(highlightRect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.drawPath(highlightPath, highlightPaint)
    }

    override fun setAlpha(alpha: Int) {
        baseDrawable.alpha = alpha
        reflectionPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable.colorFilter = colorFilter
        reflectionPaint.colorFilter = colorFilter
    }



    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart)
    }

    override fun start() {
    }

    override fun stop() {
    }

    override fun isRunning(): Boolean = false
}
