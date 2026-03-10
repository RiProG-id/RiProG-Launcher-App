package com.riprog.launcher;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;

public class LauncherModelTest {
    @Test
    public void testFilterApps() {
        List<AppItem> apps = new ArrayList<>();
        apps.add(new AppItem("Camera", "com.android.camera", ""));
        apps.add(new AppItem("Settings", "com.android.settings", ""));
        apps.add(new AppItem("Phone", "com.android.phone", ""));

        List<AppItem> filtered = LauncherModel.filterApps(apps, "cam");
        assertEquals(1, filtered.size());
        assertEquals("Camera", filtered.get(0).label);

        filtered = LauncherModel.filterApps(apps, "");
        assertEquals(3, filtered.size());

        filtered = LauncherModel.filterApps(apps, "xyz");
        assertEquals(0, filtered.size());
    }
}
