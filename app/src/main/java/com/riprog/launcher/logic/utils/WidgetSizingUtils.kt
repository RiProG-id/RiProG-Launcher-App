package com.riprog.launcher.logic.utils

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.logic.managers.SettingsManager
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object WidgetSizingUtils {

    fun calculateWidgetSpan(context: Context, homeView: HomeView?, info: AppWidgetProviderInfo): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val settingsManager = SettingsManager(context)
        val maxColumns = settingsManager.columns
        val maxRows = HomeView.GRID_ROWS

        var cellWidth = homeView?.getCellWidth() ?: 0f
        var cellHeight = homeView?.getCellHeight() ?: 0f

        if (cellWidth <= 0f || cellHeight <= 0f) {
            // Fallback estimation if homeView is not measured yet
            val dm = context.resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels

            val horizontalPadding = 16 * density * 2
            cellWidth = if (screenWidth > horizontalPadding) (screenWidth - horizontalPadding) / maxColumns.toFloat() else 0f

            // Alignment with HomeView.getCellHeight()
            val topPadding = 48 * density
            val bottomPadding = 16 * density
            val indicatorHeight = 20 * density // Matches HomeView.kt
            val systemInsets = 72 * density // Combined status bar and nav bar estimation
            val usableHeight = screenHeight - topPadding - bottomPadding - indicatorHeight - systemInsets
            cellHeight = if (usableHeight > 0) usableHeight / maxRows.toFloat() else 0f
        }

        var spanX: Int
        var spanY: Int

        // Android 12+ provides targetCellWidth/Height which are already in spans
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (info.targetCellWidth > 0 || info.targetCellHeight > 0)) {
            spanX = info.targetCellWidth.coerceAtLeast(1)
            spanY = info.targetCellHeight.coerceAtLeast(1)
        } else {
            // Use cell metrics for accurate span calculation
            // If cell dimensions are invalid, fallback to standard 70dp formula
            if (cellWidth > 0 && cellHeight > 0) {
                spanX = ceil(info.minWidth * density / cellWidth).toInt().coerceAtLeast(1)
                spanY = ceil(info.minHeight * density / cellHeight).toInt().coerceAtLeast(1)
            } else {
                spanX = ceil((info.minWidth + 30) / 70.0).toInt().coerceAtLeast(1)
                spanY = ceil((info.minHeight + 30) / 70.0).toInt().coerceAtLeast(1)
            }
        }

        // Clamp span values to launcher grid maximum to avoid unrealistic sizes
        spanX = min(spanX, maxColumns)
        spanY = min(spanY, maxRows)

        return Pair(spanX, spanY)
    }

    fun getMinSpanX(info: AppWidgetProviderInfo, cellWidth: Float, density: Float): Int {
        val minWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
        val spanX = if (cellWidth > 0) {
            ceil(minWidth * density / cellWidth).toInt().coerceAtLeast(1)
        } else {
            ceil((minWidth + 30) / 70.0).toInt().coerceAtLeast(1)
        }
        return min(spanX, HomeView.GRID_COLUMNS) // Note: HomeView.GRID_COLUMNS is 4, but we should ideally use SettingsManager.columns if available
    }

    fun getMinSpanY(info: AppWidgetProviderInfo, cellHeight: Float, density: Float): Int {
        val minHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        val spanY = if (cellHeight > 0) {
            ceil(minHeight * density / cellHeight).toInt().coerceAtLeast(1)
        } else {
            ceil((minHeight + 30) / 70.0).toInt().coerceAtLeast(1)
        }
        return min(spanY, HomeView.GRID_ROWS)
    }

    fun getMaxSpanX(info: AppWidgetProviderInfo, cellWidth: Float, density: Float): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeWidth > 0 && cellWidth > 0) {
            val spanX = (info.maxResizeWidth * density / cellWidth).toInt().coerceAtLeast(1)
            return min(spanX, HomeView.GRID_COLUMNS)
        }
        return HomeView.GRID_COLUMNS
    }

    fun getMaxSpanY(info: AppWidgetProviderInfo, cellHeight: Float, density: Float): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.maxResizeHeight > 0 && cellHeight > 0) {
            val spanY = (info.maxResizeHeight * density / cellHeight).toInt().coerceAtLeast(1)
            return min(spanY, HomeView.GRID_ROWS)
        }
        return HomeView.GRID_ROWS
    }
}
