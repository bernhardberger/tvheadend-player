import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "at.bernhardberger.tvhplayer"
    compileSdk = 37

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

val releasedSdkSources = configurations.create("releasedSdkSources") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val releasedSdkFfmpegSources = configurations.create("releasedSdkFfmpegSources") {
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
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Presentation API only; the released SDK owns concrete playback and codecs.
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Device-only surface fixture intentionally constructs a concrete player.
    androidTestImplementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val syncReleasedSdkEvidence = tasks.register<Sync>("syncReleasedSdkEvidence") {
    val media3Aar = configurations.getByName("debugRuntimeClasspath").incoming.artifactView {
        componentFilter { id ->
            id is ModuleComponentIdentifier &&
                id.group == "at.bernhardberger.tvheadend" &&
                id.module == "sdk-media3"
        }
    }.files
    from(media3Aar)
    from(releasedSdkSources)
    from(releasedSdkFfmpegSources)
    into(layout.buildDirectory.dir("released-sdk-evidence"))
}
tasks.register("verifyExternalSdkConsumption") {
    group = "verification"
    description = "Proves the app consumes only the exact public TVHeadend SDK release."
    dependsOn("assembleDebug", syncReleasedSdkEvidence)

    val sdkGroup = "at.bernhardberger.tvheadend"
    val sdkVersion = libs.versions.tvheadend.sdk.get()
    val productionClasspaths = listOf(
        "debugCompileClasspath",
        "debugRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeClasspath",
    ).associateWith(configurations::getByName)
    val productionConfigurations = productionClasspaths.values.flatMap { it.hierarchy }.toSet()
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
    val forbiddenLocalDependencies = productionClasspaths.flatMap { (classpathName, classpath) ->
        classpath.hierarchy.flatMap { configuration ->
            configuration.dependencies.mapNotNull { dependency ->
                when (dependency) {
                    is ProjectDependency -> "$classpathName:${configuration.name}:project:${dependency.path}"
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
    listOf("debug", "release").forEach { variant ->
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
                            "project=${id.displayName}".takeIf { id.projectPath != appProjectPath },
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
        check(sdkVersion == "0.6.1") { "Expected public SDK 0.6.1 but found $sdkVersion" }
        val expectedDirectSdkDependencies = setOf(
            "implementation:sdk-android:$sdkVersion",
            "implementation:sdk-media3:$sdkVersion",
        )
        check(directSdkDependencies == expectedDirectSdkDependencies) {
            "App SDK declarations $directSdkDependencies do not match $expectedDirectSdkDependencies"
        }
        check(forbiddenLocalDependencies.isEmpty()) {
            "App dependencies contain local fallbacks: $forbiddenLocalDependencies"
        }
        check(includedBuildNames.isEmpty()) { "The app must not use included builds: $includedBuildNames" }
        check(projectPaths == setOf(":", ":app")) {
            "Unexpected Gradle projects: $projectPaths"
        }

        val expectedPublicRepositoryUrls = setOf(
            "https://dl.google.com/dl/android/maven2",
            "https://repo.maven.apache.org/maven2",
        )
        check(publicRepositoryUrls == expectedPublicRepositoryUrls) {
            "App repositories $publicRepositoryUrls do not match $expectedPublicRepositoryUrls"
        }
        check(repositoriesMode == "FAIL_ON_PROJECT_REPOS") {
            "Project repositories must remain forbidden"
        }

        val expectedTvheadendModules = mapOf(
            "htsp" to "0.7.0",
            "sdk-android" to sdkVersion,
            "sdk-core" to sdkVersion,
            "sdk-media3" to sdkVersion,
            "sdk-playback" to sdkVersion,
        )
        listOf("debug", "release").forEach { variant ->
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
            check(resolvedTvheadendModules == expectedTvheadendModules) {
                "$variant runtime TVHeadend graph $resolvedTvheadendModules " +
                    "does not match $expectedTvheadendModules"
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
            check(media3Versions == setOf("1.11.0")) {
                "$variant runtime Media3 versions are $media3Versions"
            }
            val coilVersions = runtimeGraph.filter { it.startsWith("coil=") }
                .map { it.removePrefix("coil=") }
                .toSet()
            check(coilVersions == setOf("3.5.0")) {
                "$variant runtime Coil versions are $coilVersions"
            }
            val dataStoreVersions = runtimeGraph.filter { it.startsWith("datastore=") }
                .map { it.removePrefix("datastore=") }
                .toSet()
            check(dataStoreVersions == setOf("1.2.1")) {
                "$variant runtime DataStore versions are $dataStoreVersions"
            }
            val coroutinesVersions = runtimeGraph.filter { it.startsWith("coroutines=") }
                .map { it.removePrefix("coroutines=") }
                .toSet()
            check(coroutinesVersions == setOf("1.10.2")) {
                "$variant runtime Coroutines versions are $coroutinesVersions"
            }
        }
    }
}
