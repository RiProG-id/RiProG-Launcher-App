package com.riprog.launcher.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [HomeItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun homeItemDao(): HomeItemDao
}
