package com.riprog.launcher

import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.ArrayList

class LauncherModelTest {
    @Test
    fun testFilterApps() {
        val apps: MutableList<AppItem> = ArrayList()
        apps.add(AppItem("Camera", "com.android.camera", ""))
        apps.add(AppItem("Settings", "com.android.settings", ""))
        apps.add(AppItem("Phone", "com.android.phone", ""))

        var filtered = LauncherModel.filterApps(apps, "cam")
        assertEquals(1, filtered.size)
        assertEquals("Camera", filtered[0].label)

        filtered = LauncherModel.filterApps(apps, "")
        assertEquals(3, filtered.size)

        filtered = LauncherModel.filterApps(apps, "xyz")
        assertEquals(0, filtered.size)
    }
}
