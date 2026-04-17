# 🛡️ RoomGuard

RoomGuard is a modular, framework-agnostic Android library designed to provide a robust, production-ready solution for database backup and restoration. It supports seamless **Google Drive** synchronization and **Local backup export/import** with both human-readable CSV and compressed backup formats, all delivered through a polished **Material 3 Compose UI**.

## ✨ Features

- **Google Drive Backup**: Uses the secure `appDataFolder` namespace (hidden from users, accessible only by your app).
- **Safe Restore Strategy**: Implements a native SQLite `ATTACH` pattern to restore individual tables without breaking Room's active `Flow` or `LiveData` observers.
- **Local Backup Management**: Export and Import database tables as portable CSV or compressed backup files using the Android Storage Access Framework (SAF).
- **Modular Architecture**: Pay only for what you use (Core, Drive, Local, UI, or Hilt).
- **Modern UI**: Drop-in Jetpack Compose screen with Material 3 design, including loading states and confirmation dialogs.

---

## 🏗️ Architecture

RoomGuard is split into 5 modules to minimize APK size and dependency bloat:

1.  **`roomguard-core`**: Pure Kotlin/JVM. Contains all interfaces and shared data models.
2.  **`roomguard-drive`**: Android logic for Google Drive REST API and OAuth.
3.  **`roomguard-local`**: Android logic for local backup serialization and SAF file handling.
4.  **`roomguard-ui`**: The pre-built Compose `RoomGuardBackupScreen`.
5.  **`roomguard-hilt`**: Dagger-Hilt bindings for easy dependency injection.

---

## 🧩 Usage Combinations

Depending on your needs, you can mix and match modules to keep your APK lean:

| Combination | Required Modules | Best For... | Implementation |
| :--- | :--- | :--- | :--- |
| **Full Package** | `roomguard` | Complete backup strategy (Cloud + Local) | `RoomGuard.Builder` with all dependencies |
| **Drive Only** | `roomguard-core`, `roomguard-drive` | Cloud-only sync, zero local footprint | Instantiate `RoomGuardDrive` directly |
| **Local Only** | `roomguard-core`, `roomguard-local` | Privacy-focused or offline-only backups | Instantiate `RoomGuardLocal` directly |
| **Core Only** | `roomguard-core` | Custom backup engines (e.g., S3, Dropbox) | Implement `DatabaseProvider` + custom logic |

### 🎨 The UI Layer (`roomguard-ui`)
The UI module is an **optional plug-and-play layer** that sits on top of any of the above.
- **Full Setup**: Displays both Cloud and Local options.
- **Partial Setup**: Automatically hides unavailable options (e.g., if you only have a `driveManager`, the Local UI section is hidden).
- **Custom Setup**: Use it if your custom logic follows the Manager interfaces.

---

## 📦 Installation

Add the GitHub Packages repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/Dhanfinix/RoomGuard")
            credentials {
                username = "YOUR_GITHUB_USERNAME"
                password = "YOUR_GITHUB_TOKEN"
            }
        }
    }
}
```

Add the modules you need to your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.dhanfinix.roomguard:roomguard:1.0.0")
    implementation("dev.dhanfinix.roomguard:roomguard-ui:1.0.0")
    // implementation("dev.dhanfinix.roomguard:roomguard-hilt:1.0.0")
}
```

---

## 🚀 Quick Start

### 1. Implement Providers
You need to tell RoomGuard how to access your Room database and how to convert your entities to CSV.
You can then let users export a human-readable CSV or a compressed local backup, and import either format back into the app.

```kotlin
class MyDatabaseProvider(private val context: Context, private val db: MyDatabase) : DatabaseProvider {
    override fun getDatabaseFile(): File = context.getDatabasePath("my_db")
    override fun getDatabaseName(): String = "my_db"
    override fun checkpoint() {
        // Trigger a WAL checkpoint to ensure data is in the main .db file
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
    }
}
```

### 2. Launch the UI
Just drop the `RoomGuardBackupScreen` into your Compose hierarchy.

```kotlin
@Composable
fun BackupSettings(db: MyDatabase) {
    val context = LocalContext.current
    
    // 1. Setup RoomGuard once
    val tokenStore = DataStoreDriveTokenStore(context)
    val dbProvider = MyDatabaseProvider(context, db)
    val roomGuard = RoomGuard.Builder(context)
        .appName("AppName")
        .databaseProvider(dbProvider)
        .tokenStore(tokenStore)
        .csvSerializer(MyCsvSerializer())
        .build()
    
    // 2. Define what to restore
    val restoreConfig = RestoreConfig(
        tables = listOf("users", "settings"), 
        mode = RestoreMode.ATTACH
    )

    // 3. Display screen
    RoomGuardBackupScreen(
        driveManager = roomGuard.driveManager,
        localManager = roomGuard.localManager,
        tokenStore = roomGuard.tokenStore,
        restoreConfig = restoreConfig
    )
}
```

---

## 🛠️ Configuration

### Restore Strategies
- **`RestoreMode.ATTACH` (Recommended)**: Attaches the backup as a second database and performs a `DELETE + INSERT` per table. Safer for active apps as it doesn't delete the database file.
- **`RestoreMode.REPLACE`**: Physically swaps the `.db` file on disk. Requires the app to restart or the database to be closed.

---

## 📄 License
Copyright © 2026 Dhanfinix. Published under the MIT License.
