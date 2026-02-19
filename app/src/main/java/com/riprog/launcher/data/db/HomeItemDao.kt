package com.riprog.launcher.data.db

import androidx.room.*
import com.riprog.launcher.data.model.HomeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {
    @Query("SELECT * FROM home_items")
    fun getAllItemsFlow(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    fun getAllItems(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE parentId IS NULL")
    fun getRootItems(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE parentId = :parentId")
    fun getItemsByParent(parentId: Long): List<HomeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<HomeItemEntity>): List<Long>

    @Update
    fun update(item: HomeItemEntity): Int

    @Delete
    fun delete(item: HomeItemEntity): Int

    @Query("DELETE FROM home_items")
    fun deleteAll(): Int
}
