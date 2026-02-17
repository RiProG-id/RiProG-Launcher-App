package com.riprog.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.riprog.launcher.data.local.db.HomeItemDao
import com.riprog.launcher.data.local.db.HomeItemEntity
import com.riprog.launcher.data.local.db.HomeItemWithChildren
import com.riprog.launcher.data.local.datastore.SettingsDataStore
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.riprog.launcher.model.AppItem
import com.riprog.launcher.model.HomeItem
import com.riprog.launcher.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

class LauncherRepository(
    private val context: Context,
    private val homeItemDao: HomeItemDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val pm: PackageManager = context.packageManager

    val topLevelItems: Flow<List<HomeItem>> = homeItemDao.getTopLevelItemsWithChildren().map { entities ->
        entities.map { it.toHomeItem() }
    }

    val settings = settingsDataStore

    suspend fun checkAndMigrate() = withContext(Dispatchers.IO) {
        val isMigratedKey = "is_migrated_to_room_v2"
        val prefs = context.getSharedPreferences("riprog_launcher_prefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean(isMigratedKey, false)) return@withContext

        if (prefs.contains("columns")) settingsDataStore.setColumns(prefs.getInt("columns", 4))
        if (prefs.contains("widget_id")) settingsDataStore.setWidgetId(prefs.getInt("widget_id", -1))
        if (prefs.contains("freeform_home")) settingsDataStore.setFreeformHome(prefs.getBoolean("freeform_home", false))
        if (prefs.contains("icon_scale")) settingsDataStore.setIconScale(prefs.getFloat("icon_scale", 1.0f))
        if (prefs.contains("hide_labels")) settingsDataStore.setHideLabels(prefs.getBoolean("hide_labels", false))
        if (prefs.contains("liquid_glass")) settingsDataStore.setLiquidGlass(prefs.getBoolean("liquid_glass", true))
        if (prefs.contains("darken_wallpaper")) settingsDataStore.setDarkenWallpaper(prefs.getBoolean("darken_wallpaper", true))
        if (prefs.contains("theme_mode")) settingsDataStore.setThemeMode(prefs.getString("theme_mode", "system") ?: "system")
        if (prefs.contains("page_count")) settingsDataStore.setPageCount(prefs.getInt("page_count", 2))

        val allPrefs = prefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith("usage_") && value is Int) {
                val pkg = key.substring("usage_".length)
                for (i in 0 until value) {
                    settingsDataStore.incrementUsage(pkg)
                }
            }
        }

        val legacySettings = SettingsManager(context)
        val legacyItems = legacySettings.getHomeItems()
        if (legacyItems.isNotEmpty()) {
            saveHomeItems(legacyItems)
        }

        prefs.edit().putBoolean(isMigratedKey, true).apply()
    }

    suspend fun loadIcon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val drawable = pm.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        val size = 192
        return try {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: OutOfMemoryError) {
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else null
        }
    }

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(mainIntent, 0)

        val apps = mutableListOf<AppItem>()
        val selfPackage = context.packageName

        for (info in infos) {
            if (info.activityInfo.packageName != selfPackage) {
                val item = AppItem(
                    info.loadLabel(pm).toString(),
                    info.activityInfo.packageName,
                    info.activityInfo.name
                )
                apps.add(item)
            }
        }
        apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    suspend fun saveHomeItems(items: List<HomeItem>) = withContext(Dispatchers.IO) {
        homeItemDao.deleteAll()
        for (item in items) {
            saveHomeItem(item)
        }
    }

    suspend fun saveHomeItem(item: HomeItem): Long = withContext(Dispatchers.IO) {
        val entity = item.toEntity()
        val id = homeItemDao.insert(entity)
        item.id = id

        if (item.type == HomeItem.Type.FOLDER && !item.folderItems.isNullOrEmpty()) {
            val children = item.folderItems!!.map { it.toEntity(parentId = id) }
            homeItemDao.insertAll(children)
        }
        id
    }

    suspend fun deleteHomeItem(item: HomeItem) = withContext(Dispatchers.IO) {
        homeItemDao.deleteItemAndChildren(item.id)
    }

    suspend fun deleteAllItems() = withContext(Dispatchers.IO) {
        homeItemDao.deleteAll()
    }

    private fun HomeItemWithChildren.toHomeItem(): HomeItem {
        val homeItem = HomeItem()
        homeItem.id = item.id
        homeItem.type = item.type
        homeItem.packageName = item.packageName
        homeItem.className = item.className
        homeItem.row = item.row
        homeItem.col = item.col
        homeItem.spanX = item.spanX
        homeItem.spanY = item.spanY
        homeItem.page = item.page
        homeItem.widgetId = item.widgetId
        homeItem.folderName = item.folderName
        homeItem.rotation = item.rotation
        homeItem.scaleX = item.scaleX
        homeItem.scaleY = item.scaleY
        homeItem.tiltX = item.tiltX
        homeItem.tiltY = item.tiltY

        if (item.type == HomeItem.Type.FOLDER) {
            homeItem.folderItems = children.map { it.toHomeItem() }.toMutableList()
        }
        return homeItem
    }

    private fun HomeItemEntity.toHomeItem(): HomeItem {
        val homeItem = HomeItem()
        homeItem.id = id
        homeItem.type = type
        homeItem.packageName = packageName
        homeItem.className = className
        homeItem.row = row
        homeItem.col = col
        homeItem.spanX = spanX
        homeItem.spanY = spanY
        homeItem.page = page
        homeItem.widgetId = widgetId
        homeItem.folderName = folderName
        homeItem.rotation = rotation
        homeItem.scaleX = scaleX
        homeItem.scaleY = scaleY
        homeItem.tiltX = tiltX
        homeItem.tiltY = tiltY
        return homeItem
    }

    private fun HomeItem.toEntity(parentId: Long = -1): HomeItemEntity {
        return HomeItemEntity(
            id = id,
            type = type ?: HomeItem.Type.APP,
            packageName = packageName,
            className = className,
            row = row,
            col = col,
            spanX = spanX,
            spanY = spanY,
            page = page,
            widgetId = widgetId,
            folderName = folderName,
            parentId = parentId,
            rotation = rotation,
            scaleX = scaleX,
            scaleY = scaleY,
            tiltX = tiltX,
            tiltY = tiltY
        )
    }
}
