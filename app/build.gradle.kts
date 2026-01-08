plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.abdulhai.datasentinel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.abdulhai.datasentinel"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0" // This marks the official "v1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-prerelease-check")
    }
    buildFeatures {
        // This is the most important part
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Biometrics & Lifecycle
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    implementation(libs.generativeai)
    kapt("androidx.room:room-compiler:2.6.1")

    // Lifecycle (needed for the code to run correctly)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // UI components
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    //Paging
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
}