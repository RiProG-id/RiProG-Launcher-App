package com.riprog.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsManager {
    private static final String PREFS_NAME = "riprog_launcher_prefs";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_WIDGET_ID = "widget_id";
    private static final String KEY_USAGE_PREFIX = "usage_";
    private static final String KEY_HOME_ITEMS = "home_items";
    private static final String KEY_FREEFORM_HOME = "freeform_home";
    private static final String KEY_ICON_SCALE = "icon_scale";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_HIDE_LABELS = "hide_labels";
    private static final String KEY_LIQUID_GLASS = "liquid_glass";
    private static final String KEY_DARKEN_WALLPAPER = "darken_wallpaper";
    private static final String KEY_DRAWER_OPEN_COUNT = "drawer_open_count";
    private static final String KEY_DEFAULT_PROMPT_TIMESTAMP = "default_prompt_ts";
    private static final String KEY_DEFAULT_PROMPT_COUNT = "default_prompt_count";

    private final SharedPreferences prefs;
    private final Context context;

    public SettingsManager(Context context) {
        this.context = context;
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

    public boolean isFreeformHome() {
        return prefs.getBoolean(KEY_FREEFORM_HOME, false);
    }

    public void setFreeformHome(boolean freeform) {
        prefs.edit().putBoolean(KEY_FREEFORM_HOME, freeform).apply();
    }

    public float getIconScale() {
        return prefs.getFloat(KEY_ICON_SCALE, 1.0f);
    }

    public void setIconScale(float scale) {
        prefs.edit().putFloat(KEY_ICON_SCALE, scale).apply();
    }

    public boolean isHideLabels() {
        return prefs.getBoolean(KEY_HIDE_LABELS, false);
    }

    public void setHideLabels(boolean hide) {
        prefs.edit().putBoolean(KEY_HIDE_LABELS, hide).apply();
    }

    public boolean isLiquidGlass() {
        return prefs.getBoolean(KEY_LIQUID_GLASS, true);
    }

    public void setLiquidGlass(boolean enabled) {
        prefs.edit().putBoolean(KEY_LIQUID_GLASS, enabled).apply();
    }

    public boolean isDarkenWallpaper() {
        return prefs.getBoolean(KEY_DARKEN_WALLPAPER, true);
    }

    public void setDarkenWallpaper(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARKEN_WALLPAPER, enabled).apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, "system");
    }

    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public void incrementUsage(String packageName) {
        int current = prefs.getInt(KEY_USAGE_PREFIX + packageName, 0);
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + 1).apply();
    }

    public int getUsage(String packageName) {
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0);
    }

    public int getDrawerOpenCount() {
        return prefs.getInt(KEY_DRAWER_OPEN_COUNT, 0);
    }

    public void incrementDrawerOpenCount() {
        prefs.edit().putInt(KEY_DRAWER_OPEN_COUNT, getDrawerOpenCount() + 1).apply();
    }

    public long getLastDefaultPromptTimestamp() {
        return prefs.getLong(KEY_DEFAULT_PROMPT_TIMESTAMP, 0);
    }

    public void setLastDefaultPromptTimestamp(long ts) {
        prefs.edit().putLong(KEY_DEFAULT_PROMPT_TIMESTAMP, ts).apply();
    }

    public int getDefaultPromptCount() {
        return prefs.getInt(KEY_DEFAULT_PROMPT_COUNT, 0);
    }

    public void incrementDefaultPromptCount() {
        prefs.edit().putInt(KEY_DEFAULT_PROMPT_COUNT, getDefaultPromptCount() + 1).apply();
    }

    private File getPageFile(int pageIndex) {
        return new File(context.getFilesDir(), "home_page_" + pageIndex + ".json");
    }

    private void writeToFile(File file, String data) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    private String readFromFile(File file) {
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            in.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void savePageItems(int pageIndex, List<HomeItem> items) {
        if (items == null) return;
        JSONArray array = new JSONArray();
        for (HomeItem item : items) {
            if (item.page == pageIndex) {
                JSONObject obj = serializeItem(item);
                if (obj != null) array.put(obj);
            }
        }
        writeToFile(getPageFile(pageIndex), array.toString());
    }

    public List<HomeItem> getPageItems(int pageIndex) {
        List<HomeItem> items = new ArrayList<>();
        String json = readFromFile(getPageFile(pageIndex));

        // Fallback to SharedPreferences during migration
        if (json == null) {
            json = prefs.getString(KEY_HOME_ITEMS + "_page_" + pageIndex, null);
        }

        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    HomeItem item = deserializeItem(array.getJSONObject(i));
                    if (item != null) items.add(item);
                }
            } catch (Exception ignored) {}
        }
        return items;
    }

    public void removePageData(int index, int oldPageCount) {
        // Shift file data from index+1...oldPageCount-1 down to index...oldPageCount-2
        for (int i = index; i < oldPageCount - 1; i++) {
            File currentFile = getPageFile(i);
            File nextFile = getPageFile(i + 1);
            if (nextFile.exists()) {
                nextFile.renameTo(currentFile);
            } else {
                currentFile.delete();
            }

            // Also clean up SharedPreferences if they exist
            prefs.edit().remove(KEY_HOME_ITEMS + "_page_" + i).apply();
        }
        // Remove the last one
        getPageFile(oldPageCount - 1).delete();
        prefs.edit().remove(KEY_HOME_ITEMS + "_page_" + (oldPageCount - 1)).apply();

        prefs.edit().putInt("page_count", oldPageCount - 1).apply();
    }

    public void saveHomeItems(List<HomeItem> items, int pageCount) {
        if (items == null) return;

        for (int i = 0; i < pageCount; i++) {
            savePageItems(i, items);
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("page_count", pageCount);
        editor.remove(KEY_HOME_ITEMS); // Clear old unified storage
        editor.apply();
    }

    private JSONObject serializeItem(HomeItem item) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("type", item.type.name());
            obj.put("packageName", item.packageName);
            obj.put("className", item.className);
            obj.put("col", (double) item.col);
            obj.put("row", (double) item.row);
            obj.put("spanX", (double) item.spanX);
            obj.put("spanY", (double) item.spanY);
            obj.put("page", item.page);
            obj.put("widgetId", item.widgetId);
            obj.put("rotation", (double) item.rotation);
            obj.put("scaleX", (double) item.scaleX);
            obj.put("scaleY", (double) item.scaleY);
            obj.put("tiltX", (double) item.tiltX);
            obj.put("tiltY", (double) item.tiltY);
            obj.put("folderName", item.folderName);
            if (item.folderItems != null) {
                JSONArray folderArray = new JSONArray();
                for (HomeItem subItem : item.folderItems) {
                    JSONObject subObj = serializeItem(subItem);
                    if (subObj != null) folderArray.put(subObj);
                }
                obj.put("folderItems", folderArray);
            }
            return obj;
        } catch (JSONException e) {
            return null;
        }
    }

    public List<HomeItem> getHomeItems() {
        List<HomeItem> items = new ArrayList<>();
        int pageCount = prefs.getInt("page_count", 0);

        if (pageCount == 0) {
            // Migration logic for old unified storage
            String json = prefs.getString(KEY_HOME_ITEMS, null);
            if (json != null) {
                try {
                    JSONArray array = new JSONArray(json);
                    for (int i = 0; i < array.length(); i++) {
                        HomeItem item = deserializeItem(array.getJSONObject(i));
                        if (item != null) items.add(item);
                    }
                } catch (Exception ignored) {}
            }
            return items;
        }

        for (int i = 0; i < pageCount; i++) {
            String json = prefs.getString(KEY_HOME_ITEMS + "_page_" + i, null);
            if (json != null) {
                try {
                    JSONArray array = new JSONArray(json);
                    for (int j = 0; j < array.length(); j++) {
                        HomeItem item = deserializeItem(array.getJSONObject(j));
                        if (item != null) items.add(item);
                    }
                } catch (Exception ignored) {}
            }
        }
        return items;
    }

    public int getPageCount() {
        return prefs.getInt("page_count", 2); // Default to 2 if not set
    }

    private HomeItem deserializeItem(JSONObject obj) {
        if (!obj.has("type")) return null;
        HomeItem item = new HomeItem();
        try {
            item.type = HomeItem.Type.valueOf(obj.getString("type"));
        } catch (Exception e) {
            return null;
        }
        item.packageName = obj.optString("packageName", "");
        item.className = obj.optString("className", "");
        if (obj.has("col")) {
            item.col = (float) obj.optDouble("col", 0.0);
        } else {
            item.col = (float) (obj.optDouble("x", 0.0) / 100.0);
        }
        if (obj.has("row")) {
            item.row = (float) obj.optDouble("row", 0.0);
        } else {
            item.row = (float) (obj.optDouble("y", 0.0) / 100.0);
        }
        item.spanX = (float) obj.optDouble("spanX", obj.optDouble("width", 100.0) / 100.0);
        item.spanY = (float) obj.optDouble("spanY", obj.optDouble("height", 100.0) / 100.0);
        if (item.spanX <= 0) item.spanX = 1f;
        if (item.spanY <= 0) item.spanY = 1f;
        item.page = obj.optInt("page", 0);
        item.widgetId = obj.optInt("widgetId", -1);
        item.rotation = (float) obj.optDouble("rotation", 0.0);
        if (obj.has("scaleX")) {
            item.scaleX = (float) obj.optDouble("scaleX", 1.0);
            item.scaleY = (float) obj.optDouble("scaleY", 1.0);
        } else {
            float s = (float) obj.optDouble("scale", 1.0);
            item.scaleX = s;
            item.scaleY = s;
        }
        item.tiltX = (float) obj.optDouble("tiltX", 0.0);
        item.tiltY = (float) obj.optDouble("tiltY", 0.0);
        item.folderName = obj.optString("folderName", null);
        if (obj.has("folderItems")) {
            item.folderItems = new ArrayList<>();
            JSONArray folderArray = obj.optJSONArray("folderItems");
            if (folderArray != null) {
                for (int i = 0; i < folderArray.length(); i++) {
                    HomeItem subItem = deserializeItem(folderArray.optJSONObject(i));
                    if (subItem != null) item.folderItems.add(subItem);
                }
            }
        }
        return item;
    }
}
