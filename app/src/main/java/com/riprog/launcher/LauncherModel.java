package com.riprog.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherModel {
    public interface OnAppsLoadedListener {
        void onAppsLoaded(List<AppItem> apps);
    }

    private final Context context;
    private final PackageManager pm;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> iconCache;
    private final Map<String, List<OnIconLoadedListener>> pendingListeners = new HashMap<>();
    private final DiskCache diskCache;

    public LauncherModel(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();
        this.diskCache = new DiskCache(this.context);

        // Extremely low RAM usage: fixed 4MB icon cache
        final int cacheSize = 4 * 1024;
        iconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void onTrimMemory(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            iconCache.trimToSize(iconCache.size() / 2);
        }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            iconCache.evictAll();
            diskCache.performCleanup();
            System.gc();
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.evictAll();
            System.gc();
        }
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void loadApps(OnAppsLoadedListener listener) {
        loadApps(listener, false);
    }

    public void loadApps(OnAppsLoadedListener listener, boolean forceRefresh) {
        executor.execute(() -> {
            if (!forceRefresh) {
                String cached = diskCache.loadData(DiskCache.TYPE_METADATA, "app_list");
                if (cached != null) {
                    List<AppItem> apps = deserializeAppList(cached);
                    if (!apps.isEmpty()) {
                        mainHandler.post(() -> listener.onAppsLoaded(apps));
                        return;
                    }
                }
            }

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);

            List<AppItem> apps = new ArrayList<>();
            String selfPackage = context.getPackageName();

            for (ResolveInfo info : infos) {
                if (!info.activityInfo.packageName.equals(selfPackage)) {
                    AppItem item = new AppItem(
                        info.loadLabel(pm).toString(),
                        info.activityInfo.packageName,
                        info.activityInfo.name
                    );
                    apps.add(item);
                }
            }

            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
            diskCache.saveData(DiskCache.TYPE_METADATA, "app_list", serializeAppList(apps));

            mainHandler.post(() -> listener.onAppsLoaded(apps));

            // Background pre-generation of icons for first 20 apps
            final List<AppItem> finalApps = apps;
            new Thread(() -> {
                for (int i = 0; i < Math.min(20, finalApps.size()); i++) {
                    AppItem app = finalApps.get(i);
                    if (diskCache.loadIcon(DiskCache.TYPE_ICONS, app.packageName) == null) {
                        try {
                            Drawable drawable = pm.getApplicationIcon(app.packageName);
                            Bitmap bitmap = drawableToBitmap(drawable);
                            if (bitmap != null) {
                                diskCache.saveIcon(DiskCache.TYPE_ICONS, app.packageName, bitmap);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }).start();
        });
    }

    private String serializeAppList(List<AppItem> apps) {
        try {
            JSONArray array = new JSONArray();
            for (AppItem app : apps) {
                JSONObject obj = new JSONObject();
                obj.put("l", app.label);
                obj.put("p", app.packageName);
                obj.put("c", app.className);
                array.put(obj);
            }
            return array.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<AppItem> deserializeAppList(String json) {
        List<AppItem> apps = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                apps.add(new AppItem(obj.getString("l"), obj.getString("p"), obj.getString("c")));
            }
        } catch (Exception ignored) {}
        return apps;
    }

    public void invalidateAppListCache() {
        diskCache.invalidateData("app_list");
    }

    public void clearAppIconCache(String packageName) {
        iconCache.remove(packageName);
        diskCache.removeIcon(packageName);
    }

    public void loadIcon(AppItem item, OnIconLoadedListener listener) {
        synchronized (pendingListeners) {
            Bitmap cached = iconCache.get(item.packageName);
            if (cached != null) {
                listener.onIconLoaded(cached);
                // Clear reference from listener immediately after call to keep RAM light
                return;
            }

            List<OnIconLoadedListener> listeners = pendingListeners.get(item.packageName);
            if (listeners != null) {
                listeners.add(listener);
                return;
            }

            listeners = new ArrayList<>();
            listeners.add(listener);
            pendingListeners.put(item.packageName, listeners);
        }

        executor.execute(() -> {
            Bitmap bitmap = diskCache.loadIcon(DiskCache.TYPE_ICONS, item.packageName);

            if (bitmap == null) {
                try {
                    Drawable drawable = pm.getApplicationIcon(item.packageName);
                    bitmap = drawableToBitmap(drawable);
                    if (bitmap != null) {
                        diskCache.saveIcon(DiskCache.TYPE_ICONS, item.packageName, bitmap);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }

            if (bitmap != null) {
                iconCache.put(item.packageName, bitmap);
            }

            final Bitmap finalBitmap = bitmap;
            mainHandler.post(() -> {
                List<OnIconLoadedListener> listeners;
                synchronized (pendingListeners) {
                    listeners = pendingListeners.remove(item.packageName);
                }
                if (listeners != null) {
                    for (OnIconLoadedListener l : listeners) {
                        l.onIconLoaded(finalBitmap);
                    }
                }
                // Memory optimization: clear heavy reference after delivery
            });
        });
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;


        int size = 192;

        try {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (OutOfMemoryError e) {

            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
            return null;
        }
    }

    public interface OnIconLoadedListener {
        void onIconLoaded(Bitmap icon);
    }

    public static List<AppItem> filterApps(List<AppItem> apps, String query) {
        if (query == null || query.isEmpty()) return new ArrayList<>(apps);
        List<AppItem> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase(Locale.getDefault());
        for (AppItem item : apps) {
            if (item.label.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
