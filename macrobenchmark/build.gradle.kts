plugins {
    alias(libs.plugins.android.test)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.macrobenchmark"
    compileSdk = 37
    targetProjectPath = ":app"
    // Run in the harness process, not inside the minified target's classloader.
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("distribution", "play")
    }
    buildTypes {
        create("profileServer") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
}

androidComponents.beforeVariants { variant ->
    variant.enable = variant.buildType == "profileServer"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}

// Never silently time a non-minified target, or let AGP wipe its configured profile.
tasks.matching { it.name == "preProfileServerBuild" || it.name == "connectedProfileServerAndroidTest" }.configureEach {
    val optimized = providers.gradleProperty("tvhplayer.profileMinify").orNull == "true"
    val keepTargetInstalled = providers
        .gradleProperty("android.injected.androidTest.leaveApksInstalledAfterRun").orNull == "true"
    val connected = name.startsWith("connected")
    doFirst {
        require(optimized) { "Pass -Ptvhplayer.profileMinify=true; measurements require the optimized target" }
        require(!connected || keepTargetInstalled) {
            "Pass -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true so the target keeps its server profile"
        }
    }
}
