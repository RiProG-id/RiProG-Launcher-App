package com.riprog.launcher

import com.riprog.launcher.data.model.HomeItem
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FreeformZOrderTest {

    @Test
    fun testZOrderSortingLogic() {

        val largeItem = HomeItem.createApp("large", "cls", 0f, 0f, 0).apply {
            spanX = 2f
            spanY = 2f
            lastInteractionTime = 1000L
        }

        val smallItem = HomeItem.createApp("small", "cls", 0f, 0f, 0).apply {
            spanX = 1f
            spanY = 1f
            lastInteractionTime = 500L
        }

        val items = listOf(largeItem, smallItem)

        val sortedItems = items.sortedWith(compareByDescending<HomeItem> {
            it.spanX * it.spanY
        }.thenBy {
            it.lastInteractionTime
        })

        assertEquals("Larger item should come first in sorted list (to be at bottom)", largeItem, sortedItems[0])
        assertEquals("Smaller item should come last in sorted list (to be at top)", smallItem, sortedItems[1])
    }

    @Test
    fun testZOrderSameSizeSortingLogic() {

        val itemOld = HomeItem.createApp("old", "cls", 0f, 0f, 0).apply {
            spanX = 1f
            spanY = 1f
            lastInteractionTime = 500L
        }

        val itemNew = HomeItem.createApp("new", "cls", 0f, 0f, 0).apply {
            spanX = 1f
            spanY = 1f
            lastInteractionTime = 1000L
        }

        val items = listOf(itemNew, itemOld)

        val sortedItems = items.sortedWith(compareByDescending<HomeItem> {
            it.spanX * it.spanY
        }.thenBy {
            it.lastInteractionTime
        })

        assertEquals("Older item should come first (bottom)", itemOld, sortedItems[0])
        assertEquals("Newer item should come last (top)", itemNew, sortedItems[1])
    }
}
