package at.bernhardberger.tvhplayer.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPlaybackLifecycleTest {
    private val repositoryRoot = generateSequence(
        File(requireNotNull(System.getProperty("user.dir"))),
    ) { it.parentFile }.first { File(it, ".git").exists() }

    @Test
    fun activityLifecycleDelegatesTargetAwareForegroundTransitions() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/MainActivity.kt",
        ).readText()
        val onStart = source.substringAfter("override fun onStart()")
            .substringBefore("override fun onNewIntent")
        val onStop = source.substringAfter("override fun onStop()")
            .substringBefore("private fun requestApplianceEntry")

        assertTrue(onStart.contains("playbackRuntime.onAppForegrounded()"))
        assertTrue(onStop.contains("playbackRuntime.onAppBackgrounded()"))
        assertFalse(onStop.contains("playbackRuntime.stop()"))
    }

    @Test
    fun rootExitUsesSerializedPlaybackTeardown() {
        val source = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/AppRoot.kt",
        ).readText()
        val activitySource = File(
            repositoryRoot,
            "app/src/main/java/at/bernhardberger/tvhplayer/ui/MainActivity.kt",
        ).readText()
        val rootBack = source.substringAfter("val handleRootBack")
            .substringBefore("BackHandler(")
        val requestRootExit = activitySource.substringAfter("private fun requestRootExit()")
            .substringBefore("private fun requestApplianceEntry")

        assertTrue(rootBack.contains("onRequestExit()"))
        assertTrue(activitySource.contains("onBackPressedDispatcher.addCallback(this) { requestRootExit() }"))
        assertTrue(activitySource.contains("onRequestExit = ::requestRootExit"))
        assertTrue(requestRootExit.contains("if (!rootExitGate.tryBegin()) return"))
        assertTrue(requestRootExit.contains("stopPlaybackAndClose("))
        assertTrue(requestRootExit.contains("stopPlayback = playbackRuntime::stop"))
        assertTrue(requestRootExit.contains("closePlayer = ::finish"))
    }

    @Test
    fun twoImmediateRootExitCallbacksStartOneTeardown() {
        val gate = MainRootExitGate()
        var teardownCount = 0

        repeat(2) {
            if (gate.tryBegin()) teardownCount++
        }

        assertEquals(1, teardownCount)
    }
}
