plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Apply Google services plugin so google-services.json is processed
    id("com.google.gms.google-services")
}

android {
    namespace = "com.laptop.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.laptop.gallery"
        minSdk = 29          // Android 10: MediaStore.loadThumbnail + scoped storage
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("int", "GALLERY_SERVER_PORT", "65501")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    // Firebase BoM - ensures compatible Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))

    // Example Firebase product: Analytics (no explicit version when using BoM)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Tiny single-file HTTP server (~50KB, no transitive deps).
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
