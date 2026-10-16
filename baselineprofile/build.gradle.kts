plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.baselineprofile"
    compileSdk = 37
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "play")
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}

// Without this flag AGP uninstalls the target after the run, wiping its stored server profile.
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    // A local Boolean keeps the action free of script references for the configuration cache.
    val keepTargetInstalled = providers
        .gradleProperty("android.injected.androidTest.leaveApksInstalledAfterRun").orNull == "true"
    doFirst {
        require(keepTargetInstalled) {
            "Pass -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true so the target keeps its server profile"
        }
    }
}
