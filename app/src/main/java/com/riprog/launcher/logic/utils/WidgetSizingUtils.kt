package com.riprog.launcher.logic.utils

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import com.riprog.launcher.ui.views.home.HomeView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object WidgetSizingUtils {

    fun calculateWidgetSpan(context: Context, info: AppWidgetProviderInfo): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val cellWidth = screenWidth / HomeView.GRID_COLUMNS.toFloat()
        val cellHeight = (screenHeight - dpToPx(context, 48f)) / HomeView.GRID_ROWS.toFloat()

        var spanX: Int
        var spanY: Int

        // 1. Try to use targetCellWidth/Height (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            spanX = info.targetCellWidth
            spanY = info.targetCellHeight
        } else {
            // 2. Use preview image metadata if available
            val preview = info.loadPreviewImage(context, 0)
            if (preview != null && preview.intrinsicWidth > 0 && preview.intrinsicHeight > 0) {
                val pw = preview.intrinsicWidth
                val ph = preview.intrinsicHeight

                // Heuristic: assume the preview is rendered at a scale where
                // a standard cell is around 80-100dp or depends on the image size.
                // But the most reliable way with preview is aspect ratio.

                val aspect = pw.toFloat() / ph.toFloat()

                // Still need a base dimension. Use minWidth.
                val baseSpanX = ((info.minWidth * density) / cellWidth).roundToInt().coerceAtLeast(1)
                spanX = baseSpanX
                spanY = (spanX / aspect).roundToInt().coerceAtLeast(1)
            } else {
                // 3. Fallback to minWidth/minHeight with improved formula
                spanX = ceil((info.minWidth * density - 30) / cellWidth).toInt().coerceAtLeast(1)
                spanY = ceil((info.minHeight * density - 30) / cellHeight).toInt().coerceAtLeast(1)
            }
        }

        return Pair(spanX, spanY)
    }

    fun applySizeReduction(context: Context, info: AppWidgetProviderInfo, spanX: Int, spanY: Int): Pair<Int, Int> {
        // Apply 25% reduction from current span as a proxy for dimensions
        val newSpanX = ceil(spanX * 0.75f).toInt().coerceAtLeast(1)
        val newSpanY = ceil(spanY * 0.75f).toInt().coerceAtLeast(1)
        return Pair(newSpanX, newSpanY)
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
