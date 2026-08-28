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
    testImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.tvheadend.sdk.testing) {
        version { strictly(libs.versions.tvheadend.sdk.get()) }
    }
    // Device-only surface fixture intentionally constructs a concrete player.
    androidTestImplementation(libs.androidx.media3.exoplayer)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
}

tasks.register("verifyExternalSdkConsumption") {
    group = "verification"
    description = "Proves the app consumes only the exact public TVHeadend SDK release."
    dependsOn("assembleDebug", syncReleasedSdkEvidence)

    doLast {
        val sdkGroup = "at.bernhardberger.tvheadend"
        val sdkVersion = libs.versions.tvheadend.sdk.get()
        check(sdkVersion == "0.3.1") { "Expected public SDK 0.3.1 but found $sdkVersion" }
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
        val expectedDirectSdkDependencies = setOf(
            "implementation:sdk-android:$sdkVersion",
            "implementation:sdk-media3:$sdkVersion",
        )
        check(directSdkDependencies == expectedDirectSdkDependencies) {
            "App SDK declarations $directSdkDependencies do not match $expectedDirectSdkDependencies"
        }

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
        check(forbiddenLocalDependencies.isEmpty()) {
            "App dependencies contain local fallbacks: $forbiddenLocalDependencies"
        }
        check(gradle.includedBuilds.isEmpty()) { "The app must not use included builds" }
        check(rootProject.allprojects.map { it.path }.toSet() == setOf(":", ":app")) {
            "Unexpected Gradle projects: ${rootProject.allprojects.map { it.path }}"
        }

        val publicRepositoryUrls = gradle.extensions.extraProperties.get("dependencyRepositoryUrls")
        val expectedPublicRepositoryUrls = setOf(
            "https://dl.google.com/dl/android/maven2",
            "https://repo.maven.apache.org/maven2",
        )
        check(publicRepositoryUrls == expectedPublicRepositoryUrls) {
            "App repositories $publicRepositoryUrls do not match $expectedPublicRepositoryUrls"
        }
        check(gradle.extensions.extraProperties.get("dependencyRepositoriesMode") == "FAIL_ON_PROJECT_REPOS") {
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
            val components = productionClasspaths.getValue("${variant}RuntimeClasspath")
                .incoming.resolutionResult.allComponents
            val projectComponents = components.mapNotNull { it.id as? ProjectComponentIdentifier }
                .filter { it.projectPath != project.path }
            check(projectComponents.isEmpty()) {
                "$variant runtime contains project components: $projectComponents"
            }

            val resolvedTvheadendModules = components.mapNotNull { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                if (id.group != sdkGroup) return@mapNotNull null
                id.module to id.version
            }.toMap()
            check(resolvedTvheadendModules == expectedTvheadendModules) {
                "$variant runtime TVHeadend graph $resolvedTvheadendModules " +
                    "does not match $expectedTvheadendModules"
            }
            val compileTvheadendModules = productionClasspaths.getValue("${variant}CompileClasspath")
                .incoming.resolutionResult.allComponents.mapNotNull { component ->
                    val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                    id.module.takeIf { id.group == sdkGroup }
                }.toSet()
            check("htsp" !in compileTvheadendModules) {
                "HTSP must remain runtime-only but $variant compile graph contains $compileTvheadendModules"
            }

            val media3Versions = components.mapNotNull { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                id.version.takeIf { id.group == "androidx.media3" }
            }.toSet()
            check(media3Versions == setOf("1.11.0")) {
                "$variant runtime Media3 versions are $media3Versions"
            }
            val coilVersions = components.mapNotNull { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                id.version.takeIf { id.group.startsWith("io.coil-kt.coil3") }
            }.toSet()
            check(coilVersions == setOf("3.5.0")) {
                "$variant runtime Coil versions are $coilVersions"
            }
            val dataStoreVersions = components.mapNotNull { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@mapNotNull null
                id.version.takeIf { id.group == "androidx.datastore" }
            }.toSet()
            check(dataStoreVersions == setOf("1.2.1")) {
                "$variant runtime DataStore versions are $dataStoreVersions"
            }
        }
    }
}
