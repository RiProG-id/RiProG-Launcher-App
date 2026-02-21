package com.riprog.launcher.logic.utils

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import com.riprog.launcher.ui.views.home.HomeView
import com.riprog.launcher.logic.managers.SettingsManager
import kotlin.math.ceil

object WidgetSizingUtils {

    fun calculateWidgetSpan(context: Context, homeView: HomeView?, info: AppWidgetProviderInfo): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density

        var cellWidth = homeView?.getCellWidth() ?: 0f
        var cellHeight = homeView?.getCellHeight() ?: 0f

        if (cellWidth <= 0f || cellHeight <= 0f) {
            // Fallback estimation if homeView is not measured yet
            val dm = context.resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels
            val settingsManager = SettingsManager(context)
            val horizontalPadding = 16 * density * 2
            cellWidth = (screenWidth - horizontalPadding) / settingsManager.columns.toFloat()

            val topPadding = 48 * density
            val bottomPadding = 16 * density
            val indicatorHeight = 80 * density
            val systemInsets = 72 * density // Estimation
            val usableHeight = screenHeight - topPadding - bottomPadding - indicatorHeight - systemInsets
            cellHeight = usableHeight / HomeView.GRID_ROWS.toFloat()

            if (cellHeight > cellWidth * 1.1f) cellHeight = cellWidth * 1.1f
        }

        var spanX: Int
        var spanY: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (info.targetCellWidth > 0 || info.targetCellHeight > 0)) {
            spanX = info.targetCellWidth.coerceAtLeast(1)
            spanY = info.targetCellHeight.coerceAtLeast(1)
        } else {
            // Use cell metrics for accurate span calculation
            spanX = ceil(info.minWidth * density / cellWidth).toInt().coerceAtLeast(1)
            spanY = ceil(info.minHeight * density / cellHeight).toInt().coerceAtLeast(1)
        }

        return Pair(spanX, spanY)
    }
}
