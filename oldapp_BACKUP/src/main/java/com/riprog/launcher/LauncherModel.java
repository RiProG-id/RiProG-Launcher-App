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

    public LauncherModel(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.min(24 * 1024, maxMemory / 10);
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
            System.gc();
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            iconCache.trimToSize(0);
        }
    }

    public void shutdown() {
        executor.shutdown();
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
        synchronized (pendingListeners) {
            Bitmap cached = iconCache.get(item.packageName);
            if (cached != null) {
                listener.onIconLoaded(cached);
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
            Bitmap bitmap = null;
            try {
                Drawable drawable = pm.getApplicationIcon(item.packageName);
                bitmap = drawableToBitmap(drawable);
                if (bitmap != null) {
                    iconCache.put(item.packageName, bitmap);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
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
