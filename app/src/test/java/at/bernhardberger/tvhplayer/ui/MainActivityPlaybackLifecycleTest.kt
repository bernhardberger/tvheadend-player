package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityPlaybackLifecycleTest {
    @Test
    fun activityStartAndStopDelegateToTargetAwarePlaybackOwner() {
        val events = mutableListOf<String>()
        val lifecycle = MainActivityPlaybackLifecycle(
            onAppForegrounded = { events += "foreground" },
            onAppBackgrounded = { events += "background" },
            stopPlayback = { events += "stop" },
            finishActivity = { events += "finish" },
        )

        lifecycle.onActivityStarted()
        lifecycle.onActivityStopped()

        assertEquals(listOf("foreground", "background"), events)
    }

    @Test
    fun overlappingRootExitRequestsShareOneSerializedStopBeforeFinish() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val lifecycle = MainActivityPlaybackLifecycle(
            onAppForegrounded = { events += "foreground" },
            onAppBackgrounded = { events += "background" },
            stopPlayback = {
                events += "stop"
                stopStarted.complete(Unit)
                releaseStop.await()
            },
            finishActivity = { events += "finish" },
        )

        val ready = readyState()
        val firstExit = launch {
            lifecycle.onRootExitRequested(ready)
        }
        stopStarted.await()
        val overlappingExit = launch {
            lifecycle.onRootExitRequested(ready)
        }
        runCurrent()

        assertEquals(listOf("stop"), events)
        assertFalse(firstExit.isCompleted)
        assertFalse(overlappingExit.isCompleted)

        releaseStop.complete(Unit)
        firstExit.join()
        overlappingExit.join()
        assertEquals(listOf("stop", "finish"), events)
    }



    @Test
    fun unresolvedStartupBackDoesNotStopPlaybackOrFinishActivity() = runTest {
        val events = mutableListOf<String>()
        val lifecycle = MainActivityPlaybackLifecycle(
            onAppForegrounded = {},
            onAppBackgrounded = {},
            stopPlayback = { events += "stop" },
            finishActivity = { events += "finish" },
        )

        lifecycle.onRootExitRequested(
            startupState = MainStartupState.ResolvingLocal,
        )

        assertEquals(emptyList<String>(), events)
    }

    private fun readyState() = MainStartupState.Ready(
        server = ServerSettings(host = "tvh.invalid"),
        autoStartPlayback = false,
    )
}
