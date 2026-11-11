@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
)

package at.bernhardberger.tvhplayer.playback
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.SilenceMediaSource
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class SessionPlaybackTransportTest : SessionPlaybackFixture() {
    @Test fun commandsAreAnExactAllowlistForEachTarget() {
        val read = setOf(Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_STOP)
        fun commands(target: AppPlaybackTarget?, timeshift: Boolean = false, seekable: Boolean = true): Set<Int> =
            sessionPlaybackCommands(target, timeshift, seekable).let { value ->
                (0 until value.size()).map { value[it] }.toSet()
            }
        val live = AppPlaybackTarget.Live(ChannelId(1))
        val recording = AppPlaybackTarget.Recording(DvrEntryId(1))
        assertEquals(emptySet<Int>(), commands(null))
        assertEquals(read, commands(live))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(live, timeshift = true))
        assertEquals(read + Player.COMMAND_PLAY_PAUSE, commands(recording, seekable = false))
        assertEquals(read + setOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM), commands(recording))
    }

    @Test fun liveWithoutTimeshiftDoesNotPauseOrSendServerCommands() = exercise {
        live(false)
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        wrapper.pause()
        wrapper.play()
        wrapper.seekTo(10_000)
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun timeshiftPauseAndPlayReachServerExactlyOnceAndSeekIsUnavailable() = exercise {
        live(true)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun sessionPlayRetiresAPauseAwaitingTheFirstPictureWithoutServerCommands() = exercise {
        live(true, firstPicture = false)
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }
        val requests = focusRequests

        wrapper.play()
        await { !runtime.livePause.value.pending && player.playWhenReady }
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(requests + 1, focusRequests)
    }

    @Test fun startingLiveOffersPauseAndHoldsTheServerOnceAfterGrantAndFirstPicture() = exercise {
        startingLive()
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)

        grant()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun sessionPlayBeforeTheGrantClearsThePendingPauseWithoutAServerResume() = exercise {
        startingLive()
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        wrapper.pause()
        await { runtime.livePause.value.pending && !player.playWhenReady }

        wrapper.play()
        await { !runtime.livePause.value.pending && player.playWhenReady }
        grant()
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun liveWithoutAGrantAtTheFirstPictureRejectsSessionPause() = exercise {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val install = scope.async { runtime.playLive(liveSelection()) }
        await { connection.subscribeCount == 1 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { install.isCompleted }
        playerReady()
        await { runtime.livePause.value.availability == LivePauseAvailability.UNAVAILABLE }
        // The session withdraws the Pause it offered while the start was undecided.
        await { wrapper.isCommandAvailable(Player.COMMAND_STOP) && !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }

        runtime.setSessionPlayWhenReady(false) { true }.join()
        settle()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun liveWithTimeshiftOffRejectsSessionPause() = exercise {
        live(false)
        assertEquals(LivePauseAvailability.OFF, runtime.livePause.value.availability)
        runtime.setSessionPlayWhenReady(false) { true }.join()
        settle()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun sessionPauseDuringAPendingChannelChangeIsHeldForTheNewTarget() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        wrapper.pause()
        await { runtime.livePause.value.pending }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(runtime.livePause.value.selectionPending)

        startingLive(intent)
        assertFalse(player.playWhenReady)
        assertTrue(runtime.livePause.value.pending)
        grant(subscription = 2)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun sessionKeepsReportingPauseWhileAPausedSeekRefreshesItsPicture() = exercise {
        settings.setTunnelingEnabled(true)
        await { player.trackSelectionParameters.tunnelingRequested }
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady && !wrapper.playWhenReady }
        val reported = mutableListOf<Boolean>()
        wrapper.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { reported += playWhenReady }
        })
        runtime.nudgePausedSeek(confirmation = PausedSeekConfirmation.KEY_FRAME_QUEUED)
        await { player.playWhenReady }
        settle()
        assertFalse(wrapper.playWhenReady)
        // The fake stream renders no frame: the refresh ends after its budget on the runtime's clock.
        advanceTimeBy(PAUSED_SEEK_NUDGE_MILLIS)
        await { !runtime.livePause.value.pausedSeekRefresh }
        settle()
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(emptyList<Boolean>(), reported)
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun sessionReportsTheViewersIntentWhileAChannelChangeIsPending() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        await { runtime.livePause.value.selectionPending }
        settle()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        assertTrue(wrapper.playWhenReady)

        wrapper.pause()
        await { !wrapper.playWhenReady }
        // The channel still installed plays on; the session reports the Pause held for the change.
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        wrapper.play()
        await { wrapper.playWhenReady }
        assertFalse(runtime.livePause.value.pending)
        wrapper.pause()
        await { !wrapper.playWhenReady }

        // The new target installs paused and still reports (and offers) Pause until it holds.
        startingLive(intent)
        await { !runtime.livePause.value.selectionPending }
        settle()
        assertFalse(wrapper.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        grant(subscription = 2)
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(wrapper.playWhenReady)
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test fun sessionOffersPauseForAPendingChannelChangeAndReportsWhatPlaysOnceItIsAbandoned() = exercise {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val install = scope.async { runtime.playLive(liveSelection()) }
        await { connection.subscribeCount == 1 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { install.isCompleted }
        playerReady()
        await { wrapper.isCommandAvailable(Player.COMMAND_STOP) && !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }

        // A channel change is pending: the next channel may pause, so the session offers it.
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        assertTrue(wrapper.playWhenReady)
        wrapper.pause()
        await { !wrapper.playWhenReady }
        assertTrue(player.playWhenReady)

        // Its start was cancelled: the held Pause goes and the session reports the channel that plays.
        runtime.abandonLiveSelection(intent)
        await { !wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
        assertTrue(wrapper.playWhenReady)
        assertTrue(player.playWhenReady)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun startCancelledBehindTheSerializerLetsASessionPauseActOnTheChannelThatPlays() = exercise {
        live(true)
        val intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        whileCommandsBlocked {
            val start = scope.async { runtime.playLive(liveSelection(), intent) }
            settle()
            start.cancel()
            await { start.isCompleted }
        }
        assertFalse(runtime.livePause.value.selectionPending)
        wrapper.pause()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun sessionPauseQueuedBeforeAChannelChangePausesNeitherChannel() = exercise {
        live(true)
        var intent = 0L
        whileCommandsBlocked {
            wrapper.pause()
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 2)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun keyPauseQueuedBeforeAChannelChangePausesNeitherChannel() = exercise {
        live(true)
        lateinit var pause: Deferred<TimeshiftCommandResult?>
        var intent = 0L
        whileCommandsBlocked {
            pause = scope.async { runtime.pauseTimeshiftPlayback() }
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { pause.isCompleted }
        assertNull(pause.await())
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 2)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun keyPlayQueuedBeforeAChannelChangeResumesNothing() = exercise {
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        val requests = focusRequests
        lateinit var resume: Deferred<TimeshiftCommandResult>
        whileCommandsBlocked {
            resume = scope.async { runtime.resumeTimeshift() }
            settle()
            runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { resume.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertEquals(requests, focusRequests)
    }

    @Test fun sessionPauseAfterARetryBeganHoldsTheRetriedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = true) { wrapper.pause() }
    }

    @Test fun sessionPauseDuringTheAutomaticReconnectHoldsTheReconnectedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = false) { wrapper.pause() }
    }

    @Test fun keyPauseAfterARetryBeganHoldsTheRetriedChannel() = exercise {
        pauseDuringARetryHoldsTheRetriedChannel(viewerRetry = true) {
            scope.launch { runtime.pauseTimeshiftPlayback() }
        }
    }

    @Test fun sessionPauseAfterARetryFromAFailedStartHoldsTheRetriedChannel() = exercise {
        settings.setTimeshiftEnabled(true)
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        assertFalse(runtime.playLive(liveSelection())?.isStarted == true)
        assertNull(runtime.activeTarget.value)
        session.scriptLivePlaybackSuccess(manager)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        lateinit var pause: Job
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry = true) }
            settle()
            pause = runtime.setSessionPlayWhenReady(false) { true }
        }
        await { retry.isCompleted && pause.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertFalse(player.playWhenReady)
        grant(subscription = 1)
        playerReady()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun channelChangeAfterASessionPauseDuringARetryWinsOverThePause() = exercise {
        live(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        var intent = 0L
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry = true) }
            settle()
            wrapper.pause()
            settle()
            intent = runtime.notePlaybackIntent().also(runtime::noteLiveSelection)
        }
        await { retry.isCompleted }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        startingLive(intent)
        grant(subscription = 3)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.playWhenReady)
    }

    @Test fun rejectedTimeshiftPauseRollsBackLocalIntent() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        var localPauseBeforeServerCommand = false
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) localPauseBeforeServerCommand = connection.speeds.isEmpty()
            }
        })
        wrapper.pause()
        await { connection.speeds == listOf(0) }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(wrapper.playWhenReady)
        assertTrue(localPauseBeforeServerCommand)
        assertEquals(1, focusRequests)
    }

    @Test fun deniedFocusDoesNotResumeServerWhenRejectedHoldKeepsMutedVideoRunning() = exercise {
        live(true)
        focusGranted = false
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        wrapper.play()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun resumingFocusInterruptionSendsOnlyOneServerResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && player.playWhenReady }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertEquals(2, focusRequests)
    }

    @Test fun rejectedResumeAfterFocusInterruptionPausesLocallyAndSendsOneResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        wrapper.play()
        await { connection.speeds == listOf(0, 100) && focusRequests == 2 }
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
    }

    @Test fun pauseDuringInterruptionDoesNotSendASecondServerHoldOrAutoResume() = exercise {
        live(true)
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        wrapper.pause()
        settle()
        focusListener?.invoke(AudioInterruption.GAIN)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
    }

    @Test fun pauseWhileInterruptionMutedRestoresSoundWithoutServerCommands() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertFalse(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun pauseWhileInterruptionMutedAndFocusDeniedPreservesMuteWithoutServerPause() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        focusListener?.invoke(AudioInterruption.TRANSIENT_LOSS)
        await { androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes }
        focusGranted = false
        wrapper.pause()
        await { focusRequests == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertTrue(androidx.media3.common.C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes)
    }

    @Test fun closeDuringLocalPauseLetsServerRejectionRollbackComplete() = exercise {
        live(true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        val closed = CompletableDeferred<Unit>()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && !closed.isCompleted) {
                    observation.cancel()
                    wrapper.close()
                    closed.complete(Unit)
                }
            }
        })
        wrapper.pause()
        await { closed.isCompleted && connection.speeds == listOf(0) && player.playWhenReady }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertTrue(player.playWhenReady)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun recordingPauseAndPlayUseRuntimeFocusPath() = exercise {
        recording()
        wrapper.pause()
        await { !player.playWhenReady }
        wrapper.play()
        await { player.playWhenReady && focusRequests == 2 }
        settle()
        assertEquals(2, focusRequests)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun queuedPlayAfterCloseDoesNotRequestFocusOrResumeWhileRuntimeIsForeground() = exercise {
        live(true)
        wrapper.pause()
        await { connection.speeds == listOf(0) && !player.playWhenReady }
        whileCommandsBlocked {
            wrapper.play()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(1, focusRequests)
        assertFalse(player.playWhenReady)
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun queuedPauseAfterCloseDoesNotPauseWhileRuntimeIsForeground() = exercise {
        live(true)
        whileCommandsBlocked {
            wrapper.pause()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(1, focusRequests)
    }

    @Test fun queuedSeekAfterCloseDoesNotMoveRecordingWhileRuntimeIsForeground() = exercise {
        recording()
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        player.pause()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        val position = player.currentPosition
        whileCommandsBlocked {
            wrapper.seekTo(12_000)
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(position, player.currentPosition)
        assertEquals(1, focusRequests)
    }

    @Test fun recordingSeekUsesSerializedRuntimeCommandAndRequiresSeekableTimeline() = exercise {
        recording()
        assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        // Replace only the fixture's transport with a deterministic, seekable offline source.
        player.setMediaSource(SilenceMediaSource.Factory().setDurationUs(60_000_000).createMediaSource())
        player.prepare()
        await { wrapper.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
        val position = player.currentPosition
        val seeks = sessionSeeks
        whileCommandsBlocked {
            wrapper.seekTo(12_000)
            settle()
            assertEquals(position, player.currentPosition)
            assertEquals(seeks, sessionSeeks)
        }
        await { player.currentPosition >= 12_000 }
        assertEquals(seeks + 1, sessionSeeks)
        assertTrue(player.currentPosition < 13_000)
        assertTrue(connection.speeds.isEmpty())
    }

    /*
     * A target of the other kind is stopped before the replacement's source is set, so the
     * replacement never plays through the old target's decoders. The same kind is replaced in place.
     */

}
