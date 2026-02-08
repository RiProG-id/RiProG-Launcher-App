package com.riprog.launcher;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREFS_NAME = "riprog_launcher_prefs";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_WIDGET_ID = "widget_id";
    private static final String KEY_USAGE_PREFIX = "usage_";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getColumns() {
        return prefs.getInt(KEY_COLUMNS, 4);
    }

    public void setColumns(int columns) {
        prefs.edit().putInt(KEY_COLUMNS, columns).apply();
    }

    public int getWidgetId() {
        return prefs.getInt(KEY_WIDGET_ID, -1);
    }

    public void setWidgetId(int widgetId) {
        prefs.edit().putInt(KEY_WIDGET_ID, widgetId).apply();
    }

    public void incrementUsage(String packageName) {
        int current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0);
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + 1).apply();
    }

    public int getUsage(String packageName) {
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0);
    }
}
