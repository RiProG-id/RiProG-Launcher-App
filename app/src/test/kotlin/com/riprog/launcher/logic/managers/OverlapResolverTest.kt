package com.riprog.launcher.logic.managers

import com.riprog.launcher.data.model.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapResolverTest {

    @Test
    fun testResolveOverlap() {
        val resolver = OverlapResolver(4, 6)

        val item1 = HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0)
        item1.spanX = 2
        item1.spanY = 2

        val item2 = HomeItem.createApp("pkg2", "cls2", 1f, 1f, 0)
        item2.spanX = 1
        item2.spanY = 1

        val allItems = listOf(item1, item2)

        // Item 2 overlaps with item 1 (which is at 0,0 size 2x2)
        val repositioned = resolver.resolve(item1, allItems)

        assertEquals(1, repositioned.size)
        val newPos = repositioned[item2]
        assertTrue(newPos != null)

        // item 1 occupies (0,0), (0,1), (1,0), (1,1)
        // nearest available for item 2 (which was at 1,1) should be somewhere else
        assertTrue(newPos!!.first >= 2 || newPos.second >= 2)
    }
}
