package dev.dhanfinix.roomguard.drive

import android.content.Context
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
 * Google Drive implementation of [DriveBackupManager].
 *
 * Also provides authorization methods ([isDriveAuthorized], [requestDriveAuthorization])
 * which are not part of the core interface due to their Android-specific return types.
 *
 * @param context           Application context (used for Drive auth client, cache dir)
 * @param appName           Host app name — used as Drive service application name
 * @param databaseProvider  Host-provided database access contract
 * @param tokenStore        Where to persist/retrieve the OAuth token
 */
class RoomGuardDrive(
    private val context: Context,
    private val appName: String,
    private val databaseProvider: DatabaseProvider,
    private val tokenStore: DriveTokenStore,
    private val config: RoomGuardConfig = RoomGuardConfig(),
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
    }


    // ── Authorization (not part of core interface) ─────────────────────────────

    /**
     * Checks if the app already has a valid Drive authorization token.
     * If a new token is obtained silently, [onTokenReceived] is called with it.
     *
     * @return true if authorized (no UI resolution needed)
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
     * Requests Drive authorization via Google Identity Services.
     * Returns an [AuthorizationResult] — callers check [hasResolution()].
     * If resolution is needed, launch the returned PendingIntent.
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
     * Backs up the database to Drive appDataFolder.
     *
     * Steps:
     * 1. WAL checkpoint (flush to main file)
     * 2. Copy DB to temp file
     * 3. fsync temp file
     * 4. Find or create file in appDataFolder named [DatabaseProvider.getDatabaseName]
     * 5. Upload media
     * 6. Log size mismatch if detected
     * 7. Clean up temp file
     */
    override suspend fun backup(token: String?): BackupResult<String> =
        withContext(Dispatchers.IO) {
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
                val existingId = findBackupFile(drive)
                
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
                    if (existingId == null) parents = listOf("appDataFolder")
                }
                
                val mimeType = if (useGzip) GZIP_MIME_TYPE else BACKUP_MIME_TYPE
                val media = FileContent(mimeType, uploadFile)

                val uploaded = if (existingId != null) {
                    drive.files().update(existingId, metadata, media)
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
     * Restores database from Drive.
     *
     * If [RestoreConfig.mode] == ATTACH:
     *   - Uses ATTACH DATABASE + INSERT SELECT for each table
     *   - DB stays open; Room observers continue working
     *
     * If [RestoreConfig.mode] == REPLACE:
     *   - Calls [DatabaseProvider.closeDatabase]
     *   - Overwrites DB file with temp backup
     *   - Calls [DatabaseProvider.onRestoreComplete]
     */
    override suspend fun restore(token: String?, config: RestoreConfig): BackupResult<String> =
        withContext(Dispatchers.IO) {
            val tempFile = java.io.File(context.cacheDir, TEMP_RESTORE_FILE)
            try {
                val resolvedToken = resolveToken(token)
                val drive = buildDriveService(resolvedToken)
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

                val fileId = findBackupFile(drive)
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, "No backup found on Drive")

                if (tempFile.exists()) tempFile.delete()
                drive.files().get(fileId).executeMediaAsInputStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
                }

                // Auto-detect compression regardless of current config
                if (ZipUtils.isGzipped(tempFile)) {
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

                val file = drive.files().get(fileId)
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
    private fun findBackupFile(drive: Drive): String? {
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
        return validFiles.find { it.name.endsWith(GZ_SUFFIX) }?.id ?: validFiles.firstOrNull()?.id
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
