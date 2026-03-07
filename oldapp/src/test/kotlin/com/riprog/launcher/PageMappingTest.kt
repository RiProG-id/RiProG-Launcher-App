package com.riprog.launcher

import com.riprog.launcher.data.model.HomeItem
import org.junit.Test
import org.junit.Assert.assertEquals

class PageMappingTest {
    @Test
    fun testAddPageLeftShiftsIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 1))

        val indexToAdd = 0
        items.forEach {
            if (it.page >= indexToAdd) it.page++
        }

        assertEquals(1, items[0].page)
        assertEquals(2, items[1].page)
    }

    @Test
    fun testAddPageRightDoesNotShiftIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 1))

        val indexToAdd = 2
        items.forEach {
            if (it.page >= indexToAdd) it.page++
        }

        assertEquals(0, items[0].page)
        assertEquals(1, items[1].page)
    }

    @Test
    fun testRemovePageShiftsIndices() {
        val items = mutableListOf<HomeItem>()
        items.add(HomeItem.createApp("pkg1", "cls1", 0f, 0f, 0))
        items.add(HomeItem.createApp("pkg2", "cls2", 0f, 0f, 1))
        items.add(HomeItem.createApp("pkg3", "cls3", 0f, 0f, 2))

        val indexToRemove = 1
        items.removeAll { it.page == indexToRemove }
        items.forEach {
            if (it.page > indexToRemove) it.page--
        }

        assertEquals(0, items[0].page)
        assertEquals(1, items[1].page)
        assertEquals(2, items.size)
    }
}
