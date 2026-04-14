package dev.dhanfinix.roomguard.core

/**
 * Library-wide configuration for RoomGuard.
 *
 * @param useCompression If true, backups and exports will be GZIP compressed.
 *                       Defaults to true as it significantly reduces data usage.
 */
data class RoomGuardConfig(
    val useCompression: Boolean = true
)
