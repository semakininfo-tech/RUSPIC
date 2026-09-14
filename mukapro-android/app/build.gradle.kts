plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.mukapro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.mukapro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"

        val mapkitKey = project.findProperty("MAPKIT_API_KEY")?.toString() ?: ""
        buildConfigField("String", "MAPKIT_API_KEY", "\"$mapkitKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.yandex.android:maps.mobile:4.42.0-full")
}
