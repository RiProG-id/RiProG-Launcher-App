package com.riprog.launcher.features.home

import com.riprog.launcher.data.models.HomeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PageMappingTest {

    @Test
    fun testAddPageLeftShiftsIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 1))

        // Simulating addPageAtIndex(0)
        val indexToAdd = 0
        items.forEach {
            if (it.page >= indexToAdd) it.page++
        }

        assertEquals(1, items[0].page)
        assertEquals(2, items[1].page)
    }

    @Test
    fun testAddPageMiddleShiftsIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 1))

        // Simulating addPageAtIndex(1)
        val indexToAdd = 1
        items.forEach {
            if (it.page >= indexToAdd) it.page++
        }

        assertEquals(0, items[0].page)
        assertEquals(2, items[1].page)
    }

    @Test
    fun testRemovePageShiftsIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg0", "cls0", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 1))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 2))

        // Simulating removePage(1)
        val indexToRemove = 1
        items.removeAll { it.page == indexToRemove }
        items.forEach {
            if (it.page > indexToRemove) it.page--
        }

        assertEquals(2, items.size)
        assertEquals(0, items[0].page)
        assertEquals(1, items[1].page)
    }
}
