pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RoomGuard"

include(":roomguard-core")
include(":roomguard-drive")
include(":roomguard-local")
include(":roomguard")
include(":roomguard-hilt")
include(":roomguard-ui")
include(":app")
