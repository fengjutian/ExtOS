plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.extos.runtime"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.extos.runtime"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
