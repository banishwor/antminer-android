plugins {
    alias(libs.plugins.android.application)
}

// Relay Lite Step 1 — API 22 compatibility probe (throwaway).
//
// Deliberately independent of :app: no AndroidX, no Compose, no third-party
// libraries — framework APIs and plain Views only, plain Java, so nothing
// modern can sneak an API requirement into the APK. This module exists to
// answer one question on a real Android 5.1.1 device (Vivo Y21L): can API 22
// be Relay Lite's floor? See MainActivity for the four tests.
android {
    namespace = "com.banishwor.relayprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.banishwor.relayprobe"
        minSdk = 22
        targetSdk = 22
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// No dependencies block on purpose: the probe must run on the bare framework.
