package com.riprog.launcher.manager

import com.riprog.launcher.utils.SettingsManager

class GridManager(private val settingsManager: SettingsManager) {

    val columns: Int
        get() = settingsManager.columns

    val rows: Int
        get() = 6

    fun getCellWidth(availableWidth: Int): Int {
        val cols = columns
        return if (availableWidth > 0 && cols > 0) availableWidth / cols else 0
    }

    fun getCellHeight(availableHeight: Int): Int {
        val r = rows
        return if (availableHeight > 0 && r > 0) availableHeight / r else 0
    }

    fun calculateSpanX(pxWidth: Float, cellWidth: Int): Float {
        if (cellWidth <= 0) return 1f
        return pxWidth / cellWidth
    }

    fun calculateSpanY(pxHeight: Float, cellHeight: Int): Float {
        if (cellHeight <= 0) return 1f
        return pxHeight / cellHeight
    }
}
