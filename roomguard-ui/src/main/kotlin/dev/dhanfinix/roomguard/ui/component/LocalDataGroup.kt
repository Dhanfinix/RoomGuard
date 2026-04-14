package dev.dhanfinix.roomguard.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Local data management section.
 * Separates "Exporting" and "Importing" for better clarity.
 */
@Composable
fun LocalDataGroup(
    isProcessing: Boolean,
    onExportCsv: () -> Unit,
    onSaveToDevice: () -> Unit,
    onImportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "Local Backup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Manage data stored offline on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Export Section
            DataActionSection(
                title = "EXPORT DATA",
                actions = listOf(
                    DataAction(
                        label = "Share via App",
                        subtitle = "Send CSV to email, drive, or messaging apps",
                        icon = Icons.Outlined.Share,
                        onClick = onExportCsv
                    ),
                    DataAction(
                        label = "Save to Storage",
                        subtitle = "Pick a folder on your device to save the file",
                        icon = Icons.Outlined.Storage,
                        onClick = onSaveToDevice
                    )
                ),
                enabled = !isProcessing
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Import Section
            DataActionSection(
                title = "IMPORT DATA",
                actions = listOf(
                    DataAction(
                        label = "Import from File",
                        subtitle = "Restore data from a previously saved .csv file",
                        icon = Icons.Outlined.Publish,
                        onClick = onImportCsv
                    )
                ),
                enabled = !isProcessing
            )
        }
    }
}

@Composable
private fun DataActionSection(
    title: String,
    actions: List<DataAction>,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        actions.forEach { action ->
            OutlinedCard(
                onClick = action.onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder(enabled)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = action.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

private data class DataAction(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Preview(showBackground = true)
@Composable
private fun LocalDataGroupPreview() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            LocalDataGroup(
                isProcessing = false,
                onExportCsv = {},
                onSaveToDevice = {},
                onImportCsv = {}
            )
        }
    }
}

