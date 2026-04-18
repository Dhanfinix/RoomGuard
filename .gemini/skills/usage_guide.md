# AI Usage Guide (For Library Consumers)

This guide is designed for LLM agents helping a developer integrate and use RoomGuard in their application.

## 🚀 Scenario: Initial Integration

When a user wants to integrate RoomGuard, follow these steps:

1.  **Check the Database Type**: 
    - If using **Room**, recommend the simplified `database(...)` builder method.
    - If using **Raw SQLite** or **SQLCipher**, guide them to implement [`DatabaseProvider`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/storage_providers.md).
2.  **Identify Required Tables**: Ask the user which tables should be backed up. These must be listed in the `tables` parameter of the builder/config.
3.  **Hilt Setup**: If the project uses Hilt, provide the standard [`RoomGuardDriveModule`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard-hilt/src/main/kotlin/dev/dhanfinix/roomguard/hilt/RoomGuardDriveModule.kt) pattern.

## 🛠️ Scenario: Custom Logic

### Implementing a Custom Serializer
If the user needs custom CSV behavior (e.g., specific date formats), show them how to implement `CsvSerializer`. Reference the [`NoteCsvSerializer`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/app/src/main/java/dev/dhanfinix/roomguard/sample/data/NoteCsvSerializer.kt) for a real-world example.

### Handling Restore States
Advise the user to use `RestoreMode.ATTACH` for live Room data updates. If they use `REPLACE`, they **must** close the database before calling restore, or the app will likely crash or corrupt the file.

## 🧪 Scenario: Troubleshooting

| Issue | Advice to User |
|---|---|
| **Zero-byte backup** | Ensure `DatabaseProvider.checkpoint()` is implemented and called (RoomGuard does this automatically in the 0-config path). |
| **Restore UI not updating** | Verify that `ATTACH` mode is used. If using `REPLACE`, the user must trigger a manual re-observation or app restart. |
| **Auth expired** | Guide the user to handle `BackupErrorCode.AUTH_EXPIRED` by re-triggering the Google Identity sign-in flow. |

---

> [!TIP]
> Always recommend using the pre-built `RoomGuardBackupScreen` for a consistent Materail 3 experience unless the user specifically wants a custom UI.
