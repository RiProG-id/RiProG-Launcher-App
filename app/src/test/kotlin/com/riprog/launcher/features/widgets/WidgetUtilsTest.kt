package com.riprog.launcher.features.widgets

import android.appwidget.AppWidgetProviderInfo
import com.riprog.launcher.features.home.HomeView
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetUtilsTest {

    @Test
    fun testGetMinSpanX() {
        val info = AppWidgetProviderInfo()
        info.minWidth = 140 // 2 columns if 70dp per col
        val density = 1.0f
        val cellWidth = 70f

        val spanX = WidgetUtils.getMinSpanX(info, cellWidth, density)
        assertEquals(2, spanX)
    }

    @Test
    fun testGetMinSpanX_Clamped() {
        val info = AppWidgetProviderInfo()
        info.minWidth = 1000
        val density = 1.0f
        val cellWidth = 100f

        val spanX = WidgetUtils.getMinSpanX(info, cellWidth, density)
        assertEquals(HomeView.GRID_COLUMNS, spanX)
    }

    @Test
    fun testGetMinSpanY() {
        val info = AppWidgetProviderInfo()
        info.minHeight = 140
        val density = 1.0f
        val cellHeight = 70f

        val spanY = WidgetUtils.getMinSpanY(info, cellHeight, density, 6)
        assertEquals(2, spanY)
    }

    @Test
    fun testGetMaxSpanX() {
        val info = AppWidgetProviderInfo()
        // If maxResizeWidth is 0, it should return HomeView.GRID_COLUMNS
        info.maxResizeWidth = 0
        val density = 1.0f
        val cellWidth = 100f
        assertEquals(HomeView.GRID_COLUMNS, WidgetUtils.getMaxSpanX(info, cellWidth, density))
    }
}
