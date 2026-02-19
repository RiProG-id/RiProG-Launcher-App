package com.riprog.launcher.data.repository

import com.riprog.launcher.data.db.HomeItemDao
import com.riprog.launcher.data.model.HomeItem
import com.riprog.launcher.data.model.HomeItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class HomeItemRepository(
    private val dao: HomeItemDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun getHomeItems(): List<HomeItem> = withContext(Dispatchers.IO) {
        val entities = dao.getAllItems()
        if (entities.isEmpty()) {
            val migrated = checkAndMigrate()
            if (migrated != null) return@withContext migrated
        }
        buildHierarchy(entities, null)
    }

    fun getHomeItemsFlow(): Flow<List<HomeItem>> = flow {
        val current = withContext(Dispatchers.IO) { dao.getAllItems() }
        if (current.isEmpty()) {
            checkAndMigrate()
        }
        emitAll(dao.getAllItemsFlow().map { buildHierarchy(it, null) })
    }

    private fun buildHierarchy(entities: List<HomeItemEntity>, parentId: Long?): List<HomeItem> {
        return entities.filter { it.parentId == parentId }.map { entity ->
            val item = HomeItem()
            item.type = try { HomeItem.Type.valueOf(entity.type) } catch (e: Exception) { HomeItem.Type.APP }
            item.packageName = entity.packageName
            item.className = entity.className
            item.folderName = entity.folderName
            item.row = entity.row
            item.col = entity.col
            item.spanX = entity.spanX
            item.spanY = entity.spanY
            item.page = entity.page
            item.widgetId = entity.widgetId
            item.rotation = entity.rotation
            item.scale = entity.scale
            item.tiltX = entity.tiltX
            item.tiltY = entity.tiltY

            if (item.type == HomeItem.Type.FOLDER) {
                item.folderItems = buildHierarchy(entities, entity.id).toMutableList()
            }
            item
        }
    }

    suspend fun saveHomeItems(items: List<HomeItem>) = withContext(Dispatchers.IO) {
        dao.deleteAll()
        saveRecursive(items, null)
    }

    private suspend fun saveRecursive(items: List<HomeItem>, parentId: Long?) {
        for (item in items) {
            val entity = HomeItemEntity(
                type = item.type?.name ?: HomeItem.Type.APP.name,
                packageName = item.packageName,
                className = item.className,
                folderName = item.folderName,
                parentId = parentId,
                row = item.row,
                col = item.col,
                spanX = item.spanX,
                spanY = item.spanY,
                page = item.page,
                widgetId = item.widgetId,
                rotation = item.rotation,
                scale = item.scale,
                tiltX = item.tiltX,
                tiltY = item.tiltY
            )
            val id = dao.insert(entity)
            if (item.type == HomeItem.Type.FOLDER) {
                saveRecursive(item.folderItems, id)
            }
        }
    }

    private suspend fun checkAndMigrate(): List<HomeItem>? {
        val json = settingsRepository.getHomeItemsJson() ?: return null
        val items = parseHomeItems(json)
        if (items.isNotEmpty()) {
            saveHomeItems(items)
            settingsRepository.clearHomeItemsJson()
            return items
        }
        return null
    }

    private fun parseHomeItems(json: String): List<HomeItem> {
        val items: MutableList<HomeItem> = mutableListOf()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = parseSingleItem(obj) ?: continue
                items.add(item)
            }
        } catch (ignored: Exception) {
        }
        return items
    }

    private fun parseSingleItem(obj: JSONObject): HomeItem? {
        if (!obj.has("type")) return null
        val item = HomeItem()
        try {
            item.type = HomeItem.Type.valueOf(obj.getString("type"))
        } catch (e: Exception) {
            return null
        }
        item.packageName = obj.optString("packageName", "")
        item.className = obj.optString("className", "")
        item.folderName = obj.optString("folderName", "")
        if (obj.has("col")) {
            item.col = obj.optDouble("col", 0.0).toFloat()
        } else {
            item.col = (obj.optDouble("x", 0.0) / 100.0).toFloat()
        }
        if (obj.has("row")) {
            item.row = obj.optDouble("row", 0.0).toFloat()
        } else {
            item.row = (obj.optDouble("y", 0.0) / 100.0).toFloat()
        }
        item.spanX = obj.optInt("spanX", obj.optInt("width", 100) / 100)
        item.spanY = obj.optInt("spanY", obj.optInt("height", 100) / 100)
        if (item.spanX <= 0) item.spanX = 1
        if (item.spanY <= 0) item.spanY = 1
        item.page = obj.optInt("page", 0)
        item.widgetId = obj.optInt("widgetId", -1)
        item.rotation = obj.optDouble("rotation", 0.0).toFloat()
        item.scale = obj.optDouble("scale", 1.0).toFloat()
        item.tiltX = obj.optDouble("tiltX", 0.0).toFloat()
        item.tiltY = obj.optDouble("tiltY", 0.0).toFloat()

        if (obj.has("folderItems")) {
            try {
                val innerArray = obj.getJSONArray("folderItems")
                for (i in 0 until innerArray.length()) {
                    val innerObj = innerArray.getJSONObject(i)
                    val innerItem = parseSingleItem(innerObj)
                    if (innerItem != null) item.folderItems.add(innerItem)
                }
            } catch (ignored: Exception) {}
        }
        return item
    }
}
