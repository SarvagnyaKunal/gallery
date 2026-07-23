plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Apply Google services plugin so google-services.json is processed
    id("com.google.gms.google-services")
}

android {
    namespace = "com.laptop.gallery"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.laptop.gallery"
        minSdk = 29          // Android 10: MediaStore.loadThumbnail + scoped storage
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Firebase BoM - ensures compatible Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))

    // Example Firebase product: Analytics (no explicit version when using BoM)
    implementation("com.google.firebase:firebase-analytics")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Tiny single-file HTTP server (~50KB, no transitive deps).
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
