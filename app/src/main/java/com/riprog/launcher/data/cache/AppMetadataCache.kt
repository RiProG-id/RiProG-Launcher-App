package com.riprog.launcher.data.cache

import android.content.ContentValues
import android.content.Context
import com.riprog.launcher.data.model.AppItem

class AppMetadataCache(context: Context) {
    private val dbHelper = AppCacheHelper(context)

    fun getAllApps(): List<AppItem> {
        val apps = mutableListOf<AppItem>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("apps", null, null, null, null, null, "label COLLATE NOCASE ASC")

        with(cursor) {
            while (moveToNext()) {
                val label = getString(getColumnIndexOrThrow("label"))
                val packageName = getString(getColumnIndexOrThrow("package_name"))
                val className = getString(getColumnIndexOrThrow("class_name"))
                apps.add(AppItem(label, packageName, className))
            }
        }
        cursor.close()
        return apps
    }

    fun saveApps(apps: List<AppItem>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("apps", null, null)
            for (app in apps) {
                val values = ContentValues().apply {
                    put("package_name", app.packageName)
                    put("class_name", app.className)
                    put("label", app.label)
                    put("last_update_time", System.currentTimeMillis())
                }
                db.insert("apps", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteApp(packageName: String) {
        val db = dbHelper.writableDatabase
        db.delete("apps", "package_name = ?", arrayOf(packageName))
    }
}
