plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qbdlx.lyrics"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qbdlx.lyrics"
        minSdk = 29
        targetSdk = 35
        versionCode = 64
        versionName = "0.8.39"
    }

    // A release build signed with a key given on the command line (-PreleaseKeystore=... -PreleasePassword=...); never stored in the repo.
    signingConfigs {
        if (project.hasProperty("releaseKeystore")) create("release") {
            storeFile = file(project.property("releaseKeystore") as String)
            storePassword = project.property("releasePassword") as String
            keyAlias = "qbdlx"
            keyPassword = project.property("releasePassword") as String
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources { noCompress += "bin" }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    // The android.jar stubs make org.json throw in JVM unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
}
