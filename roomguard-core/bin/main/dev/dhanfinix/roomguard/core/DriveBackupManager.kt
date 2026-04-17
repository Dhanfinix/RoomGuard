package dev.dhanfinix.roomguard.core

/**
 * Contract for Google Drive backup/restore operations.
 *
 * Implement via `RoomGuardDrive` (in the `roomguard-drive` module),
 * or provide your own implementation for testing.
 *
 * Note: Authorization methods (like `isDriveAuthorized` and `requestDriveAuthorization`)
 * are available on the implementation class since they require Android-specific types.
 * This interface remains pure JVM for clean architecture and testability.
 */
interface DriveBackupManager {

    /**
     * Backs up the database file to Drive appDataFolder.
     *
     * Steps internally:
     * 1. [DatabaseProvider.checkpoint] (flush WAL)
     * 2. Copy DB file to temp file
     * 3. Sync temp file to disk
     * 4. Find or create file in Drive appDataFolder
     * 5. Upload media content
     * 6. Clean up temp file
     *
     * @param token OAuth2 access token (nullable — fetched from token store if null)
     * @return [BackupResult.Success] with file name, or [BackupResult.Error] with code
     */
    suspend fun backup(token: String? = null): BackupResult<String>

    /**
     * Restores the database from Drive appDataFolder.
     *
     * @param token OAuth2 access token
     * @param config Describes which tables to restore and which [RestoreMode] to use
     * @return [BackupResult.Success] or [BackupResult.Error]
     */
    suspend fun restore(
        token: String? = null,
        config: RestoreConfig
    ): BackupResult<String>

    /**
     * Fetches metadata about the existing Drive backup file.
     * Returns [BackupResult.Success] with null if no backup exists yet.
     */
    suspend fun getBackupInfo(token: String? = null): BackupResult<BackupInfo?>

    /**
     * Revokes Drive access and clears stored credentials.
     * Drive backup data is NOT deleted.
     */
    suspend fun revokeAccess()

    /**
     * Explicitly clears a specific access token from the Google auth system.
     * Used when token refresh fails (auth expired).
     */
    suspend fun clearToken(token: String)
}
