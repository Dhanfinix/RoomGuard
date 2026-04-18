package dev.dhanfinix.roomguard.core

/**
 * Library-wide configuration for RoomGuard.
 *
 * @param useCompression If true, cloud backups will be GZIP compressed.
 *                       Defaults to true as it significantly reduces data usage.
 * @param localBackupFormat Default local backup format used by backward-compatible helpers.
 */
data class RoomGuardConfig(
    val useCompression: Boolean = true,
    val localBackupFormat: LocalBackupFormat = LocalBackupFormat.COMPRESSED,
    val blobStrategy: BlobStrategy = BlobStrategy.NONE,
    val backupStrategy: BackupStrategy = BackupStrategy.PHYSICAL,
    val incrementalConfig: IncrementalConfig = IncrementalConfig()
)

/**
 * Configuration for [BackupStrategy.INCREMENTAL] backups.
 * 
 * @param dataFolderName The name of the hidden folder in Google Drive (appDataFolder) 
 *                       where incremental data is stored.
 * @param trackingColumn The name of the column used for change tracking. 
 *                       Default is "last_update". 
 * @param syncDeletions  If true, rows deleted locally will be synced during restore.
 */
data class IncrementalConfig(
    val dataFolderName: String = "RoomGuard_Data",
    val trackingColumn: String = "last_update",
    val syncDeletions: Boolean = true
)
