# GCloud Setup Guide (For AI Agents)

This guide provides instructions for LLM agents helping a developer set up the Google Cloud environment required for RoomGuard.

## 🔐 GCloud Console Checklist

1.  **Project**: Create or select a Google Cloud project.
2.  **APIs**: Enable the **Google Drive API**.
3.  **OAuth Consent Screen**:
    - Select User Type (External is required for production).
    - Add Scope: `https://www.googleapis.com/auth/drive.appdata`.
4.  **Credentials**: Create an **Android OAuth 2.0 Client ID**.
    - **Package Name**: Must match the `applicationId` in the host app's `build.gradle`.
    - **SHA-1 Fingerprint**:
        - *Debug*: Generate using `keytool -list -v -keystore ~/.android/debug.keystore`.
        - *Release*: Copy from the Google Play Console (Setup -> App Integrity) or your local release keystore.

## 📱 Android Manifest Requirements

Ensure the host app has these permissions in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## ⚠️ Common Configuration Pitfalls

- **Scope Mismatch**: If the user doesn't add `drive.appdata` to the OAuth consent screen, authentication will succeed but file operations will fail with a 403 Forbidden error.
- **SHA-1 Conflict**: If the app works in debug but fails in release, the release SHA-1 fingerprint is likely missing from the Cloud Console.
- **AppDataFolder Restricted**: Remind users that `appDataFolder` is **hidden from the user**. They cannot see the backup file in the Google Drive app or website.

---

> [!IMPORTANT]
> Always verify the SHA-1 fingerprint by running `./gradlew signingReport` in the host project if the user is unsure.
