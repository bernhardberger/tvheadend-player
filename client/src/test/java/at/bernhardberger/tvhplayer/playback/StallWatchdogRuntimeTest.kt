@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DecoderCounters
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.RECORDING
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackRuntimeTest.Companion.exercise
import java.time.Duration
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A target that has data but makes no progress is restarted once with fresh decoders, then fails. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StallWatchdogRuntimeTest {
    @Test fun liveStuckBufferingRetunesOnceThroughAStoppedPlayer() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        runtime.setDiagnosticsEnabled(true)
        live()
        val subscribes = connection.subscribeCount
        val stoppedBeforeRetune = stoppedBefore { connection.subscribeCount > subscribes }

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }

        // The stuck target's decoders went with a stopped player before the retune subscribed.
        assertTrue(stoppedBeforeRetune())
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.diagnostics.value.stallRecoveries == 1 }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(subscribes + 1, connection.subscribeCount)
    }

    @Test fun beforeAnyStallALiveZapKeepsTheDecoders() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        runtime.setDiagnosticsEnabled(true)
        live()
        val subscribes = connection.subscribeCount
        val stopped = stoppedBefore { connection.subscribeCount > subscribes }

        live(channel = 2)

        assertFalse(stopped())
        assertFalse(runtime.diagnostics.value.freshDecoderPerZap)
    }

    @Test fun afterOneLiveStallRecoveryEveryLiveZapStopsTheOldTargetFirst() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        runtime.setDiagnosticsEnabled(true)
        live()
        val subscribes = connection.subscribeCount
        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.activeTarget.value != null && player.playWhenReady }
        // The recovered target plays on; nothing is stuck any more.
        forcedPlaybackState = null
        forcedBufferedAhead = null
        assertTrue(runtime.diagnostics.value.freshDecoderPerZap)

        repeat(2) { zap ->
            val before = connection.subscribeCount
            val stopped = stoppedBefore { connection.subscribeCount > before }
            live(channel = if (zap == 0) 2 else 1)
            assertTrue(stopped())
        }
        assertEquals(1, runtime.diagnostics.value.stallRecoveries)
    }

    @Test fun bufferingWithLittleQueuedIsNetworkBufferingAndNeverRecovered() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        val subscribes = connection.subscribeCount
        stuckBuffering(aheadMillis = STALL_BUFFERED_AHEAD_MILLIS - 1)

        idleFor(STALL_BUFFERING_MILLIS * 4)
        settle()

        assertEquals(subscribes, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun aSecondLiveStallWithinTheWindowFailsInsteadOfRetuningAgain() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        val subscribes = connection.subscribeCount
        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.activeTarget.value != null && player.playWhenReady }

        // The replacement is stuck the same way.
        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { runtime.state.value is AppPlaybackState.Failed }

        val failed = runtime.state.value as AppPlaybackState.Failed
        assertEquals(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED, failed.recoveryReason)
        assertNull(runtime.activeTarget.value)
        settle()
        assertEquals(subscribes + 1, connection.subscribeCount)
    }

    @Test fun theViewersPauseIsNeverOverriddenByARecovery() = exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }
        val epoch = runtime.videoPresentation.value.epoch
        runtime.pause()
        await { !player.playWhenReady }

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS * 4)
        settle()

        assertEquals(emptyList<PlaybackStall>(), trace.stalls)
        assertFalse(player.playWhenReady)
        assertEquals(epoch, runtime.videoPresentation.value.epoch)
        assertEquals(AppPlaybackTarget.Recording(RECORDING), runtime.activeTarget.value)
    }

    @Test fun aNewerViewingIntentWinsOverAQueuedRecovery() = exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
        runtime.setDiagnosticsEnabled(true)
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }
        val epoch = runtime.videoPresentation.value.epoch
        stuckBuffering()
        // Detected on the player's thread; the serialized recovery has not run yet.
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        assertEquals(listOf(PlaybackStall.BUFFERING), trace.stalls)
        runtime.notePlaybackIntent()

        settle()

        assertEquals(0, runtime.diagnostics.value.stallRecoveries)
        assertEquals(epoch, runtime.videoPresentation.value.epoch)
        assertEquals(AppPlaybackTarget.Recording(RECORDING), runtime.activeTarget.value)
    }

    @Test fun aLivePictureThatStopsWhileTheClockRunsRetunes() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        val subscribes = connection.subscribeCount
        val counters = DecoderCounters().also { forcedVideoDecoderCounters = it }
        forcedTracks = videoTracks()
        renderFirstFrame()
        // The cover lifts once the picture moves past its first frame.
        counters.renderedOutputBufferCount++
        idleFor(MOTION_POLL_MILLIS)
        assertTrue(runtime.videoPresentation.value.visible)

        clockRunsWithAStillPicture()
        idleFor(PICTURE_STALL_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }
        assertEquals(1, counters.renderedOutputBufferCount)
    }

    @Test fun radioWithoutAVideoTrackIsNoPictureStall() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        val subscribes = connection.subscribeCount
        val counters = DecoderCounters().also { forcedVideoDecoderCounters = it }
        renderFirstFrame()
        counters.renderedOutputBufferCount++
        idleFor(MOTION_POLL_MILLIS)
        assertTrue(runtime.videoPresentation.value.visible)
        // The same picture evidence, but no video track is selected.
        forcedTracks = audioTracks()

        clockRunsWithAStillPicture()
        idleFor(PICTURE_STALL_MILLIS * 4)
        settle()

        assertEquals(subscribes, connection.subscribeCount)
    }

    @Test fun aStuckRecordingResumesWhereItStoodThroughAStoppedPlayer() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        runtime.setDiagnosticsEnabled(true)
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        val epoch = runtime.videoPresentation.value.epoch
        forcedPosition = { STOOD_AT_MILLIS }
        val stopped = stoppedBefore { runtime.videoPresentation.value.epoch != epoch }

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { runtime.diagnostics.value.stallRecoveries == 1 && runtime.videoPresentation.value.epoch != epoch }

        assertTrue(stopped())
        assertEquals(AppPlaybackTarget.Recording(RECORDING), runtime.activeTarget.value)
        forcedPosition = null
        assertEquals(STOOD_AT_MILLIS, coordinator.pendingRecordingRecoveryPosition.value?.inWholeMilliseconds ?: player.currentPosition)
    }

    @Test fun aSecondRecordingStallWithinTheWindowFailsToRead() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }
        val first = runtime.videoPresentation.value.epoch
        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { runtime.videoPresentation.value.epoch != first && runtime.activeTarget.value != null && player.playWhenReady }

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { runtime.state.value is AppPlaybackState.Failed }

        assertEquals(AppPlaybackFailureReason.RECORDING_READ_FAILED, (runtime.state.value as AppPlaybackState.Failed).reason)
        assertNull(runtime.activeTarget.value)
    }

    @Test fun aZapCancelledWhileTheOldTargetStopsLetsTheRetiredTargetGo() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        latchFreshDecoderPerZap()
        var zap: Deferred<PlaybackTargetResult?>? = null
        var cancelled = false
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE && !cancelled) {
                    cancelled = true
                    zap?.cancel()
                }
            }
        })
        val started = zapTo(channel = 2).also { zap = it }

        await { started.isCompleted }
        settle()

        assertTrue(cancelled)
        assertNull(runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        live(channel = 2)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun aZapCancelledAfterTheOldTargetStoppedLetsTheRetiredTargetGo() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        latchFreshDecoderPerZap()
        val stopped = stoppedBefore { false }
        var zap: Deferred<PlaybackTargetResult?>? = null
        // The old target is retired; the replacement has not bound its channel yet.
        beforeLiveBinding = {
            beforeLiveBinding = {}
            assertTrue(stopped())
            requireNotNull(zap).cancel()
        }
        zap = zapTo(channel = 2)
        await { zap.isCompleted }
        settle()

        assertTrue(zap.isCancelled)
        assertNull(runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun aRetryCancelledAfterTheOldTargetStoppedEndsIdle() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        latchFreshDecoderPerZap()
        val stopped = stoppedBefore { false }
        var retry: Deferred<PlaybackTargetResult?>? = null
        beforeLiveBinding = {
            beforeLiveBinding = {}
            assertTrue(stopped())
            requireNotNull(retry).cancel()
        }
        retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { retry.isCompleted }
        settle()

        assertTrue(retry.isCancelled)
        assertNull(runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun aRecoveryRetuneThatStoppedTheOldSourceFinishesBeforeTheNewerRecovery() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        latchFreshDecoderPerZap()
        val stopped = stoppedBefore { false }
        val subscribes = connection.subscribeCount
        var escalated = false
        // Retune A stopped the old source; the SDK escalates again before A binds. The newer
        // recovery waits for A instead of cancelling an install the SDK may already run.
        beforeLiveBinding = {
            if (!escalated) {
                escalated = true
                assertTrue(stopped())
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
                scheduler.runCurrent()
            }
        }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount > subscribes }
        assertTrue(escalated)
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.state.value == AppPlaybackState.Starting }
        // The newer report concerned the source A replaced, so A's committed retune drops it.
        scheduler.advanceTimeBy(2_001)
        settle()

        assertEquals(subscribes + 1, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Starting, runtime.state.value)
        assertTrue(player.playWhenReady)
    }

    @Test fun aRetuneWhoseInstallReachedTheSdkStaysTheAppsBeforeTheNewerRecovery() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        latchFreshDecoderPerZap()
        val subscribes = connection.subscribeCount
        var requested = false
        // No runCurrent: the newer recovery runs only once the retune handed its install to the SDK.
        beforeLiveBinding = {
            if (!requested) {
                requested = true
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            }
        }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount > subscribes }
        assertTrue(requested)
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.state.value == AppPlaybackState.Starting }
        scheduler.advanceTimeBy(2_001)
        settle()

        // The app owns the source the SDK installed instead of withdrawing it as departed.
        assertEquals(subscribes + 1, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Starting, runtime.state.value)
        assertTrue(player.playWhenReady)
    }

    @Test fun aNewerRecoveryLetsTheAttemptWhoseSourceTheSdkInstalledCommit() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        settle()
        // The fake stream never shows a picture, so a tune that works stays here.
        val tuned = runtime.state.value
        var newerRequested = false
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // The SDK installed the first attempt's source; its reply has not reached the attempt yet.
                if (mediaItem == null || newerRequested) return
                newerRequested = true
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            }
        })
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        // The first attempt retunes at once.
        await { connection.subscribeCount == 2 }
        assertTrue(newerRequested)
        connection.awaitCollectionRegistered()
        startSubscription()

        // The first attempt committed that source; the newer request, reported for it, waits for its backoff.
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        scheduler.advanceTimeBy(2_001)
        await { connection.subscribeCount == 3 }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.state.value == AppPlaybackState.Starting }
        playerReady()
        settle()

        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue(player.playWhenReady)
        assertEquals(tuned, runtime.state.value)
    }

    @Test fun aBufferThatDrainsBeforeTheQueuedRecoveryRunsSupersedesIt() = supersededBeforeDispatch {
        // No poll runs in between: only the recovery's own sample sees it.
        forcedBufferedAhead = 1_000L
    }

    @Test fun readyBeforeTheQueuedRecoveryRunsSupersedesIt() = supersededBeforeDispatch {
        // No poll runs in between: only the recovery's own sample sees it.
        forcedPlaybackState = Player.STATE_READY
    }

    @Test fun aFrameBeforeTheQueuedPictureRecoveryRunsSupersedesIt() = exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
        runtime.setDiagnosticsEnabled(true)
        live()
        val subscribes = connection.subscribeCount
        val counters = DecoderCounters().also { forcedVideoDecoderCounters = it }
        forcedTracks = videoTracks()
        renderFirstFrame()
        counters.renderedOutputBufferCount++
        idleFor(MOTION_POLL_MILLIS)
        assertTrue(runtime.videoPresentation.value.visible)
        val stopped = stoppedBefore { false }
        clockRunsWithAStillPicture()
        idleFor(PICTURE_STALL_MILLIS + 2 * STALL_POLL_MILLIS)
        assertEquals(listOf(PlaybackStall.PICTURE), trace.stalls)

        // The decoder renders again before the serialized recovery runs, with no poll in between.
        counters.renderedOutputBufferCount++
        settle()

        assertFalse(stopped())
        assertEquals(subscribes, connection.subscribeCount)
        assertEquals(0, runtime.diagnostics.value.stallRecoveries)
    }

    @Test fun progressBeforeTheQueuedRecoveryRunsSupersedesIt() = supersededBeforeDispatch {
        // Media3 leaves buffering; the next poll sees it.
        forcedPlaybackState = Player.STATE_READY
        playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
        idleFor(STALL_POLL_MILLIS)
    }

    @Test fun aSeekBeforeTheQueuedRecoveryRunsSupersedesIt() = supersededBeforeDispatch {
        playerListeners.toList().forEach { it.onPositionDiscontinuity(position(), position(), Player.DISCONTINUITY_REASON_SEEK) }
    }

    @Test fun aTrackChangeBeforeTheQueuedRecoveryRunsSupersedesIt() = supersededBeforeDispatch {
        playerListeners.toList().forEach { it.onTracksChanged(Tracks.EMPTY) }
    }

    @Test fun aSeekStartsTheStallWindowOver() = windowStartsOverOn {
        it.onPositionDiscontinuity(position(), position(), Player.DISCONTINUITY_REASON_SEEK)
    }

    @Test fun aTrackChangeStartsTheStallWindowOver() = windowStartsOverOn { it.onTracksChanged(Tracks.EMPTY) }

    @Test fun aPlayWhenReadyChangeStartsTheStallWindowOver() = windowStartsOverOn {
        it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    }

    @Test fun theViewersPauseDuringARecordingRecoveryPausesItsReplacement() = pauseDuringRecordingRecovery { runtime.pause() }

    @Test fun aSessionPauseDuringARecordingRecoveryPausesItsReplacement() = pauseDuringRecordingRecovery {
        runtime.setSessionPlayWhenReady(false, isAttached = { true })
    }

    @Test fun aLiveRecoveryKeepsTheAudioFocusMute() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live(timeshift = false)
        requireNotNull(focus.callback)(AudioInterruption.NOISY)
        await { runtime.isInterruptionMuted }
        val subscribes = connection.subscribeCount

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.activeTarget.value != null && player.playWhenReady }

        assertTrue(runtime.isInterruptionMuted)
    }

    @Test fun aCoveredPictureThatNeverMovesRetunesAfterTheCoversGrace() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        live()
        val subscribes = connection.subscribeCount
        val counters = DecoderCounters().also { forcedVideoDecoderCounters = it }
        forcedTracks = videoTracks()
        // One frame, never a second: the cover keeps waiting while sound and the clock run.
        counters.renderedOutputBufferCount = 1
        renderFirstFrame()
        clockRunsWithAStillPicture()

        // The window of a shown picture is no stall while the cover may still lift.
        idleFor(PICTURE_STALL_MILLIS + 2 * STALL_POLL_MILLIS)
        settle()
        assertFalse(runtime.videoPresentation.value.visible)
        assertEquals(subscribes, connection.subscribeCount)

        idleFor(PICTURE_COVER_GRACE_MILLIS)
        await { connection.subscribeCount > subscribes }
        assertEquals(subscribes + 1, connection.subscribeCount)
    }

    @Test fun aTunneledPictureIsNoPictureStall() = exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
        settings.setTunnelingEnabled(true)
        await { player.trackSelectionParameters.tunnelingRequested }
        live()
        val subscribes = connection.subscribeCount
        val counters = DecoderCounters().also { forcedVideoDecoderCounters = it }
        forcedTracks = videoTracks()
        renderFirstFrame()
        counters.renderedOutputBufferCount = 1

        clockRunsWithAStillPicture()
        idleFor((PICTURE_STALL_MILLIS + PICTURE_COVER_GRACE_MILLIS) * 2)
        settle()

        assertEquals(subscribes, connection.subscribeCount)
    }

    @Test fun aBackgroundedTargetIsNeverRecovered() = exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }
        runtime.onAppBackgrounded()
        await { !player.playWhenReady }
        // Something outside the runtime plays the player on in the background.
        player.play()

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS * 4)
        settle()

        assertEquals(emptyList<PlaybackStall>(), trace.stalls)
    }

    /** A stall is reported and queued, then [supersede] happens before the serialized recovery runs. */
    private fun supersededBeforeDispatch(supersede: BackgroundPlaybackRuntimeTest.Fixture.() -> Unit) =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
            runtime.setDiagnosticsEnabled(true)
            recordingReadsNeverAnswer()
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            val epoch = runtime.videoPresentation.value.epoch
            val stopped = stoppedBefore { runtime.videoPresentation.value.epoch != epoch }
            stuckBuffering()
            idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
            assertEquals(listOf(PlaybackStall.BUFFERING), trace.stalls)

            supersede()
            settle()

            assertEquals(epoch, runtime.videoPresentation.value.epoch)
            assertEquals(0, runtime.diagnostics.value.stallRecoveries)
            // The budget is untouched: the next stall recovers instead of failing.
            stuckBuffering()
            idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
            await { runtime.diagnostics.value.stallRecoveries == 1 && runtime.videoPresentation.value.epoch != epoch }
            assertTrue(stopped())
            assertFalse(runtime.state.value is AppPlaybackState.Failed)
        }

    /** Stuck buffering longer than the window in total, but [event] arrives in its middle. */
    private fun windowStartsOverOn(event: (Player.Listener) -> Unit) = exercise(PlaybackRuntimePolicy.fromPlayerSettings(trace)) {
        recordingReadsNeverAnswer()
        val install = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                RecordingPlaybackStart.START_OVER)
        }
        await { install.isCompleted }

        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS)
        playerListeners.toList().forEach(event)
        idleFor(STALL_BUFFERING_MILLIS - STALL_POLL_MILLIS)

        assertEquals(emptyList<PlaybackStall>(), trace.stalls)
        // A full window after the event's first sample does count.
        idleFor(2 * STALL_POLL_MILLIS)
        assertEquals(listOf(PlaybackStall.BUFFERING), trace.stalls)
    }

    /** [pause] arrives while the stuck recording's recovery has retired it and binds its replacement. */
    private fun pauseDuringRecordingRecovery(pause: BackgroundPlaybackRuntimeTest.Fixture.() -> Unit) =
        exercise(PlaybackRuntimePolicy.fromPlayerSettings()) {
            runtime.setDiagnosticsEnabled(true)
            recordingReadsNeverAnswer()
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, RECORDING)),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            val epoch = runtime.videoPresentation.value.epoch
            var paused = false
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_IDLE && !paused) {
                        paused = true
                        pause()
                    }
                }
            })

            stuckBuffering()
            idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
            await { runtime.diagnostics.value.stallRecoveries == 1 && runtime.videoPresentation.value.epoch != epoch }
            settle()

            assertTrue(paused)
            assertEquals(AppPlaybackTarget.Recording(RECORDING), runtime.activeTarget.value)
            assertFalse(player.playWhenReady)
        }

    /** One live stall recovery turns on a fresh decoder per zap; the recovered channel 1 plays on. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.latchFreshDecoderPerZap() {
        runtime.setDiagnosticsEnabled(true)
        live()
        val subscribes = connection.subscribeCount
        stuckBuffering()
        idleFor(STALL_BUFFERING_MILLIS + 2 * STALL_POLL_MILLIS)
        await { connection.subscribeCount > subscribes }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { runtime.activeTarget.value != null && player.playWhenReady }
        forcedPlaybackState = null
        forcedBufferedAhead = null
        assertTrue(runtime.diagnostics.value.freshDecoderPerZap)
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.zapTo(channel: Int): Deferred<PlaybackTargetResult?> {
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        return scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
        }
    }

    private fun position() = Player.PositionInfo(null, 0, null, null, 0, 0L, 0L, C.INDEX_UNSET, C.INDEX_UNSET)

    private val trace = StallTrace()

    private class StallTrace : PlaybackTrace {
        val stalls = mutableListOf<PlaybackStall>()
        override val enabled: Boolean get() = true
        override fun stall(stall: PlaybackStall, epoch: Long) {
            stalls += stall
        }
    }

    /** Media3 buffers with [aheadMillis] queued: plenty means a renderer never becomes ready. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.stuckBuffering(aheadMillis: Long = 10_000L) {
        forcedPlaybackState = Player.STATE_BUFFERING
        forcedPlaying = false
        forcedBufferedAhead = aheadMillis
        playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_BUFFERING) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Ready and playing, the position runs with the main looper's clock; the decoder renders nothing more. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.clockRunsWithAStillPicture() {
        val start = SystemClock.uptimeMillis()
        forcedPosition = { SystemClock.uptimeMillis() - start }
        forcedPlaybackState = Player.STATE_READY
        forcedPlaying = true
        playerListeners.toList().forEach { it.onIsPlayingChanged(true) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The recording's file never answers, so the real player buffers on as Media3 would at a stuck server. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.recordingReadsNeverAnswer() {
        recordingBinding = UnansweredRecordingBindings::unanswered
    }

    /** Whether the player stopped (went IDLE) while [until] did not yet hold. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.stoppedBefore(until: () -> Boolean): () -> Boolean {
        var stopped = false
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE && !until()) stopped = true
            }
        })
        return { stopped }
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.renderFirstFrame() {
        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun videoTracks() = Tracks(listOf(Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
    )))

    private fun audioTracks() = Tracks(listOf(Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
    )))

    private companion object {
        const val STOOD_AT_MILLIS = 30_000L
    }
}
