package com.ner.wimap.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [PinnedNetwork::class],
    version = 2, // Incremented version to handle potential schema changes
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pinnedNetworkDao(): PinnedNetworkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2 (if needed for password field updates)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add any necessary schema changes here
                // For example, if we need to modify the savedPassword field or add new fields
                // database.execSQL("ALTER TABLE pinned_networks ADD COLUMN new_field TEXT")

                // For now, no migration is needed as the schema is already correct
                // This migration is a placeholder for future schema changes
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}