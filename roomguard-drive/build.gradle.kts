plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "dev.dhanfinix.roomguard.drive"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    api(project(":roomguard-core"))
    testImplementation(project(":roomguard"))
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.datastore.preferences)
    implementation(libs.google.api.drive)
    api(libs.google.play.auth)
    implementation(libs.google.auth)
    implementation(libs.google.http.client.gson)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.androidx.test.core)
    testImplementation("org.robolectric:robolectric:4.13")
}

android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

