package dev.dhanfinix.roomguard.sample.data

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import dev.dhanfinix.roomguard.core.DatabaseProvider

class NoteDatabaseProvider(
    private val context: Context,
    private val database: NoteDatabase
) : DatabaseProvider {
    override fun getDatabaseFilePath() = context.getDatabasePath("${NoteDatabase.DB_NAME}.db").absolutePath
    override fun getDatabaseName() = "${NoteDatabase.DB_NAME}.db"
    override suspend fun checkpoint() {
        database.noteDao().checkpoint(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
    }
    override suspend fun closeDatabase() = database.close()
    override suspend fun onRestoreComplete() {
        // In a real app, if you used REPLACE mode, you'd re-init the DB singleton or restart the process.
        // Because we use ATTACH mode, we just need to tell Room to check for external modifications
        // to instantly force Flows to emit the fresh restored data.
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
