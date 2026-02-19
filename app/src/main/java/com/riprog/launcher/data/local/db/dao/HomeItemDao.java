package com.riprog.launcher.data.local.db.dao;

import androidx.room.*;
import com.riprog.launcher.data.local.db.entity.HomeItemEntity;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface HomeItemDao {
    @Query("SELECT * FROM home_items WHERE parentId IS NULL")
    Flow<List<HomeItemEntity>> getRootItems();

    @Query("SELECT * FROM home_items WHERE parentId IS NULL")
    List<HomeItemEntity> getRootItemsOnce();

    @Query("SELECT * FROM home_items WHERE parentId = :parentId")
    List<HomeItemEntity> getItemsByParentId(long parentId);

    @Query("SELECT * FROM home_items")
    List<HomeItemEntity> getAllItems();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertItem(HomeItemEntity item);

    @Update
    void updateItem(HomeItemEntity item);

    @Delete
    void deleteItem(HomeItemEntity item);

    @Query("DELETE FROM home_items WHERE parentId = :parentId")
    void deleteByParentId(long parentId);

    @Query("DELETE FROM home_items")
    void clearAll();
}
