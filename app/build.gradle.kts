import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Locale
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

baselineProfile {
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
}

kotlin {
    jvmToolchain(21)
}

// Explicit opt-in categories keep behavior tests in mixed evidence classes in the routine lane.
val captureTests = providers.gradleProperty("tvhplayer.captureTests").orElse("all").get()
require(captureTests in setOf("all", "exclude", "only")) {
    "tvhplayer.captureTests must be all, exclude or only"
}
val testForks = providers.gradleProperty("tvhplayer.testForks").orElse("2").get()
require(testForks in setOf("1", "2")) { "tvhplayer.testForks must be 1 or 2" }
tasks.withType<Test>().configureEach {
    // Separate JVMs isolate Robolectric/Compose globals. Keep visual capture runs serial.
    maxParallelForks = if (captureTests == "exclude") testForks.toInt() else 1
    // Robolectric sandboxes grow each fork's native memory and metaspace for its whole life
    // (~3 GiB RSS after ~85 classes at -Xmx512m); recycling forks keeps two inside the 8 GiB build slice.
    forkEvery = 30
    // Motion regressions still render/assert every sampled frame in the routine lane.
    // Encoding their diagnostic PNGs belongs to explicit visual-evidence runs.
    systemProperty("tvhplayer.writeMotionCaptures", captureTests != "exclude")
    useJUnit {
        when (captureTests) {
            "exclude" -> excludeCategories("at.bernhardberger.tvhplayer.testutil.VisualCapture")
            "only" -> includeCategories("at.bernhardberger.tvhplayer.testutil.VisualCapture")
        }
    }
}

// The languages the app ships, in language-picker order (alphabetical by native name). This one
// list keeps only these translations of the app and its libraries, writes the locale config that
// Android 13+ offers in its per-app language settings, and fills the in-app picker. A bare
// language follows the system's region; a tag with a region is an entry of its own.
val appLanguages = listOf(
    "cs", "da", "de-DE", "de-AT", "de-CH", "en-GB", "en-US", "es", "fr", "hr",
    "it", "hu", "nl", "nb", "pl", "pt-BR", "pt-PT", "ro", "fi", "sv", "tr",
    "el", "ru", "uk", "zh-Hans", "zh-Hant", "ja", "ko",
)
// Debug builds add Android's pseudo-locales: en-XA for long text, ar-XB for right-to-left.
val pseudoLocales = listOf("en-XA", "ar-XB")
fun javaStringArray(values: List<String>) = values.joinToString(prefix = "{", postfix = "}") { "\"$it\"" }
// aapt2 filters by resource qualifier (de, en-rXA, b+zh+Hans), matching folder names literally; a
// regional entry also keeps its language's folder. Hebrew resources live in values-iw, the legacy
// code Android's own resources use.
fun resourceLocaleFilters(tags: List<String>) = tags.flatMap { tag ->
    val locale = Locale.forLanguageTag(tag)
    val code = if (locale.language == "he") "iw" else locale.language
    val language = if (locale.script.isEmpty()) code else "b+$code+${locale.script}"
    when {
        locale.country.isEmpty() -> listOf(language)
        locale.script.isEmpty() -> listOf(language, "$language-r${locale.country}")
        else -> listOf(language, "$language+${locale.country}")
    }
}.distinct()

