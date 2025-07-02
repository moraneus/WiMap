package com.ner.wimap.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [PinnedNetwork::class, TemporaryNetworkData::class],
    version = 4, // Incremented version to include offline status tracking
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pinnedNetworkDao(): PinnedNetworkDao
    abstract fun temporaryNetworkDataDao(): TemporaryNetworkDataDao

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

        // Migration from version 2 to 3 to add TemporaryNetworkData table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `temporary_network_data` (
                        `bssid` TEXT NOT NULL,
                        `ssid` TEXT NOT NULL,
                        `comment` TEXT NOT NULL,
                        `savedPassword` TEXT,
                        `photoPath` TEXT,
                        `isPinned` INTEGER NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`bssid`)
                    )
                """.trimIndent())
            }
        }

        // Migration from version 3 to 4 to add offline status tracking
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE pinned_networks ADD COLUMN isOffline INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE pinned_networks ADD COLUMN lastSeenTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}