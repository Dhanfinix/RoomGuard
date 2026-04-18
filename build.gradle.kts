plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.maven.publish) // Applied to root for global Sonatype service management
    alias(libs.plugins.dokka) apply false
}

allprojects {
    group = "io.github.dhanfinix"
    version = "0.0.1-alpha.2"
}

subprojects {
    // Native Property Discovery:
    // We rely on Gradle's auto-injection from environment variables 
    // prefixed with ORG_GRADLE_PROJECT_ (e.g., ORG_GRADLE_PROJECT_signingKeyId).
    
    // Explicit fallback for in-memory signing across all subprojects
    apply(plugin = "signing")
    
    // CI Debug Task (Status only, no values leaked)
    val statusTask = tasks.register("printSigningStatus") {
        doLast {
            println("--- Signing Status for ${project.name} ---")
            // Resolve using direct findProperty which natively checks ORG_GRADLE_PROJECT_ vars
            val kId = project.findProperty("signingKeyId") ?: project.findProperty("signing.keyId")
            val pwd = project.findProperty("signingPassword") ?: project.findProperty("signing.password")
            val sKey = project.findProperty("signingKey") ?: project.findProperty("signing.secretKey")
            
            println("keyId present: ${kId != null}")
            println("password present: ${pwd != null}")
            println("secretKey present: ${sKey != null}")
            println("-------------------------------------------")
        }
    }

    extensions.configure<org.gradle.plugins.signing.SigningExtension>("signing") {
        // Force the debug task to run before any signing attempt
        tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
            dependsOn(statusTask)
        }

        val keyId = project.findProperty("signingKeyId")?.toString()
            ?: project.findProperty("signing.keyId")?.toString()
        val password = project.findProperty("signingPassword")?.toString()
            ?: project.findProperty("signing.password")?.toString()
        val secretKey = project.findProperty("signingKey")?.toString()
            ?: project.findProperty("signing.secretKey")?.toString()

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
