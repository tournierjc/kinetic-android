plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.kinetick.kinetic"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kinetick.kinetic"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            // The APK that actually goes on a phone: R8 + resource shrinking
            // keep it well under the size a chat app will accept.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug-signed so it installs straight from a download: this is a
            // personal build, not a store release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // HTTP + SSE to kcode --server
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Local persistence (server base URL)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
