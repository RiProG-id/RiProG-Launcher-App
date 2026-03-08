package com.riprog.launcher.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeItemTest {

    @Test
    fun testCreateApp() {
        val item = HomeItem.createApp("com.test.pkg", "com.test.cls", 1f, 2f, 0)
        assertEquals(HomeItem.Type.APP, item.type)
        assertEquals("com.test.pkg", item.packageName)
        assertEquals("com.test.cls", item.className)
        assertEquals(1f, item.col)
        assertEquals(2f, item.row)
        assertEquals(0, item.page)
        assertEquals(1f, item.spanX)
        assertEquals(1f, item.spanY)
    }

    @Test
    fun testCreateWidget() {
        val item = HomeItem.createWidget(101, 0f, 0f, 2, 2, 1)
        assertEquals(HomeItem.Type.WIDGET, item.type)
        assertEquals(101, item.widgetId)
        assertEquals(0f, item.col)
        assertEquals(0f, item.row)
        assertEquals(1, item.page)
        assertEquals(2f, item.spanX)
        assertEquals(2f, item.spanY)
    }

    @Test
    fun testCreateClock() {
        val item = HomeItem.createClock(0f, 0f, 4, 2, 0)
        assertEquals(HomeItem.Type.CLOCK, item.type)
        assertEquals(0f, item.col)
        assertEquals(0f, item.row)
        assertEquals(4f, item.spanX)
        assertEquals(2f, item.spanY)
    }

    @Test
    fun testCreateFolder() {
        val item = HomeItem.createFolder("My Folder", 2f, 3f, 0)
        assertEquals(HomeItem.Type.FOLDER, item.type)
        assertEquals("My Folder", item.folderName)
        assertEquals(2f, item.col)
        assertEquals(3f, item.row)
    }
}
