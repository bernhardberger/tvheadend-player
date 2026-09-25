plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// UI-free client layer: TVHeadend runtime, settings persistence and pure domain policy.
// No Compose, Material, androidx.tv, Koin, navigation, Coil, palette, app resources or views.
// See docs/client-module.md.

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.client"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        buildConfigField("boolean", "PROFILE_TRACE", "false")
    }

    // Mirrors the app build types so moved profiling code keeps the app variant's PROFILE_TRACE.
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        create("profile") {
            initWith(getByName("release"))
            buildConfigField("boolean", "PROFILE_TRACE", "true")
            matchingFallbacks += "release"
        }
        create("profileServer") {
            initWith(getByName("profile"))
        }
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.tvheadend.sdk.media3) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    implementation(libs.tvheadend.sdk.android) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
}
