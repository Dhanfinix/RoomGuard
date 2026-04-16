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
    val localBackupFormat: LocalBackupFormat = LocalBackupFormat.COMPRESSED
)
