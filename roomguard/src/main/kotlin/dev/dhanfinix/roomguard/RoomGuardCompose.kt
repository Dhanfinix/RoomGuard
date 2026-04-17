package dev.dhanfinix.roomguard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dhanfinix.roomguard.ui.RoomGuardBackupScreen

/**
 * Simplified entry point for the RoomGuard backup screen.
 * 
 * Automatically extracts the necessary managers and configuration from the [RoomGuard] instance.
 * 
 * @param roomGuard The initialized RoomGuard facade.
 * @param modifier  Optional modifier for the screen layout.
 */
@Composable
fun RoomGuardBackupScreen(
    roomGuard: RoomGuard,
    modifier: Modifier = Modifier
) {
    RoomGuardBackupScreen(
        driveManager = roomGuard.driveManager(),
        localManager = roomGuard.localManager(),
        tokenStore = roomGuard.tokenStore(),
        restoreConfig = roomGuard.restoreConfig(),
        modifier = modifier
    )
}
