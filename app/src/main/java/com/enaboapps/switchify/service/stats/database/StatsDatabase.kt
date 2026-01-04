package com.enaboapps.switchify.service.stats.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for usage statistics.
 * Stores individual events with indexed date column for efficient querying.
 */
@Database(
    entities = [StatsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {

    /**
     * Provides the DAO for accessing and modifying stats entities.
     *
     * @return The StatsDao used to query and update stats.
     */
    abstract fun statsDao(): StatsDao

    companion object {
        private const val DATABASE_NAME = "stats_database"

        @Volatile
        private var INSTANCE: StatsDatabase? = null

        /**
         * Provides the singleton StatsDatabase instance, creating it if necessary.
         * Uses double-checked locking pattern for thread-safe lazy initialization.
         *
         * @param context Application context used to build the database.
         * @return The singleton StatsDatabase instance.
         */
        fun getInstance(context: Context): StatsDatabase {
            // First check (without locking)
            return INSTANCE ?: synchronized(this) {
                // Second check (with locking) - prevents race condition where multiple
                // threads pass the first check before any acquires the lock
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }

        /**
         * Closes and clears the cached singleton database instance.
         *
         * Properly closes the database connection before nulling the reference to prevent leaks.
         * Primarily intended for use in tests to ensure a fresh database state.
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        if (db.isOpen) {
                            db.close()
                            android.util.Log.d("StatsDatabase", "Database closed successfully")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("StatsDatabase", "Error closing database", e)
                    }
                }
                INSTANCE = null
            }
        }
    }
}
