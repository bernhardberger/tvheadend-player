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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityPlaybackLifecycleTest {
    @Test
    fun activityStartAndStopDelegateToTargetAwarePlaybackOwner() {
        val events = mutableListOf<String>()
        val lifecycle = MainActivityPlaybackLifecycle(
            owners = MainActivityPlaybackOwners(),
            onAppForegrounded = { events += "foreground" },
            onAppBackgrounded = { events += "background" },
            stopPlayback = { events += "stop" },
            finishActivity = { events += "finish" },
        )

        lifecycle.onActivityStarted()
        lifecycle.onActivityStarted()
        lifecycle.onActivityStopped()
        lifecycle.onActivityStopped()
        lifecycle.onActivityStarted()
        lifecycle.onActivityStopped()

        assertEquals(listOf("foreground", "background", "foreground", "background"), events)
    }

    @Test
    fun overlappingActivitiesKeepPlaybackForegroundUntilLastOwnerStops() {
        val owners = MainActivityPlaybackOwners()
        val events = mutableListOf<String>()
        var foreground = false
        fun lifecycle(name: String) = MainActivityPlaybackLifecycle(
            owners = owners,
            onAppForegrounded = { foreground = true; events += "foreground:$name" },
            onAppBackgrounded = { foreground = false; events += "background:$name" },
            stopPlayback = { events += "stop:$name" },
            finishActivity = { events += "finish:$name" },
        )
        val a = lifecycle("A")
        val b = lifecycle("B")

        a.onActivityStarted()
        b.onActivityStarted()
        a.onActivityStopped()
        assertTrue(foreground)
        assertEquals(listOf("foreground:A"), events)

        b.onActivityStopped()
        assertFalse(foreground)
        assertEquals(listOf("foreground:A", "background:B"), events)
    }

    @Test
    fun rootExitStillStopsAndFinishesWithAnotherStartedOwner() = runTest {
        val owners = MainActivityPlaybackOwners()
        val events = mutableListOf<String>()
        fun lifecycle(name: String) = MainActivityPlaybackLifecycle(
            owners = owners,
            onAppForegrounded = { events += "foreground:$name" },
            onAppBackgrounded = { events += "background:$name" },
            stopPlayback = { events += "stop:$name" },
            finishActivity = { events += "finish:$name" },
        )
        val a = lifecycle("A")
        val b = lifecycle("B")
        a.onActivityStarted()
        b.onActivityStarted()

        a.onRootExitRequested(readyState())
        a.onRootExitRequested(readyState())
        a.onActivityStopped()
        assertEquals(listOf("foreground:A", "stop:A", "finish:A"), events)

        b.onActivityStopped()
        assertEquals(listOf("foreground:A", "stop:A", "finish:A", "background:B"), events)
    }

    @Test
    fun overlappingRootExitRequestsShareOneSerializedStopBeforeFinish() = runTest {
        val stopStarted = CompletableDeferred<Unit>()
        val releaseStop = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val lifecycle = MainActivityPlaybackLifecycle(
            owners = MainActivityPlaybackOwners(),
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
            owners = MainActivityPlaybackOwners(),
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
