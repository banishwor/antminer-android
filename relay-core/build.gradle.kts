plugins {
    alias(libs.plugins.android.library)
}

// Relay Lite Step 2 — the shared relay core.
//
// Everything the 24/7 relay does that is NOT the rich dashboard UI lives
// here: miner/domain models, the Antminer CGI client + parsers, the cloud
// sync client (Apps Script webhook + sheet row mapping), worker-identity
// resolution, auto-connect classification, the cross-user upload guard,
// and the RelayEngine orchestration facade. :app depends on this module;
// the future :relay-lite app (API 22, no Compose) will depend on it too.
//
// API 22 is the floor (Gate A, verified on the Vivo Y21L): the core's own
// code sticks to framework + Kotlin APIs available at 22. OkHttp 4.12 and
// kotlinx-coroutines both support API 21+. The one API 23+ piece — the
// Android Keystore AES-GCM credential store — stays in :app behind the
// RelayStorage interface, so the lite app can plug in its own API 22
// vault implementation later without touching the engine.
android {
    namespace = "com.example.antminerconnectiontest.relaycore"
    compileSdk = 36

    defaultConfig {
        minSdk = 22
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(17)
}


dependencies {
    // api: the core's public surface exposes OkHttp (CloudSyncManager,
    // AntminerClient) and suspend functions, so consumers need both on
    // their compile classpath.
    api(libs.okhttp)
    api(libs.okhttp.digest)
    api(libs.kotlinx.coroutines.android)

    // Local tests: jUnit, coroutines, JVM org.json (same as :app).
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20231013")
}
