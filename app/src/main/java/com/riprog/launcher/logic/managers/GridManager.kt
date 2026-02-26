package com.riprog.launcher.logic.managers

import kotlin.math.max
import kotlin.math.min

class GridManager(private val columns: Int) {
    private val rows = 6

    fun getCellWidth(availableWidth: Int): Int {
        // dp estimate
        val horizontalPadding = 32
        val cw = (availableWidth - horizontalPadding).coerceAtLeast(0) / max(1, columns)
        return cw
    }

    fun getCellHeight(availableWidth: Int, availableHeight: Int): Int {
        // Estimate
        val verticalPaddings = 48 + 16 + 80 + 72
        val usableHeight = (availableHeight - verticalPaddings).coerceAtLeast(0)
        var ch = usableHeight / max(1, rows)

        val cw = getCellWidth(availableWidth)
        if (ch > cw * 1.1) ch = (cw * 1.1).toInt()

        return ch
    }

    fun calculateSpanX(widthPx: Float, cellWidth: Int): Float {
        return if (cellWidth <= 0) 1f else widthPx / cellWidth
    }

    fun calculateSpanY(heightPx: Float, cellHeight: Int): Float {
        return if (cellHeight <= 0) 1f else heightPx / cellHeight
    }
}
