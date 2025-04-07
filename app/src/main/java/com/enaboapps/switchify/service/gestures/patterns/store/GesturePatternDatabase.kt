package com.enaboapps.switchify.service.gestures.patterns.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [GesturePatternEntity::class, GestureDataEntity::class],
    version = 1
)
@TypeConverters(GestureTypeConverter::class)
abstract class GesturePatternDatabase : RoomDatabase() {
    abstract fun gesturePatternDao(): GesturePatternDao

    companion object {
        @Volatile
        private var INSTANCE: GesturePatternDatabase? = null

        fun getDatabase(context: Context): GesturePatternDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GesturePatternDatabase::class.java,
                    "gesture_pattern_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 