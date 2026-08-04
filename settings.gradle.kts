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
        exclusiveContent {
            forRepository {
                maven {
                    name = "tvheadendPlayerSdkLocal"
                    url = uri("../tvheadend-player-sdk/build/local-maven")
                }
            }
            filter {
                includeGroup("at.bernhardberger.tvheadend")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "TVHeadendPlayer"
include(":app")
