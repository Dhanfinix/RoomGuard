package dev.dhanfinix.roomguard.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.dhanfinix.roomguard.core.DatabaseProvider
import java.io.File

/**
 * A generic implementation of [DatabaseProvider] designed specifically for Android Room databases.
 *
 * This implementation encapsulates all the necessary boilerplate to bridge Room's architecture
 * with RoomGuard's backup/restore engine. It handles critical SQLite operations like WAL
 * checkpointing and transaction management, ensuring that host applications don't need to
 * implement their own database providers for standard Room usage.
 *
 * @param context      The application context used for locating the database file.
 * @param database     The actual RoomDatabase instance being protected.
 * @param databaseName The filename of the database on disk.
 */
class RoomDatabaseProvider(
    private val context: Context,
    private val database: RoomDatabase,
    private val databaseName: String
) : DatabaseProvider {

    override fun getDatabaseName(): String = databaseName

    override fun getDatabaseFilePath(): String {
        return context.getDatabasePath(databaseName).absolutePath
    }

    /**
     * Executes a full WAL checkpoint using Room's query mechanism.
     *
     * This ensures that all pending writes currently in the `-wal` file are merged into
     * the main `.db` file, making it safe to copy for backup.
     */
    override suspend fun checkpoint() {
        database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use {
            it.moveToFirst()
        }
    }

    override suspend fun closeDatabase() {
        if (database.isOpen) {
            database.close()
        }
    }

    /**
     * Refreshes Room's internal versioning trackers.
     *
     * This is critical for reactive UI components. By calling [refreshVersionsAsync],
     * Room is notified that the underlying data has changed (via restore), triggering
     * any active Paging, LiveData, or Flow observers to reload immediately.
     */
    override suspend fun onRestoreComplete() {
        database.invalidationTracker.refreshVersionsAsync()
    }

    override fun executeRawSql(sql: String) {
        database.openHelper.writableDatabase.execSQL(sql)
    }

    /**
     * Executes batch SQL in a Room-managed transaction.
     *
     * Using [RoomDatabase.runInTransaction] ensures that Room's internal state
     * remains consistent while multiple records are being inserted or updated.
     */
    override fun executeInTransaction(queries: List<String>) {
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            queries.forEach { db.execSQL(it) }
        }
    }
}
