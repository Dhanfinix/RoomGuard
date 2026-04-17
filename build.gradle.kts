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

    // Helper to find a property from local.properties or System Environment
    fun findConfig(name: String): String? {
        return localProperties.getProperty(name) 
            ?: System.getenv(name) 
            ?: System.getenv("ORG_GRADLE_PROJECT_$name")
            ?: project.findProperty(name)?.toString()
    }

    // Inject properties into the project extension so plugins can see them.
    // We map both dotted and flat names to support different plugin search patterns.
    mapOf(
        "MAVEN_CENTRAL_USERNAME" to listOf("mavenCentralUsername", "sonatypeUsername"),
        "MAVEN_CENTRAL_PASSWORD" to listOf("mavenCentralPassword", "sonatypePassword"),
        "GPG_SIGNING_KEY_ID" to listOf("signing.keyId", "signingKeyId"),
        "GPG_SIGNING_KEY_PASSWORD" to listOf("signing.password", "signingPassword"),
        "GPG_SIGNING_KEY" to listOf("signing.secretKey", "signingKey")
    ).forEach { (envKey, gradleKeys) ->
        findConfig(envKey)?.let { value ->
            gradleKeys.forEach { key ->
                project.extensions.extraProperties.set(key, value)
            }
        }
    }

    // CI Debug Task (Status only, no values leaked)
    val statusTask = tasks.register("printSigningStatus") {
        doLast {
            println("--- Signing Status for ${project.name} ---")
            // Resolve using direct findProperty which looks at project properties and extraProperties
            val kId = project.findProperty("GPG_SIGNING_KEY_ID") ?: project.findProperty("signing.keyId") ?: project.findProperty("signingKeyId")
            val pwd = project.findProperty("GPG_SIGNING_KEY_PASSWORD") ?: project.findProperty("signing.password") ?: project.findProperty("signingPassword")
            val sKey = project.findProperty("GPG_SIGNING_KEY") ?: project.findProperty("signing.secretKey") ?: project.findProperty("signingKey")
            
            println("keyId present: ${kId != null}")
            println("password present: ${pwd != null}")
            println("secretKey present: ${sKey != null}")
            println("-------------------------------------------")
        }
    }

    // Explicit fallback for in-memory signing across all subprojects
    apply(plugin = "signing")
    extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
        // Force the debug task to run before any signing attempt
        tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
            dependsOn(statusTask)
        }

        val keyId = project.findProperty("GPG_SIGNING_KEY_ID")?.toString()
            ?: project.findProperty("signing.keyId")?.toString() 
            ?: project.findProperty("signingKeyId")?.toString()
        val password = project.findProperty("GPG_SIGNING_KEY_PASSWORD")?.toString()
            ?: project.findProperty("signing.password")?.toString()
            ?: project.findProperty("signingPassword")?.toString()
        val secretKey = project.findProperty("GPG_SIGNING_KEY")?.toString()
            ?: project.findProperty("signing.secretKey")?.toString()
            ?: project.findProperty("signingKey")?.toString()

        if (keyId != null && password != null && secretKey != null) {
            // Handle literal \n if passed as a single line from CI
            val formattedKey = secretKey.replace("\\n", "\n")
            @Suppress("UnstableApiUsage")
            useInMemoryPgpKeys(keyId, formattedKey, password)
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
    }
}
