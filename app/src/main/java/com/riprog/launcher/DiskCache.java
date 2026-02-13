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
import java.util.Comparator;

public class DiskCache {
    private static final String ICON_DIR = "icons";
    private static final String DATA_DIR = "data";
    private static final long MAX_CACHE_SIZE = 50 * 1024 * 1024; // 50MB
    private final File rootDir;

    public DiskCache(Context context) {
        this.rootDir = context.getCacheDir();
        new File(rootDir, ICON_DIR).mkdirs();
        new File(rootDir, DATA_DIR).mkdirs();
    }

    public void saveIcon(String key, Bitmap bitmap) {
        File file = new File(new File(rootDir, ICON_DIR), key + ".webp");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out);
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out);
            }
        } catch (IOException ignored) {}
    }

    public void removeIcon(String key) {
        File file = new File(new File(rootDir, ICON_DIR), key + ".webp");
        if (file.exists()) file.delete();
    }

    public Bitmap loadIcon(String key) {
        File file = new File(new File(rootDir, ICON_DIR), key + ".webp");
        if (!file.exists()) return null;
        file.setLastModified(System.currentTimeMillis());
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    public void saveData(String key, String data) {
        File file = new File(new File(rootDir, DATA_DIR), key + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    public String loadData(String key) {
        File file = new File(new File(rootDir, DATA_DIR), key + ".json");
        if (!file.exists()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            file.setLastModified(System.currentTimeMillis());
            byte[] bytes = new byte[(int) file.length()];
            in.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void invalidateData(String key) {
        File file = new File(new File(rootDir, DATA_DIR), key + ".json");
        if (file.exists()) file.delete();
    }

    public long getDataLastModified(String key) {
        File file = new File(new File(rootDir, DATA_DIR), key + ".json");
        return file.exists() ? file.lastModified() : 0;
    }

    public void performCleanup() {
        new Thread(() -> {
            cleanupDirectory(new File(rootDir, ICON_DIR));
            cleanupDirectory(new File(rootDir, DATA_DIR));
        }).start();
    }

    private void cleanupDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        long currentSize = 0;
        for (File f : files) currentSize += f.length();

        if (currentSize > MAX_CACHE_SIZE / 2) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    long m1 = f1.lastModified();
                    long m2 = f2.lastModified();
                    return Long.compare(m1, m2);
                }
            });
            for (File f : files) {
                if (currentSize <= MAX_CACHE_SIZE / 4) break;
                currentSize -= f.length();
                f.delete();
            }
        }
    }
}
