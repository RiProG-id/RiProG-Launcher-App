package com.riprog.launcher.data.cache

import android.content.Context
import android.graphics.Bitmap
import com.riprog.launcher.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartCacheManager(context: Context) {
    private val metadataCache = AppMetadataCache(context)
    private val iconDiskCache = IconDiskCache(context)

    suspend fun getCachedApps(): List<AppItem> = withContext(Dispatchers.IO) {
        metadataCache.getAllApps()
    }

    suspend fun saveAppsToCache(apps: List<AppItem>) = withContext(Dispatchers.IO) {
        metadataCache.saveApps(apps)
    }

    suspend fun getCachedIcon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        iconDiskCache.loadIcon(packageName)
    }

    suspend fun saveIconToCache(packageName: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        iconDiskCache.saveIcon(packageName, bitmap)
    }

    suspend fun cleanup(currentPackages: Set<String>) = withContext(Dispatchers.IO) {
        val cachedPackages = iconDiskCache.getAllCachedPackages()
        for (pkg in cachedPackages) {
            if (!currentPackages.contains(pkg)) {
                iconDiskCache.deleteIcon(pkg)
                metadataCache.deleteApp(pkg)
            }
        }
    }

    suspend fun evictApp(packageName: String) = withContext(Dispatchers.IO) {
        iconDiskCache.deleteIcon(packageName)
        metadataCache.deleteApp(packageName)
    }
}
