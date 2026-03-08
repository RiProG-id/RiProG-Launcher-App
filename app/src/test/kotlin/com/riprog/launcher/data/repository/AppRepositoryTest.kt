package com.riprog.launcher.data.repository

import com.riprog.launcher.data.models.AppItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.ArrayList

class AppRepositoryTest {

    @Test
    fun testFilterApps() {
        val apps: MutableList<AppItem> = ArrayList()
        apps.add(AppItem("Camera", "com.android.camera", "cls1"))
        apps.add(AppItem("Settings", "com.android.settings", "cls2"))
        apps.add(AppItem("Phone", "com.android.phone", "cls3"))

        // Exact match
        var filtered = AppRepository.filterApps(apps, "Camera")
        assertEquals(1, filtered.size)
        assertEquals("Camera", filtered[0].label)

        // Case-insensitive partial match
        filtered = AppRepository.filterApps(apps, "set")
        assertEquals(1, filtered.size)
        assertEquals("Settings", filtered[0].label)

        // No match
        filtered = AppRepository.filterApps(apps, "xyz")
        assertEquals(0, filtered.size)

        // Empty query
        filtered = AppRepository.filterApps(apps, "")
        assertEquals(3, filtered.size)

        // Null query
        filtered = AppRepository.filterApps(apps, null)
        assertEquals(3, filtered.size)
    }
}
