# RoomGuard — Library Spec-Kit

> Version: 1.0.0-alpha | Status: Pre-implementation
> Source reference: Stasis app — `DriveRemoteDataSource`, `BackupViewModel`, `BackupScreen`

---

## Table of Contents
1. [Overview](#1-overview)
2. [Design Principles](#2-design-principles)
3. [Project Structure](#3-project-structure)
4. [Module: roomguard-core](#4-module-roomguard-core)
5. [Module: roomguard-drive](#5-module-roomguard-drive)
6. [Module: roomguard-local](#6-module-roomguard-local)
7. [Module: roomguard-hilt](#7-module-roomguard-hilt)
8. [Module: roomguard-ui](#8-module-roomguard-ui)
9. [Sample App (app/)](#9-sample-app-app)
10. [Build & Gradle Setup](#10-build--gradle-setup)
11. [GitHub Packages Publishing](#11-github-packages-publishing)
12. [Integration Guide (Consumer README)](#12-integration-guide-consumer-readme)
13. [Dependency Versions Locked](#13-dependency-versions-locked)
14. [Error Codes Reference](#14-error-codes-reference)

---

## 1. Overview

**RoomGuard** is a standalone Android library that provides plug-and-play database backup and restore for any app using SQLite (Room or raw). It supports two backup strategies:

| Strategy | Module | Description |
|---|---|---|
| Google Drive | `roomguard-drive` | Backs up the raw SQLite file to the host app's private Drive `appDataFolder`. Restore via `ATTACH` or file-replace. |
| Local / CSV | `roomguard-local` | Exports data as a host-app-defined CSV file. Supports share via intent, save-to-device, and import from file picker. |

The library is **framework-agnostic** at its core: no Hilt, no Room, no Compose in `roomguard-core`. Optional integration artifacts are provided for each.

The library is published to **GitHub Packages** under:
```
https://maven.pkg.github.com/Dhanfinix/RoomGuard
```

---

## 2. Design Principles

| Principle | Rationale |
|---|---|
| **No framework lock-in in core** | `roomguard-core` has zero Hilt/Room/Compose transitive deps |
| **Host app owns the data schema** | `CsvSerializer` is a pure interface — no reflection or annotation processing |
| **Drive folder is host-app-scoped** | Google OAuth appDataFolder is keyed on the OAuth client ID (= the host app), so backups are naturally isolated |
| **DB file named by host app** | `DatabaseProvider.getDatabaseName()` is used for the file uploaded to Drive — never the library name |
| **Restore mode is explicit** | `RestoreMode.ATTACH` (default, Room-safe) or `RestoreMode.REPLACE` (DB must be closed, simpler) |
| **Token store is optional default** | `DataStoreDriveTokenStore` is shipped; callers can swap for SharedPrefs, Room, or encrypted storage |
| **Coroutines-first** | All async API surface uses `suspend` functions or `Flow`. No callbacks except where the Drive SDK requires it. |

---

## 3. Project Structure

```
RoomGuard/
│
├── roomguard-core/              ← Interfaces, models, error codes (no Android framework deps)
│   └── src/main/kotlin/dev/dhanfinix/roomguard/core/
│       ├── BackupResult.kt
│       ├── BackupInfo.kt
│       ├── SyncStatus.kt
│       ├── RestoreMode.kt
│       ├── ImportSummary.kt
│       ├── BackupErrorCode.kt
│       ├── DriveBackupManager.kt    ← interface
│       ├── LocalBackupManager.kt    ← interface
│       ├── DatabaseProvider.kt      ← interface (host implements)
│       └── CsvSerializer.kt         ← interface (host implements)
│
├── roomguard-drive/             ← Google Drive engine
│   └── src/main/kotlin/dev/dhanfinix/roomguard/drive/
│       ├── RoomGuardDrive.kt        ← implements DriveBackupManager
│       ├── RestoreConfig.kt
│       ├── DriveTokenStore.kt       ← interface
│       └── token/
│           └── DataStoreDriveTokenStore.kt  ← default impl
│
├── roomguard-local/             ← CSV local backup engine
│   └── src/main/kotlin/dev/dhanfinix/roomguard/local/
│       └── RoomGuardLocal.kt        ← implements LocalBackupManager
│
├── roomguard-hilt/              ← Optional Hilt DI modules
│   └── src/main/kotlin/dev/dhanfinix/roomguard/hilt/
│       ├── RoomGuardDriveModule.kt
│       └── RoomGuardLocalModule.kt
│
├── roomguard-ui/                ← Optional pre-built Compose backup screen
│   └── src/main/kotlin/dev/dhanfinix/roomguard/ui/
│       ├── RoomGuardBackupScreen.kt
│       ├── RoomGuardBackupViewModel.kt
│       └── component/
│           ├── BackupStatusHeader.kt
│           ├── CloudActionsGroup.kt
│           └── LocalDataGroup.kt
│
├── app/                         ← Sample app (Notes app demo)
│   └── src/main/
│       ├── java/dev/dhanfinix/sample/
│       │   ├── data/
│       │   │   ├── NoteDatabase.kt
│       │   │   ├── NoteDao.kt
│       │   │   ├── NoteEntity.kt
│       │   │   ├── NoteDatabaseProvider.kt     ← implements DatabaseProvider
│       │   │   └── NoteCsvSerializer.kt        ← implements CsvSerializer
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       └── MainViewModel.kt
│       └── res/...
│
├── build.gradle.kts             ← Root build
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
└── .github/
    └── workflows/
        └── publish.yml
```

---

## 4. Module: roomguard-core

### 4.1 `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)               // Pure Kotlin, no Android plugin
    `maven-publish`
}

group = "dev.dhanfinix.roomguard"
version = "1.0.0"

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            artifactId = "roomguard-core"
        }
    }
}
```

> **Note**: This module uses `kotlin.jvm` not `android.library` on purpose — pure JVM means it can also be unit-tested with standard JUnit on the host machine with no Android dependencies.

---

### 4.2 `BackupResult.kt`

```kotlin
package dev.dhanfinix.roomguard.core

sealed class BackupResult<out T> {
    data class Success<T>(val data: T) : BackupResult<T>()
    data class Error(
        val code: String,          // Use BackupErrorCode constants
        val message: String
    ) : BackupResult<Nothing>()
}

// Extension helpers
fun <T> BackupResult<T>.isSuccess(): Boolean = this is BackupResult.Success
fun <T> BackupResult<T>.isError(): Boolean = this is BackupResult.Error
fun <T> BackupResult<T>.getOrNull(): T? = (this as? BackupResult.Success)?.data
fun <T> BackupResult<T>.errorCode(): String? = (this as? BackupResult.Error)?.code
```

---

### 4.3 `BackupInfo.kt`

```kotlin
package dev.dhanfinix.roomguard.core

/**
 * Metadata about an existing remote backup file.
 * Returned by [DriveBackupManager.getBackupInfo].
 */
data class BackupInfo(
    /** The Drive file ID (opaque string) */
    val id: String,
    /** The file name stored in Drive (= host DB name) */
    val name: String,
    /** Last modification epoch millis */
    val modifiedTime: Long,
    /** File size in bytes */
    val size: Long
)
```

---

### 4.4 `SyncStatus.kt`

```kotlin
package dev.dhanfinix.roomguard.core

/**
 * Represents the sync relationship between local data and the remote Drive backup.
 */
enum class SyncStatus {
    /** Initial state while checks are in flight */
    Checking,
    /** Local and remote timestamps match */
    Synced,
    /** Local data has changed since the last backup */
    LocalNewer,
    /** Remote backup is newer than local — user may want to restore */
    RemoteNewer
}
```

---

### 4.5 `RestoreMode.kt`

```kotlin
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
```

---

### 4.6 `ImportSummary.kt`

```kotlin
package dev.dhanfinix.roomguard.core

/**
 * Summary returned after a CSV import via [LocalBackupManager.importFromCsv].
 * The host's [CsvSerializer.fromCsv] is responsible for populating this.
 */
data class ImportSummary(
    val itemsImported: Int,
    val itemsSkipped: Int,
    val message: String
)
```

---

### 4.7 `BackupErrorCode.kt`

```kotlin
package dev.dhanfinix.roomguard.core

object BackupErrorCode {
    const val AUTH_EXPIRED     = "AUTH_EXPIRED"       // Drive token invalid or revoked
    const val BACKUP_FAILED    = "BACKUP_FAILED"      // Upload to Drive failed
    const val RESTORE_FAILED   = "RESTORE_FAILED"     // Restore from Drive failed
    const val NO_BACKUP_FOUND  = "NO_BACKUP_FOUND"    // No file in appDataFolder
    const val DB_NOT_FOUND     = "DB_NOT_FOUND"       // Local DB file missing before backup
    const val INTEGRITY_FAILED = "INTEGRITY_FAILED"   // PRAGMA quick_check returned non-ok
    const val EXPORT_FAILED    = "EXPORT_FAILED"      // CSV generation or write failed
    const val IMPORT_FAILED    = "IMPORT_FAILED"      // CSV read or parse failed
    const val NOT_AUTHORIZED   = "NOT_AUTHORIZED"     // Drive API returned not-authorized
}
```

---

### 4.8 `DriveBackupManager.kt`

```kotlin
package dev.dhanfinix.roomguard.core

import kotlinx.coroutines.flow.Flow

/**
 * Contract for Google Drive backup/restore operations.
 *
 * Implement via [RoomGuardDrive] (roomguard-drive module),
 * or provide your own implementation for testing.
 */
interface DriveBackupManager {

    /**
     * Checks if the app already has a valid Drive authorization token.
     * If a new token is obtained silently, [onTokenReceived] is called with it.
     *
     * @return true if authorized (no UI resolution needed)
     */
    suspend fun isDriveAuthorized(
        onTokenReceived: suspend (String) -> Unit
    ): Boolean

    /**
     * Requests Drive authorization via Google Identity Services.
     * Returns an [AuthorizationResult] — callers check [hasResolution()].
     * If resolution is needed, launch the returned PendingIntent.
     *
     * Note: This wraps [Identity.getAuthorizationClient(context).authorize(...).await()].
     */
    suspend fun requestDriveAuthorization(): com.google.android.gms.auth.api.identity.AuthorizationResult

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
     * @param token OAuth2 access token (nullable — fetched from [DriveTokenStore] if null)
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
```

**Note:** `RestoreConfig` is defined in `roomguard-drive` because it references `RestoreMode`.

---

### 4.9 `LocalBackupManager.kt`

```kotlin
package dev.dhanfinix.roomguard.core

import android.net.Uri
import java.io.File

/**
 * Contract for local CSV-based backup/restore operations.
 *
 * Implement via [RoomGuardLocal] (roomguard-local module).
 */
interface LocalBackupManager {

    /**
     * Exports all app data to a CSV string via [CsvSerializer.toCsv],
     * then writes it to the app's cache directory.
     *
     * @return [BackupResult.Success] containing the generated [File],
     *         or [BackupResult.Error] on failure.
     *
     * Caller decides what to do with the File (share via Intent, copy to external, etc.).
     */
    suspend fun exportToCsv(): BackupResult<File>

    /**
     * Reads a CSV file from the given [uri] and delegates parsing to [CsvSerializer.fromCsv].
     *
     * @param uri Content URI resolved by the host Activity (file picker result)
     * @return [BackupResult.Success] with [ImportSummary], or [BackupResult.Error]
     */
    suspend fun importFromCsv(uri: Uri): BackupResult<ImportSummary>
}
```

---

### 4.10 `DatabaseProvider.kt`

```kotlin
package dev.dhanfinix.roomguard.core

/**
 * The host app implements this interface to give RoomGuard access to the database.
 *
 * RoomGuard never directly touches Room or SQLiteOpenHelper — all access goes through here.
 */
interface DatabaseProvider {

    /**
     * Absolute path to the SQLite database file on disk.
     * Example: context.getDatabasePath("my_database.db").absolutePath
     */
    fun getDatabaseFilePath(): String

    /**
     * The filename of the database as stored in Drive.
     * Typically the same as the Room database name.
     * Example: "my_database.db"
     *
     * This value is used as the Drive file name, ensuring the backup
     * is identifiable by DB name, not by library name.
     */
    fun getDatabaseName(): String

    /**
     * Flush all pending WAL (Write-Ahead Logging) frames into the main database file.
     * Call this before copying the DB file for backup.
     *
     * For Room: use `database.userDao().checkpoint(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))`
     * or any DAO that has a @RawQuery method.
     *
     * This must be a suspend function — delegate to a coroutine-based DAO call.
     */
    suspend fun checkpoint()

    /**
     * (Required for [RestoreMode.REPLACE] only)
     *
     * Close the database connection so the file can be safely swapped.
     * For Room: call `database.close()`.
     * Leave this as a no-op if you only use [RestoreMode.ATTACH].
     */
    suspend fun closeDatabase() {}   // default no-op

    /**
     * (Required for [RestoreMode.REPLACE] only)
     *
     * Called after the file swap is complete. Host should reopen/reinitialize the DB.
     * For Room: re-instantiate the database singleton, or restart the process.
     * Leave this as a no-op if you only use [RestoreMode.ATTACH].
     */
    suspend fun onRestoreComplete() {}   // default no-op
}
```

---

### 4.11 `CsvSerializer.kt`

```kotlin
package dev.dhanfinix.roomguard.core

/**
 * The host app implements this interface to define how its data is serialized to/from CSV.
 *
 * RoomGuard has no knowledge of the host app's entity classes.
 * The host is fully responsible for:
 * - Generating the CSV string (schema, sections, encoding)
 * - Parsing CSV lines back into entities
 * - Duplicate detection during import
 * - Returning an [ImportSummary] with counts
 *
 * See sample app's `NoteCsvSerializer` for a reference implementation.
 */
interface CsvSerializer {

    /**
     * Convert all app data to a single CSV string.
     *
     * This is a suspend function so DB reads can be done inside a coroutine.
     * The format is entirely defined by the host app.
     *
     * Recommended structure:
     * ```
     * [SECTION_NAME]
     * column1,column2,...
     * value1,value2,...
     *
     * [ANOTHER_SECTION]
     * ...
     * ```
     *
     * @return A UTF-8 CSV string
     */
    suspend fun toCsv(): String

    /**
     * Parse the given CSV string and insert/merge data into the local database.
     *
     * This is a suspend function so DB writes can be done inside a coroutine.
     * The host is responsible for:
     * - Parsing section headers
     * - Parsing column names
     * - Type conversion
     * - Conflict resolution (skip duplicates, replace, or merge)
     *
     * @param content Full CSV string (from [exportToCsv] or user-provided file)
     * @return [ImportSummary] with counts of what was imported vs. skipped
     */
    suspend fun fromCsv(content: String): ImportSummary
}
```

---

## 5. Module: roomguard-drive

### 5.1 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "dev.dhanfinix.roomguard.drive"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":roomguard-core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)   // for DataStoreDriveTokenStore
    implementation(libs.google.api.drive)                  // com.google.apis:google-api-services-drive
    implementation(libs.google.play.auth)                  // com.google.android.gms:play-services-auth
    implementation(libs.google.auth)                       // com.google.auth:google-auth-library-oauth2-http
    implementation(libs.kotlinx.coroutines.play.services)  // kotlinx-coroutines-play-services (for .await())
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = "roomguard-drive"
        }
    }
}
```

---

### 5.2 `RestoreConfig.kt`

```kotlin
package dev.dhanfinix.roomguard.drive

import dev.dhanfinix.roomguard.core.RestoreMode

/**
 * Configuration passed to [RoomGuardDrive.restore].
 *
 * @param tables List of table names to restore (required for [RestoreMode.ATTACH]).
 *               Order matters for foreign key constraints — parent tables first.
 *               Unused (ignored) when using [RestoreMode.REPLACE].
 *
 * @param mode   Which restore strategy to use. Defaults to [RestoreMode.ATTACH].
 */
data class RestoreConfig(
    val tables: List<String> = emptyList(),
    val mode: RestoreMode = RestoreMode.ATTACH
)
```

---

### 5.3 `DriveTokenStore.kt`

```kotlin
package dev.dhanfinix.roomguard.drive

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction for persisting the Google Drive OAuth access token.
 *
 * Use [DataStoreDriveTokenStore] (default, ships with this module),
 * or implement your own backed by SharedPreferences, EncryptedSharedPreferences, etc.
 */
interface DriveTokenStore {
    /** Observable stream of the current token (null if not set) */
    fun getTokenFlow(): Flow<String?>

    /** Persist the access token */
    suspend fun saveToken(token: String?)

    /** Remove the stored token */
    suspend fun clearToken()

    /** One-shot read of the current token */
    suspend fun getToken(): String?
}
```

---

### 5.4 `DataStoreDriveTokenStore.kt`

```kotlin
package dev.dhanfinix.roomguard.drive.token

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore by preferencesDataStore(name = "roomguard_token_store")

/**
 * Default [DriveTokenStore] implementation backed by Jetpack DataStore (Preferences).
 *
 * Data is stored in a file named `roomguard_token_store.preferences_pb`
 * inside the host app's data directory.
 *
 * Usage:
 * ```kotlin
 * val tokenStore = DataStoreDriveTokenStore(context)
 * ```
 */
class DataStoreDriveTokenStore(private val context: Context) : DriveTokenStore {

    private val KEY_TOKEN = stringPreferencesKey("drive_access_token")

    override fun getTokenFlow(): Flow<String?> =
        context.tokenDataStore.data.map { it[KEY_TOKEN] }

    override suspend fun saveToken(token: String?) {
        context.tokenDataStore.edit { prefs ->
            if (token != null) prefs[KEY_TOKEN] = token
            else prefs.remove(KEY_TOKEN)
        }
    }

    override suspend fun clearToken() {
        context.tokenDataStore.edit { it.remove(KEY_TOKEN) }
    }

    override suspend fun getToken(): String? =
        context.tokenDataStore.data.first()[KEY_TOKEN]
}
```

---

### 5.5 `RoomGuardDrive.kt`

Full class structure (implementation detail follows the Stasis `DriveRemoteDataSource`):

```kotlin
package dev.dhanfinix.roomguard.drive

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Google Drive implementation of [DriveBackupManager].
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
    private val tokenStore: DriveTokenStore
) : DriveBackupManager {

    companion object {
        private const val BACKUP_MIME_TYPE = "application/x-sqlite3"
        private const val TEMP_BACKUP_FILE = "roomguard_backup_temp.db"
        private const val TEMP_RESTORE_FILE = "roomguard_restore_temp.db"
        private const val TAG_BACKUP = "RoomGuard:Backup"
        private const val TAG_RESTORE = "RoomGuard:Restore"
    }

    private val authClient = Identity.getAuthorizationClient(context)

    // ── Authorization ──────────────────────────────────────────────────────────

    override suspend fun isDriveAuthorized(
        onTokenReceived: suspend (String) -> Unit
    ): Boolean {
        val result = requestDriveAuthorization()
        result.accessToken?.let { onTokenReceived(it) }
        return result.hasResolution().not()
    }

    override suspend fun requestDriveAuthorization(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
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
     *
     * Error conditions:
     * - Drive service null → [BackupErrorCode.NOT_AUTHORIZED]
     * - DB file not found → [BackupErrorCode.DB_NOT_FOUND]
     * - IllegalStateException with "refreshing" → [BackupErrorCode.AUTH_EXPIRED] + clearToken
     * - Any other exception → [BackupErrorCode.BACKUP_FAILED]
     */
    override suspend fun backup(token: String?): BackupResult<String> =
        withContext(Dispatchers.IO) {
            val tempFile = java.io.File(context.cacheDir, TEMP_BACKUP_FILE)
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

                val existingId = findBackupFile(drive)
                val metadata = DriveFile().apply {
                    name = databaseProvider.getDatabaseName()
                    if (existingId == null) parents = listOf("appDataFolder")
                }
                val media = FileContent(BACKUP_MIME_TYPE, tempFile)

                val uploaded = if (existingId != null) {
                    drive.files().update(existingId, metadata, media)
                        .setFields("id, name, modifiedTime, size").execute()
                } else {
                    drive.files().create(metadata, media)
                        .setFields("id, name, modifiedTime, size").execute()
                }

                if (uploaded.size.toLong() != tempFile.length()) {
                    Log.w(TAG_BACKUP, "Size mismatch — Local: ${tempFile.length()}, Drive: ${uploaded.size}")
                }

                BackupResult.Success("Backup successful: ${uploaded.name}")

            } catch (e: Exception) {
                Log.e(TAG_BACKUP, "Backup failed", e)
                val isAuthError = e is IllegalStateException && e.message?.contains("refreshing") == true
                if (isAuthError) {
                    resolveToken(token)?.let { clearToken(it) }
                    BackupResult.Error(BackupErrorCode.AUTH_EXPIRED, "Drive access expired. Please reconnect.")
                } else {
                    BackupResult.Error(BackupErrorCode.BACKUP_FAILED, "Backup failed: ${e.message}")
                }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

    // ── Restore ────────────────────────────────────────────────────────────────

    /**
     * Restores database from Drive.
     *
     * If [RestoreConfig.mode] == ATTACH:
     *   - Uses ATTACH DATABASE + INSERT SELECT for each table in [RestoreConfig.tables]
     *   - DB stays open; Room observers continue working
     *   - DETACH after transaction
     *
     * If [RestoreConfig.mode] == REPLACE:
     *   - Calls [DatabaseProvider.closeDatabase]
     *   - Overwrites DB file with temp backup
     *   - Calls [DatabaseProvider.onRestoreComplete]
     *
     * Both modes: Download → quick_check → restore → cleanup
     *
     * Error conditions:
     * - No backup file on Drive → [BackupErrorCode.NO_BACKUP_FOUND]
     * - `quick_check` fails → [BackupErrorCode.INTEGRITY_FAILED]
     * - Any other exception → [BackupErrorCode.RESTORE_FAILED]
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

                if (!verifyIntegrity(tempFile)) {
                    return@withContext BackupResult.Error(
                        BackupErrorCode.INTEGRITY_FAILED,
                        "Backup file is damaged. Please create a new backup."
                    )
                }

                when (config.mode) {
                    RestoreMode.ATTACH -> executeAttachRestore(tempFile, config.tables)
                    RestoreMode.REPLACE -> executeReplaceRestore(tempFile)
                }

                BackupResult.Success("Restore successful")

            } catch (e: Exception) {
                Log.e(TAG_RESTORE, "Restore failed", e)
                BackupResult.Error(BackupErrorCode.RESTORE_FAILED, "Restore failed: ${e.message}")
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
     *
     * Important: table order should respect FK constraints (parent tables first).
     */
    private fun executeAttachRestore(tempFile: java.io.File, tables: List<String>) {
        val dbPath = databaseProvider.getDatabaseFilePath()
        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE)
        db.use {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS room_table_modification_log" +
                    "(table_id INTEGER PRIMARY KEY, invalidated INTEGER NOT NULL DEFAULT 0)"
                )
            } catch (_: Exception) {}

            db.execSQL("ATTACH DATABASE '${tempFile.absolutePath}' AS backup_db")
            try {
                db.beginTransaction()
                try {
                    tables.forEach { table ->
                        db.execSQL("DELETE FROM $table")
                        db.execSQL("INSERT INTO $table SELECT * FROM backup_db.$table")
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            } finally {
                db.execSQL("DETACH DATABASE backup_db")
            }
        }
    }

    // ── REPLACE restore (internal) ─────────────────────────────────────────────

    private suspend fun executeReplaceRestore(tempFile: java.io.File) {
        databaseProvider.closeDatabase()
        val dest = java.io.File(databaseProvider.getDatabaseFilePath())
        tempFile.copyTo(dest, overwrite = true)
        databaseProvider.onRestoreComplete()
    }

    // ── Backup Info ────────────────────────────────────────────────────────────

    override suspend fun getBackupInfo(token: String?): BackupResult<BackupInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val drive = buildDriveService(resolveToken(token))
                    ?: return@withContext BackupResult.Error(BackupErrorCode.NOT_AUTHORIZED, "Not authorized")

                val fileId = findBackupFile(drive)
                    ?: return@withContext BackupResult.Success(null)

                val file = drive.files().get(fileId)
                    .setFields("id, name, modifiedTime, size").execute()

                BackupResult.Success(
                    BackupInfo(
                        id = file.id,
                        name = file.name,
                        modifiedTime = file.modifiedTime?.value ?: 0L,
                        size = file.size.toLong()
                    )
                )
            } catch (e: Exception) {
                BackupResult.Error(BackupErrorCode.NO_BACKUP_FOUND, e.message ?: "")
            }
        }

    // ── Access management ──────────────────────────────────────────────────────

    override suspend fun revokeAccess() {
        try {
            val result = requestDriveAuthorization()
            result.toGoogleSignInAccount()?.account?.let { account ->
                val request = RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
                    .build()
                authClient.revokeAccess(request).await()
            }
        } catch (e: Exception) {
            Log.e("RoomGuard:Revoke", "Revoke failed", e)
        }
        CredentialManager.create(context)
            .clearCredentialState(ClearCredentialStateRequest())
        tokenStore.clearToken()
    }

    override suspend fun clearToken(token: String) {
        val request = ClearTokenRequest.builder().setToken(token).build()
        authClient.clearToken(request).await()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Finds the backup file in Drive appDataFolder by the DB name.
     * Returns the file ID (opaque string), or null if not found.
     */
    private fun findBackupFile(drive: Drive): String? {
        val dbName = databaseProvider.getDatabaseName()
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$dbName' and trashed = false")
            .setFields("files(id, name)")
            .execute()
        return result.files.firstOrNull()?.id
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
```

---

## 6. Module: roomguard-local

### 6.1 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "dev.dhanfinix.roomguard.local"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":roomguard-core"))
    implementation(libs.kotlinx.coroutines.android)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }
            artifactId = "roomguard-local"
        }
    }
}
```

---

### 6.2 `RoomGuardLocal.kt`

```kotlin
package dev.dhanfinix.roomguard.local

import android.content.Context
import android.net.Uri
import dev.dhanfinix.roomguard.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

/**
 * Local file (CSV) implementation of [LocalBackupManager].
 *
 * @param context    Application context (used for cache dir and content resolver)
 * @param serializer Host-provided CSV serializer — owns the data format entirely
 * @param filePrefix Prefix for the generated CSV filename. Defaults to "roomguard_backup".
 *                   Consider using your app's name, e.g. "myapp_backup".
 */
class RoomGuardLocal(
    private val context: Context,
    private val serializer: CsvSerializer,
    private val filePrefix: String = "roomguard_backup"
) : LocalBackupManager {

    // ── Export ─────────────────────────────────────────────────────────────────

    /**
     * Generates CSV via [CsvSerializer.toCsv] and writes it to the app's cache directory.
     *
     * File name format: "{filePrefix}_{epochMillis}.csv"
     *
     * The returned [File] can be:
     * - Shared via [Intent.ACTION_SEND] (use FileProvider URI)
     * - Written to a user-picked URI via SAF (CREATE_DOCUMENT)
     *
     * The caller (or [roomguard-ui]) is responsible for the UI action.
     */
    override suspend fun exportToCsv(): BackupResult<File> = withContext(Dispatchers.IO) {
        try {
            val csv = serializer.toCsv()
            val fileName = "${filePrefix}_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(csv)
                writer.flush()
            }
            BackupResult.Success(file)
        } catch (e: Exception) {
            BackupResult.Error(BackupErrorCode.EXPORT_FAILED, "Export failed: ${e.message}")
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Reads the file at [uri] and delegates to [CsvSerializer.fromCsv].
     *
     * The host's [CsvSerializer.fromCsv] handles all parsing, deduplication,
     * and insertion into the DB.
     */
    override suspend fun importFromCsv(uri: Uri): BackupResult<ImportSummary> =
        withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@withContext BackupResult.Error(
                        BackupErrorCode.IMPORT_FAILED, "Could not open file"
                    )

                val summary = serializer.fromCsv(content)
                BackupResult.Success(summary)
            } catch (e: Exception) {
                BackupResult.Error(BackupErrorCode.IMPORT_FAILED, "Import failed: ${e.message}")
            }
        }
}
```

---

## 7. Module: roomguard-hilt

### 7.1 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    kotlin("kapt")
    `maven-publish`
}

android {
    namespace = "dev.dhanfinix.roomguard.hilt"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":roomguard-core"))
    api(project(":roomguard-drive"))
    api(project(":roomguard-local"))
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
```

---

### 7.2 `RoomGuardDriveModule.kt`

```kotlin
package dev.dhanfinix.roomguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.core.DatabaseProvider
import dev.dhanfinix.roomguard.core.DriveBackupManager
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.drive.token.DataStoreDriveTokenStore
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardDriveModule {

    @Provides
    @Singleton
    fun provideDriveTokenStore(
        @ApplicationContext context: Context
    ): DriveTokenStore = DataStoreDriveTokenStore(context)

    /**
     * Provide [DriveBackupManager] with host-supplied [DatabaseProvider] and app name.
     * The host must bind [DatabaseProvider] in their own Hilt module.
     *
     * @Named("appName") — host must provide a String with this qualifier
     */
    @Provides
    @Singleton
    fun provideDriveBackupManager(
        @ApplicationContext context: Context,
        @Named("appName") appName: String,
        databaseProvider: DatabaseProvider,
        tokenStore: DriveTokenStore
    ): DriveBackupManager = RoomGuardDrive(context, appName, databaseProvider, tokenStore)
}
```

---

### 7.3 `RoomGuardLocalModule.kt`

```kotlin
package dev.dhanfinix.roomguard.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.LocalBackupManager
import dev.dhanfinix.roomguard.local.RoomGuardLocal
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomGuardLocalModule {

    /**
     * Provide [LocalBackupManager] with host-supplied [CsvSerializer] and optional file prefix.
     * The host must bind [CsvSerializer] in their own Hilt module.
     *
     * @Named("csvFilePrefix") — optional, host can provide a String, e.g. "myapp_backup"
     */
    @Provides
    @Singleton
    fun provideLocalBackupManager(
        @ApplicationContext context: Context,
        serializer: CsvSerializer,
        @Named("csvFilePrefix") filePrefix: String = "roomguard_backup"
    ): LocalBackupManager = RoomGuardLocal(context, serializer, filePrefix)
}
```

---

## 8. Module: roomguard-ui

### 8.1 `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "dev.dhanfinix.roomguard.ui"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

dependencies {
    api(project(":roomguard-core"))
    api(project(":roomguard-drive"))
    api(project(":roomguard-local"))
    implementation(libs.androidx.compose.bom)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

---

### 8.2 `RoomGuardBackupViewModel.kt`

```kotlin
package dev.dhanfinix.roomguard.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dhanfinix.roomguard.core.*
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RestoreConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for [RoomGuardBackupScreen].
 *
 * Does NOT use Hilt — instantiate manually via [Factory].
 * This keeps roomguard-ui free of Hilt as a transitive dependency.
 */
class RoomGuardBackupViewModel(
    private val driveManager: DriveBackupManager,
    private val localManager: LocalBackupManager,
    private val tokenStore: DriveTokenStore,
    private val defaultRestoreConfig: RestoreConfig
) : ViewModel() {

    // ── State ──────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    // One-shot events (Drive auth intent, file share/save)
    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BackupUiEvent> = _events.asSharedFlow()

    private var remoteModifiedTime: Long? = null

    init {
        checkDriveAuthorized()
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    fun onAction(action: BackupScreenAction) {
        when (action) {
            is BackupScreenAction.ConnectDrive     -> requestDriveAuth(action.context)
            is BackupScreenAction.RevokeAccess     -> confirmRevokeAccess()
            is BackupScreenAction.Backup           -> onBackupRequested(action.context)
            is BackupScreenAction.Restore          -> onRestoreRequested(action.context)
            is BackupScreenAction.AuthResult       -> handleAuthResult(action.token)
            is BackupScreenAction.AuthFailed       -> showError("Drive authorization failed")
            is BackupScreenAction.ExportCsv        -> exportCsv()
            is BackupScreenAction.SaveCsvToDevice  -> saveCsvToDevice()
            is BackupScreenAction.ImportCsv        -> importCsv(action.uri)
        }
    }

    // ── Drive Auth ─────────────────────────────────────────────────────────────

    private fun checkDriveAuthorized() {
        viewModelScope.launch {
            val authorized = driveManager.isDriveAuthorized { token ->
                tokenStore.saveToken(token)
            }
            _uiState.update { it.copy(isDriveAuthorized = authorized) }
            if (authorized) fetchBackupInfo()
        }
    }

    private fun requestDriveAuth(context: Context) {
        setProcessing(true, "Authorizing Drive...")
        viewModelScope.launch {
            val result = driveManager.requestDriveAuthorization()
            if (result.hasResolution()) {
                _events.emit(BackupUiEvent.LaunchDriveAuth(result.pendingIntent!!))
            } else {
                result.accessToken?.let { token ->
                    tokenStore.saveToken(token)
                    checkDriveAuthorized()
                    fetchAndHandleFirstConnect(token)
                }
            }
            setProcessing(false)
        }
    }

    private fun handleAuthResult(token: String?) {
        if (token != null) {
            viewModelScope.launch {
                tokenStore.saveToken(token)
                checkDriveAuthorized()
                fetchAndHandleFirstConnect(token)
            }
        } else {
            showError("Drive authorization failed")
        }
    }

    // ── Backup ─────────────────────────────────────────────────────────────────

    private fun onBackupRequested(context: Context) {
        val status = _uiState.value.syncStatus
        if (status == SyncStatus.RemoteNewer) {
            _events.tryEmit(BackupUiEvent.ConfirmOverwriteRemote(
                onConfirm = { backup() }
            ))
        } else {
            backup()
        }
    }

    private fun backup() {
        setProcessing(true, "Backing up data...")
        viewModelScope.launch {
            val token = tokenStore.getToken()
            when (val result = driveManager.backup(token)) {
                is BackupResult.Success -> {
                    showSuccess("Backup successful")
                    fetchBackupInfo()
                }
                is BackupResult.Error -> {
                    if (result.code == BackupErrorCode.AUTH_EXPIRED) requestDriveAuth(null!!)
                    else showError(result.message)
                }
            }
            setProcessing(false)
        }
    }

    // ── Restore ────────────────────────────────────────────────────────────────

    private fun onRestoreRequested(context: Context) {
        val status = _uiState.value.syncStatus
        if (status == SyncStatus.LocalNewer) {
            _events.tryEmit(BackupUiEvent.ConfirmOverwriteLocal(
                onConfirm = { restore() }
            ))
        } else {
            restore()
        }
    }

    private fun restore() {
        setProcessing(true, "Restoring data...")
        viewModelScope.launch {
            val token = tokenStore.getToken()
            when (val result = driveManager.restore(token, defaultRestoreConfig)) {
                is BackupResult.Success -> showSuccess("Restore successful")
                is BackupResult.Error   -> showError(result.message)
            }
            setProcessing(false)
        }
    }

    // ── CSV Export / Import ────────────────────────────────────────────────────

    private fun exportCsv() {
        setProcessing(true, "Preparing file...")
        viewModelScope.launch {
            when (val result = localManager.exportToCsv()) {
                is BackupResult.Success -> _events.emit(BackupUiEvent.ShareFile(result.data.absolutePath))
                is BackupResult.Error   -> showError(result.message)
            }
            setProcessing(false)
        }
    }

    private fun saveCsvToDevice() {
        setProcessing(true, "Preparing data...")
        viewModelScope.launch {
            when (val result = localManager.exportToCsv()) {
                is BackupResult.Success -> _events.emit(
                    BackupUiEvent.SaveFileToDevice(
                        fileName = result.data.name,
                        filePath = result.data.absolutePath
                    )
                )
                is BackupResult.Error -> showError(result.message)
            }
            setProcessing(false)
        }
    }

    private fun importCsv(uri: String) {
        setProcessing(true, "Importing data...")
        viewModelScope.launch {
            val parsedUri = android.net.Uri.parse(uri)
            when (val result = localManager.importFromCsv(parsedUri)) {
                is BackupResult.Success -> showSuccess(result.data.message)
                is BackupResult.Error   -> showError(result.message)
            }
            setProcessing(false)
        }
    }

    // ── Info ───────────────────────────────────────────────────────────────────

    private fun fetchBackupInfo() {
        setProcessing(true, "Checking status...")
        viewModelScope.launch {
            val token = tokenStore.getToken()
            when (val result = driveManager.getBackupInfo(token)) {
                is BackupResult.Success -> {
                    result.data?.let { info ->
                        remoteModifiedTime = info.modifiedTime
                        _uiState.update { it.copy(lastBackupDate = info.modifiedTime) }
                    }
                }
                is BackupResult.Error -> { /* silently ignore metadata errors */ }
            }
            setProcessing(false)
        }
    }

    private fun fetchAndHandleFirstConnect(token: String) {
        setProcessing(true, "Checking for existing backup...")
        viewModelScope.launch {
            when (val result = driveManager.getBackupInfo(token)) {
                is BackupResult.Success -> {
                    val info = result.data
                    if (info != null) {
                        remoteModifiedTime = info.modifiedTime
                        _events.emit(BackupUiEvent.AskRestoreOnFirstConnect(
                            onRestore = { restore() },
                            onKeepLocal = { showSuccess("Drive connected. Backup or restore anytime.") }
                        ))
                    } else {
                        showSuccess("Drive connected successfully")
                    }
                }
                is BackupResult.Error -> showSuccess("Drive connected successfully")
            }
            setProcessing(false)
        }
    }

    private fun confirmRevokeAccess() {
        _events.tryEmit(BackupUiEvent.ConfirmRevoke(
            onConfirm = {
                viewModelScope.launch {
                    driveManager.revokeAccess()
                    _uiState.update { it.copy(isDriveAuthorized = false) }
                    showSuccess("Drive access revoked")
                }
            }
        ))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun setProcessing(isProcessing: Boolean, message: String? = null) {
        _uiState.update { it.copy(isProcessing = isProcessing, loadingMessage = message) }
    }

    private fun showSuccess(msg: String) {
        _events.tryEmit(BackupUiEvent.ShowMessage(msg, isError = false))
    }

    private fun showError(msg: String) {
        _events.tryEmit(BackupUiEvent.ShowMessage(msg, isError = true))
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    class Factory(
        private val driveManager: DriveBackupManager,
        private val localManager: LocalBackupManager,
        private val tokenStore: DriveTokenStore,
        private val restoreConfig: RestoreConfig
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomGuardBackupViewModel(driveManager, localManager, tokenStore, restoreConfig) as T
    }
}

// ── Supporting types ──────────────────────────────────────────────────────────

data class BackupUiState(
    val isDriveAuthorized: Boolean = false,
    val isProcessing: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Checking,
    val loadingMessage: String? = null,
    val lastBackupDate: Long? = null
)

sealed interface BackupScreenAction {
    data class ConnectDrive(val context: Context) : BackupScreenAction
    data object RevokeAccess : BackupScreenAction
    data class Backup(val context: Context) : BackupScreenAction
    data class Restore(val context: Context) : BackupScreenAction
    data class AuthResult(val token: String?) : BackupScreenAction
    data object AuthFailed : BackupScreenAction
    data object ExportCsv : BackupScreenAction
    data object SaveCsvToDevice : BackupScreenAction
    data class ImportCsv(val uri: String) : BackupScreenAction
}

sealed interface BackupUiEvent {
    data class LaunchDriveAuth(val pendingIntent: android.app.PendingIntent) : BackupUiEvent
    data class ShareFile(val filePath: String) : BackupUiEvent
    data class SaveFileToDevice(val fileName: String, val filePath: String) : BackupUiEvent
    data class ShowMessage(val message: String, val isError: Boolean) : BackupUiEvent
    data class ConfirmOverwriteRemote(val onConfirm: () -> Unit) : BackupUiEvent
    data class ConfirmOverwriteLocal(val onConfirm: () -> Unit) : BackupUiEvent
    data class ConfirmRevoke(val onConfirm: () -> Unit) : BackupUiEvent
    data class AskRestoreOnFirstConnect(
        val onRestore: () -> Unit,
        val onKeepLocal: () -> Unit
    ) : BackupUiEvent
}
```

---

### 8.3 `RoomGuardBackupScreen.kt`

```kotlin
package dev.dhanfinix.roomguard.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dhanfinix.roomguard.core.DriveBackupManager
import dev.dhanfinix.roomguard.core.LocalBackupManager
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RestoreConfig

/**
 * Drop-in Compose backup screen.
 *
 * Usage:
 * ```kotlin
 * RoomGuardBackupScreen(
 *     driveManager = myDriveManager,
 *     localManager = myLocalManager,
 *     tokenStore = myTokenStore,
 *     restoreConfig = RestoreConfig(tables = listOf("notes"), mode = RestoreMode.ATTACH)
 * )
 * ```
 *
 * The screen handles:
 * - Drive auth intent launching
 * - File share via ACTION_SEND
 * - File save via CREATE_DOCUMENT
 * - File import via OPEN_DOCUMENT
 * - Confirmation dialogs (delegated back via events to AlertDialog composables)
 * - Loading bar overlay
 */
@Composable
fun RoomGuardBackupScreen(
    driveManager: DriveBackupManager,
    localManager: LocalBackupManager,
    tokenStore: DriveTokenStore,
    restoreConfig: RestoreConfig,
    modifier: Modifier = Modifier
) {
    val factory = remember(driveManager, localManager, tokenStore, restoreConfig) {
        RoomGuardBackupViewModel.Factory(driveManager, localManager, tokenStore, restoreConfig)
    }
    val viewModel: RoomGuardBackupViewModel = viewModel(factory = factory)
    RoomGuardBackupScreenContent(viewModel = viewModel, modifier = modifier)
}

// Internal composable that wires events → launchers
@Composable
internal fun RoomGuardBackupScreenContent(
    viewModel: RoomGuardBackupViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingSaveContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dialogEvent by remember { mutableStateOf<BackupUiEvent?>(null) }

    // Drive Auth launcher
    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Extract token from result and call viewModel.onAction(BackupScreenAction.AuthResult(...))
        // (Implementation detail — uses Identity.getAuthorizationClient)
    }

    // Save CSV to device launcher
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { dest ->
            pendingSaveContent?.let { (_, path) ->
                context.contentResolver.openOutputStream(dest)?.use { out ->
                    java.io.File(path).inputStream().copyTo(out)
                }
            }
            pendingSaveContent = null
        }
    }

    // Import CSV from device launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onAction(BackupScreenAction.ImportCsv(it.toString())) }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.LaunchDriveAuth -> {
                    // launch driveAuthLauncher with event.pendingIntent
                }
                is BackupUiEvent.ShareFile -> {
                    val file = java.io.File(event.filePath)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Data"))
                }
                is BackupUiEvent.SaveFileToDevice -> {
                    pendingSaveContent = event.fileName to event.filePath
                    saveLauncher.launch(event.fileName)
                }
                is BackupUiEvent.ConfirmOverwriteRemote,
                is BackupUiEvent.ConfirmOverwriteLocal,
                is BackupUiEvent.ConfirmRevoke,
                is BackupUiEvent.AskRestoreOnFirstConnect -> {
                    dialogEvent = event
                }
                is BackupUiEvent.ShowMessage -> {
                    // Host can customize; default: Snackbar or Toast
                }
            }
        }
    }

    // Dialog rendering
    dialogEvent?.let { event ->
        // AlertDialog composable based on event type
        // Each dialog calls onConfirm/onRestore/onKeepLocal then sets dialogEvent = null
    }

    // Main content
    BackupScreenLayout(
        uiState = uiState,
        onAction = viewModel::onAction,
        onImportClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) },
        modifier = modifier
    )
}
```

---

## 9. Sample App (`app/`)

### 9.1 Entity & DAO
```kotlin
// NoteEntity.kt
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 9.2 `NoteDatabaseProvider.kt`
```kotlin
class NoteDatabaseProvider(
    private val context: Context,
    private val database: NoteDatabase
) : DatabaseProvider {
    override fun getDatabaseFilePath() = context.getDatabasePath("notes.db").absolutePath
    override fun getDatabaseName() = "notes.db"
    override suspend fun checkpoint() {
        database.noteDao().checkpoint(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
    }
    override suspend fun closeDatabase() = database.close()
    override suspend fun onRestoreComplete() { /* re-init DB singleton here if needed */ }
}
```

### 9.3 `NoteCsvSerializer.kt`
```kotlin
class NoteCsvSerializer(private val dao: NoteDao) : CsvSerializer {

    override suspend fun toCsv(): String {
        val notes = dao.getAllNotes().first()
        val sb = StringBuilder()
        sb.appendLine("[NOTES]")
        sb.appendLine("id,title,body,createdAt")
        notes.forEach { sb.appendLine("${it.id},\"${it.title}\",\"${it.body}\",${it.createdAt}") }
        return sb.toString()
    }

    override suspend fun fromCsv(content: String): ImportSummary {
        val existingIds = dao.getAllNotes().first().map { it.createdAt }.toSet()
        var imported = 0; var skipped = 0
        var inSection = false
        content.lines().forEach { line ->
            when {
                line.trim() == "[NOTES]" -> { inSection = true; return@forEach }
                line.startsWith("id,") -> return@forEach   // header
                inSection && line.isNotBlank() -> {
                    val tokens = line.split(",")
                    val createdAt = tokens.getOrNull(3)?.toLongOrNull() ?: return@forEach
                    if (createdAt in existingIds) { skipped++; return@forEach }
                    dao.insert(NoteEntity(
                        title = tokens.getOrNull(1)?.trim('"') ?: "",
                        body = tokens.getOrNull(2)?.trim('"') ?: "",
                        createdAt = createdAt
                    ))
                    imported++
                }
            }
        }
        return ImportSummary(imported, skipped, "Imported $imported notes, skipped $skipped.")
    }
}
```

### 9.4 `MainActivity.kt` wiring
```kotlin
// Manual wiring (no Hilt in sample)
val tokenStore = DataStoreDriveTokenStore(this)
val dbProvider = NoteDatabaseProvider(this, NoteDatabase.getInstance(this))
val driveManager = RoomGuardDrive(this, getString(R.string.app_name), dbProvider, tokenStore)
val localManager = RoomGuardLocal(this, NoteCsvSerializer(db.noteDao()), filePrefix = "notes_backup")
val restoreConfig = RestoreConfig(tables = listOf("notes"), mode = RestoreMode.ATTACH)

setContent {
    RoomGuardBackupScreen(
        driveManager = driveManager,
        localManager = localManager,
        tokenStore = tokenStore,
        restoreConfig = restoreConfig
    )
}
```

---

## 10. Build & Gradle Setup

### Root `settings.gradle.kts`
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "RoomGuard"
include(":roomguard-core", ":roomguard-drive", ":roomguard-local", ":roomguard-hilt", ":roomguard-ui", ":app")
```

### Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
}
```

---

## 11. GitHub Packages Publishing

### `.github/workflows/publish.yml`
```yaml
name: Publish to GitHub Packages

on:
  push:
    tags:
      - 'v*'          # Trigger on version tags: v1.0.0, v1.1.0, etc.
  workflow_dispatch:  # Allow manual trigger

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Publish all modules
        run: |
          ./gradlew \
            :roomguard-core:publishReleasePublicationToGitHubPackagesRepository \
            :roomguard-drive:publishReleasePublicationToGitHubPackagesRepository \
            :roomguard-local:publishReleasePublicationToGitHubPackagesRepository \
            :roomguard-hilt:publishReleasePublicationToGitHubPackagesRepository \
            :roomguard-ui:publishReleasePublicationToGitHubPackagesRepository
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Publishing block (each library `build.gradle.kts`)
```kotlin
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Dhanfinix/RoomGuard")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

---

## 12. Integration Guide (Consumer README)

### Step 1: Add repository
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        // ...
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Dhanfinix/RoomGuard")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("USER_ID")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("ACCESS_TOKEN")
            }
        }
    }
}
```

### Step 2: Add dependencies
```kotlin
// build.gradle.kts (:app)
dependencies {
    implementation("dev.dhanfinix.roomguard:roomguard-core:1.0.0")
    implementation("dev.dhanfinix.roomguard:roomguard-drive:1.0.0")   // Drive backup
    implementation("dev.dhanfinix.roomguard:roomguard-local:1.0.0")   // CSV backup
    implementation("dev.dhanfinix.roomguard:roomguard-ui:1.0.0")      // Pre-built Compose screen
    implementation("dev.dhanfinix.roomguard:roomguard-hilt:1.0.0")    // Optional Hilt wiring
}
```

### Step 3: Implement contracts
```kotlin
class MyDatabaseProvider(context: Context, db: AppDatabase) : DatabaseProvider { ... }
class MyCsvSerializer(dao: MyDao) : CsvSerializer { ... }
```

### Step 4: Wire up
```kotlin
// Manual (no Hilt)
val tokenStore     = DataStoreDriveTokenStore(context)
val driveManager   = RoomGuardDrive(context, "MyApp", MyDatabaseProvider(context, db), tokenStore)
val localManager   = RoomGuardLocal(context, MyCsvSerializer(dao), filePrefix = "myapp_backup")
val restoreConfig  = RestoreConfig(tables = listOf("my_table"), mode = RestoreMode.ATTACH)
```

### Step 5: Use the screen
```kotlin
RoomGuardBackupScreen(
    driveManager  = driveManager,
    localManager  = localManager,
    tokenStore    = tokenStore,
    restoreConfig = restoreConfig
)
```

---

## 13. Dependency Versions Locked

```toml
[versions]
agp                   = "8.13.0"
kotlin                = "2.3.0"
kotlinx-coroutines    = "1.9.0"
datastore             = "1.2.0"
compose-bom           = "2026.01.00"
lifecycle-viewmodel   = "2.10.0"
activity-compose      = "1.12.2"
hilt                  = "2.58"
google-api-drive      = "v3-rev197-1.25.0"
google-play-auth      = "21.5.0"
google-auth           = "1.42.1"
credentials           = "1.5.0"
coroutines-play       = "1.9.0"
```

---

## 14. Error Codes Reference

| Code | Module | Meaning | Recovery |
|---|---|---|---|
| `AUTH_EXPIRED` | drive | OAuth token revoked/expired | Re-request Drive auth |
| `NOT_AUTHORIZED` | drive | Drive API returned 401 | Connect Drive |
| `BACKUP_FAILED` | drive | Upload to Drive failed | Check network, retry |
| `RESTORE_FAILED` | drive | Restore process threw | Check Drive file, retry |
| `NO_BACKUP_FOUND` | drive | No file in appDataFolder | Create a backup first |
| `DB_NOT_FOUND` | drive | SQLite file missing | Check DB path |
| `INTEGRITY_FAILED` | drive | PRAGMA quick_check ≠ "ok" | File corrupted; re-backup |
| `EXPORT_FAILED` | local | CSV write failed | Check storage, catch serializer errors |
| `IMPORT_FAILED` | local | CSV read/parse failed | Validate file format |
