package dev.dhanfinix.roomguard.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dhanfinix.roomguard.core.SyncStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * Premium status header displaying Drive connection state,
 * sync status with animated indicator, and last backup timestamp.
 */
@Composable
fun BackupStatusHeader(
    isDriveAuthorized: Boolean,
    syncStatus: SyncStatus,
    lastBackupDate: Long?,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    statusMessage: String? = null,
    userEmail: String? = null
) {
    val containerColor = if (isDriveAuthorized) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isDriveAuthorized) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status icon with background
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDriveAuthorized)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val checking = !isDriveAuthorized && syncStatus == SyncStatus.Checking
                        Icon(
                            imageVector = when {
                                isDriveAuthorized -> Icons.Outlined.Cloud
                                checking -> Icons.Outlined.Sync
                                else -> Icons.Outlined.CloudOff
                            },
                            contentDescription = when {
                                isDriveAuthorized -> "Connected"
                                checking -> "Checking"
                                else -> "Not Connected"
                            },
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Google Drive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                        Text(
                            text = when {
                                userEmail != null -> userEmail
                                isDriveAuthorized -> "Connected"
                                syncStatus == SyncStatus.Checking -> "Checking connection..."
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isDriveAuthorized) {
                    SyncStatusIndicator(
                        syncStatus = syncStatus,
                        tint = contentColor,
                        isProcessing = isProcessing,
                        statusMessage = statusMessage
                    )
                }
            }

            // Last backup info ... (rest remains same)
            if (isDriveAuthorized && lastBackupDate != null) {
                HorizontalDivider(
                    color = contentColor.copy(alpha = 0.12f),
                    thickness = 1.dp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatBackupDate(lastBackupDate),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusIndicator(
    syncStatus: SyncStatus,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    statusMessage: String? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation"
    )

    // Icons and descriptive text
    val (icon, description) = when {
        isProcessing -> Icons.Outlined.Sync to (statusMessage ?: "Processing...")
        syncStatus == SyncStatus.Checking -> Icons.Outlined.Sync to "Checking..."
        syncStatus == SyncStatus.Synced -> Icons.Outlined.CloudSync to "Up to date"
        syncStatus == SyncStatus.LocalNewer -> Icons.Outlined.CloudSync to "Changes pending"
        syncStatus == SyncStatus.RemoteNewer -> Icons.Outlined.CloudSync to "Update available"
        else -> Icons.Outlined.Sync to ""
    }

    val shouldAnimate = isProcessing || syncStatus == SyncStatus.Checking

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint.copy(alpha = 0.7f)
        )
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint.copy(alpha = 0.7f),
            modifier = Modifier
                .size(18.dp)
                .then(
                    if (shouldAnimate) Modifier.rotate(rotation)
                    else Modifier
                )
        )
    }
}

private fun formatBackupDate(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis

    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        diff < 172_800_000L -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            sdf.format(Date(epochMillis))
        }
    }
}
