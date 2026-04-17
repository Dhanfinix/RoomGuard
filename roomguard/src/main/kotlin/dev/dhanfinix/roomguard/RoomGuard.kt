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
 * Shared initialization facade for RoomGuard.
 *
 * Build once, then reuse the created managers throughout the host app.
 */
class RoomGuard(
    private val _driveManager: RoomGuardDrive?,
    private val _localManager: RoomGuardLocal?,
    private val _tokenStore: DriveTokenStore?,
    private val _restoreConfig: RestoreConfig
) {
    fun driveManager() = _driveManager
    fun localManager() = _localManager
    fun tokenStore() = _tokenStore
    fun restoreConfig() = _restoreConfig

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

        fun appName(value: String) = apply {
            appName = value.normalizedOrThrow("appName")
        }

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

        fun tokenStore(value: DriveTokenStore) = apply {
            tokenStore = value
        }

        fun csvSerializer(value: CsvSerializer) = apply {
            csvSerializer = value
        }

        fun localFilePrefix(value: String) = apply {
            localFilePrefix = value.normalizedOrThrow("localFilePrefix")
        }

        fun config(value: RoomGuardConfig) = apply {
            config = value
        }

        fun restoreConfig(value: RestoreConfig) = apply {
            restoreConfig = value
        }

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
