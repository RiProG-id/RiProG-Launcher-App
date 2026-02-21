package com.riprog.launcher.logic.managers

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class DimensionCalculatorTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    private lateinit var mockInfo: AppWidgetProviderInfo

    private lateinit var dimensionCalculator: DimensionCalculator

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        mockDisplayMetrics.density = 2.0f
        dimensionCalculator = DimensionCalculator(mockContext)
    }

    @Test
    fun testCalculateSpan_InitialScaling() {
        // minWidth = 100dp, minHeight = 100dp
        // density = 2.0
        // minWidthPx = 200px, minHeightPx = 200px
        // Initial scaling 0.75x -> 150px, 150px

        val dims = DimensionCalculator.WidgetDimensions(100, 100, 100, 100, 0)

        val cellWidthPx = 100f
        val cellHeightPx = 100f

        val spans = dimensionCalculator.calculateSpan(dims, cellWidthPx, cellHeightPx, isInitial = true)

        // ceil(150 / 100) = 2
        assertEquals(2, spans.first)
        assertEquals(2, spans.second)
    }

    @Test
    fun testCalculateSpan_NoScaling() {
        val dims = DimensionCalculator.WidgetDimensions(100, 100, 100, 100, 0)

        val cellWidthPx = 100f
        val cellHeightPx = 100f

        val spans = dimensionCalculator.calculateSpan(dims, cellWidthPx, cellHeightPx, isInitial = false)

        // ceil(200 / 100) = 2
        assertEquals(2, spans.first)
        assertEquals(2, spans.second)
    }
}
