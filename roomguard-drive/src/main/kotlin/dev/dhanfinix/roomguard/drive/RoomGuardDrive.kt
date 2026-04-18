package dev.dhanfinix.roomguard.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import dev.dhanfinix.roomguard.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Implementation of [DriveBackupManager] that leverages Google Drive's `appDataFolder`
 * for cloud-based database persistence.
 *
 * This class is the core engine for cloud protection in RoomGuard. it orchestrates:
 * 1. **Authentication**: Integration with Google Identity Services (GIS) for OAuth2 tokens.
 * 2. **Integrity**: WAL checkpointing and atomic file copies before upload.
 * 3. **Concurrency**: Automatic file updates vs. creation in the restricted `appDataFolder`.
 * 4. **Atomicity**: Transaction-based restorations to prevent partial data corruption.
 *
 * ### AppDataFolder Security
 * Every backup is stored in a hidden, app-specific folder on the user's Google Drive. 
 * This ensures that other applications cannot access the user's database backups.
 *
 * @param context           Application context, used for GIS and temporary file management.
 * @param appName           The application name used for identifying the Drive service.
 * @param databaseProvider  The [DatabaseProvider] contract that bridges this engine to SQLite/Room.
 * @param tokenStore        The persistence layer for the OAuth2 access token.
 * @param config            General configuration (compression, timeouts, etc.).
 * @param authClient        The GIS client for authorization requests.
 * @param signInClient      The GIS client for account management and sign-out.
 */
