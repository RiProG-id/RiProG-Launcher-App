package com.riprog.launcher.logic.managers

import android.appwidget.AppWidgetProviderInfo
import com.riprog.launcher.data.model.HomeItem
import java.util.*
import kotlin.math.roundToInt

class PlacementEngine(private val columns: Int, private val rows: Int) {

    fun findSpawnPosition(spanX: Int, spanY: Int, pageItems: List<HomeItem>): Pair<Int, Int>? {
        val occupied = Array(rows) { BooleanArray(columns) }
        for (item in pageItems) {
            val rStart = item.row.roundToInt().coerceIn(0, rows - 1)
            val rEnd = (rStart + item.spanY - 1).coerceIn(0, rows - 1)
            val cStart = item.col.roundToInt().coerceIn(0, columns - 1)
            val cEnd = (cStart + item.spanX - 1).coerceIn(0, columns - 1)
            for (i in rStart..rEnd) {
                for (j in cStart..cEnd) {
                    occupied[i][j] = true
                }
            }
        }

        // Compute center cell
        val centerR = (rows - spanY) / 2
        val centerC = (columns - spanX) / 2

        // If center is available
        if (canPlace(occupied, centerR, centerC, spanX, spanY)) {
            return Pair(centerR, centerC)
        }

        // Spiral search
        return spiralSearch(occupied, centerR, centerC, spanX, spanY)
    }

    private fun spiralSearch(occupied: Array<BooleanArray>, startR: Int, startC: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val directions = listOf(Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0))
        var stepSize = 1
        var currR = startR
        var currC = startC
        var dirIdx = 0

        val maxDimension = maxOf(rows, columns)
        while (stepSize <= maxDimension * 2) {
            for (i in 0 until 2) {
                for (j in 0 until stepSize) {
                    currR += directions[dirIdx].first
                    currC += directions[dirIdx].second

                    if (canPlace(occupied, currR, currC, spanX, spanY)) {
                        return Pair(currR, currC)
                    }
                }
                dirIdx = (dirIdx + 1) % 4
            }
            stepSize++
        }
        return null
    }

    private fun canPlace(occupied: Array<BooleanArray>, r: Int, c: Int, spanX: Int, spanY: Int): Boolean {
        if (r < 0 || r + spanY > rows || c < 0 || c + spanX > columns) return false
        for (i in r until r + spanY) {
            for (j in c until c + spanX) {
                if (occupied[i][j]) return false
            }
        }
        return true
    }

    fun resetWidget(item: HomeItem, calculator: DimensionCalculator, info: AppWidgetProviderInfo, cellWidthPx: Float, cellHeightPx: Float, pageItems: List<HomeItem>) {
        val spans = calculator.calculateSpan(info, cellWidthPx, cellHeightPx, isInitial = true)
        item.spanX = spans.first
        item.spanY = spans.second

        val pos = findSpawnPosition(item.spanX, item.spanY, pageItems.filter { it !== item })
        if (pos != null) {
            item.row = pos.first.toFloat()
            item.col = pos.second.toFloat()
        }

        item.rotation = 0f
        item.scale = 1.0f
        item.tiltX = 0f
        item.tiltY = 0f
    }
}
