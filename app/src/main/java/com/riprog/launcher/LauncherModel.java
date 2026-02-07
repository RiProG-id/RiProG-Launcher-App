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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    public LauncherModel(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        iconCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public void onTrimMemory(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            iconCache.evictAll();
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.trimToSize(iconCache.size() / 2);
        }
    }

    public void loadApps(OnAppsLoadedListener listener) {
        executor.execute(() -> {
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

            mainHandler.post(() -> listener.onAppsLoaded(apps));
        });
    }

    public void loadIcon(AppItem item, OnIconLoadedListener listener) {
        Bitmap cached = iconCache.get(item.packageName);
        if (cached != null) {
            item.icon = cached;
            listener.onIconLoaded(cached);
            return;
        }

        executor.execute(() -> {
            try {
                Drawable drawable = pm.getApplicationIcon(item.packageName);
                Bitmap bitmap = drawableToBitmap(drawable);
                iconCache.put(item.packageName, bitmap);
                item.icon = bitmap;
                mainHandler.post(() -> listener.onIconLoaded(bitmap));
            } catch (PackageManager.NameNotFoundException e) {
                mainHandler.post(() -> listener.onIconLoaded(null));
            }
        });
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = 128;
        if (height <= 0) height = 128;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public interface OnIconLoadedListener {
        void onIconLoaded(Bitmap icon);
    }

    public static List<AppItem> filterApps(List<AppItem> apps, String query) {
        if (query == null || query.isEmpty()) return new ArrayList<>(apps);
        List<AppItem> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (AppItem item : apps) {
            if (item.label.toLowerCase().contains(lowerQuery)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
}
