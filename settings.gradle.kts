import org.gradle.api.artifacts.repositories.MavenArtifactRepository

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/**
 * SDK sources: released versions come from Maven Central. A development build, always the
 * timestamped `X.Y.Z-yyyyMMdd.HHmmss-N` (the gates reject a floating `-SNAPSHOT`), pinned in
 * `gradle/libs.versions.toml` resolves from Central's snapshot repository, which serves only
 * timestamped SDK versions. Such a pin builds and passes `./tools/verify`, but
 * `tools/prepare-release` and `tools/sign-release` refuse it: a Player release needs a released SDK.
 *
 * Opt-in staged SDK checkout, for testing an SDK fix before it is published.
 *
 * Enable with `-Ptvheadend.sdk.local=true` after running `./gradlew stageLocalPublication` in the
 * SDK checkout, and set the matching version in `gradle/libs.versions.toml`. A build using it is
 * deliberately not release-verifiable: `:app:verifyExternalSdkConsumption` fails while it is on.
 */
val stagedSdkRepository = file(
    providers.gradleProperty("tvheadend.sdk.local.repository")
        .getOrElse("../tvheadend-sdk/build/local-maven"),
).canonicalFile
    .takeIf {
        providers.gradleProperty("tvheadend.sdk.local").map(String::toBooleanStrict).getOrElse(false)
    }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        stagedSdkRepository?.let { staged ->
            check(staged.isDirectory) {
                "Opt-in staged SDK is unavailable at $staged. " +
                    "Run './gradlew stageLocalPublication' in the SDK checkout first."
            }
            logger.warn(
                "WARNING: consuming the staged TVHeadend SDK from $staged. " +
                    "This build is not a released artifact and must not be signed, published or " +
                    "installed on a production device.",
            )
            maven {
                name = "stagedTvheadendSdk"
                url = staged.toURI()
                // Never let anything but the SDK resolve from an unpublished local directory.
                content { includeGroup("at.bernhardberger.tvheadend") }
            }
        }
        google()
        mavenCentral()
        maven {
            name = "centralSdkSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            // Only timestamped SDK development builds; every release, every floating -SNAPSHOT and
            // every other group stays off this repository.
            content {
                includeVersionByRegex(
                    "at\\.bernhardberger\\.tvheadend",
                    ".*",
                    "[0-9]+\\.[0-9]+\\.[0-9]+-[0-9]{8}\\.[0-9]{6}-[0-9]+",
                )
            }
        }
    }
}

gradle.extensions.extraProperties.set(
    "dependencyRepositoryUrls",
    dependencyResolutionManagement.repositories.map { repository ->
        if (repository is MavenArtifactRepository) {
            repository.url.toString().trimEnd('/')
        } else {
            "non-maven:${repository.name}"
        }
    }.toSet(),
)
gradle.extensions.extraProperties.set(
    "dependencyRepositoriesMode",
    dependencyResolutionManagement.repositoriesMode.get().name,
)

rootProject.name = "TVHeadendPlayer"
include(":app")
include(":baselineprofile")
include(":client")
include(":decoder-ffmpeg")
