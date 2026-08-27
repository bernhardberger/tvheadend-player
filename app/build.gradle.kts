import java.security.MessageDigest
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.paparazzi)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

val releasedSdkSources by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val releasedSdkFfmpegSources by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    implementation(libs.tvheadend.sdk.media3) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    implementation(libs.tvheadend.sdk.android) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }

    listOf("sdk-android", "sdk-core", "sdk-media3", "sdk-playback").forEach { module ->
        add(releasedSdkSources.name, "at.bernhardberger.tvheadend:$module") {
            version { strictly(libs.versions.tvheadend.sdk.get()) }
            artifact {
                classifier = "sources"
                type = "jar"
                extension = "jar"
            }
        }
    }
    add(releasedSdkFfmpegSources.name, "at.bernhardberger.tvheadend:sdk-media3") {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
        artifact {
            classifier = "ffmpeg-sources"
            type = "tar.xz"
            extension = "tar.xz"
        }
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.timber)

    // Presentation API only; the released SDK owns concrete playback and codecs.
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Device-only surface fixture intentionally constructs a concrete player.
    androidTestImplementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

private fun sha256(file: java.io.File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { byte -> "%02x".format(byte) }

private fun executableGradleSource(source: String): String {
    val result = StringBuilder(source.length)
    var index = 0
    var blockCommentDepth = 0
    var state = "code"
    while (index < source.length) {
        when (state) {
            "code" -> when {
                source.startsWith("//", index) -> {
                    result.append("  ")
                    index += 2
                    state = "line-comment"
                }
                source.startsWith("/*", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth = 1
                    state = "block-comment"
                }
                source.startsWith("\"\"\"", index) -> {
                    result.append("   ")
                    index += 3
                    state = "triple-string"
                }
                source[index] == '"' -> {
                    result.append(' ')
                    index++
                    state = "double-string"
                }
                source[index] == '\'' -> {
                    result.append(' ')
                    index++
                    state = "single-string"
                }
                else -> result.append(source[index++])
            }
            "line-comment" -> {
                val character = source[index++]
                result.append(if (character == '\n') '\n' else ' ')
                if (character == '\n') state = "code"
            }
            "block-comment" -> when {
                source.startsWith("/*", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth++
                }
                source.startsWith("*/", index) -> {
                    result.append("  ")
                    index += 2
                    blockCommentDepth--
                    if (blockCommentDepth == 0) state = "code"
                }
                else -> {
                    val character = source[index++]
                    result.append(if (character == '\n') '\n' else ' ')
                }
            }
            "triple-string" -> if (source.startsWith("\"\"\"", index)) {
                result.append("   ")
                index += 3
                state = "code"
            } else {
                val character = source[index++]
                result.append(if (character == '\n') '\n' else ' ')
            }
            "double-string", "single-string" -> {
                val delimiter = if (state == "double-string") '"' else '\''
                val character = source[index++]
                result.append(if (character == '\n') '\n' else ' ')
                if (character == '\\' && index < source.length) {
                    val escaped = source[index++]
                    result.append(if (escaped == '\n') '\n' else ' ')
                } else if (character == delimiter) {
                    state = "code"
                }
            }
        }
    }
    return result.toString()
}

val syncReleasedSdkEvidence by tasks.registering(Sync::class) {
    val runtimeClasspath = configurations.named("debugRuntimeClasspath")
    val media3Aar = providers.provider {
        runtimeClasspath.get().incoming.artifacts.artifacts.single { artifact ->
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier
            id?.group == "at.bernhardberger.tvheadend" && id.module == "sdk-media3"
        }.file
    }
    from(media3Aar)
    from(releasedSdkSources)
    from(releasedSdkFfmpegSources)
    into(layout.buildDirectory.dir("released-sdk-evidence"))

    doLast {
        val version = libs.versions.tvheadend.sdk.get()
        val expected = setOf(
            "sdk-android-$version-sources.jar",
            "sdk-core-$version-sources.jar",
            "sdk-media3-$version-sources.jar",
            "sdk-media3-$version.aar",
            "sdk-media3-$version-ffmpeg-sources.tar.xz",
            "sdk-playback-$version-sources.jar",
        )
        val actual = destinationDir.listFiles().orEmpty().filter { it.isFile }.mapTo(linkedSetOf()) { it.name }
        check(actual == expected) { "Released SDK evidence files $actual do not match $expected" }
    }
}

tasks.register("verifyExternalSdkConsumption") {
    group = "verification"
    description = "Proves the app consumes only the byte-pinned public TVHeadend SDK release."
    dependsOn("assembleDebug", syncReleasedSdkEvidence)

    doLast {
        val sdkGroup = "at.bernhardberger.tvheadend"
        val sdkVersion = libs.versions.tvheadend.sdk.get()
        val productionBucket = Regex(
            "^(api|compileOnly|implementation|runtimeOnly|" +
                "debug(Api|CompileOnly|Implementation|RuntimeOnly)|" +
                "release(Api|CompileOnly|Implementation|RuntimeOnly))$",
        )
        val productionConfigurations = configurations.filter { productionBucket.matches(it.name) }
        val directSdkDependencies = productionConfigurations.flatMap { configuration ->
            configuration.dependencies.mapNotNull { dependency ->
                (dependency as? ExternalModuleDependency)
                    ?.takeIf { it.group == sdkGroup }
                    ?.let {
                        val strictVersion = it.versionConstraint.strictVersion
                        "${configuration.name}:${it.name}:$strictVersion"
                    }
            }
        }.toSet()
        val expectedDirectSdkDependencies = setOf(
            "implementation:sdk-android:$sdkVersion",
            "implementation:sdk-media3:$sdkVersion",
        )
        check(directSdkDependencies == expectedDirectSdkDependencies) {
            "App SDK declarations $directSdkDependencies do not match $expectedDirectSdkDependencies"
        }

        val declarationBucket = Regex("^(api|compileOnly|implementation|runtimeOnly|.*(Api|CompileOnly|Implementation|RuntimeOnly))$")
        val forbiddenLocalDependencies = configurations
            .filter { declarationBucket.matches(it.name) }
            .flatMap { configuration ->
                configuration.dependencies.mapNotNull { dependency ->
                    when (dependency) {
                        is ProjectDependency -> "${configuration.name}:project:${dependency.path}"
                        is FileCollectionDependency -> dependency.files.files
                            .filterNot { it.name in setOf("android.jar", "core-for-system-modules.jar") }
                            .takeIf { it.isNotEmpty() }
                            ?.let { "${configuration.name}:files:$it" }
                        else -> null
                    }
                }
            }
        check(forbiddenLocalDependencies.isEmpty()) {
            "App dependencies contain local fallbacks: $forbiddenLocalDependencies"
        }
        check(gradle.includedBuilds.isEmpty()) { "The app must not use included builds" }
        check(rootProject.allprojects.map { it.path }.toSet() == setOf(":", ":app")) {
            "Unexpected Gradle projects: ${rootProject.allprojects.map { it.path }}"
        }

        val gradleSources = listOf(
            rootProject.file("settings.gradle.kts"),
            rootProject.file("build.gradle.kts"),
            project.file("build.gradle.kts"),
        )
        val forbiddenFallbacks = linkedMapOf(
            "mavenLocal" to Regex("""\bmavenLocal\b"""),
            "flatDir" to Regex("""\bflatDir\b"""),
            "included build" to Regex("""\bincludeBuild\b"""),
            "dependency substitution" to Regex("""\bdependencySubstitution\b"""),
            "local Maven bridge" to Regex("""local-maven|build/local-maven"""),
            "sibling repository" to Regex("""\.\./tvheadend|tvheadend-player-sdk"""),
        )
        val fallbackViolations = gradleSources.flatMap { source ->
            forbiddenFallbacks.filterValues { it.containsMatchIn(executableGradleSource(source.readText())) }
                .keys.map { "${source.relativeTo(rootDir).invariantSeparatorsPath}:$it" }
        }
        check(fallbackViolations.isEmpty()) { "Gradle source contains SDK fallbacks: $fallbackViolations" }

        val settingsText = rootProject.file("settings.gradle.kts").readText()
        val customRepositoryTokens = linkedMapOf(
            "custom Maven repository" to Regex("""\bmaven\s*\{"""),
            "Ivy repository" to Regex("""\bivy\b"""),
            "repository URL" to Regex("""\burl\s*="""),
            "exclusive repository" to Regex("""\bexclusiveContent\b"""),
        ).filterValues { it.containsMatchIn(settingsText) }.keys
        check(customRepositoryTokens.isEmpty()) {
            "settings.gradle.kts contains non-public repository routing: $customRepositoryTokens"
        }
        check("google()" in settingsText && "mavenCentral()" in settingsText) {
            "settings.gradle.kts must resolve only through the declared public repositories"
        }

        val forbiddenSdkOwnedPaths = listOf(
            "sdk",
            "app/libs/README.md",
            "app/libs/lib-decoder-ffmpeg-release.aar",
            "app/libs/native-dependencies.json",
            "app/libs/licenses",
            "app/libs/patches",
            "tools/build-media3-ffmpeg",
        ).filter { rootProject.file(it).exists() }
        check(forbiddenSdkOwnedPaths.isEmpty()) { "Duplicate SDK-owned paths: $forbiddenSdkOwnedPaths" }

        val predecessorImport = Regex(
            "(?m)^import at\\.bernhardberger\\.tvheadend\\.(client|core|playback)(\\.|$)",
        )
        val invalidImports = listOf("src/main", "src/test", "src/androidTest")
            .flatMap { sourceRoot ->
                file(sourceRoot).walkTopDown().filter { it.isFile && it.extension == "kt" }
                    .filter { predecessorImport.containsMatchIn(it.readText()) }
                    .map { it.relativeTo(projectDir).invariantSeparatorsPath }
                    .toList()
            }
        check(invalidImports.isEmpty()) { "App sources retain predecessor imports: $invalidImports" }

        val runtimeClasspath = configurations.getByName("debugRuntimeClasspath")
        val components = runtimeClasspath.incoming.resolutionResult.allComponents
        val projectSdkComponents = components.mapNotNull { it.id as? ProjectComponentIdentifier }
            .filter { it.projectPath.startsWith(":sdk") }
        check(projectSdkComponents.isEmpty()) { "Runtime contains project SDK components: $projectSdkComponents" }

        val resolvedTvheadendModules = components.mapNotNull { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
            if (id.group != sdkGroup) return@mapNotNull null
            id.module to id.version
        }.toMap()
        val expectedTvheadendModules = mapOf(
            "htsp" to "0.7.0",
            "sdk-android" to sdkVersion,
            "sdk-core" to sdkVersion,
            "sdk-media3" to sdkVersion,
            "sdk-playback" to sdkVersion,
        )
        check(resolvedTvheadendModules == expectedTvheadendModules) {
            "Runtime TVHeadend graph $resolvedTvheadendModules does not match $expectedTvheadendModules"
        }

        val media3Versions = components.mapNotNull { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
            id.version.takeIf { id.group == "androidx.media3" }
        }.toSet()
        check(media3Versions == setOf("1.11.0")) { "Runtime Media3 versions are $media3Versions" }
        val coilVersions = components.mapNotNull { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
            id.version.takeIf { id.group.startsWith("io.coil-kt.coil3") }
        }.toSet()
        check(coilVersions == setOf("3.5.0")) { "Runtime Coil versions are $coilVersions" }
        val dataStoreVersions = components.mapNotNull { component ->
            val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
            id.version.takeIf { id.group == "androidx.datastore" }
        }.toSet()
        check(dataStoreVersions == setOf("1.2.1")) { "Runtime DataStore versions are $dataStoreVersions" }

        val sdkArtifacts = runtimeClasspath.incoming.artifacts.artifacts.mapNotNull { artifact ->
            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
            if (id.group != sdkGroup || id.module == "htsp") return@mapNotNull null
            id.module to artifact.file
        }.toMap()
        val expectedExtensions = mapOf(
            "sdk-android" to "aar",
            "sdk-core" to "jar",
            "sdk-media3" to "aar",
            "sdk-playback" to "jar",
        )
        check(sdkArtifacts.mapValues { it.value.extension } == expectedExtensions) {
            "Runtime SDK artifact types ${sdkArtifacts.mapValues { it.value.extension }} do not match $expectedExtensions"
        }
        val expectedHashes = mapOf(
            "sdk-android" to "44d6ded2c59b4d8c025c56094b19b112f9fc5d9ee9757c1e23b5c2fa00bddbab",
            "sdk-core" to "a53558eb153eeaaab8fc513de0b1a369cd5631503977eb64d35a8078a171bd63",
            "sdk-media3" to "79e1db44d3db3f778ff68619a37c153fe6637bf0b0200beb077d55ad31a7c5c9",
            "sdk-playback" to "fcb9e62e7076a71448cafc127ffa212076a3934d9665d468320388d191abc942",
        )
        val resolvedHashes = sdkArtifacts.mapValues { sha256(it.value) }
        check(resolvedHashes == expectedHashes) {
            "Runtime SDK hashes $resolvedHashes do not match the released bytes"
        }

        val evidenceDirectory = layout.buildDirectory.dir("released-sdk-evidence").get().asFile
        val ffmpegSources = evidenceDirectory.resolve("sdk-media3-$sdkVersion-ffmpeg-sources.tar.xz")
        check(sha256(ffmpegSources) == "9eeca8490f794574185986c0df7800d65ccca2980f57dc26b630a398581d7929") {
            "Resolved FFmpeg corresponding source does not match SDK 0.2.0 release evidence"
        }
    }
}
