plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":sdk:htsp"))

    testImplementation(libs.junit)
    testImplementation(kotlin("compiler-embeddable"))
}
