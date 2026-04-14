package dev.dhanfinix.roomguard.core

/**
 * Controls the strategy used to restore a database from a backup file.
 *
 * Choose based on how the host app manages its database connection.
 */
enum class RestoreMode {

    /**
     * ## ATTACH — Table-level SQL restore (default, recommended for Room)
     *
     * **How it works:**
     * 1. Download backup to temp file.
     * 2. Validate integrity with `PRAGMA quick_check`.
     * 3. `ATTACH` the temp database as `backup_db`.
     * 4. Execute `DELETE FROM <table>` + `INSERT INTO <table> SELECT * FROM backup_db.<table>`
     *    for each table in [RestoreConfig.tables].
     * 5. `DETACH backup_db`. Clean up temp file.
     *
     * **Pros:**
     * - DB connection stays open during restore. Room observers keep working.
     * - Room's internal `room_table_modification_log` is never disturbed.
     * - WAL file stays consistent.
     *
     * **Cons:**
     * - Host must supply the list of table names in [RestoreConfig.tables].
     * - Schema must match between backup and current DB version (run migrations first if needed).
     *
     * **Use when:** Your app uses Room and you want live restore without restarting the process.
     */
    ATTACH,

    /**
     * ## REPLACE — File-swap restore
     *
     * **How it works:**
     * 1. Download backup to temp file.
     * 2. Validate integrity with `PRAGMA quick_check`.
     * 3. Close the DB connection (host must do this via [DatabaseProvider.closeDatabase]).
     * 4. Delete/overwrite the existing DB file with the temp backup file.
     * 5. Notify host to reopen DB.
     *
     * **Pros:**
     * - No need to enumerate table names.
     * - Works with any SQLite database, with or without Room.
     * - Entire schema is replaced atomically.
     *
     * **Cons:**
     * - Host must close the database before calling restore.
     * - Host must reopen (or force Room to reinitialize) after restore.
     * - Room's LiveData/Flow observers will not be automatically notified.
     *   Host must trigger recomposition/observation manually.
     *
     * **Use when:** Your app does not use Room, or you are okay restarting the DB connection.
     */
    REPLACE
}
