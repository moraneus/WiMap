package com.ner.wimap.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [PinnedNetwork::class, TemporaryNetworkData::class, ScanSession::class],
    version = 6, // GDPR compliance update
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pinnedNetworkDao(): PinnedNetworkDao
    abstract fun temporaryNetworkDataDao(): TemporaryNetworkDataDao
    abstract fun scanSessionDao(): ScanSessionDao

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
        
        // Migration from version 4 to 5 to add scan sessions
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS scan_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        networkCount INTEGER NOT NULL,
                        networks TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        // Migration from version 5 to 6 for GDPR compliance
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add GDPR compliance fields to pinned_networks
                database.execSQL("ALTER TABLE pinned_networks ADD COLUMN encryptedPassword TEXT")
                database.execSQL("ALTER TABLE pinned_networks ADD COLUMN dataRetentionDate INTEGER NOT NULL DEFAULT ${System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000)}")
                database.execSQL("ALTER TABLE pinned_networks ADD COLUMN consentVersion TEXT NOT NULL DEFAULT '1.0'")
                
                // Migrate existing passwords to encrypted format
                database.execSQL("""
                    UPDATE pinned_networks 
                    SET encryptedPassword = savedPassword 
                    WHERE savedPassword IS NOT NULL
                """.trimIndent())
                
                // Note: In a real migration, we would encrypt the passwords here
                // For now, we'll handle encryption in the application layer
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration() // For development - remove in production
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}