class RoomGuardDrive(
    private val context: Context,
    private val appName: String,
    private val databaseProvider: DatabaseProvider,
    private val tokenStore: DriveTokenStore,
    private val config: RoomGuardConfig = RoomGuardConfig(),
    private val serializer: CsvSerializer? = null,
    private val authClient: AuthorizationClient = Identity.getAuthorizationClient(context),
    private val signInClient: SignInClient = Identity.getSignInClient(context)
) : DriveBackupManager {

    companion object {
        private const val BACKUP_MIME_TYPE = "application/x-sqlite3"
        private const val GZIP_MIME_TYPE = "application/gzip"
        private const val GZ_SUFFIX = ".gz"
        private const val TEMP_BACKUP_FILE = "roomguard_backup_temp.db"
        private const val TEMP_COMPRESSED_FILE = "roomguard_backup_temp.db.gz"
        private const val TEMP_RESTORE_FILE = "roomguard_restore_temp.db"
        private const val TAG_BACKUP = "RoomGuard:Backup"
        private const val TAG_RESTORE = "RoomGuard:Restore"
        private const val METADATA_FILE_NAME = "data.csv"
    }

    private data class RemoteBackupFile(
        val id: String,
        val isCompressed: Boolean
    )


    // ── Authorization (not part of core interface) ─────────────────────────────

    /**
     * Checks if the application already holds a valid authorization token for Google Drive.
     *
     * This method attempts to silenty refresh the token if one exists in the [tokenStore].
     * If successful, the token is passed to [onTokenReceived] for immediate use.
     *
     * @param onTokenReceived High-order callback triggered when a valid token is available.
     * @return true if the user is fully authorized with required scopes; false if UI
     *         resolution (account picker/consent) is required.
     */
    suspend fun isDriveAuthorized(
        onTokenReceived: suspend (String) -> Unit
    ): Boolean {
        if (!tokenStore.isAuthorized()) return false
        val result = requestDriveAuthorization()
        result.accessToken?.let { onTokenReceived(it) }
        return result.hasResolution().not()
    }

    /**
     * Initializes the Google Identity Services request for Drive access.
     *
     * The request includes scopes for `drive.appdata` (file access), `email`, and `profile`.
     * Callers should check the resulting [AuthorizationResult.hasResolution] and
     * launch the provided PendingIntent if necessary.
     *
     * AI Agents: This is a low-level GIS call. For a simplified Compose integration,
     * see `RoomGuardActionHelper.requestAuth`.
     *
     * @return The authorization result containing either an access token or an intent for resolution.
     */
    suspend fun requestDriveAuthorization(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope("email"),
                Scope("profile")
            ))
            // Note: In GIS AuthorizationClient, we don't necessarily need the client ID
            // but the package name + SHA-1 must match in the console for this project.
            .build()
        return authClient.authorize(request).await()
    }

    /**
     * Handles the result from the Drive authentication launcher.
     * Extracts the access token and verifies the required scopes.
     *
     * @param resultCode The result code from the activity result
     * @param data The intent data from the activity result
     * @return Result containing the access token on success
     */
    fun handleAuthResult(resultCode: Int, data: Intent?): Result<String> {
        if (resultCode != Activity.RESULT_OK) {
            val msg = if (resultCode == Activity.RESULT_CANCELED) "Sign-in canceled"
            else "Sign-in failed (result: $resultCode)"
            return Result.failure(Exception(msg))
        }

        return try {
            val authResult = authClient.getAuthorizationResultFromIntent(data)
            
            // The user must explicitly check the Drive permission box.
            // If they don't, GIS still returns OK but the token lacks the scope.
            val driveScope = Scope(DriveScopes.DRIVE_APPDATA).toString()
            val hasDriveScope = authResult.grantedScopes?.any { 
                it.equals(driveScope, ignoreCase = true) || it.contains("drive.appdata") 
            } == true
            
            val token = authResult.accessToken
            if (hasDriveScope && token != null) {
                Result.success(token)
            } else {
                Result.failure(Exception("Google Drive permission is required. Please make sure to check the box to allow access."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Drive Service Builder ──────────────────────────────────────────────────

    private fun buildDriveService(token: String?): Drive? {
        val accessToken = token ?: return null
        val credentials = GoogleCredentials.create(AccessToken(accessToken, null))
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        ).setApplicationName(appName).build()
    }

    private suspend fun resolveToken(token: String?): String? =
        token ?: tokenStore.getToken()

    // ── Backup ─────────────────────────────────────────────────────────────────

    /**
     * Performs a full backup of the application database to the Google Drive cloud.
     *
     * This operation is atomic and follows a high-integrity sequence:
     * 1. **Checkpoint**: Flushes WAL frames into the main database file via [DatabaseProvider.checkpoint].
     * 2. **Staging**: Copies the database file to a temporary location to prevent locking.
     * 3. **Fsync**: Forces a hardware-level sync of the temporary file to ensure data durability.
     * 4. **Compression**: Optionally compresses the database using GZIP (determined by [RoomGuardConfig.useCompression]).
     * 5. **Sync**: Uploads the staged file to Drive's hidden `appDataFolder`.
     *
     * @param token The OAuth2 access token. If null, the engine attempts to pull from [tokenStore].
     * @return A [BackupResult] indicating success or details of the failure.
     */
    override suspend fun backup(token: String?): BackupResult<String> =
        withContext(Dispatchers.IO) {
            if (config.backupStrategy == BackupStrategy.INCREMENTAL) {
                return@withContext performIncrementalBackup(token)
            }
            
            val tempFile = java.io.File(context.cacheDir, TEMP_BACKUP_FILE)
            val compressedFile = java.io.File(context.cacheDir, TEMP_COMPRESSED_FILE)
            try {
                val resolvedToken = resolveToken(token)
                val drive = buildDriveService(resolvedToken)
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

                databaseProvider.checkpoint()

                val dbFile = java.io.File(databaseProvider.getDatabaseFilePath())
                if (!dbFile.exists()) {
                    return@withContext BackupResult.Error(BackupErrorCode.DB_NOT_FOUND, "Database file not found")
                }

                if (tempFile.exists()) tempFile.delete()
                dbFile.copyTo(tempFile, overwrite = true)
                FileOutputStream(tempFile, true).use { it.fd.sync() }

                val dbName = databaseProvider.getDatabaseName()
                val existingBackup = findBackupFile(drive)
                
                val useGzip = config.useCompression
                val uploadFile = if (useGzip) {
                    if (compressedFile.exists()) compressedFile.delete()
                    ZipUtils.compressFile(tempFile, compressedFile)
                    compressedFile
                } else {
                    tempFile
                }

                val metadata = DriveFile().apply {
                    name = if (useGzip) "$dbName$GZ_SUFFIX" else dbName
                    if (existingBackup == null) parents = listOf("appDataFolder")
                }
                
                val mimeType = if (useGzip) GZIP_MIME_TYPE else BACKUP_MIME_TYPE
                val media = FileContent(mimeType, uploadFile)

                val uploaded = if (existingBackup != null) {
                    drive.files().update(existingBackup.id, metadata, media)
                        .setFields("id, name, modifiedTime, size").execute()
                } else {
                    drive.files().create(metadata, media)
                        .setFields("id, name, modifiedTime, size").execute()
                }

                if (uploaded.getSize().toLong() != uploadFile.length()) {
                    Log.w(TAG_BACKUP, "Size mismatch — Local: ${uploadFile.length()}, Drive: ${uploaded.getSize()}")
                }

                BackupResult.Success("Backup successful: ${uploaded.name}")

            } catch (e: Throwable) {
                Log.e(TAG_BACKUP, "Backup failed", e)
                if (isAuthError(e)) {
                    handleAuthFailure()
                    BackupResult.Error(BackupErrorCode.AUTH_EXPIRED, "Google Drive access was revoked. Please reconnect.")
                } else {
                    BackupResult.Error(BackupErrorCode.BACKUP_FAILED, "Backup failed: ${e.message}")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
                if (compressedFile.exists()) compressedFile.delete()
            }
        }

    // ── Restore ────────────────────────────────────────────────────────────────

    /**
     * Restores the application database from a backup stored on Google Drive.
     *
     * The engine supports two distinct restoration modes as defined in [RestoreConfig.mode]:
     * - **[RestoreMode.ATTACH]**: Preferred for Room. Merges or overwrites data via SQL while 
     *   keeping the primary database connection open and UI observers active.
     * - **[RestoreMode.REPLACE]**: Performs a raw file-swap. Requires the database connection
     *   to be closed and restarted.
     *
     * Integrity is verified using `PRAGMA quick_check` before any data modification occurs.
     *
     * @param token  The OAuth2 access token.
     * @param config Configuration detailing the tables to restore and the mode to use.
     * @return A [BackupResult] with success message or error details.
     */
    override suspend fun restore(token: String?, config: RestoreConfig): BackupResult<String> =
        withContext(Dispatchers.IO) {
            if (this@RoomGuardDrive.config.backupStrategy == BackupStrategy.INCREMENTAL) {
                return@withContext performIncrementalRestore(token, config)
            }

            val tempFile = java.io.File(context.cacheDir, TEMP_RESTORE_FILE)
            try {
                val resolvedToken = resolveToken(token)
                val drive = buildDriveService(resolvedToken)
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

                val remoteBackup = findBackupFile(drive)
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, "No backup found on Drive")

                if (tempFile.exists()) tempFile.delete()
                drive.files().get(remoteBackup.id).executeMediaAsInputStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
                }

                // Restore compressed backups transparently.
                if (remoteBackup.isCompressed || ZipUtils.isGzipped(tempFile)) {
                    val decompressedFile = java.io.File(context.cacheDir, "${TEMP_RESTORE_FILE}_decomp")
                    try {
                        ZipUtils.decompressFile(tempFile, decompressedFile)
                        decompressedFile.copyTo(tempFile, overwrite = true)
                    } finally {
                        if (decompressedFile.exists()) decompressedFile.delete()
                    }
                }

                if (!verifyIntegrity(tempFile)) {
                    return@withContext BackupResult.Error(
                        BackupErrorCode.INTEGRITY_FAILED,
                        "Backup file is damaged or format is unrecognized."
                    )
                }

                when (config.mode) {
                    RestoreMode.ATTACH -> executeAttachRestore(tempFile, config)
                    RestoreMode.REPLACE -> executeReplaceRestore(tempFile)
                }

                BackupResult.Success("Restore successful")

            } catch (e: Throwable) {
                Log.e(TAG_RESTORE, "Restore failed", e)
                if (isAuthError(e)) {
                    handleAuthFailure()
                    BackupResult.Error(BackupErrorCode.AUTH_EXPIRED, "Google Drive access was revoked. Please reconnect.")
                } else {
                    BackupResult.Error(BackupErrorCode.RESTORE_FAILED, "Restore failed: ${e.message}")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

    // ── ATTACH restore (internal) ──────────────────────────────────────────────

    /**
     * Runs inside a transaction:
     * 1. Ensure room_table_modification_log exists (Room warmup safeguard)
     * 2. ATTACH backup database
     * 3. For each table: DELETE + INSERT SELECT *
     * 4. DETACH
     */
    private suspend fun executeAttachRestore(tempFile: java.io.File, config: RestoreConfig) {
        val tables = config.tables
        databaseProvider.executeRawSql("ATTACH DATABASE '${tempFile.absolutePath}' AS backup_db")
        try {
            val queries = tables.flatMap { table ->
                if (config.strategy == RestoreStrategy.MERGE) {
                    // Merge: Only insert missing data
                    listOf("INSERT OR IGNORE INTO $table SELECT * FROM backup_db.$table")
                } else {
                    // Overwrite: Delete existing and insert backup data
                    listOf("DELETE FROM $table", "INSERT INTO $table SELECT * FROM backup_db.$table")
                }
            }
            databaseProvider.executeInTransaction(queries)
        } finally {
            databaseProvider.executeRawSql("DETACH DATABASE backup_db")
        }
        databaseProvider.onRestoreComplete()
    }

    // ── REPLACE restore (internal) ─────────────────────────────────────────────

    private suspend fun executeReplaceRestore(tempFile: java.io.File) {
        databaseProvider.closeDatabase()
        val dest = java.io.File(databaseProvider.getDatabaseFilePath())

        // Clean up sidecar files (WAL/SHM) to prevent corruption on next open
        val path = dest.absolutePath
        val walFile = java.io.File("$path-wal")
        val shmFile = java.io.File("$path-shm")
        if (walFile.exists()) walFile.delete()
        if (shmFile.exists()) shmFile.delete()

        tempFile.copyTo(dest, overwrite = true)
        databaseProvider.onRestoreComplete()
    }

    // ── Backup Info ────────────────────────────────────────────────────────────

    override suspend fun getBackupInfo(token: String?): BackupResult<BackupInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(resolveToken(token))
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

                // Fetch user info (email)
                // Fetch user info (email) - Silently ignore if this fails specifically with auth
                val userEmail = try {
                    drive.about().get().setFields("user").execute()?.user?.emailAddress
                } catch (e: Exception) {
                    Log.w(TAG_BACKUP, "Optional email fetch failed: ${e.message}")
                    null
                }

                val fileId = findBackupFile(drive)
                if (fileId == null) {
                    return@withContext BackupResult.Success(
                        BackupInfo(
                            id = null,
                            name = null,
                            modifiedTime = null,
                            size = null,
                            userEmail = userEmail
                        )
                    )
                }

                val file = drive.files().get(fileId.id)
                    .setFields("id, name, modifiedTime, size").execute()

                BackupResult.Success(
                    BackupInfo(
                        id = file.id,
                        name = file.name,
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        size = file.getSize()?.toLong() ?: 0L,
                        isCompressed = file.name?.endsWith(GZ_SUFFIX) == true,
                        userEmail = userEmail
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG_BACKUP, "Error fetching backup info", e)
                if (isAuthError(e)) {
                    handleAuthFailure()
                    BackupResult.Error(BackupErrorCode.AUTH_EXPIRED, "Google Drive access was revoked. Please reconnect.")
                } else {
                    BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, e.message ?: "Failed to fetch backup metadata")
                }
            }
        }

    // ── Access management ──────────────────────────────────────────────────────

    override suspend fun revokeAccess() {
        // 1. Clear the current token from the Google Auth server/cache (if available)
        try {
            val currentToken = tokenStore.getToken()
            if (currentToken != null) {
                val request = ClearTokenRequest.builder().setToken(currentToken).build()
                authClient.clearToken(request).await()
            }
        } catch (e: Exception) {
            Log.e("RoomGuard:Revoke", "Clear token failed", e)
        }

        // 2. Sign out from Identity (forces account picker on next auth)
        try {
            @Suppress("DEPRECATION")
            signInClient.signOut().await()
        } catch (e: Exception) {
            Log.e("RoomGuard:Revoke", "Identity sign out failed", e)
        }

        // 3. Clear local state
        tokenStore.clearToken()
        tokenStore.setAuthorized(false)

        // 4. Credential Manager cleanup (best effort)
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("RoomGuard:Revoke", "Credential Manager clear state failed", e)
        }
    }

    override suspend fun clearToken(token: String) {
        val request = ClearTokenRequest.builder().setToken(token).build()
        authClient.clearToken(request).await()
    }

    private suspend fun handleAuthFailure() {
        // 1. Clear the current token from the Google Auth cache so Play Services forgets it
        try {
            val currentToken = tokenStore.getToken()
            if (currentToken != null) {
                authClient.clearToken(ClearTokenRequest.builder().setToken(currentToken).build()).await()
            }
        } catch (_: Exception) {}

        tokenStore.clearToken()
        tokenStore.setAuthorized(false)
        
        // Clear Identity session so account picker shows up next time
        try {
            @Suppress("DEPRECATION")
            signInClient.signOut().await()
        } catch (_: Exception) {}

        // Clear Credential Manager state
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {}
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Detects if an exception is caused by an expired or revoked OAuth token
     * that cannot be refreshed by the client.
     */
    private fun isAuthError(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val msg = current.message ?: ""
            if (msg.contains("refreshing", ignoreCase = true) || 
                msg.contains("OAuth2", ignoreCase = true) ||
                msg.contains("401", ignoreCase = true)
            ) {
                return true
            }
            if (current is com.google.api.client.googleapis.json.GoogleJsonResponseException && current.statusCode == 401) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Finds the backup file in Drive appDataFolder by the DB name.
     * Returns the file ID (opaque string), or null if not found.
     */
    private fun findBackupFile(drive: Drive): RemoteBackupFile? {
        val dbName = databaseProvider.getDatabaseName()
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("(name = '$dbName' or name = '$dbName$GZ_SUFFIX') and trashed = false")
            .setFields("files(id, name, size)")
            .execute()
        
        // Filter out files that have 0 size or are missing size metadata
        // This prevents "zombie" files from being detected as valid backups.
        val validFiles = result.files.filter { (it.getSize() ?: 0L) > 0 }
        
        // Prioritize compressed version if both exist for some reason
        val compressed = validFiles.firstOrNull { it.name.endsWith(GZ_SUFFIX) }
        if (compressed != null) {
            return RemoteBackupFile(compressed.id, isCompressed = true)
        }

        val plain = validFiles.firstOrNull() ?: return null
        return RemoteBackupFile(plain.id, isCompressed = false)
    }

    private suspend fun performIncrementalBackup(token: String?): BackupResult<String> {
        return try {
            val resolvedToken = resolveToken(token)
            val drive = buildDriveService(resolvedToken)
                ?: return BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

            val serializer = serializer ?: return BackupResult.Error(BackupErrorCode.BACKUP_FAILED, "No CSV serializer provided")

            // 1. Ensure/Resolve the logical data folder
            val folderId = getOrCreateDataFolder(drive)
            val blobsFolderId = getOrCreateSubFolder(drive, folderId, "blobs")

            // 2. Serialize database to logical bundle (full sync to support deletion syncing)
            // The blobDir in serializer should point to a temporary location for export
            val tempBlobDir = java.io.File(context.cacheDir, "roomguard_logical_blobs")
            if (tempBlobDir.exists()) tempBlobDir.deleteRecursively()
            tempBlobDir.mkdirs()

            if (serializer is BlobCapableSerializer) {
                serializer.blobDir = tempBlobDir
            }

            val bundle = serializer.toBackupBundle(null)

            // 3. Differential BLOB uploads
            val remoteBlobs = listRemoteFiles(drive, blobsFolderId)
            bundle.blobFiles.forEach { (relativePath, file) ->
                // relativePath is like "blobs/hash.bin"
                val fileName = file.name
                if (fileName !in remoteBlobs) {
                    uploadFileToFolder(drive, blobsFolderId, fileName, file, "application/octet-stream")
                }
            }

            // 4. Update Metadata CSV
            val metadataFile = java.io.File(context.cacheDir, "roomguard_metadata.csv")
            metadataFile.writeText(bundle.csvContent)
            uploadOrUpdateFileInFolder(drive, folderId, METADATA_FILE_NAME, metadataFile, "text/csv")

            BackupResult.Success("Incremental backup complete")
        } catch (e: Exception) {
            Log.e(TAG_BACKUP, "Incremental backup failed", e)
            BackupResult.Error(BackupErrorCode.BACKUP_FAILED, "Incremental backup failed: ${e.message}")
        }
    }

    private suspend fun performIncrementalRestore(token: String?, config: RestoreConfig): BackupResult<String> {
        return try {
            val resolvedToken = resolveToken(token)
            val drive = buildDriveService(resolvedToken)
                ?: return BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

            val serializer = serializer ?: return BackupResult.Error(BackupErrorCode.RESTORE_FAILED, "No CSV serializer provided")

            // 1. Resolve folder and metadata
            val folderId = findFolder(drive, "appDataFolder", this.config.incrementalConfig.dataFolderName)
                ?: return BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, "No incremental backup found")
            
            val metadataFileId = findFileInFolder(drive, folderId, METADATA_FILE_NAME)
                ?: return BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, "Metadata file not found")

            // 2. Download metadata
            val csvContent = drive.files().get(metadataFileId).executeMediaAsInputStream().use { it.bufferedReader().readText() }

            // 3. Identify and Download needed blobs
            val blobsFolderId = findFolder(drive, folderId, "blobs")
            val tempBlobDir = java.io.File(context.cacheDir, "roomguard_restore_blobs")
            if (tempBlobDir.exists()) tempBlobDir.deleteRecursively()
            tempBlobDir.mkdirs()

            val remoteBlobFiles = if (blobsFolderId != null) listRemoteFiles(drive, blobsFolderId) else emptyMap()

            // We need to parse the CSV roughly to find [FILE]: markers
            // Or better, let the serializer handle it if we provide a way.
            // For now, we'll download ALL blobs in the remote folder to the temp dir
            // to keep it simple, or iterate the CSV.
            // Iterating CSV is more "Holy Grail" (only download what is used).
            
            val neededBlobs = csvContent.lineSequence()
                .filter { it.contains("[FILE]:") }
                .map { it.substringAfter("[FILE]:").substringBefore(",") }
                .toSet()

            for (blobPath in neededBlobs) {
                val fileName = blobPath.substringAfter("blobs/")
                val fileId = remoteBlobFiles[fileName]
                if (fileId != null) {
                    val destFile = java.io.File(tempBlobDir, blobPath)
                    destFile.parentFile?.mkdirs()
                    drive.files().get(fileId).executeMediaAsInputStream().use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            // 4. Ingest into database
            if (serializer is BlobCapableSerializer) {
                serializer.blobDir = tempBlobDir
            }
            serializer.fromCsv(csvContent, config.strategy)

            databaseProvider.onRestoreComplete()
            BackupResult.Success("Incremental restore complete")
        } catch (e: Exception) {
            Log.e(TAG_RESTORE, "Incremental restore failed", e)
            BackupResult.Error(BackupErrorCode.RESTORE_FAILED, "Incremental restore failed: ${e.message}")
        }
    }

    // ── Drive Helpers ──────────────────────────────────────────────────────────

    private fun getOrCreateDataFolder(drive: Drive): String {
        val folderName = config.incrementalConfig.dataFolderName
        return findFolder(drive, "appDataFolder", folderName) ?: createFolder(drive, "appDataFolder", folderName)
    }

    private fun getOrCreateSubFolder(drive: Drive, parentId: String, folderName: String): String {
        return findFolder(drive, parentId, folderName) ?: createFolder(drive, parentId, folderName)
    }

    private fun findFolder(drive: Drive, parentId: String, folderName: String): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$folderName' and '$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setFields("files(id)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun createFolder(drive: Drive, parentId: String, folderName: String): String {
        val metadata = DriveFile().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        return drive.files().create(metadata).setFields("id").execute().id
    }

    private fun listRemoteFiles(drive: Drive, folderId: String): Map<String, String> {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("'$folderId' in parents and trashed = false")
            .setFields("files(id, name)")
            .execute()
        return result.files?.associate { it.name to it.id } ?: emptyMap()
    }

    private fun findFileInFolder(drive: Drive, folderId: String, fileName: String): String? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$fileName' and '$folderId' in parents and trashed = false")
            .setFields("files(id)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun uploadFileToFolder(drive: Drive, folderId: String, fileName: String, file: java.io.File, mimeType: String) {
        val metadata = DriveFile().apply {
            name = fileName
            parents = listOf(folderId)
        }
        val media = FileContent(mimeType, file)
        drive.files().create(metadata, media).execute()
    }

    private fun uploadOrUpdateFileInFolder(drive: Drive, folderId: String, fileName: String, file: java.io.File, mimeType: String) {
        val existingId = findFileInFolder(drive, folderId, fileName)
        val metadata = DriveFile().apply { name = fileName }
        val media = FileContent(mimeType, file)
        if (existingId != null) {
            drive.files().update(existingId, metadata, media).execute()
        } else {
            metadata.parents = listOf(folderId)
            drive.files().create(metadata, media).execute()
        }
    }

    /**
     * Runs PRAGMA quick_check against the downloaded temp file.
     * @return true if the database is intact ("ok")
     */
    private fun verifyIntegrity(file: java.io.File): Boolean = try {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        }
    } catch (_: Exception) { false }
}
