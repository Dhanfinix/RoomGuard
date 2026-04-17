# 🛡️ RoomGuard

RoomGuard is a modular, framework-agnostic Android library designed to provide a robust, production-ready solution for database backup and restoration. It supports seamless **Google Drive** synchronization and **Local backup export/import** with both human-readable CSV and compressed backup formats, all delivered through a polished **Material 3 Compose UI**.

## ✨ Features

- **Zero-Config Room Integration**: Protect your entire database in 3 lines of code.
- **Google Drive Backup**: Uses the secure `appDataFolder` namespace (hidden from users, accessible only by your app).
- **Safe Restore Strategy**: Implements native SQLite `ATTACH` pattern to restore individual tables without breaking Room's active `Flow` or `LiveData` observers.
- **Local Data Portability**: Export and Import database tables as portable CSV or compressed GZIP files.
- **Unified Architecture**: Single `RoomGuard` instance manages both Cloud and Local engines and their shared configuration.
- **Modern UI**: Drop-in Jetpack Compose screen with Material 3 design, including loading states and confirmation dialogs.

---

## 🏗️ Architecture

RoomGuard is split into 5 modules to minimize APK size:

1.  **`roomguard-core`**: The contract layer. Contains interfaces and configuration models.
2.  **`roomguard-drive`**: Cloud engine logic for Google Drive REST API and OAuth.
3.  **`roomguard-local`**: Local engine for serialization and filesystem operations.
4.  **`roomguard-ui`**: High-level Compose components and ViewModels.
5.  **`roomguard`**: The main entry point that unifies all of the above.

---

## 📦 Installation

RoomGuard is available on **Maven Central**. Add the unified dependency to your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.dhanfinix.roomguard:roomguard:0.0.1-alpha.1")
    implementation("io.github.dhanfinix.roomguard:roomguard-ui:0.0.1-alpha.1")
}
```

---

## 🚀 Quick Start (Zero-Config)

With version 0.0.1-alpha.1, RoomGuard now handles all the SQLite/Room boilerplate for you.

### 1. Initialize RoomGuard
Initialize the engine once in your Application class or Singleton.

```kotlin
val roomGuard = RoomGuard.Builder(context)
    .database(
        db = myRoomDatabase, 
        dbFileName = "app_database.db", 
        tables = listOf("notes", "categories") 
    )
    .build()
```

### 2. Plug into your UI
Just drop the `RoomGuardBackupScreen` into your Compose hierarchy and pass the `roomGuard` instance.

```kotlin
@Composable
fun SettingsScreen() {
    // Drop-in recovery center
    RoomGuardBackupScreen(roomGuard = roomGuard)
}
```

---

## 🛠️ Advanced Configuration

### Custom Providers
If you aren't using Room, you can manually implement the bridge interfaces:

```kotlin
val roomGuard = RoomGuard.Builder(context)
    .databaseProvider(MyCustomDatabaseProvider())
    .csvSerializer(MyCustomCsvSerializer())
    .build()
```

### Restore Modes
- **`RestoreMode.ATTACH` (Recommended)**: Seamlessly merges data into the live database. Room observers (Flow/LiveData) keep working, and no app restart is required.
- **`RestoreMode.REPLACE`**: Physically replaces the `.db` file on disk. Requires closing the database connection and re-initializing the app.

---

## 📄 License
Copyright © 2026 Dhanfinix. Published under the MIT License.
