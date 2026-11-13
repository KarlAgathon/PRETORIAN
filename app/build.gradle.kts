plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pretorian.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pretorian.tv"
        minSdk = 24
        targetSdk = 34
        // raise both numbers for every new version (needed for updates)
        versionCode = 14
        versionName = "0.4.6"
        // Google sign-in for Drive sync ("TVs and Limited Input devices" client), from the GitHub secrets
        // GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET. Empty: the app asks for them in Settings.
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"" + (System.getenv("GOOGLE_CLIENT_ID") ?: "") + "\"")
        buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"" + (System.getenv("GOOGLE_CLIENT_SECRET") ?: "") + "\"")
    }

    // Private signing key (same as LEAD Launcher): written by GitHub Actions from the secrets
    // LEAD_KEYSTORE_B64 and LEAD_KEY_PASSWORD to app/release.keystore. Never committed.
    val keyPass: String? = System.getenv("LEAD_KEY_PASSWORD")
    val keyFile = file("release.keystore")
    signingConfigs {
        if (keyPass != null && keyFile.exists()) {
            create("lead") {
                storeFile = keyFile
                storePassword = keyPass
                keyAlias = "lead"
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("lead")
                ?: throw GradleException("Signing key missing: set the GitHub secrets LEAD_KEYSTORE_B64 and LEAD_KEY_PASSWORD")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
