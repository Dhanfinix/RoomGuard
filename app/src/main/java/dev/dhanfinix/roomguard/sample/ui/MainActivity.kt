package dev.dhanfinix.roomguard.sample.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dhanfinix.roomguard.core.RestoreConfig
import dev.dhanfinix.roomguard.core.RestoreMode
import dev.dhanfinix.roomguard.drive.RoomGuardDrive
import dev.dhanfinix.roomguard.drive.token.DataStoreDriveTokenStore
import dev.dhanfinix.roomguard.local.RoomGuardLocal
import dev.dhanfinix.roomguard.sample.data.NoteCsvSerializer
import dev.dhanfinix.roomguard.sample.data.NoteDatabase
import dev.dhanfinix.roomguard.sample.data.NoteDatabase.Companion.DB_NAME
import dev.dhanfinix.roomguard.sample.data.NoteDatabaseProvider
import dev.dhanfinix.roomguard.sample.data.NoteEntity
import dev.dhanfinix.roomguard.ui.RoomGuardBackupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = NoteDatabase.getInstance(this)
        val dao = db.noteDao()

        // RoomGuard wiring
        val tokenStore = DataStoreDriveTokenStore(this)
        val dbProvider = NoteDatabaseProvider(this, db)
        val driveManager = RoomGuardDrive(
            context = this,
            appName = getString(dev.dhanfinix.roomguard.sample.R.string.app_name),
            databaseProvider = dbProvider,
            tokenStore = tokenStore
        )
        val localManager = RoomGuardLocal(this, NoteCsvSerializer(dao), filePrefix = "${DB_NAME}_backup")
        val restoreConfig = RestoreConfig(tables = listOf(DB_NAME), mode = RestoreMode.ATTACH)

        setContent {
            MaterialTheme(
                colorScheme = dynamicLightColorScheme(this)
            ) {
                SampleApp(
                    dao = dao,
                    driveManager = driveManager,
                    localManager = localManager,
                    tokenStore = tokenStore,
                    restoreConfig = restoreConfig
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp(
    dao: dev.dhanfinix.roomguard.sample.data.NoteDao,
    driveManager: RoomGuardDrive,
    localManager: RoomGuardLocal,
    tokenStore: DataStoreDriveTokenStore,
    restoreConfig: RestoreConfig
) {
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(dao))
    val notes by mainViewModel.notes.collectAsState()
    var showBackupScreen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showBackupScreen) {
        androidx.activity.compose.BackHandler {
            showBackupScreen = false
            mainViewModel.refresh()
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Backup & Restore") },
                    navigationIcon = {
                        IconButton(onClick = {
                            showBackupScreen = false
                            mainViewModel.refresh()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            RoomGuardBackupScreen(
                driveManager = driveManager,
                localManager = localManager,
                tokenStore = tokenStore,
                restoreConfig = restoreConfig,
                modifier = Modifier.padding(padding)
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RoomGuard Sample") },
                    actions = {
                        IconButton(onClick = { showBackupScreen = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Backup Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        ) { padding ->
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No notes yet.\nTap + to add one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteItem(
                            note = note,
                            onDelete = { mainViewModel.deleteNote(note) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNoteDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, body ->
                mainViewModel.addNote(title, body)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun NoteItem(
    note: NoteEntity,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (note.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, body) },
                enabled = title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
