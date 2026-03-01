package com.riprog.launcher.logic.utils

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.riprog.launcher.data.model.HomeItem
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

            val dm = context.resources.displayMetrics
            val screenWidth = dm.widthPixels
            val screenHeight = dm.heightPixels

            val horizontalPadding = HomeView.HORIZONTAL_PADDING_DP * density * 2
            cellWidth = if (screenWidth > horizontalPadding) (screenWidth - horizontalPadding) / maxColumns.toFloat() else 0f
            cellHeight = cellWidth
        }

        var spanX: Int
        var spanY: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (info.targetCellWidth > 0 || info.targetCellHeight > 0)) {
            spanX = info.targetCellWidth.coerceAtLeast(1)
            spanY = info.targetCellHeight.coerceAtLeast(1)
        } else {

            if (cellWidth > 0 && cellHeight > 0) {
                spanX = ceil(info.minWidth * density / cellWidth).toInt().coerceAtLeast(1)
                spanY = ceil(info.minHeight * density / cellHeight).toInt().coerceAtLeast(1)
            } else {
                spanX = ceil((info.minWidth + 30) / 70.0).toInt().coerceAtLeast(1)
                spanY = ceil((info.minHeight + 30) / 70.0).toInt().coerceAtLeast(1)
            }
        }

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
        return min(spanX, HomeView.GRID_COLUMNS)
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

    fun getVisualBounds(view: View): RectF {
        val lp = view.layoutParams
        val vWidth = if (view.width > 0) view.width.toFloat() else if (lp != null && lp.width > 0) lp.width.toFloat() else 0f
        val vHeight = if (view.height > 0) view.height.toFloat() else if (lp != null && lp.height > 0) lp.height.toFloat() else 0f

        if (view !is ViewGroup) return RectF(0f, 0f, vWidth, vHeight)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var hasVisibleChildren = false

        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val clp = child.layoutParams
                val cw = if (child.width > 0) child.width.toFloat() else if (clp != null && clp.width > 0) clp.width.toFloat() else if (clp != null && clp.width == -1) vWidth else 0f
                val ch = if (child.height > 0) child.height.toFloat() else if (clp != null && clp.height > 0) clp.height.toFloat() else if (clp != null && clp.height == -1) vHeight else 0f

                if (cw > 0 && ch > 0) {
                    if (child is TextView && view.tag is HomeItem) {
                        val type = (view.tag as HomeItem).type
                        if (type == HomeItem.Type.APP || type == HomeItem.Type.FOLDER) continue
                    }
                    minX = min(minX, child.x)
                    minY = min(minY, child.y)
                    maxX = max(maxX, child.x + cw)
                    maxY = max(maxY, child.y + ch)
                    hasVisibleChildren = true
                }
            }
        }

        if (!hasVisibleChildren) return RectF(0f, 0f, vWidth, vHeight)
        return RectF(minX, minY, maxX, maxY)
    }
}