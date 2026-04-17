plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "dev.dhanfinix.roomguard"
    compileSdk = 35

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

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":roomguard-core"))
    api(project(":roomguard-drive"))
    api(project(":roomguard-local"))
    api(project(":roomguard-ui"))
    api(libs.google.play.auth)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
