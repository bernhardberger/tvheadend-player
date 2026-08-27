@file:OptIn(
    at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class,
    at.bernhardberger.tvheadend.playback.ExperimentalRecordingCoordinationApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Player
import at.bernhardberger.tvheadend.core.DvrEntry
import at.bernhardberger.tvheadend.core.DvrFile
import at.bernhardberger.tvheadend.core.DvrState
import at.bernhardberger.tvheadend.core.RecordingPlaybackIntent
import at.bernhardberger.tvheadend.core.SubscriptionFailureKind
import at.bernhardberger.tvheadend.core.TimeshiftSeekDecision
import at.bernhardberger.tvheadend.core.TimeshiftState
import at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSnapshot
import at.bernhardberger.tvheadend.playback.PlaybackDiagnosticsSource
import at.bernhardberger.tvheadend.playback.PlaybackFormatDiagnostics
import at.bernhardberger.tvheadend.playback.PlaybackQueueDiagnostics
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import at.bernhardberger.tvheadend.playback.PlaybackSessionState
import at.bernhardberger.tvheadend.playback.PlaybackTransportDiagnostics
import at.bernhardberger.tvheadend.playback.RecordingProgressSyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlaybackRuntimeTest {
    @Test
    fun exposesAppOwnedStateTargetTimeshiftAndMediaDiagnostics() = runTest {
        val backend = FakePlaybackRuntime().apply {
            stateFlow.value = PlaybackSessionState.Recovering(retryDelayMillis = 2_000L)
            timeshiftFlow.value = TimeshiftState(
                available = true,
                paused = true,
                bufferStartMs = -90_000L,
                positionMs = -30_000L,
                liveEdgeMs = 0L,
            )
            diagnosticsFlow.value = PlaybackDiagnosticsSnapshot(
                source = PlaybackDiagnosticsSource.LIVE_TV,
                state = PlaybackSessionState.Playing,
                isPlaying = true,
                positionMs = 12_000L,
                bufferedMs = 4_000L,
                video = PlaybackFormatDiagnostics(codec = "video/avc", resolution = "1920x1080"),
                transport = PlaybackTransportDiagnostics(
                    queue = PlaybackQueueDiagnostics(packets = 999L),
                ),
            )
            nextSeek = TimeshiftSeekDecision(
                targetMs = -60_000L,
                deltaMs = -30_000L,
                clamped = false,
            )
        }
        val runtime = AppPlaybackRuntime(backend)

        assertEquals(AppPlaybackState.Recovering(2_000L), runtime.state.value)
        assertEquals(
            AppTimeshiftState(
                available = true,
                paused = true,
                bufferStartMs = -90_000L,
                positionMs = -30_000L,
                liveEdgeMs = 0L,
            ),
            runtime.timeshiftState.value,
        )
        assertEquals(AppPlaybackSource.LIVE_TV, runtime.diagnostics.value.source)
        assertEquals("video/avc", runtime.diagnostics.value.video?.codec)
        assertFalse(
            AppPlaybackDiagnostics::class.java.declaredFields.any {
                it.name.contains("transport", ignoreCase = true)
            },
        )

        assertEquals(AppPlaybackCommandResult.SUBMITTED, runtime.playLive(serviceId = 42))
        assertEquals(AppPlaybackTarget.Live(serviceId = 42), runtime.submittedTarget.value)
        assertEquals(
            AppTimeshiftSeekResult.Applied(-60_000L, -30_000L, clamped = false),
            runtime.seekTimeshift(-30_000L),
        )

        runtime.stop()

        assertNull(runtime.submittedTarget.value)
        assertEquals(listOf("live:42", "seek:-30000", "stop"), backend.commands)
    }

    @Test
    fun unavailableBackendCommandsHaveTypedOutcomesAndKeepSubmittedIdentityForRetry() = runTest {
        val backend = FakePlaybackRuntime().apply {
            liveAccepted = false
            timeshiftAccepted = false
        }
        val runtime = AppPlaybackRuntime(backend)

        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, runtime.playLive(serviceId = 7))
        assertEquals(AppPlaybackTarget.Live(7), runtime.submittedTarget.value)
        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, runtime.pauseTimeshift())
        assertEquals(AppTimeshiftSeekResult.Unavailable, runtime.goLive())
    }

    @Test
    fun newerTuneReachesBackendBeforeOlderTuneCompletesAndOwnsTarget() = runTest {
        val olderStarted = CompletableDeferred<Unit>()
        val allowOlder = CompletableDeferred<Unit>()
        val backend = FakePlaybackRuntime().apply {
            liveHandler = { serviceId ->
                if (serviceId == 1) {
                    olderStarted.complete(Unit)
                    allowOlder.await()
                }
                true
            }
        }
        val runtime = AppPlaybackRuntime(backend)

        val older = async { runtime.playLive(1) }
        olderStarted.await()
        val newer = async { runtime.playLive(2) }
        runCurrent()

        assertTrue(newer.isCompleted)
        assertEquals(AppPlaybackCommandResult.SUBMITTED, newer.await())
        assertEquals(AppPlaybackTarget.Live(2), runtime.submittedTarget.value)

        allowOlder.complete(Unit)
        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, older.await())
    }

    @Test
    fun stopReachesBackendBeforeOlderTuneCompletesWithoutClearingANewerTarget() = runTest {
        val olderStarted = CompletableDeferred<Unit>()
        val allowOlder = CompletableDeferred<Unit>()
        val stopReached = CompletableDeferred<Unit>()
        val backend = FakePlaybackRuntime().apply {
            liveHandler = {
                olderStarted.complete(Unit)
                allowOlder.await()
                true
            }
            stopHandler = { stopReached.complete(Unit) }
        }
        val runtime = AppPlaybackRuntime(backend)

        val older = async { runtime.playLive(1) }
        olderStarted.await()
        val stopping = async { runtime.stop() }
        stopReached.await()
        stopping.await()

        assertNull(runtime.submittedTarget.value)
        allowOlder.complete(Unit)
        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, older.await())
    }

    @Test
    fun releaseOvertakesOlderTuneAndRejectsLaterLiveAndRecordingCommands() = runTest {
        val olderStarted = CompletableDeferred<Unit>()
        val allowOlder = CompletableDeferred<Unit>()
        val releaseReached = CompletableDeferred<Unit>()
        val backend = FakePlaybackRuntime().apply {
            liveHandler = {
                olderStarted.complete(Unit)
                allowOlder.await()
                true
            }
            releaseHandler = { releaseReached.complete(Unit) }
        }
        val runtime = AppPlaybackRuntime(backend)

        val older = async { runtime.playLive(1) }
        olderStarted.await()
        val closing = async { runtime.release() }
        releaseReached.await()
        closing.await()

        assertNull(runtime.submittedTarget.value)
        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, runtime.playLive(2))
        assertEquals(
            AppPlaybackCommandResult.UNAVAILABLE,
            runtime.playRecording(recordingEntry(9), RecordingPlaybackIntent.DefaultPolicy),
        )
        runtime.release()
        assertEquals(1, backend.releaseCalls)
        assertEquals(0, backend.recordingCalls)
        assertFalse(backend.commands.contains("live:2"))

        allowOlder.complete(Unit)
        assertEquals(AppPlaybackCommandResult.UNAVAILABLE, older.await())
        assertNull(runtime.submittedTarget.value)
    }

    @Test
    fun cancelledReleaseInitiatorCannotAbandonExactOnceTeardown() = runTest {
        val releaseStarted = CompletableDeferred<Unit>()
        val allowRelease = CompletableDeferred<Unit>()
        val backend = FakePlaybackRuntime().apply {
            releaseHandler = {
                releaseStarted.complete(Unit)
                allowRelease.await()
            }
        }
        val runtime = AppPlaybackRuntime(backend)

        val initiator = launch { runtime.release() }
        releaseStarted.await()
        val observer = async { runtime.release() }
        initiator.cancel()
        runCurrent()

        assertFalse(observer.isCompleted)
        assertEquals(1, backend.releaseCalls)

        allowRelease.complete(Unit)
        observer.await()
        initiator.join()
        assertEquals(1, backend.releaseCalls)
    }

    private fun recordingEntry(id: Int) = DvrEntry(
        id = id,
        eventId = null,
        channelId = 3,
        start = 100L,
        stop = 200L,
        title = "Recording $id",
        state = DvrState.COMPLETED,
        files = listOf(DvrFile(path = "archive/$id.ts")),
    )
}

