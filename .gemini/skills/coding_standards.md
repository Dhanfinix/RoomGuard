# Coding Standards

Follow these engineering principles when contributing to RoomGuard.

## 🧱 Modular Purity
- **`roomguard-core`**: MUST NOT import `android.*` classes (except for extremely common ones like `android.net.Uri` if absolutely necessary, but prefer raw strings or interfaces). It should be a pure JVM/Kotlin module.
- **Dependencies**: Keep the dependency graph flat. Avoid adding heavy transitive dependencies to the core module.

## 🧵 Coroutines & Threads
- **I/O Operations**: All I/O (Database, Network, Filesystem) must be performed using `suspend` functions.
- **Dispatchers**: Never hardcode `Dispatchers.IO` inside the implementation. Instead, inject the dispatcher or use a caller-provided scope to ensure testability.
- **State**: Use `StateFlow` in ViewModels for UI state management.

## 💎 UI Aestetics (roomguard-ui)
- **Material 3**: All components must use Material 3 design tokens.
- **Theming**: Support both Light and Dark modes.
- **Feedback**: Every asynchronous action (backup, restore, auth) must have a corresponding loading state and error feedback (e.g., Snackbars or Dialogs).

## 📝 Documentation
- **KDoc**: All public classes, interfaces, and methods in the `roomguard` and `roomguard-core` modules must have clear KDoc.
- **Consistency**: Use standard names for common parameters: `context`, `token`, `config`, `result`.

---

> [!WARNING]
> Do not use `GlobalScope`. Always link coroutines to a relevant lifecycle scope (e.g., `ViewModelScope` or a library-managed scope).
