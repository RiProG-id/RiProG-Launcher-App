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
}