private class FakePlaybackRuntime : PlaybackRuntime {
    val commands = mutableListOf<String>()
    var liveAccepted = true
    var timeshiftAccepted = true
    var nextSeek: TimeshiftSeekDecision? = null
    var liveHandler: suspend (Int) -> Boolean = { liveAccepted }
    var stopHandler: suspend () -> Unit = {}
    var releaseHandler: suspend () -> Unit = {}
    var recordingCalls = 0
    var releaseCalls = 0

    val stateFlow = MutableStateFlow<PlaybackSessionState>(PlaybackSessionState.Idle)
    val timeshiftFlow = MutableStateFlow(TimeshiftState())
    val diagnosticsFlow = MutableStateFlow(PlaybackDiagnosticsSnapshot())

    override val player: Player
        get() = error("Player access is outside this contract test")
    override val state: StateFlow<PlaybackSessionState> = stateFlow
    override val activeChannelId: StateFlow<Int?> = MutableStateFlow(null)
    override val playingLiveChannelId: StateFlow<Int?> = MutableStateFlow(null)
    override val activeRecordingId: StateFlow<Int?> = MutableStateFlow(null)
    override val timeshiftState: StateFlow<TimeshiftState> = timeshiftFlow
    override val liveSubscriptionFailure: StateFlow<SubscriptionFailureKind?> = MutableStateFlow(null)
    override val recordingProgressSyncState: StateFlow<RecordingProgressSyncState> =
        MutableStateFlow(RecordingProgressSyncState.Inactive)
    override val diagnostics: StateFlow<PlaybackDiagnosticsSnapshot> = diagnosticsFlow

    override suspend fun playLive(channelId: Int): Boolean {
        commands += "live:$channelId"
        return liveHandler(channelId)
    }

    override suspend fun playRecording(entry: DvrEntry, intent: RecordingPlaybackIntent) {
        recordingCalls++
    }

    override suspend fun stop() {
        commands += "stop"
        stopHandler()
    }

    override suspend fun retryLive(): Boolean = liveAccepted
    override suspend fun retryRecording(): Boolean = liveAccepted
    override suspend fun pauseTimeshift(): Boolean = timeshiftAccepted
    override suspend fun resumeTimeshift(): Boolean = timeshiftAccepted

    override suspend fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision? {
        commands += "seek:$deltaMs"
        return nextSeek
    }

    override suspend fun goLive(): TimeshiftSeekDecision? = nextSeek
    override fun recordingPaused() = Unit
    override fun recordingSeekSettled() = Unit
    override suspend fun setRefreshRateMatchingEnabled(enabled: Boolean) = Unit
    override fun setDiagnosticsEnabled(enabled: Boolean) = Unit
    override suspend fun release() {
        releaseCalls++
        releaseHandler()
    }
}
