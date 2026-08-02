import java.io.File
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer.sdk.playback.consumer"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer.sdk.playback.consumer"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
}

dependencies {
    implementation(project(":sdk:playback-media3"))

    testImplementation(libs.junit)
    testImplementation(kotlin("compiler-embeddable"))
}

tasks.register("verifyDebugPlaybackConsumerContract") {
    group = "verification"
    description = "Proves the standalone playback consumer and app package audited FFmpeg."
    dependsOn("assembleDebug", ":app:assembleDebug")

    doLast {
        val requiredAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val requiredNativeEntries = requiredAbis.mapTo(linkedSetOf()) { abi ->
            "lib/$abi/libffmpegJNI.so"
        }
        val ffmpegRuntimeClass =
            "androidx/media3/decoder/ffmpeg/FfmpegAudioRenderer".toByteArray()

        fun ByteArray.containsBytes(needle: ByteArray): Boolean {
            if (needle.isEmpty()) return true
            for (start in 0..size - needle.size) {
                var matches = true
                for (offset in needle.indices) {
                    if (this[start + offset] != needle[offset]) {
                        matches = false
                        break
                    }
                }
                if (matches) return true
            }
            return false
        }

        fun singleDebugApk(directory: File, label: String): File {
            val apks = directory.listFiles()
                .orEmpty()
                .filter { file -> file.isFile && file.extension == "apk" }
            check(apks.size == 1) {
                "$label must produce exactly one debug APK in $directory, found ${apks.size}"
            }
            return apks.single()
        }

        fun verifyApk(apk: File, label: String) {
            check(apk.isFile) { "$label APK does not exist: $apk" }
            ZipFile(apk).use { archive ->
                val entries = archive.entries().asSequence().toList()
                val packagedNativeEntries = entries
                    .map { entry -> entry.name }
                    .filterTo(linkedSetOf()) { name -> name.endsWith("/libffmpegJNI.so") }
                check(packagedNativeEntries == requiredNativeEntries) {
                    "$label FFmpeg JNI entries $packagedNativeEntries do not match " +
                        requiredNativeEntries
                }
                val extensionAvailable = entries
                    .filter { entry -> entry.name.endsWith(".dex") }
                    .any { entry ->
                        archive.getInputStream(entry).use { input ->
                            input.readBytes().containsBytes(ffmpegRuntimeClass)
                        }
                    }
                check(extensionAvailable) {
                    "$label does not contain the FFmpeg Media3 runtime extension"
                }
            }
        }

        val directImplementationNames = configurations
            .getByName("implementation")
            .dependencies
            .map { dependency -> dependency.name }
        check("playback-media3" in directImplementationNames) {
            "The consumer contract must depend directly on :sdk:playback-media3"
        }
        check(directImplementationNames.none { name -> name.contains("ffmpeg", ignoreCase = true) }) {
            "The consumer contract must not directly depend on the FFmpeg binary"
        }
        check(
            configurations.getByName("runtimeOnly").dependencies.none { dependency ->
                dependency.name.contains("ffmpeg", ignoreCase = true)
            }
        ) {
            "The consumer contract must receive FFmpeg only through :sdk:playback-media3"
        }

        verifyApk(
            apk = singleDebugApk(
                layout.buildDirectory.dir("outputs/apk/debug").get().asFile,
                "standalone playback consumer",
            ),
            label = "standalone playback consumer",
        )
        verifyApk(
            apk = singleDebugApk(
                rootProject.layout.projectDirectory.dir("app/build/outputs/apk/debug").asFile,
                "app",
            ),
            label = "app",
        )
    }
}
