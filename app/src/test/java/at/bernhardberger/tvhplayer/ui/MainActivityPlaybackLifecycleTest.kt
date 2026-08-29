package at.bernhardberger.tvhplayer.ui

import java.io.File
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
        val finishActivity = source.substringAfter("val finishActivity")
            .substringBefore("val handleRootBack")
        val rootBack = source.substringAfter("val handleRootBack")
            .substringBefore("BackHandler(")
        val fallbackBack = activitySource.substringAfter("onBackPressedDispatcher.addCallback")
            .substringBefore("setContent")

        assertTrue(finishActivity.contains("stopPlaybackAndClose("))
        assertTrue(finishActivity.contains("stopPlayback = playbackRuntime::stop"))
        assertTrue(rootBack.contains("finishActivity()"))
        assertTrue(fallbackBack.contains("playbackRuntime.stop()"))
        assertTrue(fallbackBack.indexOf("playbackRuntime.stop()") < fallbackBack.indexOf("finish()"))
    }
}
