# RoomGuard Drive — Consumer ProGuard Rules
# These rules are automatically applied to any app that depends on roomguard-drive.

# Google Drive API client
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.http.** { *; }

# Google Auth
-keep class com.google.auth.** { *; }

# Google HTTP Client (Gson)
-keep class com.google.api.client.json.gson.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Drive model classes
-keep class com.google.api.services.drive.model.** { *; }

# Play Services Auth
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Credentials Manager
-keep class androidx.credentials.** { *; }
