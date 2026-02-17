package com.riprog.launcher.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items WHERE parentId = -1")
    fun getTopLevelItems(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items WHERE parentId = :parentId")
    fun getFolderItems(parentId: Long): List<HomeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeItemEntity>)

    @Update
    suspend fun update(item: HomeItemEntity)

    @Delete
    suspend fun delete(item: HomeItemEntity)

    @Query("DELETE FROM home_items")
    suspend fun deleteAll()

    @Query("DELETE FROM home_items WHERE id = :id OR parentId = :id")
    suspend fun deleteItemAndChildren(id: Long)

    @Transaction
    @Query("SELECT * FROM home_items WHERE parentId = -1")
    fun getTopLevelItemsWithChildren(): Flow<List<HomeItemWithChildren>>
}

data class HomeItemWithChildren(
    @Embedded val item: HomeItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentId"
    )
    val children: List<HomeItemEntity>
)
