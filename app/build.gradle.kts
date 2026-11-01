plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qbdlx.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qbdlx.mobile"
        minSdk = 29
        targetSdk = 35
        versionCode = 150
        versionName = "0.41.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// Forward an optional -Dqbz.bundle=<bundle.js> to the forked unit-test JVM so the real-bundle
// extraction test can run; when unset it is empty and that test skips itself.
tasks.withType<Test>().configureEach {
    systemProperty("qbz.bundle", System.getProperty("qbz.bundle") ?: "")
    // Optional folder of real .flac/.mp3 files for the real-audio tagging test (skipped when unset).
    systemProperty("qbz.audioDir", System.getProperty("qbz.audioDir") ?: "")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // Backdrop blur for the translucent bars; 1.1.x matches Compose 1.7 from the BOM above.
    implementation("com.materialkolor:material-kolor:2.1.1")
    implementation("com.airbnb.android:lottie-compose:6.6.2")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("com.valentinilk.shimmer:compose-shimmer:1.3.1")
    implementation("dev.chrisbanes.haze:haze:1.1.1")
    implementation("dev.chrisbanes.haze:haze-materials:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    // The android.jar stubs make org.json throw in JVM unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // A real SQLite for the iPod library tests (the android.jar one is a stub on the JVM).
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}
