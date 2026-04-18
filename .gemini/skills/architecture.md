# Architecture Overview

RoomGuard is a modular Android library designed for loose coupling and high portability.

## 🏗️ Module Breakdown

| Module | Purpose | Dependency Rules |
|---|---|---|
| **`roomguard-core`** | Platform-agnostic interfaces and models. | **Strictly PURE JVM.** No Android framework dependencies. |
| **`roomguard-drive`** | Google Drive REST API implementation. | Depends on `core` and Google Auth SDKs. |
| **`roomguard-local`** | CSV serialization and filesystem logic. | Depends on `core` and Android Platform. |
| **`roomguard-ui`** | Material 3 Jetpack Compose components. | Depends on `core`, `drive`, `local`, and Compose. |
| **`roomguard`** | The public Facade and Builder entry point. | Aggregates all other modules. |
| **`roomguard-hilt`** | Dependency Injection modules. | Optional bridge for Hilt users. |

## 🔗 Core Abstractions

The library communicates with the host app exclusively through these interfaces:

- [**`DatabaseProvider`**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-core/src/main/kotlin/dev/dhanfinix/roomguard/core/DatabaseProvider.kt): Manages DB file path and checkpointing (WAL flush).
- [**`CsvSerializer`**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-core/src/main/kotlin/dev/dhanfinix/roomguard/core/CsvSerializer.kt): Manages table-to-CSV logic and conflict resolution during import.

## 🔄 Data Flow

1. **Physical Backup (Default)**: 
    - `DatabaseProvider.checkpoint()` flushes data to disk.
    - File is copied to a `roomguard_backup_temp.db` in cache.
    - File is optionally GZipped and uploaded to Drive's `appDataFolder`.
2. **Logical Sync (Incremental)**:
    - Tables are serialized to `data.csv.gz` (Metadata).
    - Binary fields are hashed (SHA-256) and uploaded as sidecar files to `/blobs`.
    - Differential sync ensures only new/modified blobs are uploaded.
3. **Restoration**:
    - Backup is downloaded to a temporary cache location.
    - `RestoreMode` (ATTACH/REPLACE) is applied for physical backups.
    - Logical ingestion (CSV) is applied for incremental backups.

---

> [!TIP]
> When adding new features, always place the interface in `roomguard-core` and the implementation in a specialized module.
