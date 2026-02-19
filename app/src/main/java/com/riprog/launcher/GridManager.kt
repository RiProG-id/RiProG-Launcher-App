package com.riprog.launcher

import kotlin.math.max

class GridManager(private val columns: Int) {
    private val rows = 6

    fun getCellWidth(availableWidth: Int): Int {
        return availableWidth / max(1, columns)
    }

    fun getCellHeight(availableHeight: Int): Int {
        return availableHeight / max(1, rows)
    }

    fun calculateSpanX(widthPx: Float, cellWidth: Int): Float {
        return if (cellWidth <= 0) 1f else widthPx / cellWidth
    }

    fun calculateSpanY(heightPx: Float, cellHeight: Int): Float {
        return if (cellHeight <= 0) 1f else heightPx / cellHeight
    }
}
