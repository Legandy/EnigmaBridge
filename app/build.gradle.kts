plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the kotlin-parcelize plugin to handle Parcelable objects automatically
    id("kotlin-parcelize")
}

android {
    namespace = "io.github.legandy.enigmabridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.legandy.enigmabridge"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        viewBinding = true
        aidl = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // THE CRITICAL FIX: Using the latest stable versions that fully support Material 3
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
