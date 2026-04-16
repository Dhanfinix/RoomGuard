package dev.dhanfinix.roomguard.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dhanfinix.roomguard.core.LocalBackupFormat

/**
 * Local backup section with a single format switch.
 */
@Composable
fun LocalDataGroup(
    isProcessing: Boolean,
    selectedFormat: LocalBackupFormat,
    onFormatChange: (LocalBackupFormat) -> Unit,
    onShareLocal: () -> Unit,
    onSaveLocal: () -> Unit,
    onImportLocal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCompressed = selectedFormat == LocalBackupFormat.COMPRESSED

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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Local Backup",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose between a human-readable CSV or a compressed backup. Import accepts both .csv and .csv.gz files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder(!isProcessing)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = if (!isProcessing) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCompressed) "Compressed backup" else "Human-readable CSV",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isProcessing) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Text(
                                text = if (isCompressed) {
                                    "Smaller file size for archiving and transport."
                                } else {
                                    "Easy to inspect, edit, and share."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!isProcessing) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Switch(
                                checked = isCompressed,
                                onCheckedChange = { checked ->
                                    onFormatChange(
                                        if (checked) LocalBackupFormat.COMPRESSED else LocalBackupFormat.CSV
                                    )
                                },
                                enabled = !isProcessing
                            )
                            Text(
                                text = if (isCompressed) "Compressed" else "CSV",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onShareLocal,
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share ${if (isCompressed) "Compressed" else "CSV"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        OutlinedButton(
                            onClick = onSaveLocal,
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storage,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save to Device",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "Save as human-readable CSV when the switch is off. Turn it on for a compressed local backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedCard(
                onClick = onImportLocal,
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder(!isProcessing)
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
                            imageVector = Icons.Outlined.Publish,
                            contentDescription = null,
                            tint = if (!isProcessing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Import from Device",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isProcessing) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Text(
                            text = "Accepts both .csv and .csv.gz backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isProcessing) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LocalDataGroupPreview() {
    MaterialTheme {
        Box(Modifier.padding(16.dp)) {
            LocalDataGroup(
                isProcessing = false,
                selectedFormat = LocalBackupFormat.COMPRESSED,
                onFormatChange = {},
                onShareLocal = {},
                onSaveLocal = {},
                onImportLocal = {}
            )
        }
    }
}
