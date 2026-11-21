plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.touch"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer"
        minSdk = 28
        targetSdk = 36
    }
    buildTypes.getByName("debug").applicationIdSuffix = ".touch.debug"
    buildFeatures.compose = true
    testOptions.unitTests.isIncludeAndroidResources = true
}

androidComponents.beforeVariants { variant ->
    variant.enable = variant.buildType == "debug"
}

tasks.withType<Test>().configureEach {
    val captureDir = layout.buildDirectory.dir("captures").get().asFile
    outputs.dir(captureDir)
    systemProperty("touch.captureDir", captureDir.absolutePath)
}

dependencies {
    implementation(project(":brand"))
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ComponentActivity for screen tests that compose a single screen on fixture state.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
