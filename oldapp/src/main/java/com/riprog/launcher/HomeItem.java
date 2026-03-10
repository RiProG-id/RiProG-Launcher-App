package com.riprog.launcher;

public class HomeItem {
    public enum Type { APP, WIDGET, CLOCK }

    public Type type;
    public String packageName;
    public String className;
    public float row;
    public float col;
    public int spanX = 1;
    public int spanY = 1;
    public int page;
    public int widgetId = -1;

    // Advanced Freeform Transformations
    public float rotation = 0f;
    public float scale = 1.0f;
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

    public static HomeItem createWidget(int widgetId, float col, float row, int spanX, int spanY, int page) {
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

    public static HomeItem createClock(float col, float row, int spanX, int spanY, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.CLOCK;
        item.col = col;
        item.row = row;
        item.spanX = spanX;
        item.spanY = spanY;
        item.page = page;
        return item;
    }
}
