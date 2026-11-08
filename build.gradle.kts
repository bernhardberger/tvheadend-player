import java.time.Duration
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.play.publisher) apply false
}

// Every TVHeadend SDK module (sdk-*) resolves to the catalog version, before its metadata is
// fetched. An SDK development build's POM and module metadata point at the floating X.Y.Z-SNAPSHOT
// of its sibling modules, which no repository here serves. A no-op for a released SDK. The HTSP
// library shares the group but keeps its own version.
val tvheadendSdkVersion = libs.versions.tvheadend.sdk.get()

subprojects {
    pluginManager.withPlugin("dev.detekt") {
        // Detekt is an explicit tools/verify gate, not part of ordinary build/check.
        tasks.matching { it.name == "check" }.configureEach {
            setDependsOn(dependsOn - tasks.named("detekt"))
        }
        // Source-wide checks cover every flavor/build type without compiling Android variants.
        val kotlinSources = fileTree("src") {
            include("**/*.kt", "**/*.kts")
        }
        val sourceRoot = file("src")
        // Includes unit, instrumentation and profile tests, including variant-specific sets.
        val testSources = kotlinSources.filter {
            it.relativeTo(sourceRoot).invariantSeparatorsPath.substringBefore('/')
                .contains("test", ignoreCase = true)
        }
        val productionSources = kotlinSources.minus(testSources)
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(rootProject.file("detekt.yml"))
            source.setFrom(productionSources)
            baseline.set(file("detekt-baseline.xml"))
        }
        tasks.named<dev.detekt.gradle.Detekt>("detekt") {
            setSource(productionSources)
        }
        tasks.named<dev.detekt.gradle.DetektCreateBaselineTask>("detektBaseline") {
            setSource(productionSources)
        }
        // ForbiddenMethodCall needs analysis. The JDK and detekt's Kotlin stdlib suffice for
        // Thread.sleep; no Android compilation or application dependency resolution is needed.
        val testAnalysisClasspath = configurations.named("detekt").map { configuration ->
            configuration.filter { it.name.startsWith("kotlin-stdlib-") }
        }
        tasks.register<dev.detekt.gradle.Detekt>("detektTests") {
            description = "Checks all test sources for real-clock waits and process-named classes."
            group = "verification"
            setSource(testSources)
            config.setFrom(rootProject.file("detekt-test.yml"))
            buildUponDefaultConfig.set(false)
            baseline.set(file("detekt-test-baseline.xml"))
            classpath.from(testAnalysisClasspath)
        }
        tasks.register<dev.detekt.gradle.DetektCreateBaselineTask>("detektTestBaseline") {
            description = "Baselines the two test-only detekt rules across all test sources."
            group = "verification"
            setSource(testSources)
            config.setFrom(rootProject.file("detekt-test.yml"))
            buildUponDefaultConfig.set(false)
            baseline.set(file("detekt-test-baseline.xml"))
            classpath.from(testAnalysisClasspath)
        }
    }
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
        // CI keeps no test reports: its log is the only record of why a test failed.
        testLogging.exceptionFormat = TestExceptionFormat.FULL
    }
}
