package com.enaboapps.switchify.service.gestures.patterns.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GesturePatternEntity::class, GestureDataEntity::class],
    version = 2,
    exportSchema = false
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
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE gesture_patterns ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
} 