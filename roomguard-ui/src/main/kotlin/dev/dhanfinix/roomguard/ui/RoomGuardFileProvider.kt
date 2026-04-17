package dev.dhanfinix.roomguard.ui

import androidx.core.content.FileProvider

/**
 * Dedicated [FileProvider] for RoomGuard library to avoid namespace collisions
 * and simplify the "drop-in" experience for developers.
 */
class RoomGuardFileProvider : FileProvider()
