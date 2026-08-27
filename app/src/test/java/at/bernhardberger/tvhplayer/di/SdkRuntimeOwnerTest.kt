@file:kotlin.OptIn(
    at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class,
    at.bernhardberger.tvheadend.playback.ExperimentalRecordingCoordinationApi::class,
)

package at.bernhardberger.tvhplayer.di

import androidx.media3.common.Player
import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvheadend.core.DvrEntry
import at.bernhardberger.tvheadend.core.RecordingPlaybackIntent
import at.bernhardberger.tvheadend.core.SubscriptionFailureKind
import at.bernhardberger.tvheadend.core.TimeshiftSeekDecision
import at.bernhardberger.tvheadend.core.TimeshiftState
import at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import at.bernhardberger.tvheadend.playback.PlaybackSessionState
import at.bernhardberger.tvheadend.playback.RecordingProgressSyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SdkRuntimeOwnerTest {
    @Test
    fun requestedShutdownLeavesMainSchedulableAndClosesClientAfterPlayback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val releaseStarted = CompletableDeferred<Unit>()
        val allowRelease = CompletableDeferred<Unit>()
        val playback = FakePlaybackRuntime {
            releaseStarted.complete(Unit)
            allowRelease.await()
        }
        val client = TvheadendClient(ioDispatcher = dispatcher)
        val owner = SdkRuntimeOwner(
            client = client,
            legacyPlaybackRuntime = playback,
            shutdownDispatcher = dispatcher,
        )

        val shutdown = owner.requestClose()
        assertFalse(releaseStarted.isCompleted)
        runCurrent()

        assertTrue(releaseStarted.isCompleted)
        assertFalse(shutdown.isCompleted)
        assertClientOpen(client)

        var mainContinuationRan = false
        launch(dispatcher) { mainContinuationRan = true }
        runCurrent()
        assertTrue(mainContinuationRan)

        allowRelease.complete(Unit)
        runCurrent()
        shutdown.await()

        assertClientClosed(client)
    }

    @Test
    fun repeatedShutdownRequestsReleasePlaybackAndClientExactlyOnce() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var releaseCalls = 0
        val playback = FakePlaybackRuntime { releaseCalls++ }
        val client = TvheadendClient(ioDispatcher = dispatcher)
        val owner = SdkRuntimeOwner(
            client = client,
            legacyPlaybackRuntime = playback,
            shutdownDispatcher = dispatcher,
        )

        val first = owner.requestClose()
        val second = owner.requestClose()

        assertSame(first, second)
        runCurrent()
        first.await()
        owner.close()

        assertEquals(1, releaseCalls)
        assertClientClosed(client)
    }

    private suspend fun assertClientOpen(client: TvheadendClient) {
        try {
            client.readFileBytes("/not-connected")
            fail("A disconnected client must reject file reads")
        } catch (expected: IllegalStateException) {
            assertFalse(expected.message.orEmpty().contains("closed", ignoreCase = true))
        }
    }

    private suspend fun assertClientClosed(client: TvheadendClient) {
        try {
            client.readFileBytes("/closed")
            fail("Shutdown must close the client")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed", ignoreCase = true))
        }
    }
}

private class FakePlaybackRuntime(
    private val releaseBlock: suspend () -> Unit,
) : PlaybackRuntime {
    override val player: Player
        get() = error("The shutdown test does not borrow a Player")
    override val state: StateFlow<PlaybackSessionState> =
        MutableStateFlow(PlaybackSessionState.Idle)
    override val activeChannelId: StateFlow<Int?> = MutableStateFlow(null)
    override val playingLiveChannelId: StateFlow<Int?> = MutableStateFlow(null)
    override val activeRecordingId: StateFlow<Int?> = MutableStateFlow(null)
    override val timeshiftState: StateFlow<TimeshiftState> = MutableStateFlow(TimeshiftState())
    override val liveSubscriptionFailure: StateFlow<SubscriptionFailureKind?> =
        MutableStateFlow(null)
    override val recordingProgressSyncState: StateFlow<RecordingProgressSyncState> =
        MutableStateFlow(RecordingProgressSyncState.Inactive)
    override val diagnostics: StateFlow<PlaybackDiagnosticsSnapshot> =
        MutableStateFlow(PlaybackDiagnosticsSnapshot())

    override suspend fun playLive(channelId: Int): Boolean = false

    override suspend fun playRecording(entry: DvrEntry, intent: RecordingPlaybackIntent) = Unit

    override suspend fun stop() = Unit
    override suspend fun retryLive(): Boolean = false
    override suspend fun retryRecording(): Boolean = false
    override suspend fun pauseTimeshift(): Boolean = false
    override suspend fun resumeTimeshift(): Boolean = false
    override suspend fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision? = null
    override suspend fun goLive(): TimeshiftSeekDecision? = null
    override fun recordingPaused() = Unit
    override fun recordingSeekSettled() = Unit
    override suspend fun setRefreshRateMatchingEnabled(enabled: Boolean) = Unit
    override fun setDiagnosticsEnabled(enabled: Boolean) = Unit
    override suspend fun release() = releaseBlock()
}
