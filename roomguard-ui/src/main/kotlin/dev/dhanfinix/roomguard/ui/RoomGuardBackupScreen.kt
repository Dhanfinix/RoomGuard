package dev.dhanfinix.roomguard.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dhanfinix.roomguard.core.LocalBackupFormat
import dev.dhanfinix.roomguard.core.LocalBackupManager
import dev.dhanfinix.roomguard.core.RestoreConfig
import dev.dhanfinix.roomguard.core.SyncStatus
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.ui.component.BackupStatusHeader
import dev.dhanfinix.roomguard.ui.component.CloudActionsGroup
import dev.dhanfinix.roomguard.ui.component.LocalDataGroup
import java.io.File

/**
 * Drop-in Compose backup screen.
 *
 * Usage:
 * ```kotlin
 * val roomGuard = RoomGuard.Builder(context)
 *     .appName("AppName")
 *     .databaseProvider(myDatabaseProvider)
 *     .tokenStore(myTokenStore)
 *     .csvSerializer(myCsvSerializer)
 *     .build()
 *
 * RoomGuardBackupScreen(
 *     driveManager = roomGuard.driveManager,
 *     localManager = roomGuard.localManager,
 *     tokenStore = roomGuard.tokenStore,
 *     restoreConfig = RestoreConfig(tables = listOf("your_table"), mode = RestoreMode.ATTACH)
 * )
 * ```
 *
 * The screen handles:
 * - Drive auth intent launching
 * - File share via ACTION_SEND
 * - File save via CREATE_DOCUMENT
 * - File import via OPEN_DOCUMENT
 * - Confirmation dialogs
 * - Loading overlay
 */
