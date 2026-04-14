package dev.dhanfinix.roomguard.core

/**
 * The host app implements this interface to give RoomGuard access to the database.
 *
 * RoomGuard never directly touches Room or SQLiteOpenHelper — all access goes through here.
 */
interface DatabaseProvider {

    /**
     * Absolute path to the SQLite database file on disk.
     * Example: context.getDatabasePath("my_database.db").absolutePath
     */
    fun getDatabaseFilePath(): String

    /**
     * The filename of the database as stored in Drive.
     * Typically the same as the Room database name.
     * Example: "my_database.db"
     *
     * This value is used as the Drive file name, ensuring the backup
     * is identifiable by DB name, not by library name.
     */
    fun getDatabaseName(): String

    /**
     * Flush all pending WAL (Write-Ahead Logging) frames into the main database file.
     * Call this before copying the DB file for backup.
     *
     * For Room: use `database.userDao().checkpoint(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))`
     * or any DAO that has a @RawQuery method.
     *
     * This must be a suspend function — delegate to a coroutine-based DAO call.
     */
    suspend fun checkpoint()

    /**
     * (Required for [RestoreMode.REPLACE] only)
     *
     * Close the database connection so the file can be safely swapped.
     * For Room: call `database.close()`.
     * Leave this as a no-op if you only use [RestoreMode.ATTACH].
     */
    suspend fun closeDatabase() {}   // default no-op

    /**
     * (Required for [RestoreMode.REPLACE] only)
     *
     * Called after the file swap is complete. Host should reopen/reinitialize the DB.
     * For Room: re-instantiate the database singleton, or restart the process.
     * Leave this as a no-op if you only use [RestoreMode.ATTACH].
     */
    suspend fun onRestoreComplete() {}   // default no-op

    /**
     * Required for highly-reactive [RestoreMode.ATTACH] restore.
     * Execute SQL statements seamlessly on the host's primary database connection.
     * For Room, implement this via: `database.openHelper.writableDatabase.execSQL(sql)`
     */
    fun executeRawSql(sql: String)

    /**
     * Executes the list of queries within a single transaction seamlessly on the host's primary database connection.
     * Highly recommended for [RestoreMode.ATTACH] to guarantee Room properly detects the completion 
     * of the dataset insertion so observers (Flows/LiveData) will reload immediately without errors or reboots.
     * 
     * For Room, implement this via:
     * ```
     * database.runInTransaction {
     *     val db = database.openHelper.writableDatabase
     *     queries.forEach { db.execSQL(it) }
     * }
     * ```
     */
    fun executeInTransaction(queries: List<String>)
}
