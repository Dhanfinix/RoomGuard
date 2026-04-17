package dev.dhanfinix.roomguard.core

/**
 * Defines the strategy for data reconciliation during a [RestoreMode.ATTACH] operation.
 */
enum class RestoreStrategy {
    /**
     * Clears all existing records from the target tables before inserting the backup data.
     * This ensures the application state exactly matches the backup, but results in the 
     * loss of any local changes made since the last backup.
     */
    OVERWRITE,

    /**
     * Preserves existing local data and only inserts missing records from the backup.
     * This uses "INSERT OR IGNORE" logic based on primary keys. It is ideal for 
     * non-destructive data synchronization.
     */
    MERGE
}

/**
 * Configuration that defines the behavior of a restore operation.
 *
 * @property tables   The list of table names to be processed. 
 *                    **Critical**: List parent tables before child tables to avoid 
 *                    foreign key constraint violations during the restore process.
 * @property mode     The technical method used to restore data (SQL-based vs File-based).
 * @property strategy The data reconciliation policy used when in [RestoreMode.ATTACH].
 */
data class RestoreConfig(
    val tables: List<String> = emptyList(),
    val mode: RestoreMode = RestoreMode.ATTACH,
    val strategy: RestoreStrategy = RestoreStrategy.OVERWRITE
)
