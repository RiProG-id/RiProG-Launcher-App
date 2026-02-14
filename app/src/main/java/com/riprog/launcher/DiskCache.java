package com.riprog.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class DiskCache {
    public static final String TYPE_ICONS = "icons";
    public static final String TYPE_METADATA = "metadata";
    public static final String TYPE_LAYOUT = "layout";
    public static final String TYPE_PREVIEWS = "previews";

    private static final long MAX_CACHE_SIZE = 25 * 1024 * 1024; // 25MB
    private final File rootDir;
    private final android.content.SharedPreferences metaPrefs;

    public DiskCache(Context context) {
        this.rootDir = context.getCacheDir();
        this.metaPrefs = context.getSharedPreferences("cache_metadata", Context.MODE_PRIVATE);
        new File(rootDir, TYPE_ICONS).mkdirs();
        new File(rootDir, TYPE_METADATA).mkdirs();
        new File(rootDir, TYPE_LAYOUT).mkdirs();
        new File(rootDir, TYPE_PREVIEWS).mkdirs();
        // Legacy support
        new File(rootDir, "data").mkdirs();
    }

    private void updateMetadata(String key) {
        long now = System.currentTimeMillis();
        int count = metaPrefs.getInt("access_count_" + key, 0);
        metaPrefs.edit()
                .putLong("last_access_" + key, now)
                .putInt("access_count_" + key, count + 1)
                .apply();
    }

    public void saveIcon(String key, Bitmap bitmap) {
        saveIcon(TYPE_ICONS, key, bitmap);
    }

    public void saveIcon(String type, String key, Bitmap bitmap) {
        File file = new File(new File(rootDir, type), key + ".webp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out);
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out);
            }
            updateMetadata(key);
        } catch (IOException ignored) {}
    }

    public void removeIcon(String key) {
        removeIcon(TYPE_ICONS, key);
    }

    public void removeIcon(String type, String key) {
        File file = new File(new File(rootDir, type), key + ".webp");
        if (file.exists()) file.delete();
        metaPrefs.edit().remove("last_access_" + key).remove("access_count_" + key).apply();
    }

    public Bitmap loadIcon(String key) {
        return loadIcon(TYPE_ICONS, key);
    }

    public Bitmap loadIcon(String type, String key) {
        File file = new File(new File(rootDir, type), key + ".webp");
        if (!file.exists()) return null;
        updateMetadata(key);
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    public void saveData(String key, String data) {
        saveData(TYPE_METADATA, key, data);
    }

    public void saveData(String type, String key, String data) {
        File file = new File(new File(rootDir, type), key + (TYPE_LAYOUT.equals(type) ? ".json" : ".dat"));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
            updateMetadata(key);
        } catch (IOException ignored) {}
    }

    public String loadData(String key) {
        String data = loadData(TYPE_METADATA, key);
        if (data == null) data = loadData("data", key); // Legacy
        return data;
    }

    public String loadData(String type, String key) {
        File file = new File(new File(rootDir, type), key + (TYPE_LAYOUT.equals(type) ? ".json" : ".dat"));
        if (!file.exists() && "data".equals(type)) {
            file = new File(new File(rootDir, type), key + ".json");
        }
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            in.read(bytes);
            updateMetadata(key);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void invalidateData(String key) {
        invalidateData(TYPE_METADATA, key);
        invalidateData("data", key);
    }

    public void invalidateData(String type, String key) {
        File file = new File(new File(rootDir, type), key + (TYPE_LAYOUT.equals(type) ? ".json" : ".dat"));
        if (!file.exists() && "data".equals(type)) {
            file = new File(new File(rootDir, type), key + ".json");
        }
        if (file.exists()) file.delete();
        metaPrefs.edit().remove("last_access_" + key).remove("access_count_" + key).apply();
    }

    public long getDataLastModified(String key) {
        return getDataLastModified(TYPE_METADATA, key);
    }

    public long getDataLastModified(String type, String key) {
        File file = new File(new File(rootDir, type), key + (TYPE_LAYOUT.equals(type) ? ".json" : ".dat"));
        return file.exists() ? file.lastModified() : 0;
    }

    public void performCleanup() {
        new Thread(this::performSmartCleanup).start();
    }

    private void performSmartCleanup() {
        File[] dirs = {
            new File(rootDir, TYPE_ICONS),
            new File(rootDir, TYPE_METADATA),
            new File(rootDir, TYPE_LAYOUT),
            new File(rootDir, TYPE_PREVIEWS),
            new File(rootDir, "data")
        };

        java.util.List<File> allFiles = new java.util.ArrayList<>();
        long totalSize = 0;
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    allFiles.add(f);
                    totalSize += f.length();
                }
            }
        }

        if (totalSize > MAX_CACHE_SIZE) {
            // Sort by score: higher is better (keep), lower is worse (delete)
            // score = access_count / (hours_since_last_access + 1)
            Collections.sort(allFiles, (f1, f2) -> {
                String name1 = f1.getName();
                String name2 = f2.getName();
                String k1 = name1.contains(".") ? name1.substring(0, name1.lastIndexOf('.')) : name1;
                String k2 = name2.contains(".") ? name2.substring(0, name2.lastIndexOf('.')) : name2;
                float s1 = calculateScore(k1);
                float s2 = calculateScore(k2);
                return Float.compare(s1, s2); // Ascending: lowest scores first
            });

            for (File f : allFiles) {
                if (totalSize <= MAX_CACHE_SIZE * 0.75) break;
                long size = f.length();
                if (f.delete()) {
                    totalSize -= size;
                    String name = f.getName();
                    String key = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                    metaPrefs.edit().remove("last_access_" + key).remove("access_count_" + key).apply();
                }
            }
        }
    }

    private float calculateScore(String key) {
        long lastAccess = metaPrefs.getLong("last_access_" + key, 0);
        int count = metaPrefs.getInt("access_count_" + key, 0);
        long hoursSince = (System.currentTimeMillis() - lastAccess) / (1000 * 60 * 60);
        return (float) count / (hoursSince + 1);
    }
}
