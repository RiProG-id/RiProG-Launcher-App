package com.riprog.launcher.data.repository

import com.riprog.launcher.data.model.AppItem

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.ArrayList

class AppRepositoryTest {
    @Test
    fun testFilterApps() {
        val apps: MutableList<AppItem> = ArrayList()
        apps.add(AppItem("Camera", "com.android.camera", ""))
        apps.add(AppItem("Settings", "com.android.settings", ""))
        apps.add(AppItem("Phone", "com.android.phone", ""))

        var filtered = AppRepository.filterApps(apps, "cam")
        assertEquals(1, filtered.size)
        assertEquals("Camera", filtered[0].label)

        filtered = AppRepository.filterApps(apps, "")
        assertEquals(3, filtered.size)

        filtered = AppRepository.filterApps(apps, "xyz")
        assertEquals(0, filtered.size)
    }
}
