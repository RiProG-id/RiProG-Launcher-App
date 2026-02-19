package com.riprog.launcher.data.cache

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppCacheHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_APPS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS apps")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "app_cache.db"
        private const val DATABASE_VERSION = 1

        private const val CREATE_APPS_TABLE = """
            CREATE TABLE apps (
                package_name TEXT PRIMARY KEY,
                class_name TEXT,
                label TEXT,
                last_update_time INTEGER
            )
        """
    }
}
