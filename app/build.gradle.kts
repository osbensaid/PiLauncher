plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pilauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pilauncher"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    // Fixed key so every new build can update the installed app
    signingConfigs {
        getByName("debug") {
            storeFile = file("pilauncher.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
