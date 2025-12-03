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
    version = 2,
    exportSchema = false
)
abstract class MenuDatabase : RoomDatabase() {

    /**
 * Provides the DAO for accessing and modifying MenuItemConfiguration entities.
 *
 * @return The MenuItemConfigurationDao used to query and update menu item configurations.
 */
abstract fun menuItemConfigurationDao(): MenuItemConfigurationDao

    companion object {
        private const val DATABASE_NAME = "menu_database"

        @Volatile
        private var INSTANCE: MenuDatabase? = null

        /**
         * Provides the singleton MenuDatabase instance, creating it if necessary.
         *
         * @param context Application context used to build the database.
         * @return The singleton MenuDatabase instance.
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
         * Resets the cached singleton database instance.
         *
         * Sets the internal INSTANCE reference to null so a new MenuDatabase can be created on next access.
         * Primarily intended for use in tests to ensure a fresh database state.
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}