# Storage Providers

RoomGuard uses a provider-based architecture to remain agnostic of the underlying database engine.

## 💾 [`DatabaseProvider`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-core/src/main/kotlin/dev/dhanfinix/roomguard/core/DatabaseProvider.kt)

This provider handles low-level file and SQLite management.

### Key Responsibilities:
- **`getDatabaseFilePath()`**: Returning the absolute path to the `.db` file.
- **`checkpoint()`**: Executing `PRAGMA wal_checkpoint(FULL)`. This is critical for data integrity before copying the DB file for backup.
- **`closeDatabase()`**: Used during `RestoreMode.REPLACE` to safely close connections before file swapping.

## 📄 [`CsvSerializer`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-core/src/main/kotlin/dev/dhanfinix/roomguard/core/CsvSerializer.kt)

This provider defines how data is exported to and imported from human-readable CSV files.

### Implementation Checklist:
1.  **Concurrency**: Use `coroutineScope` to perform DB operations inside `toCsv()` and `fromCsv()`.
2.  **Conflict Resolution**: During `fromCsv()`, the implementation must decide whether to overwrite, skip, or merge existing records.
3.  **Schema Versioning**: It is recommended to include a header or version tag in the CSV output to handle future schema changes.

## 🛠️ Automatic Room Support

The `roomguard-local` module provides [`AutomaticRoomCsvSerializer`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-local/src/main/kotlin/dev/dhanfinix/roomguard/local/AutomaticRoomCsvSerializer.kt) which reflects on the Room schema to handle basic tables automatically.

---

> [!CAUTION]
> Always ensure `checkpoint()` is called before file-based backups. Failure to do so may result in backing up an empty or outdated database file if the app is in WAL mode.
