package dev.dhanfinix.roomguard

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.SignInClient
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.DatabaseProvider
import dev.dhanfinix.roomguard.core.RestoreConfig
import dev.dhanfinix.roomguard.core.RoomGuardConfig
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.drive.token.DataStoreDriveTokenStore
import dev.dhanfinix.roomguard.local.AutomaticRoomCsvSerializer
import dev.dhanfinix.roomguard.local.RoomDatabaseProvider
import dev.dhanfinix.roomguard.local.RoomGuardLocal
import androidx.room.RoomDatabase

/**
 * The main architectural facade and synchronization coordinator for the RoomGuard library.
 *
 * `RoomGuard` is designed as a single, immutable instance that holds all the necessary
 * managers and configuration required to protect your application's database.
 *
 * ### Architectural Overview
 * The library follows a "Facade" pattern to hide the complexity of:
 * - Google Drive OAuth2 & API orchestration.
 * - Local filesystem CSV serialization and compression.
 * - Room/SQLite WAL checkpointing and transaction management.
 *
 * ### Lifecycle Note
 * It is recommended to initialize a single `RoomGuard` instance (e.g., via Dependency Injection
 * or as a singleton in your Application class) and reuse it throughout your app's lifecycle.
 */
class RoomGuard(
    private val _driveManager: RoomGuardDrive?,
    private val _localManager: RoomGuardLocal?,
    private val _tokenStore: DriveTokenStore?,
    private val _restoreConfig: RestoreConfig
) {
    /** The manager responsible for Google Drive operations. Null if Drive features are not configured. */
    fun driveManager() = _driveManager

    /** The manager responsible for local file export/import. Null if [CsvSerializer] is not provided. */
    fun localManager() = _localManager

    /** The storage used to persist OAuth2 tokens. Managed by the [driveManager]. */
    fun tokenStore() = _tokenStore

    /** The configuration (tables, mode, strategy) used for restoration operations. */
    fun restoreConfig() = _restoreConfig

    /**
     * Fluent Builder for creating [RoomGuard] instances with sensible defaults.
     *
     * Example (Minimum Zero-Config Setup):
     * ```kotlin
     * val roomGuard = RoomGuard.Builder(context)
     *     .database(db, "notes.db", listOf("notes"))
     *     .build()
     * ```
     */
    class Builder(private val context: Context) {
        private var appName: String? = null
        private var databaseProvider: DatabaseProvider? = null
        private var tokenStore: DriveTokenStore? = null
        private var csvSerializer: CsvSerializer? = null
        private var localFilePrefix: String? = null
        private var config: RoomGuardConfig = RoomGuardConfig()
        private var restoreConfig: RestoreConfig? = null
        private var authClient: AuthorizationClient? = null
        private var signInClient: SignInClient? = null

        /**
         * Sets the app name used for creating Google Drive backups.
         * Default: Uses the name from your AndroidManifest.
         */
        fun appName(value: String) = apply {
            appName = value.normalizedOrThrow("appName")
        }

        /**
         * Connects a custom [DatabaseProvider] for non-Room databases.
         * Note: If you use Room, use the [database] helper instead.
         */
        fun databaseProvider(value: DatabaseProvider) = apply {
            databaseProvider = value
        }

        /**
         * Simplified Room database setup.
         * Automatically sets up [DatabaseProvider] and [CsvSerializer] for the given [RoomDatabase].
         *
         * @param db The Room database instance.
         * @param dbFileName The database filename on disk (e.g., "my_database.db").
         * @param tables List of table names to include in the backup/restore process.
         * @param allowCsv If true, also sets up automatic CSV export/import support.
         */
        fun database(
            db: RoomDatabase,
            dbFileName: String,
            tables: List<String>,
            allowCsv: Boolean = true
        ) = apply {
            databaseProvider = RoomDatabaseProvider(context, db, dbFileName)
            if (allowCsv) {
                csvSerializer = AutomaticRoomCsvSerializer(db)
            }
            if (restoreConfig == null) {
                restoreConfig = RestoreConfig(tables = tables)
            }
        }

        /**
         * Connects a custom [DriveTokenStore] to manage OAuth tokens.
         * Default: Uses a DataStore-backed storage (`DataStoreDriveTokenStore`).
         */
        fun tokenStore(value: DriveTokenStore) = apply {
            tokenStore = value
        }

        /**
         * Connects a custom [CsvSerializer] for manual data portability logic.
         * Note: If you use Room, use the [database] helper instead.
         */
        fun csvSerializer(value: CsvSerializer) = apply {
            csvSerializer = value
        }

        /**
         * Customize the prefix used for local CSV file exports.
         * Default: "{appName}_backup"
         */
        fun localFilePrefix(value: String) = apply {
            localFilePrefix = value.normalizedOrThrow("localFilePrefix")
        }

        /** Sets library-wide configuration settings. */
        fun config(value: RoomGuardConfig) = apply {
            config = value
        }

        /** Sets the strategy and metadata for the restoration process. */
        fun restoreConfig(value: RestoreConfig) = apply {
            restoreConfig = value
        }

        /** Provide custom Google Identity clients (useful for testing or advanced auth flows). */
        fun driveClients(
            authClient: AuthorizationClient,
            signInClient: SignInClient
        ) = apply {
            this.authClient = authClient
            this.signInClient = signInClient
        }

        fun build(): RoomGuard {
            // Smart defaults
            val resolvedAppName = appName ?: context.applicationInfo.loadLabel(context.packageManager).toString()
            val resolvedTokenStore = tokenStore ?: DataStoreDriveTokenStore(context)
            
            val resolvedDatabaseProvider = requireValue(databaseProvider, "databaseProvider")
            val resolvedLocalFilePrefix = localFilePrefix ?: (resolvedAppName.replace(" ", "_").lowercase() + "_backup")

            val hasCustomDriveClients = authClient != null || signInClient != null
            require(!hasCustomDriveClients || (authClient != null && signInClient != null)) {
                "driveClients() must provide both authClient and signInClient"
            }

            // Only build DriveManager if tokenStore is provided
            val driveManager = run {
                if (hasCustomDriveClients) {
                    RoomGuardDrive(
                        context = context,
                        appName = resolvedAppName,
                        databaseProvider = resolvedDatabaseProvider,
                        tokenStore = resolvedTokenStore,
                        config = config,
                        authClient = requireNotNull(authClient),
                        signInClient = requireNotNull(signInClient)
                    )
                } else {
                    RoomGuardDrive(
                        context = context,
                        appName = resolvedAppName,
                        databaseProvider = resolvedDatabaseProvider,
                        tokenStore = resolvedTokenStore,
                        config = config
                    )
                }
            }

            // Only build LocalManager if csvSerializer is provided
            val localManager = csvSerializer?.let { resolvedSerializer ->
                RoomGuardLocal(
                    context = context,
                    serializer = resolvedSerializer,
                    filePrefix = resolvedLocalFilePrefix,
                    config = config
                )
            }

            return RoomGuard(
                _driveManager = driveManager,
                _localManager = localManager,
                _tokenStore = resolvedTokenStore,
                _restoreConfig = restoreConfig ?: RestoreConfig()
            )
        }

        private fun <T> requireValue(value: T?, name: String): T =
            requireNotNull(value) { "$name must be provided" }

        private fun String.normalizedOrThrow(name: String): String {
            val normalized = trim()
            require(normalized.isNotEmpty()) { "$name must not be blank" }
            return normalized
        }
    }
}
