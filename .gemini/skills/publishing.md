# Publishing & CI/CD

RoomGuard is published to Maven Central.

## 🚀 Release Workflow
1.  **Tagging**: Push a tag matching `v*` (e.g., `v0.0.1-alpha.1`).
2.  **GitHub Actions**: The `publish.yml` workflow is triggered automatically.
3.  **Signing**: Artifacts are signed using GPG. The signing key is stored in GitHub Secrets.
4.  **Verification**: After the workflow passes, artifacts are available in the Sonatype Staging Repository for 15 minutes before being synced to Maven Central.

## 🔐 Credentials
The following environment variables are required for local and CI publishing:

| Variable | Description |
|---|---|
| `OSSRH_USERNAME` | Sonatype (Maven Central) account username. |
| `OSSRH_PASSWORD` | Sonatype account password. |
| `GPG_KEY_ID` | Last 8 characters of the GPG signing key. |
| `GPG_KEY_PASSWORD` | Password for the GPG key. |
| `GPG_SIGNING_KEY` | The full GPG private key (exported as armored text). |

## 🛠️ Local Publishing
To publish to the local Maven repository (for testing in a local project):
```bash
./gradlew publishToMavenLocal
```

---

> [!CAUTION]
> Never commit actual credentials or GPG keys to the repository. Always use `local.properties` (which is git-ignored) or environment variables.
