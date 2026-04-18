# 🛡️ RoomGuard

RoomGuard is a modular, production-ready Android library designed for plug-and-play database backup and restoration. It provides a unified API for **Google Drive** synchronization and **Local Data Portability** (CSV), combined with a polished **Material 3 Compose UI**.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dhanfinix/roomguard)](https://central.sonatype.com/artifact/io.github.dhanfinix/roomguard)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## ✨ Features

- **Zero-Config Room Integration**: Protect your entire database with minimal boilerplate.
- **Google Drive Backup**: Secure, private backup using the `appDataFolder` namespace.
- **Incremental (Logical) Backups**: Differential binary uploads + CSV metadata—perfect for apps with many images.
- **Safe Restore Strategy**: Table-level `ATTACH` logic to preserve active Room observers (Flow/LiveData).
- **Local Data Portability**: Export and Import tables as portable CSV or compressed GZIP files.
- **Modern UI**: Drop-in Jetpack Compose recovery center with Material 3 design.
- **Modular Design**: 5 modules to minimize APK size (Core, Drive, Local, UI, Hilt).

---

## 📦 Installation

Add the dependencies to your `build.gradle.kts`:

```kotlin
dependencies {
    val version = "0.0.1-alpha.3"
    implementation("io.github.dhanfinix.roomguard:roomguard:$version")
    implementation("io.github.dhanfinix.roomguard:roomguard-ui:$version")
    
    // Optional Hilt Support
    implementation("io.github.dhanfinix.roomguard:roomguard-hilt:$version")
}
```

---

## 🔐 Prerequisites & Setup (Google Drive)

To use the Google Drive cloud backup features, you must configure your project in the **Google Cloud Console**.

### 1. Google Cloud Configuration
- **Enable Drive API**: Search for "Google Drive API" and enable it for your project.
- **OAuth Consent Screen**:
    - Configure the consent screen (External/Internal).
    - **Required Scope**: Add `https://www.googleapis.com/auth/drive.appdata` to the list of scopes.
- **Credentials**: Create an **Android OAuth 2.0 Client ID**.
    - **Package Name**: Must match your app's package name.
    - **SHA-1 Fingerprint**: Required for both Debug and Release environments.
        - *Debug*: Run `keytool -list -v -keystore ~/.android/debug.keystore`.
        - *Release*: Obtain from the Play Console or your release signing key.

### 2. Android Manifest
Add these permissions to your `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🚀 Quick Start (Zero-Config)

### 1. Initialize
Initialize RoomGuard once in your Application class or Singleton.

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
Drop the `RoomGuardBackupScreen` into your Compose hierarchy.

```kotlin
@Composable
fun SettingsScreen() {
    RoomGuardBackupScreen(roomGuard = roomGuard)
}
```

---

## 🛠️ Use Cases

### A. Local Data Portability (CSV)
Export and import specific data as human-readable CSV files. Excellent for inter-app portability or user-managed local backups.
```kotlin
// Manual use of the local export engine
val result = roomGuard.localManager()?.exportToCsv()
```

### B. Dependency Injection (Hilt)
Use the pre-built Hilt modules for clean architecture. Requires providing your `appName` and `databaseProvider`.
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppBackupModule {
    @Provides
    @Named("appName")
    fun provideAppName() = "MyNotesApp"
}
```

### C. Custom Database Providers (Non-Room)
If you use raw SQLite, SQLCipher, or another database engine, implement the bridge interfaces:
```kotlin
class MyCustomProvider : DatabaseProvider {
    override fun getDatabaseFilePath() = "path/to/db"
    override suspend fun checkpoint() { /* Flush WAL manually */ }
}
```

### D. Restore Modes
- **`RestoreMode.ATTACH` (Recommended)**: Merges data into the live database. Observers keep working.
- **`RestoreMode.REPLACE`**: Physically replaces the `.db` file. Requires closing connections first.

### E. Incremental (Logical) Backups (Premium Sync)
For apps with significant binary data (e.g., thousands of gallery images), raw `.db` uploads are slow and expensive. 

RoomGuard's **Incremental Strategy** uses content-based deduplication (SHA-256):
1.  **Deduplication**: Only new or modified images are uploaded to a hidden sidecar folder.
2.  **Tracking**: Uses a `last_update` timestamp column to filter changed records.
3.  **Sync Deletion**: Mirror local deletions to the cloud automatically.
4.  **Metadata Compression**: Metadata (`data.csv`) is automatically GZIP compressed (`data.csv.gz`) when syncing to Drive, reducing bandwidth usage by up to 90%.

```kotlin
val roomGuard = RoomGuard.Builder(context)
    .database(db, "app.db", tables)
    .config(RoomGuardConfig(
        backupStrategy = BackupStrategy.INCREMENTAL,
        blobStrategy = BlobStrategy.FILE_POINTER,
        useCompression = true, // Enables GZIP for Drive metadata and Local exports
        incrementalConfig = IncrementalConfig(trackingColumn = "last_update")
    ))
    .build()
```

---

## 🤖 For LLM & AI Agents

If you are an AI assistant helping a developer integrate this library, use the provided **AI Skill Kit** for deep technical context:

1.  **GCloud Setup**: [**`setup_guide.md`**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/setup_guide.md) — Step-by-step for OAuth, SHA-1, and Scopes.
2.  **Usage Scenarios**: [**`usage_guide.md`**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/usage_guide.md) — Hilt patterns, custom serialization, and troubleshooting.
3.  **Logical Strategy**: [**`incremental_backup.md`**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/incremental_backup.md) — Content hashing, change tracking, and differential sync.
4.  **Main Entry**: [`.gemini/skills/index.md`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/index.md) — Directory of all architectural skills.

---

## 📐 Architecture

| Module | Purpose |
|---|---|
| **`roomguard`** | **The official entry point.** Facade & Builder that unifies all features. |
| **`roomguard-core`** | Platform-agnostic interfaces (JVM pure). |
| **`roomguard-drive`** | Google Drive REST API & Auth logic. |
| **`roomguard-local`** | CSV Serialization & Filesystem tasks. |
| **`roomguard-ui`** | Material 3 Jetpack Compose Components. |
| **`roomguard-hilt`** | Hilt Dependency Injection Modules. |

---

## 📄 License
Copyright © 2026 Dhanfinix. Published under the **MIT License**.
