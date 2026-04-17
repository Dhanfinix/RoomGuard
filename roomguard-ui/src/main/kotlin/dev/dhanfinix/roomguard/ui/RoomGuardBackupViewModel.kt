package dev.dhanfinix.roomguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.dhanfinix.roomguard.core.*
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for [RoomGuardBackupScreen].
 *
 * Does NOT use Hilt — instantiate manually via [Factory].
 * This keeps roomguard-ui free of Hilt as a transitive dependency.
 */
class RoomGuardBackupViewModel(
    private val driveManager: RoomGuardDrive?,
    private val localManager: LocalBackupManager?,
    private val tokenStore: DriveTokenStore?,
    private val defaultRestoreConfig: RestoreConfig
) : ViewModel() {

    // ── State ──────────────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    // One-shot events (Drive auth intent, file share/save)
    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BackupUiEvent> = _events.asSharedFlow()

    private var remoteModifiedTime: Long? = null

    init {
        refreshStatus()
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    fun onAction(action: BackupScreenAction) {
        when (action) {
            is BackupScreenAction.ConnectDrive     -> requestDriveAuth()
            is BackupScreenAction.RevokeAccess     -> confirmRevokeAccess()
            is BackupScreenAction.Backup           -> onBackupRequested()
            is BackupScreenAction.Refresh          -> refreshStatus()
            is BackupScreenAction.Restore          -> onRestoreRequested(action.strategy)
            is BackupScreenAction.AuthResult       -> handleAuthResult(action.token, action.error)
            is BackupScreenAction.AuthFailed       -> handleAuthResult(null, action.error)
            is BackupScreenAction.SetLocalFormat   -> setLocalFormat(action.format)
            is BackupScreenAction.ExportLocal      -> exportLocal()
            is BackupScreenAction.SaveLocalToDevice -> saveLocalToDevice()
            is BackupScreenAction.ImportLocal -> onImportRequested(action.uri, action.strategy)
        }
    }

    // ── Drive Auth ─────────────────────────────────────────────────────────────

    private fun refreshStatus() {
        if (driveManager == null || tokenStore == null) {
            _uiState.update { it.copy(syncStatus = SyncStatus.Synced) }
            return
        }
        setCloudProcessing(true, "Refreshing status...")
        viewModelScope.launch {
            try {
                // Add a 10-second timeout to prevent the UI from hanging
                val authorized = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    driveManager!!.isDriveAuthorized { token ->
                        tokenStore!!.saveToken(token)
                    }
                } ?: false
                
                _uiState.update { it.copy(isDriveAuthorized = authorized) }
                if (authorized) {
                    fetchBackupInfo()
                } else {
                    _uiState.update {
                        it.copy(
                            syncStatus = SyncStatus.NotAuthorized,
                            lastBackupDate = null,
                            userEmail = null
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isDriveAuthorized = false) }
                handleAuthFailureState("Unable to check Drive status")
            }
            setCloudProcessing(false)
        }
    }

    private fun requestDriveAuth() {
        val manager = driveManager ?: return
        val store = tokenStore ?: return
        setCloudProcessing(true, "Authorizing Drive...")
        viewModelScope.launch {
            try {
                store.setAuthorized(true)
                val result = manager.requestDriveAuthorization()
                if (result.hasResolution()) {
                    _events.emit(BackupUiEvent.LaunchDriveAuth(result.pendingIntent!!))
                } else {
                    val token = result.accessToken
                    if (token != null) {
                        store.saveToken(token)
                        _uiState.update { it.copy(isDriveAuthorized = true) }
                        fetchAndHandleFirstConnect(token)
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                showError("Authorization start failed: $errorMsg")
            }
            setCloudProcessing(false)
        }
    }

    private fun handleAuthResult(token: String?, error: String? = null) {
        val store = tokenStore ?: return
        if (token != null) {
            viewModelScope.launch {
                store.setAuthorized(true)
                store.saveToken(token)
                _uiState.update { it.copy(isDriveAuthorized = true) }
                fetchAndHandleFirstConnect(token)
            }
        } else {
            val msg = error ?: "Drive authorization failed"
            showError(msg)
        }
    }

    // ── Backup ─────────────────────────────────────────────────────────────────

    private fun onBackupRequested() {
        val status = _uiState.value.syncStatus
        if (status == SyncStatus.RemoteNewer) {
            _events.tryEmit(BackupUiEvent.ConfirmOverwriteRemote(
                onConfirm = { backup() }
            ))
        } else {
            backup()
        }
    }

    private fun backup() {
        val manager = driveManager ?: return
        val store = tokenStore ?: return
        setCloudProcessing(true, "Backing up data...")
        viewModelScope.launch {
            val token = store.getToken()
            when (val result = manager.backup(token)) {
                is BackupResult.Success -> {
                    showSuccess("Backup successful")
                    fetchBackupInfo()
                }
                is BackupResult.Error -> {
                    if (result.code == BackupErrorCode.AUTH_EXPIRED || result.code == BackupErrorCode.NOT_AUTHORIZED) {
                        handleAuthFailureState(result.message)
                    } else {
                        showError(result.message)
                    }
                }
            }
            setCloudProcessing(false)
        }
    }

    // ── Restore ────────────────────────────────────────────────────────────────

    private fun onRestoreRequested(strategy: RestoreStrategy? = null) {
        if (strategy != null) {
            restore(strategy)
            return
        }

        if (defaultRestoreConfig.mode == RestoreMode.ATTACH) {
            _events.tryEmit(BackupUiEvent.ChooseRestoreStrategy(
                onStrategySelected = { selected ->
                    checkAndRestore(selected)
                }
            ))
        } else {
            checkAndRestore(RestoreStrategy.OVERWRITE)
        }
    }

    private fun checkAndRestore(strategy: RestoreStrategy) {
        val status = _uiState.value.syncStatus
        if (status == SyncStatus.LocalNewer) {
            _events.tryEmit(BackupUiEvent.ConfirmOverwriteLocal(
                onConfirm = { restore(strategy) }
            ))
        } else {
            restore(strategy)
        }
    }

    private fun restore(strategy: RestoreStrategy) {
        val manager = driveManager ?: return
        val store = tokenStore ?: return
        setCloudProcessing(true, "Restoring data...")
        viewModelScope.launch {
            val token = store.getToken()
            val config = defaultRestoreConfig.copy(strategy = strategy)
            when (val result = manager.restore(token, config)) {
                is BackupResult.Success -> showSuccess("Restore successful")
                is BackupResult.Error   -> {
                    if (result.code == BackupErrorCode.AUTH_EXPIRED || result.code == BackupErrorCode.NOT_AUTHORIZED) {
                        handleAuthFailureState(result.message)
                    } else {
                        showError(result.message)
                    }
                }
            }
            setCloudProcessing(false)
        }
    }

    // ── CSV Export / Import ────────────────────────────────────────────────────

    private fun setLocalFormat(format: LocalBackupFormat) {
        _uiState.update { it.copy(localBackupFormat = format) }
    }

    private fun exportLocal() {
        val manager = localManager ?: return
        val format = _uiState.value.localBackupFormat
        setLocalProcessing(true, "Preparing ${format.title}...")
        viewModelScope.launch {
            when (val result = manager.exportLocalBackup(format)) {
                is BackupResult.Success -> _events.emit(BackupUiEvent.ShareFile(result.data, format.mimeType))
                is BackupResult.Error   -> showError(result.message)
            }
            setLocalProcessing(false)
        }
    }

    private fun saveLocalToDevice() {
        val manager = localManager ?: return
        val format = _uiState.value.localBackupFormat
        setLocalProcessing(true, "Preparing ${format.title}...")
        viewModelScope.launch {
            when (val result = manager.exportLocalBackup(format)) {
                is BackupResult.Success -> {
                    val file = java.io.File(result.data)
                    _events.emit(
                        BackupUiEvent.SaveFileToDevice(
                            fileName = file.name,
                            filePath = result.data,
                            mimeType = format.mimeType
                        )
                    )
                }
                is BackupResult.Error -> showError(result.message)
            }
            setLocalProcessing(false)
        }
    }

    private fun onImportRequested(uri: String, strategy: RestoreStrategy? = null) {
        if (strategy != null) {
            importCsv(uri, strategy)
            return
        }

        _events.tryEmit(BackupUiEvent.ChooseRestoreStrategy(
            onStrategySelected = { selected ->
                importCsv(uri, selected)
            }
        ))
    }

    private fun importCsv(uri: String, strategy: RestoreStrategy) {
        val manager = localManager ?: return
        setLocalProcessing(true, "Importing data...")
        viewModelScope.launch {
            when (val result = manager.importFromLocal(uri, strategy)) {
                is BackupResult.Success -> showSuccess(result.data.message)
                is BackupResult.Error   -> showError(result.message)
            }
            setLocalProcessing(false)
        }
    }

    // ── Info ───────────────────────────────────────────────────────────────────

    private fun fetchBackupInfo() {
        val manager = driveManager ?: return
        val store = tokenStore ?: return
        setCloudProcessing(true, "Checking status...")
        viewModelScope.launch {
            val token = store.getToken()
            when (val result = manager.getBackupInfo(token)) {
                is BackupResult.Success -> {
                    result.data?.let { info ->
                        remoteModifiedTime = info.modifiedTime
                        _uiState.update { it.copy(
                            lastBackupDate = info.modifiedTime,
                            syncStatus = SyncStatus.Synced,
                            userEmail = info.userEmail
                        ) }
                    } ?: run {
                        _uiState.update { it.copy(syncStatus = SyncStatus.Synced) }
                    }
                }
                is BackupResult.Error -> {
                    if (result.code == BackupErrorCode.AUTH_EXPIRED || result.code == BackupErrorCode.NOT_AUTHORIZED) {
                        handleAuthFailureState(result.message)
                    }
                }
            }
            setCloudProcessing(false)
        }
    }

    private fun fetchAndHandleFirstConnect(token: String) {
        val manager = driveManager ?: return
        setCloudProcessing(true, "Checking for existing backup...")
        viewModelScope.launch {
            when (val result = manager.getBackupInfo(token)) {
                is BackupResult.Success -> {
                    val info = result.data
                    if (info?.size != null) {
                        remoteModifiedTime = info.modifiedTime
                        _uiState.update { it.copy(syncStatus = SyncStatus.Synced, userEmail = info.userEmail) }
                        _events.emit(BackupUiEvent.AskRestoreOnFirstConnect(
                            onRestore = { onRestoreRequested() },
                            onKeepLocal = { showSuccess("Drive connected. Backup or restore anytime.") }
                        ))
                    } else {
                        _uiState.update { it.copy(syncStatus = SyncStatus.Synced) }
                        showSuccess("Drive connected successfully")
                    }
                }
                is BackupResult.Error -> {
                    if (result.code == BackupErrorCode.AUTH_EXPIRED || result.code == BackupErrorCode.NOT_AUTHORIZED) {
                        handleAuthFailureState(result.message)
                    } else {
                        _uiState.update { it.copy(syncStatus = SyncStatus.Synced) }
                        showSuccess("Drive connected successfully")
                    }
                }
            }
            setCloudProcessing(false)
        }
    }

    private fun confirmRevokeAccess() {
        val manager = driveManager ?: return
        _events.tryEmit(BackupUiEvent.ConfirmRevoke(
            onConfirm = {
                viewModelScope.launch {
                    manager.revokeAccess()
                    handleAuthFailureState("Drive access revoked")
                }
            }
        ))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun handleAuthFailureState(message: String) {
        _uiState.update { it.copy(
            isDriveAuthorized = false,
            lastBackupDate = null,
            userEmail = null,
            syncStatus = SyncStatus.NotAuthorized // Reset to disconnected state
        ) }
        showError(message)
    }

    private fun setCloudProcessing(isProcessing: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                isCloudProcessing = isProcessing,
                isProcessing = isProcessing || it.isLocalProcessing,
                loadingMessage = message
            )
        }
    }

    private fun setLocalProcessing(isProcessing: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                isLocalProcessing = isProcessing,
                isProcessing = isProcessing || it.isCloudProcessing,
                loadingMessage = message
            )
        }
    }

    private fun showSuccess(msg: String) {
        _events.tryEmit(BackupUiEvent.ShowMessage(msg, isError = false))
    }

    private fun showError(msg: String) {
        _events.tryEmit(BackupUiEvent.ShowMessage(msg, isError = true))
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    class Factory(
        private val driveManager: RoomGuardDrive?,
        private val localManager: LocalBackupManager?,
        private val tokenStore: DriveTokenStore?,
        private val restoreConfig: RestoreConfig
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RoomGuardBackupViewModel(driveManager, localManager, tokenStore, restoreConfig) as T
    }
}

// ── Supporting types ──────────────────────────────────────────────────────────

data class BackupUiState(
    val isDriveAuthorized: Boolean = false,
    val isCloudProcessing: Boolean = false,
    val isLocalProcessing: Boolean = false,
    val isProcessing: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Checking,
    val localBackupFormat: LocalBackupFormat = LocalBackupFormat.COMPRESSED,
    val loadingMessage: String? = null,
    val lastBackupDate: Long? = null,
    val userEmail: String? = null
)

sealed interface BackupScreenAction {
    data object ConnectDrive : BackupScreenAction
    data object RevokeAccess : BackupScreenAction
    data object Backup : BackupScreenAction
    data object Refresh : BackupScreenAction
    data class Restore(val strategy: RestoreStrategy? = null) : BackupScreenAction
    data class AuthResult(val token: String?, val error: String? = null) : BackupScreenAction
    data class AuthFailed(val error: String? = null) : BackupScreenAction
    data class SetLocalFormat(val format: LocalBackupFormat) : BackupScreenAction
    data object ExportLocal : BackupScreenAction
    data object SaveLocalToDevice : BackupScreenAction
    data class ImportLocal(val uri: String, val strategy: RestoreStrategy? = null) : BackupScreenAction
}

sealed interface BackupUiEvent {
    data class LaunchDriveAuth(val pendingIntent: android.app.PendingIntent) : BackupUiEvent
    data class ShareFile(val filePath: String, val mimeType: String) : BackupUiEvent
    data class SaveFileToDevice(val fileName: String, val filePath: String, val mimeType: String) : BackupUiEvent
    data class ShowMessage(val message: String, val isError: Boolean) : BackupUiEvent
    data class ConfirmOverwriteRemote(val onConfirm: () -> Unit) : BackupUiEvent
    data class ConfirmOverwriteLocal(val onConfirm: () -> Unit) : BackupUiEvent
    data class ConfirmRevoke(val onConfirm: () -> Unit) : BackupUiEvent
    data class ChooseRestoreStrategy(
        val onStrategySelected: (RestoreStrategy) -> Unit
    ) : BackupUiEvent
    data class AskRestoreOnFirstConnect(
        val onRestore: () -> Unit,
        val onKeepLocal: () -> Unit
    ) : BackupUiEvent
}