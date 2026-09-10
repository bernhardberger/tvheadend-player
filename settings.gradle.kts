import org.gradle.api.artifacts.repositories.MavenArtifactRepository

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

/**
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
