package com.riprog.launcher;

import java.util.ArrayList;
import java.util.List;

public class HomeItem {
    public enum Type { APP, WIDGET, CLOCK, FOLDER }

    public Type type;
    public String packageName;
    public String className;
    public float row;
    public float col;
    public float spanX = 1f;
    public float spanY = 1f;
    public int page;
    public int widgetId = -1;
    public String folderName;
    public List<HomeItem> folderItems;

    // Advanced Freeform Transformations
    public float rotation = 0f;
    public float scaleX = 1.0f;
    public float scaleY = 1.0f;
    public float tiltX = 0f;
    public float tiltY = 0f;

    public HomeItem() {}

    public static HomeItem createApp(String packageName, String className, float col, float row, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.APP;
        item.packageName = packageName;
        item.className = className;
        item.col = col;
        item.row = row;
        item.spanX = 1;
        item.spanY = 1;
        item.page = page;
        return item;
    }

    public static HomeItem createWidget(int widgetId, float col, float row, float spanX, float spanY, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.WIDGET;
        item.widgetId = widgetId;
        item.col = col;
        item.row = row;
        item.spanX = spanX;
        item.spanY = spanY;
        item.page = page;
        return item;
    }

    public static HomeItem createClock(float col, float row, float spanX, float spanY, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.CLOCK;
        item.col = col;
        item.row = row;
        item.spanX = spanX;
        item.spanY = spanY;
        item.page = page;
        return item;
    }

    public static HomeItem createFolder(String name, float col, float row, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.FOLDER;
        item.folderName = name;
        item.col = col;
        item.row = row;
        item.spanX = 1;
        item.spanY = 1;
        item.page = page;
        item.folderItems = new ArrayList<>();
        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HomeItem item = (HomeItem) o;
        if (type != item.type) return false;
        if (widgetId != -1 && widgetId == item.widgetId) return true;
        if (packageName != null ? !packageName.equals(item.packageName) : item.packageName != null) return false;
        if (className != null ? !className.equals(item.className) : item.className != null) return false;
        return Float.compare(item.col, col) == 0 && Float.compare(item.row, row) == 0 && page == item.page;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (packageName != null ? packageName.hashCode() : 0);
        result = 31 * result + (className != null ? className.hashCode() : 0);
        result = 31 * result + Float.floatToIntBits(col);
        result = 31 * result + Float.floatToIntBits(row);
        result = 31 * result + page;
        result = 31 * result + widgetId;
        return result;
    }
}
