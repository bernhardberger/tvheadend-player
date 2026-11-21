plugins {
    alias(libs.plugins.android.library)
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
