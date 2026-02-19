package com.riprog.launcher.data.repository

import android.content.Context
import com.riprog.launcher.data.local.db.dao.HomeItemDao
import com.riprog.launcher.data.local.db.entity.HomeItemEntity
import com.riprog.launcher.data.model.HomeItem
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HomeRepository(private val context: Context, private val homeItemDao: HomeItemDao) {

    fun getHomeItems(): Flow<List<HomeItem>> = flow {
        homeItemDao.getRootItems().collect { entities ->
            val domainItems = entities.map { entity ->
                val item = entity.toDomainModel()
                if (item.type == HomeItem.Type.FOLDER) {
                    item.folderItems = withContext(Dispatchers.IO) {
                        homeItemDao.getItemsByParentId(entity.id)
                            .map { it.toDomainModel() }
                            .toMutableList()
                    }
                }
                item
            }
            emit(domainItems)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun saveHomeItems(items: List<HomeItem>) = withContext(Dispatchers.IO) {
        homeItemDao.clearAll()
        for (item in items) {
            saveItemRecursive(item, null)
        }
    }

    private suspend fun saveItemRecursive(item: HomeItem, parentId: Long?): Unit = withContext(Dispatchers.IO) {
        val entity = HomeItemEntity.fromDomainModel(item, parentId)
        val id = homeItemDao.insertItem(entity)
        if (item.type == HomeItem.Type.FOLDER) {
            for (subItem in item.folderItems) {
                saveItemRecursive(subItem, id)
            }
        }
    }

    suspend fun checkAndMigrate() = withContext(Dispatchers.IO) {
        val rootItems = homeItemDao.getRootItemsOnce()
        if (rootItems.isNotEmpty()) return@withContext

        val prefs = context.getSharedPreferences("riprog_launcher_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("home_items", null) ?: return@withContext

        try {
            val array = JSONArray(json)
            val items = mutableListOf<HomeItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val item = parseHomeItem(obj) ?: continue
                items.add(item)
            }
            if (items.isNotEmpty()) {
                saveHomeItems(items)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseHomeItem(obj: JSONObject): HomeItem? {
        val item = HomeItem()
        try {
            item.type = HomeItem.Type.valueOf(obj.getString("type"))
        } catch (e: Exception) {
            return null
        }
        item.packageName = obj.optString("packageName", "")
        item.className = obj.optString("className", "")

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

        return item
    }
}
