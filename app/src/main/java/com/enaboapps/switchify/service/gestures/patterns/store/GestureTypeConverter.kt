package com.enaboapps.switchify.service.gestures.patterns.store

import androidx.room.TypeConverter
import com.enaboapps.switchify.service.gestures.data.GestureType

class GestureTypeConverter {
    @TypeConverter
    fun fromGestureType(value: GestureType): String {
        return value.name
    }

    @TypeConverter
    fun toGestureType(value: String): GestureType {
        return GestureType.valueOf(value)
    }
} 