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
    private val KEY_AUTHORIZED = androidx.datastore.preferences.core.booleanPreferencesKey("is_drive_authorized")

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

    override suspend fun setAuthorized(authorized: Boolean) {
        context.tokenDataStore.edit { it[KEY_AUTHORIZED] = authorized }
    }

    override suspend fun isAuthorized(): Boolean =
        context.tokenDataStore.data.first()[KEY_AUTHORIZED] ?: false
}
