package dev.dhanfinix.roomguard.core

/**
 * Metadata about an existing remote backup file.
 * Returned by [DriveBackupManager.getBackupInfo].
 */
data class BackupInfo(
    /** The Drive file ID (opaque string). Null if no backup exists. */
    val id: String?,
    /** The file name stored in Drive (= host DB name). Null if no backup exists. */
    val name: String?,
    /** Last modification epoch millis. Null if no backup exists. */
    val modifiedTime: Long?,
    /** File size in bytes. Null if no backup exists. */
    val size: Long?,
    /** Whether the backup is GZIP compressed. */
    val isCompressed: Boolean = false,
    /** The email address of the connected Google account. */
    val userEmail: String? = null
)
