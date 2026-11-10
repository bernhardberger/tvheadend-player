import java.time.Duration
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

val defaultUpdateFeed = "https://api.github.com/repos/bernhardberger/tvheadend-player/releases/latest"
val updateFeed = providers.gradleProperty("tvhplayer.applianceUpdateFeed").getOrElse(defaultUpdateFeed)
val updateFeedUri = URI(updateFeed)
require(updateFeedUri.userInfo == null && updateFeedUri.fragment == null &&
    (updateFeedUri.scheme == "https" ||
        (updateFeedUri.scheme == "http" && updateFeedUri.host in setOf("127.0.0.1", "10.0.2.2")))) {
    "Appliance update feed must use HTTPS or a debug loopback HTTP URL"
}
val updateFeedLiteral = "\"${updateFeed.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "at.bernhardberger.tvhplayer.appliance"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer.appliance"
        minSdk = 28
        targetSdk = 36
        versionCode = 1003
        versionName = "0.2.2"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "PLAYER_PACKAGE", "\"at.bernhardberger.tvhplayer.debug\"")
            manifestPlaceholders["playerPackage"] = "at.bernhardberger.tvhplayer.debug"
            buildConfigField("String", "UPDATE_FEED", updateFeedLiteral)
            manifestPlaceholders["updateCleartext"] = (updateFeedUri.scheme == "http").toString()
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("String", "PLAYER_PACKAGE", "\"at.bernhardberger.tvhplayer\"")
            manifestPlaceholders["playerPackage"] = "at.bernhardberger.tvhplayer"
            // A cleartext local fixture feed can never leak into a release artifact.
            buildConfigField("String", "UPDATE_FEED", if (updateFeedUri.scheme == "https") updateFeedLiteral else "\"$defaultUpdateFeed\"")
            manifestPlaceholders["updateCleartext"] = "false"
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(15))
}

dependencies {
    implementation(project(":brand"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.tv.material)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
