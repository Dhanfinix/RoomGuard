package dev.dhanfinix.roomguard.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.dhanfinix.roomguard.core.DatabaseProvider
import java.io.File

/**
 * A generic [DatabaseProvider] for any Android Room database.
 * 
 * This removes the need for host applications to implement their own
 * bridge class for RoomGuard.
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

    override suspend fun checkpoint() {
        // Force a WAL checkpoint to ensure the .db file is up-to-date before backup
        database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use {
            it.moveToFirst()
        }
    }

    override suspend fun closeDatabase() {
        if (database.isOpen) {
            database.close()
        }
    }

    override suspend fun onRestoreComplete() {
        // Tell Room to refresh its internal versioning trackers
        // This ensures UI flows (like paging or livedata) refresh immediately
        database.invalidationTracker.refreshVersionsAsync()
    }

    override fun executeRawSql(sql: String) {
        database.openHelper.writableDatabase.execSQL(sql)
    }

    override fun executeInTransaction(queries: List<String>) {
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            queries.forEach { db.execSQL(it) }
        }
    }
}
