package dev.dhanfinix.roomguard

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.SignInClient
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.DatabaseProvider
import dev.dhanfinix.roomguard.core.RoomGuardConfig
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.local.RoomGuardLocal

/**
 * Shared initialization facade for RoomGuard.
 *
 * Build once, then reuse the created managers throughout the host app.
 */
class RoomGuard internal constructor(
    val driveManager: RoomGuardDrive,
    val localManager: RoomGuardLocal,
    val tokenStore: DriveTokenStore
) {
    class Builder(private val context: Context) {
        private var appName: String? = null
        private var databaseProvider: DatabaseProvider? = null
        private var tokenStore: DriveTokenStore? = null
        private var csvSerializer: CsvSerializer? = null
        private var localFilePrefix: String? = null
        private var config: RoomGuardConfig = RoomGuardConfig()
        private var authClient: AuthorizationClient? = null
        private var signInClient: SignInClient? = null

        fun appName(value: String) = apply {
            appName = value.normalizedOrThrow("appName")
        }

        fun databaseProvider(value: DatabaseProvider) = apply {
            databaseProvider = value
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

        fun driveClients(
            authClient: AuthorizationClient,
            signInClient: SignInClient
        ) = apply {
            this.authClient = authClient
            this.signInClient = signInClient
        }

        fun build(): RoomGuard {
            val resolvedAppName = requireValue(appName, "appName")
            val resolvedDatabaseProvider = requireValue(databaseProvider, "databaseProvider")
            val resolvedTokenStore = requireValue(tokenStore, "tokenStore")
            val resolvedCsvSerializer = requireValue(csvSerializer, "csvSerializer")
            val resolvedLocalFilePrefix = localFilePrefix ?: "${resolvedAppName}_backup"

            val hasCustomDriveClients = authClient != null || signInClient != null
            require(!hasCustomDriveClients || (authClient != null && signInClient != null)) {
                "driveClients() must provide both authClient and signInClient"
            }

            val driveManager = if (hasCustomDriveClients) {
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

            val localManager = RoomGuardLocal(
                context = context,
                serializer = resolvedCsvSerializer,
                filePrefix = resolvedLocalFilePrefix,
                config = config
            )

            return RoomGuard(
                driveManager = driveManager,
                localManager = localManager,
                tokenStore = resolvedTokenStore
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
