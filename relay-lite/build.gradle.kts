plugins {
    alias(libs.plugins.android.application)
}

// Relay Lite Step 3 — the lightweight 24/7 relay app.
//
// The rich dashboard stays in :app; this module is the phone that lives
// at a miner site: a foreground service driving the shared RelayEngine
// from :relay-core, a boot receiver, an AlarmManager watchdog, and one
// plain setup/status screen. Native Views only — no Compose, no
// AndroidX UI — and the floor is API 22 (the Vivo Y21L, Gate A), so
// everything here sticks to framework APIs that exist at 22:
// Notification.Builder (no channels), ConnectivityManager's
// activeNetworkInfo, KeyPairGeneratorSpec for the credential vault.
android {
    namespace = "com.banishwor.antminerrelay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.banishwor.antminerrelay"
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
    lint {
        // Deliberate: targetSdk 22 is a product decision, not neglect —
        // this is a sideloaded appliance app for an Android 5.1.1 relay
        // phone, never a Play Store listing. Lint's Play-policy check
        // would otherwise fail the release build on exactly that choice.
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        // The setup screen header shows the app's own version.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The shared relay core: models, CGI client, cloud sync, engine.
    // It brings OkHttp + coroutines as api dependencies.
    implementation(project(":relay-core"))
}