android {
    testBuildType = if (providers.gradleProperty("tvhplayer.profileTests").orNull == "true") "profile" else "debug"
    namespace = "at.bernhardberger.tvhplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "at.bernhardberger.tvhplayer"
        minSdk = 28
        targetSdk = 36
        versionCode = 162
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PROFILE_TRACE", "false")
        buildConfigField("boolean", "PROGRAMME_WINDOW_B", providers.gradleProperty("player.programmeWindowB").orElse("true").map { it.toBooleanStrict().toString() }.get())
        buildConfigField("String[]", "APP_LANGUAGES", javaStringArray(appLanguages))
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "APPLIANCE_BUILD", "false")
        }
        create("appliance") {
            dimension = "distribution"
            applicationIdSuffix = ".appliance"
            buildConfigField("boolean", "APPLIANCE_BUILD", "true")
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
            buildConfigField("String[]", "APP_LANGUAGES", javaStringArray(appLanguages + pseudoLocales))
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("profile") {
            initWith(getByName("release"))
            proguardFiles("benchmark-rules.pro")
            // Controlled code-optimization comparison; identical resources and instrumentation.
            isMinifyEnabled = providers.gradleProperty("tvhplayer.profileMinify")
                .map { it.toBooleanStrict() }.orElse(false).get()
            isDebuggable = false
            buildConfigField("boolean", "PROFILE_TRACE", "true")
            applicationIdSuffix = ".profile"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("profileServer") {
            initWith(getByName("profile"))
            applicationIdSuffix = null
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    // Keep every shipped language in the base APK: the in-app picker can choose one that is not the
    // device's language, and Play would otherwise install only the device language's split.
    bundle.language.enableSplit = false
    testOptions.unitTests.isIncludeAndroidResources = true
    sourceSets.getByName("profile").kotlin.srcDir("src/release/java")
    sourceSets.getByName("profileServer").kotlin.srcDir("src/release/java")
    if (testBuildType == "profile") {
        sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/profileTest/java"))
        sourceSets.getByName("androidTest").kotlin.setSrcDirs(listOf("src/profileTest/java"))
        sourceSets.getByName("androidTest").manifest.srcFile("src/profileTest/AndroidManifest.xml")
    }
    providers.gradleProperty("player.offlineMediaAssets").orNull?.let {
        sourceSets.getByName("androidTest").assets.srcDir(it)
    }
}

// Bundle the repository's legal sources verbatim, rather than maintaining a second list.
abstract class GenerateAboutLegalAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun generate() {
        fileSystemOperations.sync {
            from(legalFiles) { into("licenses") }
            into(outputDirectory)
        }
    }
}

// Android's own locale-config generation lists resource folders only, so it can name neither a
// region without overrides (de-AT) nor a pseudo-locale.
abstract class GenerateAppLocaleConfig : DefaultTask() {
    @get:Input
    abstract val languageTags: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.file("xml/locales_config.xml").get().asFile
        file.parentFile.mkdirs()
        file.writeText(buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""")
            languageTags.get().forEach { appendLine("""    <locale android:name="$it" />""") }
            appendLine("</locale-config>")
        })
    }
}

androidComponents.onVariants { variant ->
    val variantLanguages = if (variant.buildType == "debug") appLanguages + pseudoLocales else appLanguages
    variant.androidResources.localeFilters.set(resourceLocaleFilters(appLanguages) +
        if (variant.buildType == "debug") pseudoLocales.map { it.replace("-", "-r") } else emptyList())
    val localeConfig = tasks.register<GenerateAppLocaleConfig>(
        "generate${variant.name.replaceFirstChar { it.uppercase() }}AppLocaleConfig",
    ) {
        languageTags.set(variantLanguages)
    }
    variant.sources.res?.addGeneratedSourceDirectory(localeConfig, GenerateAppLocaleConfig::outputDirectory)

    val aboutLegalAssets = tasks.register<GenerateAboutLegalAssets>(
        "generate${variant.name.replaceFirstChar { it.uppercase() }}AboutLegalAssets",
    ) {
        legalFiles.from(rootProject.file("LICENSE"), rootProject.file("NOTICE.md"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(
        aboutLegalAssets,
        GenerateAboutLegalAssets::outputDirectory,
    )
}

androidComponents.beforeVariants { variant ->
    // Generate only Play release. Both shipping flavors consume the merged main profile.
    if (variant.buildType?.startsWith("nonMinified") == true ||
        variant.buildType?.startsWith("benchmark") == true) {
        variant.enable = variant.name == "playNonMinifiedRelease"
    }
    if (variant.productFlavors.any { it == "distribution" to "appliance" } &&
        variant.buildType in setOf("profile", "profileServer")) {
        variant.enable = false
    }
}

val releasedSdkSources = configurations.create("releasedSdkSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
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
    implementation(project(":client"))
    implementation(project(":decoder-ffmpeg"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // The SDK owns playback; :decoder-ffmpeg supplies the platform-first audio fallback.
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    add("profileImplementation", "at.bernhardberger.tvheadend:sdk-testing") {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    // Device-only surface fixture intentionally constructs a concrete player.
    androidTestImplementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val syncReleasedSdkEvidence = tasks.register<Sync>("syncReleasedSdkEvidence") {
    val media3Aar = configurations.getByName("playDebugRuntimeClasspath").incoming.artifactView {
        componentFilter { id ->
            id is ModuleComponentIdentifier &&
                id.group == "at.bernhardberger.tvheadend" &&
                id.module == "sdk-media3"
        }
    }.files
    from(media3Aar)
    from(releasedSdkSources)
    into(layout.buildDirectory.dir("released-sdk-evidence"))
}
tasks.register("verifyExternalSdkConsumption") {
    group = "verification"
    description = "Proves app and client consume only the exact public TVHeadend SDK release or development build."
    dependsOn("assemblePlayDebug", syncReleasedSdkEvidence)

    val sdkGroup = "at.bernhardberger.tvheadend"
    val sdkVersion = libs.versions.tvheadend.sdk.get()
    // A release X.Y.Z or a development build; tools/check-native-libs and tools/prepare-release
    // use the same rule, and only a release version may be released.
    val publicSdkVersion = Regex("""[0-9]+\.[0-9]+\.[0-9]+(-[0-9]{8}\.[0-9]{6}-[0-9]+)?""").matches(sdkVersion)
    val stagedSdkSubstitution = providers.gradleProperty("tvheadend.sdk.local")
        .map(String::toBooleanStrict)
        .getOrElse(false)
    evaluationDependsOn(":client")
    evaluationDependsOn(":decoder-ffmpeg")
    val productionVariants = listOf("playDebug", "playRelease", "playProfile", "playProfileServer", "applianceDebug", "applianceRelease")
        .map { ":app:$it" } +
        listOf("debug", "release", "profile", "profileServer").map { ":client:$it" } +
        listOf(":decoder-ffmpeg:debug", ":decoder-ffmpeg:release")
    val productionClasspaths = productionVariants.flatMap { variant ->
        val owner = project(variant.substringBeforeLast(":"))
        listOf("CompileClasspath", "RuntimeClasspath").map { suffix ->
            "$variant$suffix" to owner.configurations.getByName("${variant.substringAfterLast(":")}$suffix")
        }
    }.toMap()
    val directSdkDependencies = productionClasspaths.flatMap { (classpathName, classpath) ->
        classpath.hierarchy.flatMap { configuration ->
            configuration.dependencies.mapNotNull { dependency ->
                (dependency as? ExternalModuleDependency)
                    ?.takeIf { it.group == sdkGroup }
                    ?.let {
                        val strictVersion = it.versionConstraint.strictVersion
                        "${classpathName.substringBeforeLast(":")}:${configuration.name}:${it.name}:$strictVersion"
                    }
            }
        }
    }.toSet()
    // Only app-owned modules are allowed; SDK projects and file fallbacks stay forbidden.
    val clientProjectPath = ":client"
    val decoderProjectPath = ":decoder-ffmpeg"
    val forbiddenLocalDependencies = productionClasspaths.flatMap { (classpathName, classpath) ->
        classpath.hierarchy.flatMap { configuration ->
            configuration.dependencies.mapNotNull { dependency ->
                when (dependency) {
                    is ProjectDependency -> "$classpathName:${configuration.name}:project:${dependency.path}"
                        .takeIf {
                            !(classpathName.startsWith(":app:") && dependency.path == clientProjectPath) &&
                                !(classpathName.startsWith(":app:") && dependency.path == decoderProjectPath)
                        }
                    is FileCollectionDependency -> dependency.files.files
                        .filterNot { it.name in setOf("android.jar", "core-for-system-modules.jar") }
                        .takeIf { it.isNotEmpty() }
                        ?.let { "$classpathName:${configuration.name}:files:$it" }
                    else -> null
                }
            }
        }
    }.distinct()
    val includedBuildNames = gradle.includedBuilds.map { it.name }
    val projectPaths = rootProject.allprojects.map { it.path }.toSet()
    val publicRepositoryUrls = gradle.extensions.extraProperties.get("dependencyRepositoryUrls")
    val repositoriesMode = gradle.extensions.extraProperties.get("dependencyRepositoriesMode")
    val appProjectPath = project.path
    productionVariants.forEach { variant ->
        val runtimeGraph = productionClasspaths.getValue("${variant}RuntimeClasspath")
            .incoming.resolutionResult.rootComponent.map { root ->
                val components = mutableSetOf<ResolvedComponentResult>()
                val pending = ArrayDeque<ResolvedComponentResult>()
                pending.add(root)
                while (pending.isNotEmpty()) {
                    val component = pending.removeFirst()
                    if (components.add(component)) {
                        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
                            pending.add(dependency.selected)
                        }
                    }
                }
                components.flatMap { component ->
                    when (val id = component.id) {
                        is ProjectComponentIdentifier -> listOfNotNull(
                            "project=${id.displayName}".takeIf {
                                id.projectPath != appProjectPath && id.projectPath != clientProjectPath &&
                                    id.projectPath != variant.substringBeforeLast(":") &&
                                    !(variant.startsWith(":app:") && id.projectPath == decoderProjectPath)
                            },
                        )
                        is ModuleComponentIdentifier -> buildList {
                            if (id.group == sdkGroup) add("tvheadend=${id.module}:${id.version}")
                            if (id.group == "androidx.media3") add("media3=${id.version}")
                            if (id.group.startsWith("io.coil-kt.coil3")) add("coil=${id.version}")
                            if (id.group == "androidx.datastore") add("datastore=${id.version}")
                            if (id.group == "org.jetbrains.kotlinx" && id.module.startsWith("kotlinx-coroutines-")) {
                                add("coroutines=${id.version}")
                            }
                        }
                        else -> emptyList()
                    }
                }.distinct().sorted()
            }
        val compileTvheadendModules = productionClasspaths.getValue("${variant}CompileClasspath")
            .incoming.resolutionResult.rootComponent.map { root ->
                val components = mutableSetOf<ResolvedComponentResult>()
                val pending = ArrayDeque<ResolvedComponentResult>()
                pending.add(root)
                while (pending.isNotEmpty()) {
                    val component = pending.removeFirst()
                    if (components.add(component)) {
                        component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
                            pending.add(dependency.selected)
                        }
                    }
                }
                components.mapNotNull { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    id.module.takeIf { id.group == sdkGroup }
                }.distinct().sorted()
            }
        inputs.property("${variant}RuntimeGraph", runtimeGraph)
        inputs.property("${variant}CompileTvheadendModules", compileTvheadendModules)
    }

    doLast {
        check(!stagedSdkSubstitution) {
            "Staged SDK substitution is active (-Ptvheadend.sdk.local=true), so this build cannot " +
                "prove public SDK consumption. Publish the SDK release and re-run without the flag."
        }
        check(publicSdkVersion) {
            "SDK version $sdkVersion is neither a release (X.Y.Z) nor a development build " +
                "(X.Y.Z-yyyyMMdd.HHmmss-N)"
        }
        val expectedDirectSdkDependencies = setOf(
            ":app:implementation:sdk-android:$sdkVersion",
            ":app:implementation:sdk-media3:$sdkVersion",
            ":app:profileImplementation:sdk-testing:$sdkVersion",
            ":client:implementation:sdk-android:$sdkVersion",
            ":client:implementation:sdk-media3:$sdkVersion",
        )
        check(directSdkDependencies == expectedDirectSdkDependencies) {
            "App/client SDK declarations $directSdkDependencies do not match $expectedDirectSdkDependencies"
        }
        check(forbiddenLocalDependencies.isEmpty()) {
            "App/client dependencies contain local fallbacks: $forbiddenLocalDependencies"
        }
        check(includedBuildNames.isEmpty()) { "The app must not use included builds: $includedBuildNames" }
        // :baselineprofile and :macrobenchmark are test-only modules; no shipping build depends on them.
        check(projectPaths == setOf(":", ":app", clientProjectPath, decoderProjectPath, ":baselineprofile", ":macrobenchmark")) {
            "Unexpected Gradle projects: $projectPaths"
        }

        val expectedPublicRepositoryUrls = setOf(
            "https://dl.google.com/dl/android/maven2",
            "https://repo.maven.apache.org/maven2",
            "https://central.sonatype.com/repository/maven-snapshots",
        )
        check(publicRepositoryUrls == expectedPublicRepositoryUrls) {
            "App repositories $publicRepositoryUrls do not match $expectedPublicRepositoryUrls"
        }
        check(repositoriesMode == "FAIL_ON_PROJECT_REPOS") {
            "Project repositories must remain forbidden"
        }

        val expectedTvheadendModules = mapOf(
            "htsp" to "1.0.0-rc.1",
            "sdk-android" to sdkVersion,
            "sdk-core" to sdkVersion,
            "sdk-media3" to sdkVersion,
            "sdk-playback" to sdkVersion,
        )
        productionVariants.forEach { variant ->
            val runtimeGraph = (inputs.properties.getValue("${variant}RuntimeGraph") as Iterable<*>)
                .map(Any?::toString)
                .toSet()
            val projectComponents = runtimeGraph.filter { it.startsWith("project=") }
                .map { it.removePrefix("project=") }
            check(projectComponents.isEmpty()) {
                "$variant runtime contains project components: $projectComponents"
            }

            val resolvedTvheadendModules = runtimeGraph.filter { it.startsWith("tvheadend=") }
                .associate { entry ->
                    val (module, version) = entry.removePrefix("tvheadend=").split(":", limit = 2)
                    module to version
                }
            val expectedModules = if (variant.startsWith(":decoder-ffmpeg:")) {
                emptyMap()
            } else if (variant == ":app:playProfile") {
                expectedTvheadendModules + ("sdk-testing" to sdkVersion)
            } else expectedTvheadendModules
            check(resolvedTvheadendModules == expectedModules) {
                "$variant runtime TVHeadend graph $resolvedTvheadendModules " +
                    "does not match $expectedModules"
            }
            val compileTvheadendModules =
                (inputs.properties.getValue("${variant}CompileTvheadendModules") as Iterable<*>)
                    .map(Any?::toString)
                    .toSet()
            check("htsp" !in compileTvheadendModules) {
                "HTSP must remain runtime-only but $variant compile graph contains $compileTvheadendModules"
            }

            val media3Versions = runtimeGraph.filter { it.startsWith("media3=") }
                .map { it.removePrefix("media3=") }
                .toSet()
            check(media3Versions == setOf("1.11.1")) {
                "$variant runtime Media3 versions are $media3Versions"
            }
            if (variant.startsWith(":decoder-ffmpeg:")) return@forEach
            val coilVersions = runtimeGraph.filter { it.startsWith("coil=") }
                .map { it.removePrefix("coil=") }
                .toSet()
            check(coilVersions == setOf("3.6.3")) {
                "$variant runtime Coil versions are $coilVersions"
            }
            val dataStoreVersions = runtimeGraph.filter { it.startsWith("datastore=") }
                .map { it.removePrefix("datastore=") }
                .toSet()
            check(dataStoreVersions == setOf("1.3.0")) {
                "$variant runtime DataStore versions are $dataStoreVersions"
            }
            val coroutinesVersions = runtimeGraph.filter { it.startsWith("coroutines=") }
                .map { it.removePrefix("coroutines=") }
                .toSet()
            check(coroutinesVersions == setOf("1.11.0")) {
                "$variant runtime Coroutines versions are $coroutinesVersions"
            }
        }
    }
}
