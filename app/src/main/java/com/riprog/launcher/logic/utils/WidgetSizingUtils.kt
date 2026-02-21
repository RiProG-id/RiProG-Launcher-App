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
            // 2. Fallback to minWidth/minHeight with standard formula
            // We use (minWidth + 30) / cellWidth to be more generous or ceil(minWidth/cellWidth)
            // Standard Android formula is often: (minWidth + 30) / 70 for 70dp cells.
            // Here we use our actual cell dimensions.
            spanX = ceil((info.minWidth * density) / cellWidth).toInt().coerceAtLeast(1)
            spanY = ceil((info.minHeight * density) / cellHeight).toInt().coerceAtLeast(1)
        }

        return Pair(spanX, spanY)
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
