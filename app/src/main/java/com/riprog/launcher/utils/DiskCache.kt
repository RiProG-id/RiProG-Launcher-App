package com.riprog.launcher.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class DiskCache(context: Context) {
    private val rootDir: File = context.cacheDir
    private val metaPrefs = context.getSharedPreferences("cache_metadata", Context.MODE_PRIVATE)

    init {
        File(rootDir, TYPE_ICONS).mkdirs()
        File(rootDir, TYPE_METADATA).mkdirs()
        File(rootDir, TYPE_LAYOUT).mkdirs()
        File(rootDir, TYPE_PREVIEWS).mkdirs()
        File(rootDir, "data").mkdirs()
    }

    private fun updateMetadata(key: String) {
        val now = System.currentTimeMillis()
        val count = metaPrefs.getInt("access_count_$key", 0)
        metaPrefs.edit()
            .putLong("last_access_$key", now)
            .putInt("access_count_$key", count + 1)
            .apply()
    }

    @JvmOverloads
    fun saveIcon(key: String, bitmap: Bitmap, type: String = TYPE_ICONS) {
        val file = File(File(rootDir, type), "$key.webp")
        try {
            FileOutputStream(file).use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                }
                updateMetadata(key)
            }
        } catch (ignored: IOException) {
        }
    }

    @JvmOverloads
    fun removeIcon(key: String, type: String = TYPE_ICONS) {
        val file = File(File(rootDir, type), "$key.webp")
        if (file.exists()) file.delete()
        metaPrefs.edit().remove("last_access_$key").remove("access_count_$key").apply()
    }

    @JvmOverloads
    fun loadIcon(key: String, type: String = TYPE_ICONS): Bitmap? {
        val file = File(File(rootDir, type), "$key.webp")
        if (!file.exists()) return null
        updateMetadata(key)
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    @JvmOverloads
    fun saveData(key: String, data: String, type: String = TYPE_METADATA) {
        val file = File(File(rootDir, type), key + (if (TYPE_LAYOUT == type) ".json" else ".dat"))
        try {
            FileOutputStream(file).use { out ->
                out.write(data.toByteArray(StandardCharsets.UTF_8))
                updateMetadata(key)
            }
        } catch (ignored: IOException) {
        }
    }

    fun loadData(key: String): String? {
        var data = loadData(TYPE_METADATA, key)
        if (data == null) data = loadData("data", key)
        return data
    }

    fun loadData(type: String, key: String): String? {
        var file = File(File(rootDir, type), key + (if (TYPE_LAYOUT == type) ".json" else ".dat"))
        if (!file.exists() && "data" == type) {
            file = File(File(rootDir, type), "$key.json")
        }
        if (!file.exists()) return null
        return try {
            FileInputStream(file).use { `in` ->
                val bytes = ByteArray(file.length().toInt())
                `in`.read(bytes)
                updateMetadata(key)
                String(bytes, StandardCharsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        }
    }

    @JvmOverloads
    fun invalidateData(key: String, type: String = TYPE_METADATA) {
        if (type == TYPE_METADATA) {
            invalidateDataInternal(TYPE_METADATA, key)
            invalidateDataInternal("data", key)
        } else {
            invalidateDataInternal(type, key)
        }
    }

    private fun invalidateDataInternal(type: String, key: String) {
        var file = File(File(rootDir, type), key + (if (TYPE_LAYOUT == type) ".json" else ".dat"))
        if (!file.exists() && "data" == type) {
            file = File(File(rootDir, type), "$key.json")
        }
        if (file.exists()) file.delete()
        metaPrefs.edit().remove("last_access_$key").remove("access_count_$key").apply()
    }

    @JvmOverloads
    fun getDataLastModified(key: String, type: String = TYPE_METADATA): Long {
        val file = File(File(rootDir, type), key + (if (TYPE_LAYOUT == type) ".json" else ".dat"))
        return if (file.exists()) file.lastModified() else 0
    }

    fun performCleanup() {
        Thread { performSmartCleanup() }.start()
    }

    private fun performSmartCleanup() {
        val dirs = arrayOf(
            File(rootDir, TYPE_ICONS),
            File(rootDir, TYPE_METADATA),
            File(rootDir, TYPE_LAYOUT),
            File(rootDir, TYPE_PREVIEWS),
            File(rootDir, "data")
        )

        val allFiles = mutableListOf<File>()
        var totalSize: Long = 0
        for (dir in dirs) {
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) {
                    allFiles.add(f)
                    totalSize += f.length()
                }
            }
        }

        if (totalSize > MAX_CACHE_SIZE) {
            allFiles.sortWith { f1, f2 ->
                val name1 = f1.name
                val name2 = f2.name
                val k1 = if (name1.contains(".")) name1.substring(0, name1.lastIndexOf('.')) else name1
                val k2 = if (name2.contains(".")) name2.substring(0, name2.lastIndexOf('.')) else name2
                val s1 = calculateScore(k1)
                val s2 = calculateScore(k2)
                s1.compareTo(s2)
            }

            for (f in allFiles) {
                if (totalSize <= MAX_CACHE_SIZE * 0.75) break
                val size = f.length()
                if (f.delete()) {
                    totalSize -= size
                    val name = f.name
                    val key = if (name.contains(".")) name.substring(0, name.lastIndexOf('.')) else name
                    metaPrefs.edit().remove("last_access_$key").remove("access_count_$key").apply()
                }
            }
        }
    }

    private fun calculateScore(key: String): Float {
        val lastAccess = metaPrefs.getLong("last_access_$key", 0)
        val count = metaPrefs.getInt("access_count_$key", 0)
        val hoursSince = (System.currentTimeMillis() - lastAccess) / (1000 * 60 * 60)
        return count.toFloat() / (hoursSince + 1)
    }

    companion object {
        const val TYPE_ICONS = "icons"
        const val TYPE_METADATA = "metadata"
        const val TYPE_LAYOUT = "layout"
        const val TYPE_PREVIEWS = "previews"
        private const val MAX_CACHE_SIZE = (25 * 1024 * 1024).toLong()
    }
}
