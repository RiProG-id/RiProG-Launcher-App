package com.riprog.launcher.data.local.db

import androidx.room.TypeConverter
import com.riprog.launcher.model.HomeItem

class Converters {
    @TypeConverter
    fun fromType(type: HomeItem.Type): String {
        return type.name
    }

    @TypeConverter
    fun toType(value: String): HomeItem.Type {
        return HomeItem.Type.valueOf(value)
    }
}
