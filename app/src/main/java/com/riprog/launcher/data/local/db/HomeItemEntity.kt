package com.riprog.launcher.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.riprog.launcher.data.model.HomeItem

@Entity(tableName = "home_items")
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: HomeItem.Type,
    val packageName: String? = null,
    val className: String? = null,
    val row: Float = 0f,
    val col: Float = 0f,
    val spanX: Float = 1f,
    val spanY: Float = 1f,
    val page: Int = 0,
    val widgetId: Int = -1,
    val folderName: String? = null,
    val parentId: Long = -1,
    val rotation: Float = 0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f
)
