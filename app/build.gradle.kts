plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // unit tests on GitHub: Android calls give plain defaults instead of failing
    testOptions { unitTests.isReturnDefaultValues = true }
    namespace = "com.pretorian.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pretorian.tv"
        minSdk = 24
        targetSdk = 34
        // raise both numbers for every new version (needed for updates)
        versionCode = 244
        versionName = "2.4.4"
        // TMDB_KEY from GitHub Secrets: posters, cast, platform and coming episodes work for everyone without typing a key
        buildConfigField("String", "TMDB_KEY", "\"" + (System.getenv("TMDB_KEY") ?: "") + "\"")
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
    // QR code for "type on the phone" (Add playlist): ZXing core only, no camera code
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    // FFmpeg sound decoder for media3 1.5.0 (Jellyfin build, GPL-3): AC3 / E-AC3 / MP2 / DTS when the box has none
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
}
