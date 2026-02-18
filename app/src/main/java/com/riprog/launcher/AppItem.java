package com.riprog.launcher;

import android.content.ComponentName;

public class AppItem {
    public final String label;
    public final String packageName;
    public final String className;

    public AppItem(String label, String packageName, String className) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
    }

    public ComponentName getComponentName() {
        return new ComponentName(packageName, className);
    }

    public static AppItem fromPackage(android.content.Context context, String packageName) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return new AppItem(pm.getApplicationLabel(ai).toString(), packageName, "");
        } catch (Exception e) {
            return new AppItem("...", packageName, "");
        }
    }
}
