plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "io.github.dhanfinix"
    version = "0.0.1-alpha.1"
}

subprojects {
    // Load properties from local.properties if it exists (local development)
    val localProperties = java.util.Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            load(file.inputStream())
        }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        apply(plugin = "org.jetbrains.dokka")

        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            signAllPublications()

            pom {
                name.set(project.name.replace("-", " ").replaceFirstChar { it.uppercase() })
                description.set(
                    when (project.name) {
                        "roomguard" -> "Main library for RoomGuard backup and restore."
                        "roomguard-core" -> "Core logic and interfaces for RoomGuard."
                        "roomguard-drive" -> "Google Drive storage provider for RoomGuard."
                        "roomguard-local" -> "Local storage provider for RoomGuard."
                        "roomguard-hilt" -> "Hilt dependency injection support for RoomGuard."
                        "roomguard-ui" -> "Compose UI components for RoomGuard."
                        else -> "RoomGuard library component."
                    }
                )
            }
        }

        // Explicit fallback for in-memory signing if standard auto-detection fails
        extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
            val keyId = project.findProperty("signing.keyId")?.toString()
            val password = project.findProperty("signing.password")?.toString()
            val secretKey = project.findProperty("signing.secretKey")?.toString()

            if (keyId != null && password != null && secretKey != null) {
                @Suppress("UnstableApiUsage")
                useInMemoryPgpKeys(keyId, secretKey, password)
            }
        }
    }

    // Helper to find a property from local.properties or System Environment
    fun findConfig(name: String): String? {
        return localProperties.getProperty(name) 
            ?: System.getenv(name) 
            ?: System.getenv("ORG_GRADLE_PROJECT_$name")
            ?: project.findProperty(name)?.toString()
    }

    // Inject properties into the project extension so plugins can see them.
    // Standard names for Vanniktech/Signing plugins:
    // "signing.keyId", "signing.password", "signing.secretKey"
    mapOf(
        "mavenCentralUsername" to "mavenCentralUsername",
        "mavenCentralPassword" to "mavenCentralPassword",
        "signingInMemoryKeyId" to "signing.keyId",
        "signingInMemoryKeyPassword" to "signing.password",
        "signingInMemoryKey" to "signing.secretKey"
    ).forEach { (envKey, gradleKey) ->
        findConfig(envKey)?.let { value ->
            project.extensions.extraProperties.set(gradleKey, value)
        }
    }
}
