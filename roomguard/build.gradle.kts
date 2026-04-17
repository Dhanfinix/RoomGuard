plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "dev.dhanfinix.roomguard"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(project(":roomguard-core"))
    api(project(":roomguard-drive"))
    api(project(":roomguard-local"))
    api(libs.google.play.auth)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
