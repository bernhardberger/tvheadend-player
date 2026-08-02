plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("at.bernhardberger.tvhplayer.htsp.PlaybackIntegrationApi")
    }
}

android {
    namespace = "at.bernhardberger.tvhplayer.sdk.playback.media3"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    api(project(":sdk:domain"))
    api(project(":sdk:htsp"))
    api(libs.androidx.media3.common)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(project(":sdk:decoder-ffmpeg-binary"))

    testImplementation(libs.junit)
}
