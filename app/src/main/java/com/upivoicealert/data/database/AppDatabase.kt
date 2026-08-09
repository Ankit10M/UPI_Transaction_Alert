package com.upivoicealert.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, UnparsedNotificationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun unparsedNotificationDao(): UnparsedNotificationDao

    companion object {
        /**
         * v1 -> v2: multi-source metadata for the transactions table (CLAUDE.md
         * Module 4 evolution). Purely additive — ALTER TABLE ADD COLUMN only, no
         * table rebuild, no data loss. Legacy rows get safe defaults:
         *   - sourceType            = 'UNKNOWN'
         *   - packageName           = ''      (unrecoverable for old rows)
         *   - notificationKey       = NULL
         *   - originalNotificationText / cleanedNotificationText = backfilled
         *     from rawNotification (the best available text for old rows; for
         *     rows written after the cleaner task this is the cleaned text).
         */
        /**
         * v2 -> v3: voice announcement status on the transactions table.
         * Purely additive — a single ALTER TABLE ADD COLUMN, no data loss.
         * Legacy rows default to false (not announced).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN voiceAnnounced INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN packageName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN notificationKey TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalNotificationText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN cleanedNotificationText TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE transactions SET originalNotificationText = rawNotification, " +
                        "cleanedNotificationText = rawNotification WHERE originalNotificationText = ''"
                )
            }
        }
    }
}
