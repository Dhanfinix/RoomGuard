package dev.dhanfinix.roomguard.drive.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore by preferencesDataStore(name = "roomguard_token_store")

/**
 * Default [DriveTokenStore] implementation backed by Jetpack DataStore (Preferences).
 */
class DataStoreDriveTokenStore(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.tokenDataStore
) : DriveTokenStore {

    private val KEY_TOKEN = stringPreferencesKey("drive_access_token")
    private val KEY_AUTHORIZED = booleanPreferencesKey("is_drive_authorized")

    override fun getTokenFlow(): Flow<String?> =
        dataStore.data.map { it[KEY_TOKEN] }

    override suspend fun saveToken(token: String?) {
        dataStore.edit { prefs ->
            if (token != null) prefs[KEY_TOKEN] = token
            else prefs.remove(KEY_TOKEN)
        }
    }

    override suspend fun clearToken() {
        dataStore.edit { it.remove(KEY_TOKEN) }
    }

    override suspend fun getToken(): String? =
        dataStore.data.first()[KEY_TOKEN]

    override suspend fun setAuthorized(authorized: Boolean) {
        dataStore.edit { it[KEY_AUTHORIZED] = authorized }
    }

    override suspend fun isAuthorized(): Boolean =
        dataStore.data.first()[KEY_AUTHORIZED] ?: false
}
