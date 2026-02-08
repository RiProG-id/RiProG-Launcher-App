package com.riprog.launcher;

public class HomeItem {
    public enum Type { APP, WIDGET, CLOCK }

    public Type type;
    public String packageName;
    public String className;
    public int x;
    public int y;
    public int width;
    public int height;
    public int page;
    public int widgetId = -1;

    public HomeItem() {}

    public static HomeItem createApp(String packageName, String className, int x, int y, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.APP;
        item.packageName = packageName;
        item.className = className;
        item.x = x;
        item.y = y;
        item.page = page;
        return item;
    }

    public static HomeItem createWidget(int widgetId, int x, int y, int w, int h, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.WIDGET;
        item.widgetId = widgetId;
        item.x = x;
        item.y = y;
        item.width = w;
        item.height = h;
        item.page = page;
        return item;
    }

    public static HomeItem createClock(int x, int y, int page) {
        HomeItem item = new HomeItem();
        item.type = Type.CLOCK;
        item.x = x;
        item.y = y;
        item.page = page;
        return item;
    }
}
