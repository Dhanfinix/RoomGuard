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
    RemoteNewer,
    NotAuthorized
}
