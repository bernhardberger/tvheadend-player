plugins {
    alias(libs.plugins.android.library)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("upstream/libraries/decoder_ffmpeg/proguard-rules.txt")
    }
    sourceSets.getByName("main") {
        manifest.srcFile("upstream/libraries/decoder_ffmpeg/src/main/AndroidManifest.xml")
        java.srcDir("upstream/libraries/decoder_ffmpeg/src/main/java")
    }
    packaging.jniLibs.keepDebugSymbols += "**/libffmpegJNI.so"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Retain upstream source verbatim, but do not compile its experimental video renderer.
tasks.withType<JavaCompile>().configureEach {
    exclude("**/ExperimentalFfmpegVideoRenderer.java")
}

dependencies {
    // Upstream 1.11.1 module/common build convention annotation dependencies.
    implementation("androidx.annotation:annotation:1.6.0")
    compileOnly("org.checkerframework:checker-qual:3.13.0")
    api("androidx.media3:media3-decoder:${libs.versions.media3.get()}") {
        version { strictly(libs.versions.media3.get()) }
    }
    implementation(libs.androidx.media3.exoplayer) {
        version { strictly(libs.versions.media3.get()) }
    }
}
