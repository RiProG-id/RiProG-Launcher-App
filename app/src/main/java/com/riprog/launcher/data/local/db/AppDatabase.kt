package com.riprog.launcher.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.riprog.launcher.data.local.db.dao.HomeItemDao
import com.riprog.launcher.data.local.db.entity.HomeItemEntity

@Database(entities = [HomeItemEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeItemDao(): HomeItemDao

    companion object {
        private const val DATABASE_NAME = "launcher_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
