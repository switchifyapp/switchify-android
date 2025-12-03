package com.enaboapps.switchify.service.menu.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for menu customizations.
 * Stores menu item configurations including order and visibility.
 */
@Database(
    entities = [MenuItemConfiguration::class],
    version = 1,
    exportSchema = false
)
abstract class MenuDatabase : RoomDatabase() {

    abstract fun menuItemConfigurationDao(): MenuItemConfigurationDao

    companion object {
        private const val DATABASE_NAME = "menu_database"

        @Volatile
        private var INSTANCE: MenuDatabase? = null

        /**
         * Get the singleton instance of the database.
         * Creates the database if it doesn't exist.
         *
         * @param context Application context
         * @return The database instance
         */
        fun getInstance(context: Context): MenuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MenuDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Clear the database instance.
         * Mainly used for testing purposes.
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
