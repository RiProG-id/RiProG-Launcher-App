package com.riprog.launcher.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "home_items",
    foreignKeys = [
        ForeignKey(
            entity = HomeItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId")]
)
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val packageName: String? = null,
    val className: String? = null,
    val folderName: String? = null,
    val parentId: Long? = null,
    val row: Float = 0f,
    val col: Float = 0f,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val page: Int = 0,
    val widgetId: Int = -1,
    val rotation: Float = 0f,
    val scale: Float = 1.0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f
)
