package com.riprog.launcher.data.local.db.dao

import androidx.room.*
import com.riprog.launcher.data.local.db.entity.HomeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items WHERE parentId IS NULL")
    fun getRootItems(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items WHERE parentId IS NULL")
    suspend fun getRootItemsOnce(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE parentId = :parentId")
    suspend fun getItemsByParentId(parentId: Long): List<HomeItemEntity>

    @Query("SELECT * FROM home_items")
    suspend fun getAllItems(): List<HomeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: HomeItemEntity): Long

    @Update
    suspend fun updateItem(item: HomeItemEntity)

    @Delete
    suspend fun deleteItem(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE parentId = :parentId")
    suspend fun deleteByParentId(parentId: Long)

    @Query("DELETE FROM home_items")
    suspend fun clearAll()
}
