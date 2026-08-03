import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.kotlin.dsl.implementation

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
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation("at.bernhardberger.tvheadend:core:0.1.0-SNAPSHOT")
    implementation("at.bernhardberger.tvheadend:client-htsp:0.1.0-SNAPSHOT")
    implementation("at.bernhardberger.tvheadend:playback-media3:0.1.0-SNAPSHOT")

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

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.timber)

    // Media3 presentation API; concrete playback and codecs are SDK-owned.
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented / Compose UI tests (run on a device)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Device-only surface lifecycle fixture intentionally constructs a concrete player.
    androidTestImplementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("verifyExternalSdkConsumption") {
    group = "verification"
    description = "Proves the app consumes only the guarded external TVHeadend SDK coordinates."
    dependsOn("assembleDebug")

    doLast {
        val sdkGroup = "at.bernhardberger.tvheadend"
        val sdkVersion = "0.1.0-SNAPSHOT"
        val expectedDirectSdkDependencies = setOf(
            "implementation:$sdkGroup:client-htsp:$sdkVersion",
            "implementation:$sdkGroup:core:$sdkVersion",
            "implementation:$sdkGroup:playback-media3:$sdkVersion",
        )
        val productionBucket = Regex(
            "^(api|compileOnly|implementation|runtimeOnly|" +
                "debug(Api|CompileOnly|Implementation|RuntimeOnly)|" +
                "release(Api|CompileOnly|Implementation|RuntimeOnly))$",
        )
        val productionConfigurations = configurations
            .filter { configuration -> productionBucket.matches(configuration.name) }
        val dependencyDeclarationBucket = Regex(
            "^(api|compileOnly|implementation|runtimeOnly|" +
                ".*(Api|CompileOnly|Implementation|RuntimeOnly))$",
        )
        val dependencyDeclarationConfigurations = configurations
            .filter { configuration -> dependencyDeclarationBucket.matches(configuration.name) }
        val directSdkDependencies = productionConfigurations
            .flatMap { configuration ->
                configuration.dependencies.mapNotNull { dependency ->
                    (dependency as? ExternalModuleDependency)
                        ?.takeIf { it.group == sdkGroup }
                        ?.let {
                            "${configuration.name}:${it.group}:${it.name}:${it.version}"
                        }
                }
            }
            .toSet()
        check(directSdkDependencies == expectedDirectSdkDependencies) {
            "App SDK declarations $directSdkDependencies do not match " +
                expectedDirectSdkDependencies
        }

        val forbiddenLocalDependencies = dependencyDeclarationConfigurations.flatMap { configuration ->
            configuration.dependencies.mapNotNull { dependency ->
                when (dependency) {
                    is ProjectDependency ->
                        "${configuration.name}:project:${dependency.path}"
                    is FileCollectionDependency -> {
                        val nonPlatformFiles = dependency.files.files.filterNot { file ->
                            file.name in setOf("android.jar", "core-for-system-modules.jar")
                        }
                        nonPlatformFiles.takeIf { it.isNotEmpty() }
                            ?.let { "${configuration.name}:files:$it" }
                    }
                    else -> null
                }
            }
        }
        check(forbiddenLocalDependencies.isEmpty()) {
            "App dependencies contain local fallbacks: $forbiddenLocalDependencies"
        }
        check(gradle.includedBuilds.isEmpty()) {
            "The app must not use included builds or composite dependency substitution"
        }

        val settingsText = rootProject.file("settings.gradle.kts").readText()
        listOf(
            "mavenLocal(" to "mavenLocal",
            "flatDir" to "flatDir",
            "includeBuild(" to "included build",
            "dependencySubstitution" to "dependency substitution",
        ).forEach { (token, label) ->
            check(token !in settingsText) { "settings.gradle.kts contains forbidden $label" }
        }
        val absoluteFileReference = Regex(
            """(?:uri|file)\(\s*["'](?:/|[A-Za-z]:[\\/])""",
        )
        check(!absoluteFileReference.containsMatchIn(settingsText)) {
            "settings.gradle.kts contains an absolute file reference"
        }
        check("../tvheadend-player-sdk/build/local-maven" in settingsText) {
            "settings.gradle.kts must use the portable sibling SDK repository bridge"
        }
        check("exclusiveContent" in settingsText && "includeGroup(\"$sdkGroup\")" in settingsText) {
            "The TVHeadend SDK group must be exclusive to the sibling repository"
        }
        listOf(
            "include(\":sdk:domain\")",
            "include(\":sdk:htsp\")",
            "include(\":sdk:htsp-consumer-contract\")",
            "include(\":sdk:playback-media3\")",
            "include(\":sdk:playback-consumer-contract\")",
            "include(\":sdk:decoder-ffmpeg-binary\")",
        ).forEach { declaration ->
            check(declaration in settingsText) {
                "Phase 6A must retain the in-tree declaration $declaration"
            }
        }

        val excludedDirtySources = setOf(
            "src/test/java/at/bernhardberger/tvhplayer/ui/player/PlayerHumanReviewArtifact.kt",
            "src/test/java/at/bernhardberger/tvhplayer/ui/player/PlayerUxJourneyEvidenceContract.kt",
            "src/test/java/at/bernhardberger/tvhplayer/ui/player/PlayerUxJourneyEvidenceContractTest.kt",
            "src/test/java/at/bernhardberger/tvhplayer/ui/player/PlayerVisualEvidenceTest.kt",
        )
        val formerSdkImport = Regex(
            "(?m)^import at\\.bernhardberger\\.tvhplayer\\.(htsp|player|repositories)\\.",
        )
        val rawSdkLoggerImport = Regex(
            "(?m)^import at\\.bernhardberger\\.tvheadend\\.client\\.(HtspLogger|HtspLogLevel)\\b",
        )
        val forbiddenIntegrationSurface = Regex(
            "at\\.bernhardberger\\.tvheadend\\.client\\." +
                "(HtspConnectionAttemptStatus|HtspEvent|HtspMessage|HtspMuxEvent|" +
                "PlaybackHtspTransport|PlaybackIntegrationApi|PlaybackSubscriptionStart)\\b|" +
                "at\\.bernhardberger\\.tvheadend\\.(client|playback)\\." +
                "(internal|implementation)\\.|" +
                "androidx\\.media3\\.(datasource|extractor|exoplayer\\." +
                "(audio|source|upstream|video))\\.|\\.playbackTransport\\b",
        )
        val invalidSourceImports = listOf("src/main", "src/test", "src/androidTest")
            .flatMap { sourceRoot ->
                file(sourceRoot).walkTopDown()
                    .filter(File::isFile)
                    .filter { it.extension == "kt" }
                    .mapNotNull { source ->
                        val relative = source.relativeTo(projectDir).invariantSeparatorsPath
                        if (relative in excludedDirtySources) return@mapNotNull null
                        val text = source.readText()
                        when {
                            formerSdkImport.containsMatchIn(text) -> "$relative: former SDK package"
                            rawSdkLoggerImport.containsMatchIn(text) ->
                                "$relative: raw SDK logger callback"
                            forbiddenIntegrationSurface.containsMatchIn(text) ->
                                "$relative: playback-integration implementation API"
                            else -> null
                        }
                    }
                    .toList()
            }
        check(invalidSourceImports.isEmpty()) {
            "App sources bypass the supported SDK facade: $invalidSourceImports"
        }

        val runtimeClasspath = configurations.getByName("debugRuntimeClasspath")
        val runtimeComponents = runtimeClasspath.incoming.resolutionResult.allComponents
        val inTreeSdkComponents = runtimeComponents
            .mapNotNull { component -> component.id as? ProjectComponentIdentifier }
            .filter { identifier -> identifier.projectPath.startsWith(":sdk:") }
            .map { identifier -> identifier.displayName }
        check(inTreeSdkComponents.isEmpty()) {
            "The app runtime still contains in-tree SDK projects: $inTreeSdkComponents"
        }
        val resolvedSdkModules = runtimeComponents
            .mapNotNull { component ->
                val identifier = component.id as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                if (identifier.group != sdkGroup) return@mapNotNull null
                identifier.module to identifier.version
            }
            .toMap()
        val expectedSdkModules = mapOf(
            "client-htsp" to sdkVersion,
            "core" to sdkVersion,
            "decoder-ffmpeg" to sdkVersion,
            "playback-media3" to sdkVersion,
        )
        check(resolvedSdkModules == expectedSdkModules) {
            "App runtime SDK graph $resolvedSdkModules does not match $expectedSdkModules"
        }
        val resolvedSdkArtifacts = runtimeClasspath.incoming.artifacts.artifacts
            .mapNotNull { artifact ->
                val identifier = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                if (identifier.group != sdkGroup) return@mapNotNull null
                identifier.module to artifact.file.extension
            }
            .toMap()
        val expectedSdkArtifacts = mapOf(
            "client-htsp" to "jar",
            "core" to "jar",
            "decoder-ffmpeg" to "aar",
            "playback-media3" to "aar",
        )
        check(resolvedSdkArtifacts == expectedSdkArtifacts) {
            "App runtime SDK artifacts $resolvedSdkArtifacts do not match $expectedSdkArtifacts"
        }

        val siblingSdkRoot = rootDir.resolve("../tvheadend-player-sdk").canonicalFile
        val localSdkRepository = siblingSdkRoot.resolve("build/local-maven").canonicalFile
        check(localSdkRepository.isDirectory) {
            "Missing sibling SDK local repository: $localSdkRepository"
        }
        val decoderVersionDirectory = localSdkRepository
            .resolve("at/bernhardberger/tvheadend/decoder-ffmpeg/$sdkVersion")
        val decoderAars = decoderVersionDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension == "aar" }
        check(decoderAars.size == 1) {
            "Expected one published decoder AAR, found $decoderAars"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(decoderAars.single().readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        val expectedDecoderSha256 =
            "1716bd964aa4ac3e7cd868ee036161f8d5cfa47fe6564a3d57b3b8723ab3f2e0"
        check(digest == expectedDecoderSha256) {
            "Published decoder AAR SHA-256 $digest does not match $expectedDecoderSha256"
        }

        val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val apks = apkDirectory.listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.extension == "apk" }
        check(apks.size == 1) { "Expected one debug APK, found $apks" }
        val requiredNativeEntries = setOf(
            "lib/arm64-v8a/libffmpegJNI.so",
            "lib/armeabi-v7a/libffmpegJNI.so",
            "lib/x86/libffmpegJNI.so",
            "lib/x86_64/libffmpegJNI.so",
        )
        ZipFile(apks.single()).use { archive ->
            val packagedNativeEntries = archive.entries().asSequence()
                .map { entry -> entry.name }
                .filterTo(linkedSetOf()) { name -> name.endsWith("/libffmpegJNI.so") }
            check(packagedNativeEntries == requiredNativeEntries) {
                "APK FFmpeg entries $packagedNativeEntries do not match $requiredNativeEntries"
            }
        }
    }
}
