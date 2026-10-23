plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 2
        versionName = "0.2"
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
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
}
