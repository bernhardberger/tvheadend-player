plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.presentation"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    buildFeatures.compose = true
    testOptions.unitTests.isIncludeAndroidResources = true

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        create("profile") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
        }
        create("profileServer") {
            initWith(getByName("profile"))
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    implementation(project(":client"))
    implementation(libs.tvheadend.sdk.android) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.runtime:runtime-saveable")
    implementation("androidx.lifecycle:lifecycle-viewmodel:${libs.versions.lifecycle.get()}")
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
}
