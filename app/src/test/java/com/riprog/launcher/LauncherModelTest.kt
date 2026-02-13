package com.riprog.launcher

import org.junit.Test
import org.junit.Assert.*

class LauncherModelTest {
    @Test
    fun testFilterApps() {
        val apps = listOf(
            AppItem("Camera", "com.android.camera", ""),
            AppItem("Settings", "com.android.settings", ""),
            AppItem("Phone", "com.android.phone", "")
        )

        var filtered = LauncherModel.filterApps(apps, "cam")
        assertEquals(1, filtered.size)
        assertEquals("Camera", filtered[0].label)

        filtered = LauncherModel.filterApps(apps, "")
        assertEquals(3, filtered.size)

        filtered = LauncherModel.filterApps(apps, "xyz")
        assertEquals(0, filtered.size)
    }
}
