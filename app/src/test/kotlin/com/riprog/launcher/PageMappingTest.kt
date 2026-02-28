package com.riprog.launcher

import com.riprog.launcher.data.model.HomeItem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.math.floor

class PageMappingTest {

    @Test
    fun testResolvePageIndex() {
        val width = 1080
        val pageW = width

        // Scenario: scrolled to page 1 exactly (scrollX = 1080)
        // User touches at x = 540 (middle of screen)
        // adjustedX = 540
        // scrollX = 1080
        // relativeX = 540 + 1080 = 1620
        // index = floor(1620 / 1080) = 1

        val scrollX = 1080
        val adjustedX = 540f
        val relativeX = adjustedX + scrollX
        val index = floor((relativeX / pageW).toDouble()).toInt()

        assertEquals(1, index)

        // Scenario: adjustedX = 100 (left edge of screen, but on page 1)
        // relativeX = 100 + 1080 = 1180
        // index = 1
        assertEquals(1, floor(((100f + 1080) / pageW).toDouble()).toInt())

        // Scenario: adjustedX = 1000 (right edge of screen, but on page 1)
        // relativeX = 1000 + 1080 = 2080
        // index = 1
        assertEquals(1, floor(((1000f + 1080) / pageW).toDouble()).toInt())

        // Scenario: dragged to page 2
        // adjustedX = 1100 (slightly off right edge of screen)
        // relativeX = 1100 + 1080 = 2180
        // index = 2
        assertEquals(2, floor(((1100f + 1080) / pageW).toDouble()).toInt())
    }
}
