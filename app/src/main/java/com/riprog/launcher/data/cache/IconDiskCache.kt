package com.riprog.launcher.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class IconDiskCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "icons")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun saveIcon(packageName: String, bitmap: Bitmap) {
        val file = File(cacheDir, "$packageName.png")
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e("IconDiskCache", "Error saving icon: $packageName", e)
        }
    }

    fun loadIcon(packageName: String): Bitmap? {
        val file = File(cacheDir, "$packageName.png")
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.e("IconDiskCache", "Error loading icon: $packageName", e)
            null
        }
    }

    fun deleteIcon(packageName: String) {
        val file = File(cacheDir, "$packageName.png")
        if (file.exists()) {
            file.delete()
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    fun getAllCachedPackages(): Set<String> {
        return cacheDir.list()?.map { it.removeSuffix(".png") }?.toSet() ?: emptySet()
    }
}
