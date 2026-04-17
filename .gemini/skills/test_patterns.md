# Testing Patterns

RoomGuard maintains high stability through a multi-layered testing strategy.

## 🧪 Unit Testing (`roomguard-core`)

Since `roomguard-core` is pure JVM, all tests here should be standard JUnit tests.

- **Mocking**: Use MockK for mocking interfaces.
- **Coroutines**: Use `runTest` from `kotlinx-coroutines-test` for all suspend functions.

## 📱 Android Tests (`roomguard-drive`, `roomguard-local`)

These modules require an Android environment.

- **Robolectric**: Most "logic" tests should use Robolectric to avoid needing a physical device.
- **Room Fakes**: When testing `DatabaseProvider`, use an in-memory Room database.
- **File System**: Use `TemporaryFolder` rule or `Files.createTempDir()` to ensure tests are isolated and clean up after themselves.

## 🎨 UI Testing (`roomguard-ui`)

- **Compose Testing**: Use `createComposeRule()` or `createAndroidComposeRule<MainActivity>()`.
- **ViewModels**: Test ViewModels in isolation using fakes for `DriveBackupManager` and `LocalBackupManager`.

## 🏗️ Fakes for Consumers

The library provides testing fakes to help host applications test their RoomGuard integration:
- `FakeDriveBackupManager`: Simulates cloud success/failure/auth-expired states.
- `FakeDatabaseProvider`: Basic provider for in-memory testing.

---

> [!TIP]
> Always verify that your tests cover the `BackupErrorCode` edge cases (e.g., `AUTH_EXPIRED`, `NO_BACKUP_FOUND`).
