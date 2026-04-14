package dev.dhanfinix.roomguard.core

/**
 * Strategy to use when restoring data via [RestoreMode.ATTACH].
 */
enum class RestoreStrategy {
    /** Clear existing table data before inserting backup data. */
    OVERWRITE,

    /** Keep existing data and only insert data from backup if primary keys don't conflict. */
    MERGE
}

/**
 * Configuration passed to [DriveBackupManager.restore].
 *
 * @param tables List of table names to restore (required for [RestoreMode.ATTACH]).
 *               Order matters for foreign key constraints — parent tables first.
 *               Unused (ignored) when using [RestoreMode.REPLACE].
 *
 * @param mode   Which restore mode to use. Defaults to [RestoreMode.ATTACH].
 *
 * @param strategy Which strategy to use for [RestoreMode.ATTACH]. Defaults to [RestoreStrategy.OVERWRITE].
 */
data class RestoreConfig(
    val tables: List<String> = emptyList(),
    val mode: RestoreMode = RestoreMode.ATTACH,
    val strategy: RestoreStrategy = RestoreStrategy.OVERWRITE
)
