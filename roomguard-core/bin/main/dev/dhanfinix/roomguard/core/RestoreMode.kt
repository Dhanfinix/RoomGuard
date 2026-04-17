package dev.dhanfinix.roomguard.core

/**
 * Defines the low-level mechanism used to restore data from a backup file.
 *
 * Each mode has significant implications for how the host application manages its database
 * connection and how reactive UI components (like Paging, LiveData, or Flow) behave during
 * the restoration process.
 */
enum class RestoreMode {

    /**
     * ### ATTACH — Seamless SQL-level restoration (Recommended for Room)
     *
     * In this mode, RoomGuard attaches the backup database to the current active connection
     * and performs table-to-table data transfer using pure SQL.
     *
     * **Operational Steps:**
     * 1. Downloads the backup to a temporary local file.
     * 2. Uses SQLite's `ATTACH DATABASE` command to link the backup file to the primary connection.
     * 3. Executes `INSERT INTO ... SELECT * FROM` statements for each specified table.
     * 4. Detaches and cleans up the temporary file.
     *
     * **Advantages:**
     * - The database connection **remains open**. Room observers continue to function.
     * - Room's internal tracking tables (like `room_table_modification_log`) remain undisturbed,
     *   ensuring reactivity is preserved.
     *
     * **Requirements:**
     * - The host must provide an explicit list of table names in [RestoreConfig.tables].
     * - The schema versions must match, or the host must run migrations before the restore.
     */
    ATTACH,

    /**
     * ### REPLACE — Compete file-swap restoration
     *
     * In this mode, RoomGuard physically replaces the application's `.db` file on disk with
     * the backup file. This is a "destructive" operation that replaces the entire schema
     * and data atomically.
     *
     * **Operational Steps:**
     * 1. Downloads the backup to a temporary local file.
     * 2. Requests the host to close the active database connection via [DatabaseProvider.closeDatabase].
     * 3. Deletes the existing `.db` file and replaces it with the backup.
     * 4. Notifies the host that the restore is complete via [DatabaseProvider.onRestoreComplete].
     *
     * **Advantages:**
     * - Does not require a list of table names.
     * - Automatically handles schema replacements.
     *
     * **Disadvantages:**
     * - Requires the database connection to be **restarted**.
     * - Reactive UI observers (LiveData/Flow) will lose their connection to the database
     *   until the host re-initializes and re-observes.
     */
    REPLACE
}
