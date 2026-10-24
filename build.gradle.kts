import java.time.Duration

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Every TVHeadend SDK module (sdk-*) resolves to the catalog version, before its metadata is
// fetched. An SDK development build's POM and module metadata point at the floating X.Y.Z-SNAPSHOT
// of its sibling modules, which no repository here serves. A no-op for a released SDK. The HTSP
// library shares the group but keeps its own version.
val tvheadendSdkVersion = libs.versions.tvheadend.sdk.get()

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "at.bernhardberger.tvheadend" && requested.name.startsWith("sdk-") &&
                requested.version != tvheadendSdkVersion
            ) {
                useVersion(tvheadendSdkVersion)
                because("all TVHeadend SDK modules share the catalog version")
            }
        }
    }
    tasks.withType<Test>().configureEach {
        // Client tests use declared Gradle inputs and test-owned temporary files. App tests also
        // read source/evidence through user.dir and write undeclared evidence under ../artifacts.
        // Keep caching opt-in by module so future test suites are audited before enabling it.
        if (project.path != ":client") {
            outputs.doNotCacheIf("tests have undeclared file inputs and evidence outputs") { true }
        }
        // A hung test must fail instead of holding the shared Gradle build lock indefinitely.
        timeout.set(Duration.ofMinutes(15))
    }
}
