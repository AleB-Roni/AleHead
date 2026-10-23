plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Handy- und Uhr-App MÜSSEN dieselbe applicationId und denselben Signaturschlüssel haben,
    // sonst können sie über die Wear-Data-Layer-API nicht miteinander reden.
    namespace = "de.roni.alehead"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.roni.alehead"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
    }

    signingConfigs {
        create("ale") {
            storeFile = rootProject.file("alehead.keystore")
            storePassword = "alehead"
            keyAlias = "alehead"
            keyPassword = "alehead"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ale")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ale")
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

dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Erkennt, ob Android Auto gerade verbunden ist
    implementation("androidx.car.app:app:1.4.0")
    // Öffnet die Uhr-App vom Handy aus
    implementation("androidx.wear:wear-remote-interactions:1.0.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
