import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.appliance"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer.appliance"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "PLAYER_PACKAGE", "\"at.bernhardberger.tvhplayer.debug\"")
            manifestPlaceholders["playerPackage"] = "at.bernhardberger.tvhplayer.debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            buildConfigField("String", "PLAYER_PACKAGE", "\"at.bernhardberger.tvhplayer\"")
            manifestPlaceholders["playerPackage"] = "at.bernhardberger.tvhplayer"
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
}
