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

rootProject.name = "TVHeadendPlayer"
include(":app")
include(":sdk:domain")
include(":sdk:htsp")
include(":sdk:htsp-consumer-contract")
include(":sdk:playback-media3")
include(":sdk:playback-consumer-contract")
include(":sdk:decoder-ffmpeg-binary")
