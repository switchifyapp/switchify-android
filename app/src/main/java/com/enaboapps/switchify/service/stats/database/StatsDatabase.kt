package com.enaboapps.switchify.service.stats.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for usage statistics.
 * Stores both individual events and pre-aggregated stats.
 */
@Database(
    entities = [StatsEntity::class, AggregatedStatsEntity::class],
    version = 1,
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
         *
         * @param context Application context used to build the database.
         * @return The singleton StatsDatabase instance.
         */
        fun getInstance(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Resets the cached singleton database instance.
         *
         * Sets the internal INSTANCE reference to null so a new StatsDatabase can be created on next access.
         * Primarily intended for use in tests to ensure a fresh database state.
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
