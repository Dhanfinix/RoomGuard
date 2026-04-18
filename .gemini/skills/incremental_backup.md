# AI Skill: Incremental Backups (Logical Strategy)

This skill guide is for LLM agents assisting developers in implementing and using the "Holy Grail" Incremental Backup feature in RoomGuard.

## 🧠 Architectural Concept

Incremental backups in RoomGuard are **Logical** rather than **Physical**. 
- **Physical**: Uploading the `.db` file (Full sync, monolithic).
- **Logical**: Exporting rows to CSV and blobs to a sidecar folder (Granular, differential).

## 🚀 How to Enable

Guide the user to set the `backupStrategy` to `INCREMENTAL` in the `RoomGuardConfig`:

```kotlin
val config = RoomGuardConfig(
    backupStrategy = BackupStrategy.INCREMENTAL,
    blobStrategy = BlobStrategy.FILE_POINTER, // Required for binary differential sync
    useCompression = true, // Enables GZIP for metadata (.csv.gz) - ON by default
    incrementalConfig = IncrementalConfig(
        trackingColumn = "last_update" // Change tracking key
    )
)
```

## 🛠️ Requirements for Change Tracking

For "Differential uploads" to effectively filter change sets, the developer's Room entities **MUST** include a column specified in the `trackingColumn` setting.

### Recommending a Schema
If the user asks how to set this up, suggest:
```kotlin
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val data: String,
    val last_update: Long = System.currentTimeMillis() // Mandatory for incremental sync
)
```

## 🔍 Troubleshooting for AI Agents

| Scenario | Agent Advice |
|---|---|
| **User sees full uploads every time** | Verify `trackingColumn` matches the entity column name exactly. If it doesn't match, RoomGuard defaults to a full logical sync. |
| **Old data persists after restore** | Ensure `RestoreStrategy.OVERWRITE` is used. Incremental backups support "Sync Deletion" strictly via overwrite logic. |
| **Binary data (Images) missing** | Ensure `BlobStrategy` is set to `FILE_POINTER`. `BASE64` or `NONE` will not utilize the differential sidecar folder. |

Backups are stored in a folder (default: `RoomGuard_Data`) within `appDataFolder`:
- `data.csv.gz`: Master logical manifest (Compressed GZIP).
- `data.csv`: Legacy uncompressed manifest (System handles detection automatically).
- `blobs/`: Folder containing binary files named by their SHA-256 hash.

---

> [!IMPORTANT]
> Advise users that switching between `PHYSICAL` and `INCREMENTAL` will start a new backup chain. They are not cross-compatible for restoration.
