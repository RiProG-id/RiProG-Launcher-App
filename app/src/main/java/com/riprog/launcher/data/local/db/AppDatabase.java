package com.riprog.launcher.data.local.db;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.riprog.launcher.data.local.db.dao.HomeItemDao;
import com.riprog.launcher.data.local.db.entity.HomeItemEntity;

@Database(entities = {HomeItemEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract HomeItemDao homeItemDao();

    private static final String DATABASE_NAME = "launcher_db";
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DATABASE_NAME
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
