package com.riprog.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class SettingsManager {
    private static final String PREFS_NAME = "riprog_launcher_prefs";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_WIDGET_ID = "widget_id";
    private static final String KEY_USAGE_PREFIX = "usage_";
    private static final String KEY_HOME_ITEMS = "home_items";

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

    public void saveHomeItems(List<HomeItem> items) {
        JSONArray array = new JSONArray();
        for (HomeItem item : items) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", item.type.name());
                obj.put("packageName", item.packageName);
                obj.put("className", item.className);
                obj.put("x", item.x);
                obj.put("y", item.y);
                obj.put("width", item.width);
                obj.put("height", item.height);
                obj.put("page", item.page);
                obj.put("widgetId", item.widgetId);
                array.put(obj);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_HOME_ITEMS, array.toString()).apply();
    }

    public List<HomeItem> getHomeItems() {
        List<HomeItem> items = new ArrayList<>();
        String json = prefs.getString(KEY_HOME_ITEMS, null);
        if (json == null) return items;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                HomeItem item = new HomeItem();
                item.type = HomeItem.Type.valueOf(obj.getString("type"));
                item.packageName = obj.optString("packageName", null);
                item.className = obj.optString("className", null);
                item.x = obj.getInt("x");
                item.y = obj.getInt("y");
                item.width = obj.getInt("width");
                item.height = obj.getInt("height");
                item.page = obj.getInt("page");
                item.widgetId = obj.getInt("widgetId");
                items.add(item);
            }
        } catch (JSONException ignored) {}
        return items;
    }
}
