package com.riprog.launcher.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import com.riprog.launcher.R

class PageIndicator(context: Context) : LinearLayout(context) {
    private var count = 0
    private var current = 0
    private var accentColor = Color.WHITE

    init {
        orientation = HORIZONTAL
    }

    fun setPageCount(count: Int) {
        this.count = count
        updateDots()
    }

    fun setCurrentPage(current: Int) {
        this.current = current
        updateDots()
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        updateDots()
    }

    private fun updateDots() {
        removeAllViews()
        for (i in 0 until count) {
            val dot = View(context)
            val size = dpToPx(6)
            val lp = LayoutParams(size, size)
            lp.setMargins(dpToPx(4), 0, dpToPx(4), 0)
            dot.layoutParams = lp

            val shape = GradientDrawable()
            shape.shape = GradientDrawable.OVAL
            if (i == current) {
                shape.setColor(accentColor)
            } else {
                shape.setColor(context.getColor(R.color.foreground_dim) and 0x80FFFFFF.toInt())
            }
            dot.background = shape
            addView(dot)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
