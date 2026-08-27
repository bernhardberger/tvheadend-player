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

        val allDeclaredSdkDependencies = dependencyDeclarationConfigurations
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
        check(allDeclaredSdkDependencies == expectedDirectSdkDependencies) {
            "App production and test SDK declarations $allDeclaredSdkDependencies do not " +
                "match $expectedDirectSdkDependencies"
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

        val settingsFile = rootProject.file("settings.gradle.kts")
        val settingsText = settingsFile.readText()
        fun tokenizedGradleSource(text: String): Pair<String, List<String>> {
            val code = StringBuilder(text.length)
            val strings = mutableListOf<String>()
            var index = 0

            fun appendStringToken(value: String) {
                code.append("__GRADLE_STRING_")
                    .append(strings.size)
                    .append("__")
                strings += value
            }

            while (index < text.length) {
                when {
                    text.startsWith("//", index) -> {
                        code.append(' ')
                        index += 2
                        while (index < text.length && text[index] != '\n') index++
                    }
                    text.startsWith("/*", index) -> {
                        code.append(' ')
                        index += 2
                        var depth = 1
                        while (index < text.length && depth > 0) {
                            when {
                                text.startsWith("/*", index) -> {
                                    depth++
                                    index += 2
                                }
                                text.startsWith("*/", index) -> {
                                    depth--
                                    index += 2
                                }
                                else -> index++
                            }
                        }
                    }
                    text.startsWith("\"\"\"", index) -> {
                        index += 3
                        val end = text.indexOf("\"\"\"", index)
                        check(end >= 0) { "settings.gradle.kts contains an unterminated string" }
                        appendStringToken(text.substring(index, end))
                        index = end + 3
                    }
                    text[index] == '"' || text[index] == '\'' -> {
                        val quote = text[index++]
                        val value = StringBuilder()
                        var closed = false
                        while (index < text.length) {
                            val character = text[index++]
                            if (character == '\\' && index < text.length) {
                                value.append(character).append(text[index++])
                            } else if (character == quote) {
                                closed = true
                                break
                            } else {
                                value.append(character)
                            }
                        }
                        check(closed) { "settings.gradle.kts contains an unterminated string" }
                        appendStringToken(value.toString())
                    }
                    else -> code.append(text[index++])
                }
            }
            return code.toString() to strings
        }
        fun settingsRepositoryBoundaryErrors(text: String): List<String> {
            val (code, strings) = tokenizedGradleSource(text)
            val errors = mutableListOf<String>()
            val requiredRepository = "../tvheadend-player-sdk/build/local-maven"
            val requiredName = "tvheadendPlayerSdkLocal"
            val requiredGroup = sdkGroup
            val stringToken = Regex("__GRADLE_STRING_(\\d+)__")
            fun valuesFor(pattern: Regex): List<String> = pattern.findAll(code)
                .mapNotNull { match ->
                    val token = match.groups[1]?.value ?: return@mapNotNull null
                    val tokenMatch = stringToken.matchEntire(token) ?: return@mapNotNull null
                    strings.getOrNull(tokenMatch.groupValues[1].toInt())
                }
                .toList()

            val repositoryUrls = valuesFor(
                Regex("""\burl\s*=\s*uri\s*\(\s*(__GRADLE_STRING_\d+__)\s*\)"""),
            )
            if (repositoryUrls != listOf(requiredRepository)) {
                errors += "custom Maven repository URLs $repositoryUrls do not match the sibling SDK bridge"
            }
            val customMavenBlockCount = Regex("""\bmaven\s*\{""").findAll(code).count()
            if (customMavenBlockCount != 1) {
                errors += "expected exactly one custom Maven repository block, found $customMavenBlockCount"
            }
            val repositoryUrlAssignmentCount = Regex("""\burl\s*=""").findAll(code).count()
            if (repositoryUrlAssignmentCount != 1) {
                errors += "expected exactly one repository URL assignment, found $repositoryUrlAssignmentCount"
            }
            val unsupportedCustomRepositoryPatterns = linkedMapOf(
                "maven function" to Regex("""\bmaven\s*\("""),
                "Ivy block" to Regex("""\bivy\s*\{"""),
                "Ivy function" to Regex("""\bivy\s*\("""),
                "setUrl" to Regex("""\bsetUrl\s*\("""),
                "url function" to Regex("""\burl\s*\("""),
                "artifact URL" to Regex("""\b(?:artifactUrls|setArtifactUrls)\b"""),
                "repository collection mutation" to Regex(
                    """\brepositories\s*\.\s*(?:add|addAll)\s*\(""",
                ),
            )
            val unsupportedCustomRepositories = unsupportedCustomRepositoryPatterns
                .filterValues { pattern -> pattern.containsMatchIn(code) }
                .keys
            if (unsupportedCustomRepositories.isNotEmpty()) {
                errors += "unsupported custom repository declarations $unsupportedCustomRepositories"
            }
            val includedGroups = valuesFor(
                Regex("""\bincludeGroup\s*\(\s*(__GRADLE_STRING_\d+__)\s*\)"""),
            )
            if (includedGroups != listOf(requiredGroup)) {
                errors += "exclusive repository groups $includedGroups do not match $requiredGroup"
            }

            fun uniqueToken(value: String): String? {
                val indices = strings.mapIndexedNotNull { candidateIndex, candidate ->
                    candidateIndex.takeIf { candidate == value }
                }
                return indices.singleOrNull()?.let { "__GRADLE_STRING_${it}__" }
            }
            val nameToken = uniqueToken(requiredName)
            val repositoryToken = uniqueToken(requiredRepository)
            val groupToken = uniqueToken(requiredGroup)
            if (nameToken == null || repositoryToken == null || groupToken == null) {
                errors += "required SDK repository strings must each occur exactly once"
            } else {
                val compactCode = code.replace(Regex("\\s+"), "")
                val requiredDeclaration =
                    "exclusiveContent{forRepository{maven{name=$nameToken" +
                        "url=uri($repositoryToken)}}filter{includeGroup($groupToken)}}"
                if (requiredDeclaration !in compactCode) {
                    errors += "the sibling SDK repository is not one executable exclusiveContent declaration"
                }
            }
            return errors
        }
        val forbiddenFallbackPatterns = linkedMapOf(
            "mavenLocal" to Regex("""\bmavenLocal\b"""),
            "flatDir" to Regex("""\bflatDir\b"""),
            "included build" to Regex("""\bincludeBuild\b"""),
            "dependency substitution" to Regex(
                """\b(?:dependencySubstitution|substitute\s*\()""",
            ),
        )
        fun detectedFallbacks(text: String): Set<String> = forbiddenFallbackPatterns
            .filterValues { pattern -> pattern.containsMatchIn(tokenizedGradleSource(text).first) }
            .keys

        val fallbackMutationSamples = mapOf(
            "mavenLocal" to "repositories { mavenLocal /* hidden */ () }",
            "flatDir" to "repositories { flatDir { dirs(\"libs\") } }",
            "included build" to "includeBuild /* hidden */ (\"../sdk\")",
            "dependency substitution" to
                "resolutionStrategy.dependencySubstitution { " +
                "substitute(module(\"example:sdk\")).using(project(\":sdk\")) }",
        )
        fallbackMutationSamples.forEach { (expected, sample) ->
            check(expected in detectedFallbacks(sample)) {
                "Boundary self-test failed to detect synthetic $expected fallback"
            }
        }
        val commentedBridgeWithAlternateRepository = """
            // exclusiveContent includeGroup("at.bernhardberger.tvheadend")
            // url = uri("../tvheadend-player-sdk/build/local-maven")
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("../fallback-sdk/build/local-maven") }
                }
            }
        """.trimIndent()
        check(settingsRepositoryBoundaryErrors(commentedBridgeWithAlternateRepository).isNotEmpty()) {
            "Boundary self-test accepted a comment-only bridge plus alternate local repository"
        }
        val alternateRepositoryMutations = mapOf(
            "relative file URL" to
                "dependencyResolutionManagement { repositories { " +
                "maven { url = file(\"../fallback-sdk/build/local-maven\") } } }",
            "nested relative file URL" to
                "dependencyResolutionManagement { repositories { " +
                "maven { url = uri(file(\"../fallback-sdk/build/local-maven\")) } } }",
            "setUrl" to
                "dependencyResolutionManagement { repositories { " +
                "maven { setUrl(\"../fallback-sdk/build/local-maven\") } } }",
        )
        alternateRepositoryMutations.forEach { (label, mutation) ->
            check(settingsRepositoryBoundaryErrors("$settingsText\n$mutation").isNotEmpty()) {
                "Boundary self-test accepted synthetic $label alternate repository"
            }
        }
        val settingsRepositoryErrors = settingsRepositoryBoundaryErrors(settingsText)
        check(settingsRepositoryErrors.isEmpty()) {
            "settings.gradle.kts violates the external SDK repository boundary: " +
                settingsRepositoryErrors
        }

        val gradleBoundaryFiles = sequenceOf(
            settingsFile,
            rootProject.file("build.gradle.kts"),
            project.file("build.gradle.kts"),
        )
        val detectedGradleFallbacks = gradleBoundaryFiles
            .filter(File::isFile)
            .flatMap { source ->
                detectedFallbacks(source.readText()).map { fallback ->
                    "${source.relativeTo(rootDir).invariantSeparatorsPath}:$fallback"
                }
            }
            .toList()
        check(detectedGradleFallbacks.isEmpty()) {
            "Gradle source contains forbidden SDK repository/composite fallback: " +
                detectedGradleFallbacks
        }
        val absoluteFileReference = Regex(
            """(?:uri|file)\(\s*["'](?:/|[A-Za-z]:[\\/])""",
        )
        check(!absoluteFileReference.containsMatchIn(settingsText)) {
            "settings.gradle.kts contains an absolute file reference"
        }
        val boundaryViolations = mutableListOf<String>()
        val unexpectedProjects = rootProject.allprojects
            .map { candidate -> candidate.path }
            .filterNot { path -> path == ":" || path == ":app" }
        if (unexpectedProjects.isNotEmpty()) {
            boundaryViolations += "unexpected Gradle projects $unexpectedProjects"
        }
        val sdkDeclaration = Regex("""["']:sdk(?::[^"']*)?["']""")
        if (sdkDeclaration.containsMatchIn(settingsText)) {
            boundaryViolations += "settings.gradle.kts retains an in-tree :sdk declaration"
        }
        val forbiddenSdkOwnedPaths = listOf(
            "sdk",
            "app/libs/README.md",
            "app/libs/lib-decoder-ffmpeg-release.aar",
            "app/libs/native-dependencies.json",
            "app/libs/licenses",
            "app/libs/patches",
            "tools/build-media3-ffmpeg",
        )
        val presentSdkOwnedPaths = forbiddenSdkOwnedPaths
            .filter { relative -> rootProject.file(relative).exists() }
        if (presentSdkOwnedPaths.isNotEmpty()) {
            boundaryViolations += "duplicate SDK-owned paths $presentSdkOwnedPaths"
        }
        check(boundaryViolations.isEmpty()) {
            "External SDK boundary violations: ${boundaryViolations.joinToString("; ")}"
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

        fun normalizedJarSha256(file: File): String = ZipFile(file).use { archive ->
            val entries = archive.entries().asSequence().toList()
            val duplicateEntryNames = entries
                .groupingBy { entry -> entry.name }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .sorted()
            check(duplicateEntryNames.isEmpty()) {
                "JAR ${file.name} contains duplicate entries: $duplicateEntryNames"
            }

            val regularEntries = entries
                .filterNot { entry -> entry.isDirectory }
                .sortedBy { entry -> entry.name }
            val entryNames = entries.map { entry -> entry.name }
            val directoriesMasqueradingAsFiles = regularEntries
                .map { entry -> entry.name }
                .filter { regularName ->
                    entryNames.any { entryName -> entryName.startsWith("$regularName/") }
                }
            check(directoriesMasqueradingAsFiles.isEmpty()) {
                "JAR ${file.name} contains directories masquerading as files: " +
                    directoriesMasqueradingAsFiles
            }

            val digest = MessageDigest.getInstance("SHA-256")
            fun updateLength(length: Long) {
                for (shift in 56 downTo 0 step 8) {
                    digest.update((length ushr shift).toByte())
                }
            }
            regularEntries.forEach { entry ->
                val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                val contentBytes = archive.getInputStream(entry).use { input -> input.readBytes() }
                updateLength(nameBytes.size.toLong())
                digest.update(nameBytes)
                updateLength(contentBytes.size.toLong())
                digest.update(contentBytes)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        val expectedJvmArtifactContentSha256 = mapOf(
            "client-htsp" to
                "76d145f0ec4f56ce6777ef0742d13e497ced01ebf1d77e994a3ef4346ae800e1",
            "core" to
                "4b4399e8e3005439790efc60df3eda15bb75bc9ac4ee32ff2810afb25a1bdcdb",
        )
        val expectedRawSdkArtifactSha256 = mapOf(
            "decoder-ffmpeg" to
                "1716bd964aa4ac3e7cd868ee036161f8d5cfa47fe6564a3d57b3b8723ab3f2e0",
            "playback-media3" to
                "7995314ae9852fb902d6d1fdb3527bb267d8eee0887ea692fde3388d753b6deb",
        )
        val resolvedJvmArtifactContentSha256 = runtimeClasspath.incoming.artifacts.artifacts
            .mapNotNull { artifact ->
                val identifier = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                if (identifier.group != sdkGroup ||
                    identifier.module !in expectedJvmArtifactContentSha256
                ) {
                    return@mapNotNull null
                }
                identifier.module to normalizedJarSha256(artifact.file)
            }
            .toMap()
        check(resolvedJvmArtifactContentSha256 == expectedJvmArtifactContentSha256) {
            "App runtime JVM SDK artifact content hashes $resolvedJvmArtifactContentSha256 " +
                "do not match $expectedJvmArtifactContentSha256"
        }
        val resolvedRawSdkArtifactSha256 = runtimeClasspath.incoming.artifacts.artifacts
            .mapNotNull { artifact ->
                val identifier = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                if (identifier.group != sdkGroup ||
                    identifier.module !in expectedRawSdkArtifactSha256
                ) {
                    return@mapNotNull null
                }
                val sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(artifact.file.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                identifier.module to sha256
            }
            .toMap()
        check(resolvedRawSdkArtifactSha256 == expectedRawSdkArtifactSha256) {
            "App runtime raw SDK artifact hashes $resolvedRawSdkArtifactSha256 do not match " +
                expectedRawSdkArtifactSha256
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
