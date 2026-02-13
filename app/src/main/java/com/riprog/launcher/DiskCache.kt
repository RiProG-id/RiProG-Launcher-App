package com.riprog.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class DiskCache(context: Context) {
    private val rootDir: File = context.cacheDir

    init {
        File(rootDir, ICON_DIR).mkdirs()
        File(rootDir, DATA_DIR).mkdirs()
    }

    fun saveIcon(key: String, bitmap: Bitmap) {
        val file = File(File(rootDir, ICON_DIR), "$key.webp")
        try {
            FileOutputStream(file).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                }
            }
        } catch (ignored: IOException) {
        }
    }

    fun removeIcon(key: String) {
        val file = File(File(rootDir, ICON_DIR), "$key.webp")
        if (file.exists()) file.delete()
    }

    fun loadIcon(key: String): Bitmap? {
        val file = File(File(rootDir, ICON_DIR), "$key.webp")
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun saveData(key: String, data: String) {
        val file = File(File(rootDir, DATA_DIR), "$key.json")
        try {
            FileOutputStream(file).use { out ->
                out.write(data.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (ignored: IOException) {
        }
    }

    fun loadData(key: String): String? {
        val file = File(File(rootDir, DATA_DIR), "$key.json")
        if (!file.exists()) return null
        return try {
            FileInputStream(file).use { `in` ->
                file.setLastModified(System.currentTimeMillis())
                val bytes = ByteArray(file.length().toInt())
                `in`.read(bytes)
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        }
    }

    fun invalidateData(key: String) {
        val file = File(File(rootDir, DATA_DIR), "$key.json")
        if (file.exists()) file.delete()
    }

    fun getDataLastModified(key: String): Long {
        val file = File(File(rootDir, DATA_DIR), "$key.json")
        return if (file.exists()) file.lastModified() else 0
    }

    suspend fun performCleanup() {
        withContext(Dispatchers.IO) {
            cleanupDirectory(File(rootDir, ICON_DIR))
            cleanupDirectory(File(rootDir, DATA_DIR))
        }
    }

    private fun cleanupDirectory(dir: File) {
        val files = dir.listFiles() ?: return
        var currentSize = files.sumOf { it.length() }
        if (currentSize > MAX_CACHE_SIZE / 2) {
            files.sortBy { it.lastModified() }
            for (f in files) {
                if (currentSize <= MAX_CACHE_SIZE / 4) break
                currentSize -= f.length()
                f.delete()
            }
        }
    }

    companion object {
        private const val ICON_DIR = "icons"
        private const val DATA_DIR = "data"
        private const val MAX_CACHE_SIZE = 50L * 1024 * 1024 // 50MB
    }
}
