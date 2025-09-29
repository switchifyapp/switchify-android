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
    version = 4,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gesture_patterns ADD COLUMN `order` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gesture_data_patternId` ON `gesture_data` (`patternId`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add fingerCount column with default value of 1
                db.execSQL("ALTER TABLE gesture_data ADD COLUMN fingerCount INTEGER NOT NULL DEFAULT 1")
                // Add fingerMode column with default value of null
                db.execSQL("ALTER TABLE gesture_data ADD COLUMN fingerMode TEXT")
            }
        }
    }
} 