package com.riprog.launcher.logic.utils

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import com.riprog.launcher.ui.views.home.HomeView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import android.content.SharedPreferences

class WidgetSizingUtilsTest {

    @Test
    fun testCalculateWidgetSpan_ClampingReproduce() {
        val context = mock<Context>()
        val resources = mock<Resources>()
        val displayMetrics = DisplayMetrics()
        displayMetrics.density = 3.0f
        displayMetrics.widthPixels = 1080
        displayMetrics.heightPixels = 1920

        whenever(context.resources).thenReturn(resources)
        whenever(resources.displayMetrics).thenReturn(displayMetrics)

        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("riprog_launcher_prefs", 0)).thenReturn(prefs)
        whenever(prefs.getInt("columns", 4)).thenReturn(4)
        whenever(prefs.contains("first_run_init")).thenReturn(true)

        val info = AppWidgetProviderInfo()
        info.minWidth = 500
        info.minHeight = 100

        val spans = WidgetSizingUtils.calculateWidgetSpan(context, null, info)
        println("Calculated spans: ${spans.first}x${spans.second}")
        assertTrue(spans.first <= 4, "SpanX should be clamped to 4, but was ${spans.first}")
        assertTrue(spans.second <= 6, "SpanY should be clamped to 6, but was ${spans.second}")
    }

    @Test
    fun testGetMinSpanX_Clamping() {
        val info = AppWidgetProviderInfo()
        info.minWidth = 500 // 500dp
        val density = 3.0f
        val cellWidth = 200f // pixels. 500 * 3 / 200 = 7.5 -> 8

        val spanX = WidgetSizingUtils.getMinSpanX(info, cellWidth, density)
        assertEquals(4, spanX) // Should be clamped to HomeView.GRID_COLUMNS (4)
    }

    @Test
    fun testGetMaxSpanX_Clamping() {
        val info = AppWidgetProviderInfo()
        info.maxResizeWidth = 1000
        val density = 3.0f
        val cellWidth = 100f // 1000 * 3 / 100 = 30

        // This test will hit the 'else' block because Build.VERSION.SDK_INT is not >= S in unit tests
        // But the result should still be clamped to HomeView.GRID_COLUMNS (4)
        val spanX = WidgetSizingUtils.getMaxSpanX(info, cellWidth, density)
        assertEquals(4, spanX)
    }
}
