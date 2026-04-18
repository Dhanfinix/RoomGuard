# 🛡️ RoomGuard — Gemini AI Skills

This directory contains specialized context and rules for Gemini-powered assistants. These skills ensure that all AI contributions to the RoomGuard project align with its modular architecture, design principles, and engineering standards.

## 📖 For Library Consumers (Users)
*Skills focused on integrating and using the library.*

| Skill | Description |
|---|---|
| [**Setup Guide (GCloud)**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/setup_guide.md) | **Step Zero.** How to configure GCloud Console, OAuth, and SHA-1. |
| [**Usage Guide (API)**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/usage_guide.md) | Scenarios for initial setup, Hilt, and troubleshooting. |
| [**Entry Point (Facade)**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/entry_point.md) | How to use and extend the `RoomGuard` facade and builder. |
| [**Storage Providers**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/storage_providers.md) | Implementing `DatabaseProvider` and `CsvSerializer`. |
| [**Restore Strategies**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/restore_strategies.md) | Deep dive into `ATTACH` vs `REPLACE` SQL logic. |

## 🛠️ For Library Maintainers (Contributors)
*Skills focused on extending and managing the library itself.*

| Skill | Description |
|---|---|
| [**Architecture Overview**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/architecture.md) | Modular design, core contracts, and dependency flow. |
| [**Testing Patterns**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/test_patterns.md) | Standard practices for unit, integration, and UI tests. |
| [**Coding Standards**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/coding_standards.md) | Rules for pure JVM core, coroutines, and UI design. |
| [**Publishing & CI/CD**](file:///Users/muhammadramdhan/StudioProjects/RoomGuard/.gemini/skills/publishing.md) | Maven Central credentials and workflow documentation. |

---

### How to use these skills

Gemini assistants should read the **Setup Guide** first when helping a consumer with Google Drive features. Maintainers should focus on the **Architecture Overview**.
