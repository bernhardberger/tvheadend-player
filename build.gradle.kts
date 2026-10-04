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
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
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
