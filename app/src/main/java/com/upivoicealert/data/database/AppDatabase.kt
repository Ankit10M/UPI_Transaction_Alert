package com.upivoicealert.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.upivoicealert.data.sync.SyncDiagnosticEventEntity
import com.upivoicealert.data.sync.SyncQueueEntity

@Database(
    entities = [TransactionEntity::class, UnparsedNotificationEntity::class, SyncQueueEntity::class, SyncDiagnosticEventEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun unparsedNotificationDao(): UnparsedNotificationDao
    abstract fun syncQueueDao(): com.upivoicealert.data.sync.SyncQueueDao
    abstract fun syncDiagnosticDao(): com.upivoicealert.data.sync.SyncDiagnosticDao

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

        /**
         * v3 -> v4: cross-source dedup support on the transactions table
         * (BUG #2 — same payment reported by two sources was stored twice).
         * Purely additive, no data loss:
         *   - dedupFingerprint column (nullable; NULL for legacy rows, computed
         *     at insert time for new rows)
         *   - supporting indexes for the reference-ID and fingerprint lookups.
         * Indexes are deliberately NOT unique: two legitimate same-fingerprint
         * payments must remain insertable outside the dedup window, and a unique
         * reference-ID index would fail to build against existing duplicate rows
         * (dedup stays a query-time check, never a destructive migration).
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN dedupFingerprint TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transactionId ON transactions(transactionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_dedupFingerprint ON transactions(dedupFingerprint)")
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

        /**
         * v4 -> v5: sync queue foundation for future cloud sync (Phase 5.4).
         * Creates new table sync_queue; no modification to existing transaction tables.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_status ON sync_queue(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_entityType ON sync_queue(entityType)")
            }
        }

        /**
         * v5 -> v6: cloud sync identifier for transactions (Phase 6.2).
         * Adds stable transactionUuid column for sync queue linkage.
         * Preserves existing transactions: backfills transactionUuid with existing id
         * (stable, already unique), then enforces NOT NULL and index.
         * No sync_queue changes.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transactionUuid TEXT NOT NULL DEFAULT ''")
                // Backfill legacy rows with their primary key (stable UUID already)
                db.execSQL("UPDATE transactions SET transactionUuid = id WHERE transactionUuid = ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transactionUuid ON transactions(transactionUuid)")
            }
        }

        /**
         * v6 -> v7: sync failure diagnostics (Phase 7.2).
         * Adds lastErrorCode, lastErrorMessage, failedAt to sync_queue.
         * Purely additive — existing rows default to NULL (no failure diagnostics).
         * No data loss, no table rebuild.
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastErrorCode TEXT")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN lastErrorMessage TEXT")
                db.execSQL("ALTER TABLE sync_queue ADD COLUMN failedAt INTEGER")
            }
        }

        /**
         * v7 -> v8: reconciliation integrity (Phase 7.3).
         * Adds unique index on (entityType, entityId) to enforce one queue item
         * per transaction. Before creating the index, removes deterministic
         * duplicates (keep earliest id per group) if any exist due to historic
         * race conditions.
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove duplicates: keep smallest id per (entityType, entityId)
                db.execSQL(
                    """
                    DELETE FROM sync_queue WHERE id NOT IN (
                        SELECT MIN(id) FROM sync_queue GROUP BY entityType, entityId
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_queue_entity ON sync_queue(entityType, entityId)"
                )
            }
        }

        /**
         * v8 -> v9: sync diagnostic events (Phase 7.6).
         * Additive only: creates sync_diagnostic_events table with indexes.
         */
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_diagnostic_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        affectedCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        message TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_diagnostic_events_createdAt ON sync_diagnostic_events(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_diagnostic_events_category ON sync_diagnostic_events(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_diagnostic_events_eventType ON sync_diagnostic_events(eventType)")
            }
        }
    }
}
