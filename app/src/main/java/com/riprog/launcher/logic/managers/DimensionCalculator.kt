package com.riprog.launcher.logic.managers

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.ceil
import kotlin.math.max

class DimensionCalculator(private val context: Context) {

    data class WidgetDimensions(
        val minWidth: Int,
        val minHeight: Int,
        val minResizeWidth: Int,
        val minResizeHeight: Int,
        val resizeMode: Int
    )

    fun extractMetadata(info: AppWidgetProviderInfo): WidgetDimensions {
        return WidgetDimensions(
            minWidth = info.minWidth,
            minHeight = info.minHeight,
            minResizeWidth = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth,
            minResizeHeight = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight,
            resizeMode = info.resizeMode
        )
    }

    fun calculateSpan(
        info: AppWidgetProviderInfo,
        cellWidthPx: Float,
        cellHeightPx: Float,
        isInitial: Boolean = false
    ): Pair<Int, Int> {
        return calculateSpan(extractMetadata(info), cellWidthPx, cellHeightPx, isInitial)
    }

    fun calculateSpan(
        dims: WidgetDimensions,
        cellWidthPx: Float,
        cellHeightPx: Float,
        isInitial: Boolean = false
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density

        var providerWidth = dims.minWidth.toFloat()
        var providerHeight = dims.minHeight.toFloat()

        if (isInitial) {
            val scaleFactor = 0.75f
            providerWidth *= scaleFactor
            providerHeight *= scaleFactor
        }

        val providerWidthPx = providerWidth * density
        val providerHeightPx = providerHeight * density

        val spanX = max(1, ceil(providerWidthPx / cellWidthPx).toInt())
        val spanY = max(1, ceil(providerHeightPx / cellHeightPx).toInt())

        return Pair(spanX, spanY)
    }
}
