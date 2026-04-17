# Entry Point (Facade): RoomGuard

The [`RoomGuard`](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/roomguard/src/main/kotlin/dev/dhanfinix/roomguard/RoomGuard.kt) class is the primary interface for library consumers.

## 🛠️ The Builder Pattern

Consumers initialize the library using the `RoomGuard.Builder`. This ensures that all components (Drive, Local, Auth) are configured consistently.

### "Zero-Config" Room Setup

For Room users, the simplified setup is:

```kotlin
val roomGuard = RoomGuard.Builder(context)
    .database(
        db = myRoomDatabase, 
        dbFileName = "app.db", 
        tables = listOf("notes", "categories")
    )
    .build()
```

### Advanced Manual Setup

For non-Room databases or custom behavior:

```kotlin
val roomGuard = RoomGuard.Builder(context)
    .databaseProvider(MyDatabaseProvider())
    .csvSerializer(MyCsvSerializer())
    .localFilePrefix("custom_export")
    .build()
```

## 📐 Extension Points

When extending the Facade:
1.  **Defaults**: Always check `RoomGuard.Builder.build()` for how "Smart Defaults" are resolved (e.g., app name from Manifest).
2.  **Immutability**: The `RoomGuard` instance is designed to be immutable once built.
3.  **Module Responsibility**: `RoomGuard` is located in the `roomguard` module because it knows about all implementation modules (`drive`, `local`).

---

> [!IMPORTANT]
> Never expose implementation-specific classes (like `RoomGuardDrive`) directly in high-level UI. Always route through the `RoomGuard` facade.
