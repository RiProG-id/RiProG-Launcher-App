package com.riprog.launcher.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.riprog.launcher.data.model.HomeItem

@Entity(tableName = "home_items")
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val type: String,
    val packageName: String? = null,
    val className: String? = null,
    val folderName: String? = null,
    val row: Float,
    val col: Float,
    val spanX: Int,
    val spanY: Int,
    val page: Int,
    val widgetId: Int,
    val rotation: Float,
    val scale: Float,
    val tiltX: Float,
    val tiltY: Float
) {
    fun toDomainModel(): HomeItem {
        val item = HomeItem()
        item.type = HomeItem.Type.valueOf(type)
        item.packageName = packageName
        item.className = className
        item.folderName = folderName
        item.row = row
        item.col = col
        item.spanX = spanX
        item.spanY = spanY
        item.page = page
        item.widgetId = widgetId
        item.rotation = rotation
        item.scale = scale
        item.tiltX = tiltX
        item.tiltY = tiltY
        // folderItems will be populated by the repository
        return item
    }

    companion object {
        fun fromDomainModel(item: HomeItem, parentId: Long? = null): HomeItemEntity {
            return HomeItemEntity(
                parentId = parentId,
                type = item.type?.name ?: HomeItem.Type.APP.name,
                packageName = item.packageName,
                className = item.className,
                folderName = item.folderName,
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
        }
    }
}