@Composable
fun RoomGuardBackupScreen(
    driveManager: RoomGuardDrive?,
    localManager: LocalBackupManager?,
    tokenStore: DriveTokenStore?,
    restoreConfig: RestoreConfig,
    modifier: Modifier = Modifier
) {
    val factory = remember(driveManager, localManager, tokenStore, restoreConfig) {
        RoomGuardBackupViewModel.Factory(driveManager, localManager, tokenStore, restoreConfig)
    }
    val viewModel: RoomGuardBackupViewModel = viewModel(factory = factory)
    RoomGuardBackupScreenContent(viewModel = viewModel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomGuardBackupScreenContent(
    viewModel: RoomGuardBackupViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isCheckingConnection = uiState.syncStatus == SyncStatus.Checking
    val pullRefreshState = rememberPullToRefreshState()
    var pendingSaveContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dialogEvent by remember { mutableStateOf<BackupUiEvent?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Drive Auth launcher
    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onAction(BackupScreenAction.AuthResult(result.resultCode, result.data))
    }

    // Save backup file to device launcher
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { dest ->
            pendingSaveContent?.let { (_, path) ->
                viewModel.onAction(BackupScreenAction.SaveToUri(dest.toString(), path, context.contentResolver))
            }
            pendingSaveContent = null
        }
    }

    // Import backup file from device launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onAction(BackupScreenAction.ImportLocal(it.toString())) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.LaunchDriveAuth -> {
                    try {
                        val request = IntentSenderRequest.Builder(event.pendingIntent.intentSender).build()
                        driveAuthLauncher.launch(request)
                    } catch (e: IntentSender.SendIntentException) {
                        viewModel.onAction(BackupScreenAction.AuthError(e.message ?: e.toString()))
                    }
                }
                is BackupUiEvent.ShareFile -> {
                    RoomGuardActionHelper.shareFile(context, event.filePath, event.mimeType)
                }
                is BackupUiEvent.SaveFileToDevice -> {
                    pendingSaveContent = event.fileName to event.filePath
                    saveLauncher.launch(event.fileName)
                }
                is BackupUiEvent.ConfirmOverwriteRemote,
                is BackupUiEvent.ConfirmOverwriteLocal,
                is BackupUiEvent.ConfirmRevoke,
                is BackupUiEvent.ChooseRestoreStrategy,
                is BackupUiEvent.AskRestoreOnFirstConnect -> {
                    dialogEvent = event
                }
                is BackupUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Dialog rendering
    dialogEvent?.let { event ->
        when (event) {
            is BackupUiEvent.ConfirmOverwriteRemote -> {
                AlertDialog(
                    onDismissRequest = { dialogEvent = null },
                    title = { Text("Overwrite Cloud Backup?") },
                    text = { Text("The cloud backup is newer than your local data. Proceeding will overwrite it with your local data.") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialogEvent = null
                            event.onConfirm()
                        }) { Text("Overwrite") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogEvent = null }) { Text("Cancel") }
                    }
                )
            }
            is BackupUiEvent.ConfirmOverwriteLocal -> {
                AlertDialog(
                    onDismissRequest = { dialogEvent = null },
                    title = { Text("Overwrite Local Data?") },
                    text = { Text("Your local data is newer than the cloud backup. Restoring will overwrite your local changes.") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialogEvent = null
                            event.onConfirm()
                        }) { Text("Restore") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogEvent = null }) { Text("Cancel") }
                    }
                )
            }
            is BackupUiEvent.ChooseRestoreStrategy -> {
                AlertDialog(
                    onDismissRequest = { dialogEvent = null },
                    title = { Text("Restore Options") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Choose how you want to restore your data from the backup.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Option: Replace Everything
                            Surface(
                                onClick = {
                                    dialogEvent = null
                                    event.onStrategySelected(dev.dhanfinix.roomguard.core.RestoreStrategy.OVERWRITE)
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Replace Everything",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Clears all local data first. Use this for a clean state.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // Option: Insert Missing Data
                            Surface(
                                onClick = {
                                    dialogEvent = null
                                    event.onStrategySelected(dev.dhanfinix.roomguard.core.RestoreStrategy.MERGE)
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Insert Missing Data",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Merges data. Local records remain safe; only missing records are added.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { dialogEvent = null }) { Text("Cancel") }
                    }
                )
            }
            is BackupUiEvent.ConfirmRevoke -> {
                AlertDialog(
                    onDismissRequest = { dialogEvent = null },
                    title = { Text("Disconnect Google Drive?") },
                    text = { Text("This will revoke RoomGuard's access to your Google Drive. Your backup data on Drive will NOT be deleted.") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialogEvent = null
                            event.onConfirm()
                        }) { Text("Disconnect") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogEvent = null }) { Text("Cancel") }
                    }
                )
            }
            is BackupUiEvent.AskRestoreOnFirstConnect -> {
                AlertDialog(
                    onDismissRequest = { dialogEvent = null },
                    title = { Text("Existing Backup Found") },
                    text = { Text("A backup was found in your Google Drive. Would you like to restore it now?") },
                    confirmButton = {
                        TextButton(onClick = {
                            dialogEvent = null
                            event.onRestore()
                        }) { Text("Restore Now") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            dialogEvent = null
                            event.onKeepLocal()
                        }) { Text("Keep Local") }
                    }
                )
            }
            else -> { /* non-dialog events handled above */ }
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isCloudProcessing,
        onRefresh = { viewModel.onAction(BackupScreenAction.Refresh) },
        state = pullRefreshState,
        modifier = modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BackupStatusHeader(
                    isDriveAuthorized = uiState.isDriveAuthorized,
                    syncStatus = uiState.syncStatus,
                lastBackupDate = uiState.lastBackupDate,
                isProcessing = uiState.isCloudProcessing,
                statusMessage = uiState.loadingMessage,
                userEmail = uiState.userEmail
            )

                CloudActionsGroup(
                    isDriveAuthorized = uiState.isDriveAuthorized,
                    isCheckingConnection = isCheckingConnection,
                    isProcessing = uiState.isCloudProcessing,
                    onConnect = { viewModel.onAction(BackupScreenAction.ConnectDrive) },
                    onBackup = { viewModel.onAction(BackupScreenAction.Backup) },
                    onRestore = { viewModel.onAction(BackupScreenAction.Restore()) },
                    onRevoke = { viewModel.onAction(BackupScreenAction.RevokeAccess) }
                )

                LocalDataGroup(
                    isProcessing = uiState.isLocalProcessing,
                    selectedFormat = uiState.localBackupFormat,
                    onFormatChange = { viewModel.onAction(BackupScreenAction.SetLocalFormat(it)) },
                    onShareLocal = { viewModel.onAction(BackupScreenAction.ExportLocal) },
                    onSaveLocal = { viewModel.onAction(BackupScreenAction.SaveLocalToDevice) },
                    onImportLocal = {
                        val mimeTypes = LocalBackupFormat.values().map { it.mimeType }.toTypedArray()
                        importLauncher.launch(mimeTypes)
                    }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
