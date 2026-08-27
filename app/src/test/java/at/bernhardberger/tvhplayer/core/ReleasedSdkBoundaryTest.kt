package at.bernhardberger.tvhplayer.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasedSdkBoundaryTest {
    @Test
    fun appUsesOnlyTheTwoStrictReleasedSdkCoordinates() {
        val build = File("build.gradle.kts").readText()
        val strictCoordinates = Regex(
            "implementation\\(libs\\.tvheadend\\.sdk\\.(media3|android)\\) \\{[\\s\\S]*?strictly\\(libs\\.versions\\.tvheadend\\.sdk\\.get\\(\\)\\)",
        ).findAll(build).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("media3", "android"), strictCoordinates)
        assertFalse(build.contains("implementation(libs.tvheadend.sdk.core)"))
        assertFalse(build.contains("implementation(libs.tvheadend.sdk.playback)"))
        assertFalse(build.contains("implementation(\"at.bernhardberger.tvheadend:sdk-core"))
        assertFalse(build.contains("implementation(\"at.bernhardberger.tvheadend:sdk-playback"))
    }

    @Test
    fun predecessorRuntimeNamespacesAreAbsentFromApplicationSource() {
        val forbidden = listOf(
            "at.bernhardberger.tvheadend." + "client",
            "at.bernhardberger.tvheadend." + "core",
            "at.bernhardberger.tvheadend." + "playback",
        )
        val sources = File("src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        forbidden.forEach { namespace ->
            assertTrue(namespace, sources.none { it.readText().contains(namespace) })
        }
    }
}
