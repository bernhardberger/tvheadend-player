import org.gradle.api.artifacts.repositories.MavenArtifactRepository

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

gradle.extensions.extraProperties.set(
    "dependencyRepositoryUrls",
    dependencyResolutionManagement.repositories.flatMap { repository ->
        if (repository is MavenArtifactRepository) {
            listOf(repository.url) + repository.artifactUrls
        } else {
            listOf("non-maven:${repository.name}")
        }
    }.map { url ->
        url.toString().trimEnd('/')
    }.toSet(),
)
gradle.extensions.extraProperties.set(
    "dependencyRepositoriesMode",
    dependencyResolutionManagement.repositoriesMode.get().name,
)

rootProject.name = "TVHeadendPlayer"
include(":app")
