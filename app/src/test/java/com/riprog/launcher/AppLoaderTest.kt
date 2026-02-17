package com.riprog.launcher

import com.riprog.launcher.data.model.AppItem
import com.riprog.launcher.data.repository.AppLoader
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLoaderTest {
    @Test
    fun testFilterApps() {
        val apps = mutableListOf<AppItem>()
        apps.add(AppItem("Camera", "com.android.camera", ""))
        apps.add(AppItem("Settings", "com.android.settings", ""))
        apps.add(AppItem("Phone", "com.android.phone", ""))

        var filtered = AppLoader.filterApps(apps, "cam")
        assertEquals(1, filtered.size)
        assertEquals("Camera", filtered[0].label)

        filtered = AppLoader.filterApps(apps, "")
        assertEquals(3, filtered.size)

        filtered = AppLoader.filterApps(apps, "xyz")
        assertEquals(0, filtered.size)
    }
}
