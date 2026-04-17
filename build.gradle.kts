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
                name.set(project.name.replace("-", " ").capitalize())
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
    }

    // Helper to find a property from local.properties or System Environment
    fun findConfig(name: String): String? {
        return localProperties.getProperty(name) 
            ?: System.getenv(name) 
            ?: System.getenv("ORG_GRADLE_PROJECT_$name")
            ?: project.findProperty(name)?.toString()
    }

    // Inject properties into the project extension so plugins can see them
    listOf(
        "mavenCentralUsername",
        "mavenCentralPassword",
        "signingInMemoryKeyId",
        "signingInMemoryKeyPassword",
        "signingInMemoryKey"
    ).forEach { key ->
        findConfig(key)?.let { value ->
            project.extensions.extraProperties.set(key, value)
        }
    }
}
