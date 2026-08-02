plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.add("at.bernhardberger.tvhplayer.htsp.PlaybackIntegrationApi")
    }
}

dependencies {
    api(project(":sdk:domain"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
