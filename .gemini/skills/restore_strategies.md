# Restore Strategies

RoomGuard implements two distinct strategies for restoring data, defined by the [`RestoreMode`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-core/src/main/kotlin/dev/dhanfinix/roomguard/core/RestoreMode.kt) enum.

## 🔗 `RestoreMode.ATTACH` (Default/Recommended)

This mode performs a table-level SQL merge. It is the safest method for Room databases as it doesn't break active `LiveData` or `Flow` observers.

### Logic Flow:
1.  **Download**: Backup is saved locally as `roomguard_restore_temp.db`.
2.  **Attach**: The live database executes `ATTACH 'temp_path' AS backup_db`.
3.  **Transfer**: For each configured table:
    - `DELETE FROM main_table`
    - `INSERT INTO main_table SELECT * FROM backup_db.main_table`
4.  **Cleanup**: `DETACH backup_db` and delete the temp file.

### Pros/Cons:
- ✅ **Pros**: App stays reactive; observers keep working; no need to restart.
- ❌ **Cons**: Requires explicit list of table names; tables must have the same schema in the backup and the current app.

## 💿 `RestoreMode.REPLACE`

This mode performs a physical file swap on disk.

### Logic Flow:
1.  **Download**: Backup is saved locally.
2.  **Close**: `DatabaseProvider.closeDatabase()` is called.
3.  **Overwrite**: The live `.db` file is replaced by the backup file.
4.  **Restart**: `DatabaseProvider.onRestoreComplete()` is called. The host app is responsible for re-initializing the database or restarting the process.

### Pros/Cons:
- ✅ **Pros**: Works for any SQLite database; no table list required; replaces entire schema atomically.
- ❌ **Cons**: Breaks all active observers; requires complex state management or app restart.

---

> [!IMPORTANT]
> When using `ATTACH`, always ensure that foreign keys are considered. Tables should be listed in parent-to-child order in the `RestoreConfig`.
