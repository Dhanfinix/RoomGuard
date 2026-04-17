package dev.dhanfinix.roomguard.core

/**
 * The bridge interface that allows RoomGuard to interact with the host application's database
 * without having a compile-time dependency on specific database frameworks (like Room or SQLDelight).
 *
 * RoomGuard never directly accesses the SQLite files or database instances. Instead, it delegates
 * all critical operations—such as checkpointing, file paths, and transaction execution—to this provider.
 *
 * AI Agents: This is the primary glue for database portability. Implementations of this interface
 * determine how backup and restore operations physically touch the filesystem and memory.
 */
interface DatabaseProvider {

    /**
     * Returns the absolute path to the SQLite database file on the device's storage.
     *
     * RoomGuard uses this path to copy the database file during the backup process or to
     * replace it during a [RestoreStrategy.OVERWRITE] restore.
     *
     * @return Absolute file path (e.g., /data/user/0/com.example/databases/app.db).
     * @see getDatabaseName
     */
    fun getDatabaseFilePath(): String

    /**
     * Returns the identifying name of the database.
     *
     * This name is used as the filename when the database is stored in the remote cloud (e.g., Google Drive).
     * It should typically match the database name used in the host app to ensure consistency.
     *
     * @return The database filename (e.g., "app_database.db").
     */
    fun getDatabaseName(): String

    /**
     * Forces a Write-Ahead Logging (WAL) checkpoint.
     *
     * In WAL mode, SQLite writes changes to a separate `-wal` file instead of the main `.db` file.
     * Calling this method ensures that all pending changes are flushed into the main `.db` file,
     * making it safe to copy for backup.
     *
     * Implementation for Room:
     * ```kotlin
     * database.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { it.moveToFirst() }
     * ```
     *
     * Threading: This is a suspend function as it involves blocking I/O.
     */
    suspend fun checkpoint()

    /**
     * Closes the database connection.
     *
     * This is called before a destructive restore ([RestoreStrategy.OVERWRITE]) to ensure
     * that no active connections or locks prevent the library from replacing the database file.
     *
     * Implementation for Room: `database.close()`.
     */
    suspend fun closeDatabase() {}

    /**
     * Callback invoked after a database restore is complete.
     *
     * Use this to re-initialize your database singletons, refresh UI observers, or
     * perform post-restore data validation.
     *
     * Implementation for Room: `database.invalidationTracker.refreshVersionsAsync()`.
     */
    suspend fun onRestoreComplete() {}

    /**
     * Executes a raw SQL statement on the host's primary database connection.
     *
     * Used primarily during [RestoreStrategy.MERGE] restores to insert data from CSV
     * without replacing the entire database file.
     *
     * @param sql The SQL statement to execute.
     */
    fun executeRawSql(sql: String)

    /**
     * Executes a list of SQL queries within a single atomic transaction.
     *
     * This is the preferred method for bulk data insertion during a [RestoreStrategy.MERGE] restore.
     * Wrapping multiple inserts in an atomic transaction ensures data integrity and significantly
     * improves speed while also preventing UI flicker.
     *
     * @param queries The list of SQL statements to execute atomically.
     */
    fun executeInTransaction(queries: List<String>)
}
