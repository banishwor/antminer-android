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

    buildFeatures {
        // AGP 9 generates no BuildConfig unless asked; the header shows
        // the probe's own version from it.
        buildConfig = true
    }
}

// No dependencies block on purpose: the probe must run on the bare framework.
//
// AGP 9's built-in Kotlin still drags kotlin-stdlib onto the classpath of
// every module it touches (observed: 3 dex files, ~890 KB APK for a
// framework-only probe). android.builtInKotlin=false cannot help here —
// Gradle reads only the ROOT gradle.properties, and flipping it there would
// change :app's build too. Scoped to this module instead: exclude the
// stdlib. The probe is pure Java and references no kotlin.* classes, so
// nothing misses it.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
}
