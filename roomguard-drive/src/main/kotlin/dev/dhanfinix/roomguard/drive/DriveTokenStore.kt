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

    /** Persist whether the user manually authorized Google Drive connection */
    suspend fun setAuthorized(authorized: Boolean)

    /** One-shot check for manual authorization status */
    suspend fun isAuthorized(): Boolean
}
