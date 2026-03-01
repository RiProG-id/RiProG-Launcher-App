package com.riprog.launcher.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DiskCacheManager(context: Context) {
    private val cacheDir: File = File(context.cacheDir, "app_icons")
    private val dataDir: File = File(context.cacheDir, "app_data")

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!dataDir.exists()) dataDir.mkdirs()
    }

    fun saveBitmap(key: String, bitmap: Bitmap) {
        val file = File(cacheDir, getSafeKey(key))
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: IOException) {

        }
    }

    fun getBitmap(key: String): Bitmap? {
        val file = File(cacheDir, getSafeKey(key))
        if (!file.exists()) return null

        file.setLastModified(System.currentTimeMillis())

        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun removeBitmap(key: String) {
        val file = File(cacheDir, getSafeKey(key))
        if (file.exists()) file.delete()
    }

    fun saveData(key: String, data: String) {
        val file = File(dataDir, getSafeKey(key))
        try {
            file.writeText(data)
        } catch (e: IOException) {
        }
    }

    fun getData(key: String): String? {
        val file = File(dataDir, getSafeKey(key))
        if (!file.exists()) return null

        file.setLastModified(System.currentTimeMillis())

        return try {
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    private fun getSafeKey(key: String): String {

        return key.hashCode().toString()
    }

    fun performCleanup() {
        val maxSize = 50L * 1024 * 1024
        cleanupDirectory(cacheDir, maxSize)

        val maxDataSize = 5L * 1024 * 1024
        cleanupDirectory(dataDir, maxDataSize)
    }

    private fun cleanupDirectory(dir: File, maxSize: Long) {
        val files = dir.listFiles() ?: return
        var currentSize = files.sumOf { it.length() }

        if (currentSize > maxSize) {

            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                currentSize -= file.length()
                file.delete()

                if (currentSize <= maxSize * 0.7) break
            }
        }
    }
}