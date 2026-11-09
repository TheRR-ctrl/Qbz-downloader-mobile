// libusbdac (https://github.com/ostrichbat2k/libusbdac, GPL-3.0-or-later, commit 3cc831d) copied in as a module: direct USB audio
// output (UAC1/2/3) that skips Android's mixer. Changes from upstream: this build file only (Kotlin DSL, versions of this project).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "org.ostrichbat2k.usbdac"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    api("androidx.media3:media3-exoplayer:1.11.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    testImplementation("junit:junit:4.13.2")
}
