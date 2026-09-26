@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionPlaybackPlayerTest {
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
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
        wrapper.seekTo(12_000)
        await { player.currentPosition >= 12_000 }
        assertTrue(player.currentPosition < 13_000)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun sessionStopTearsDownLiveWithoutTimeshiftAndAnnouncesOnce() = exercise {
        live(false)
        val stops = collectSessionStops()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        settle()
        assertEquals(1, stops.size)
        assertEquals(1, connection.unsubscribeCount)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
    }

    @Test fun playbackRequestedAfterAQueuedStopWinsAndTheScreenStaysOpen() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var install: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // A later request (for example CH+ or a picked recording) queues behind the Stop.
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
        }
        await { install.isCompleted }
        settle()
        // The queued Stop lost to the newer request: the newer target replaces A and the
        // showing player is not told to close.
        assertEquals(1, connection.unsubscribeCount)
        assertTrue(install.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun channelAcceptedBeforeAQueuedStopRunsCancelsTheStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            // CH+ accepted by the live screen; its playLive waits for the zap to settle.
            runtime.notePlaybackIntent()
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun intentNotedAfterTheStopRanButBeforeTheScreenCollectsKeepsTheScreenOpen() = exercise {
        live(false)
        val screen = StandardTestDispatcher()
        val stops = mutableListOf<Unit>()
        scope.launch(screen, start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // The screen has not taken the event yet when the viewer tunes (zap settle window).
        runtime.notePlaybackIntent()
        screen.scheduler.advanceUntilIdle()
        assertEquals(1, connection.unsubscribeCount)
        assertEquals(emptyList<Unit>(), stops)
    }

    /*
     * Warm opening actions start nothing, so each notes viewing intent itself (AppRoot: a click
     * on the already-playing channel, a warm return). Only the order of the opening action and
     * the session Stop decides; the screen's entry follows the opening action either way.
     */

    @Test fun warmClickAfterAQueuedStopKeepsThePlayerItOpensPlaying() = exercise {
        live(false)
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Live(ChannelId(1)))
    }

    @Test fun warmReturnAfterAQueuedStopKeepsTheRecordingPlaying() = exercise {
        recording()
        warmOpeningAfterAQueuedStopKeepsThePlayerOpen(AppPlaybackTarget.Recording(DvrEntryId(1)))
    }

    @Test fun warmClickBeforeAQueuedStopClosesWithTheStop() = exercise {
        live(false)
        warmOpeningBeforeAQueuedStopClosesWithIt()
        await { connection.unsubscribeCount == 1 } // the SDK unsubscribes asynchronously
    }

    @Test fun warmPlayerEnteredWhileAStopIsQueuedClosesWithTheStop() = exercise {
        recording() // a warm return to the recording's player, then the Stop
        warmOpeningBeforeAQueuedStopClosesWithIt()
    }

    @Test fun channelStartedFromBrowseWithAStopQueuedBehindItStopsAndItsPlayerCloses() = exercise {
        recording() // warm recording while browsing
        answerNextSubscribe(1)
        lateinit var start: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        var entry = 0L
        whileCommandsBlocked {
            start = scope.async { runtime.playLive(liveSelection()) } // the click, in flight
            settle()
            wrapper.stop()
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen()) // its player composes first
            stops = collectSessionStops()
        }
        await { start.isCompleted }
        settle()
        assertTrue(start.await()?.isStarted == true)
        // The SDK may not have sent the subscribe yet when the Stop ends the target; either
        // way no subscription outlives it (the SDK unsubscribes asynchronously).
        await { connection.subscribeCount == connection.unsubscribeCount }
        settle()
        assertEquals(connection.subscribeCount, connection.unsubscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(entry))
    }

    @Test fun recordingStartedWithAStopQueuedBehindItStopsAndItsPlayerRestoresNothing() = exercise {
        live(false)
        lateinit var install: Deferred<PlaybackTargetResult?>
        lateinit var restore: Deferred<PlaybackTargetResult?>
        lateinit var stops: List<Unit>
        whileCommandsBlocked {
            install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value,
                    DvrEntryId(1))), RecordingPlaybackStart.START_OVER)
            }
            settle()
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            stops = collectSessionStops()
            restore = scope.async { restoreRecording() } // the recording screen's restore
            settle()
        }
        await { install.isCompleted && restore.isCompleted }
        settle()
        assertTrue(install.await()?.isStarted == true)
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun queuedStopThatIsSkippedLeavesTheEnteredPlayerPlaying() = exercise {
        live(false)
        var entry = 0L
        whileCommandsBlocked {
            runtime.stopFromSession { false } // the session detached before the Stop ran
            settle()
            entry = requireNotNull(runtime.enterPlayerScreen())
        }
        settle()
        val stops = collectSessionStops()
        settle()
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertEquals(emptyList<Unit>(), stops)
        // The registration ended with the skipped Stop: the next entry notes intent again.
        assertTrue(requireNotNull(runtime.enterPlayerScreen()) > entry)
    }

    @Test fun secondStopPendingUnderTheSameGenerationStillHoldsTheEntry() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var probe: Deferred<Long?>
        whileCommandsBlocked {
            runtime.stopFromSession { false } // skipped when it runs
            probe = scope.async { commands.serialize(onClosed = { null }) { runtime.enterPlayerScreen() } }
            runtime.stopFromSession { true } // still pending when the probe enters
            settle()
        }
        await { probe.isCompleted }
        settle()
        assertTrue(runtime.isPlaybackIntentStopped(requireNotNull(probe.await())))
        assertEquals(1, connection.unsubscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
    }

    @Test fun zapAfterAPendingStopSkipsTheStopAndPlays() = exercise {
        live(false)
        val stops = collectSessionStops()
        var zapIntent = 0L
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
            zapIntent = runtime.notePlaybackIntent() // CH+ after the Stop
        }
        settle()
        assertEquals(0, connection.unsubscribeCount)
        answerNextSubscribe(2)
        val zap = runtime.playLive(liveSelection(), zapIntent)
        assertTrue(zap?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun screenCollectingAfterItsPendingStopRanStillCloses() = exercise {
        live(false)
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            assertNotNull(runtime.enterPlayerScreen())
        }
        settle()
        assertEquals(null, runtime.activeTarget.value)
        // The screen's CloseOnSessionStop subscribes only now.
        assertEquals(1, collectSessionStops().size)
    }

    @Test fun stopPressedWhileTheAcceptedChannelStartsStopsIt() = exercise {
        live(false)
        val stops = collectSessionStops()
        val intent = runtime.notePlaybackIntent() // the live screen accepts the channel
        answerNextSubscribe(2)
        lateinit var zap: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            zap = scope.async { runtime.playLive(liveSelection(), intent) } // the settled zap
            settle()
            wrapper.stop() // arrives while the old target is still the active one
            settle()
        }
        await { zap.isCompleted }
        settle()
        assertTrue(zap.await()?.isStarted == true)
        // Whether the SDK resubscribes the same channel is timing-dependent; either way the
        // target the zap left behind is stopped.
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(1, stops.size)
        assertTrue(runtime.isPlaybackIntentStopped(intent))
    }

    @Test fun recordingRestoreQueuedBehindASessionStopDoesNotResurrectTheRecording() = exercise {
        recording()
        val stops = collectSessionStops()
        lateinit var restore: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            restore = scope.async { restoreRecording() }
            settle()
        }
        await { restore.isCompleted }
        settle()
        assertEquals(null, restore.await())
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertEquals(1, stops.size)
    }

    @Test fun recordingRestoreAfterTheStopButtonDoesNotResurrectTheRecording() = exercise {
        recording()
        runtime.stop()
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun recordingRestoreWithoutAnExplicitStopSinceTheLastIntentRestores() = exercise {
        // Process restore: nothing plays yet and nobody stopped.
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        // Stopped, then the viewer asked for playback again: a later restore works again.
        runtime.stop()
        runtime.notePlaybackIntent()
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun stopQueuedAfterASelectionWithdrawsItsDelayedStartWithoutAFailure() = exercise {
        live(false)
        val stops = collectSessionStops()
        lateinit var zap: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            // The live screen accepts a channel (one intent); its start waits for the zap to settle.
            val intent = runtime.notePlaybackIntent()
            settle()
            wrapper.stop() // the viewer's session Stop, after the selection
            settle()
            // The zap settles: the delayed start carries the selection's intent and mints none.
            zap = scope.async { runtime.playLive(liveSelection(), intent) }
            settle()
        }
        await { zap.isCompleted }
        settle()
        // Stop ran and closed the screen once; the delayed start neither started nor failed.
        assertEquals(null, zap.await())
        assertEquals(1, stops.size)
        assertEquals(1, connection.subscribeCount)
        assertEquals(1, connection.unsubscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun enteringALivePlayerAfterAUserStopStartsNothing() = exercise {
        live(false)
        runtime.stop() // the Stop button (or a session Stop) completes before the screen enters
        settle()
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(1, connection.subscribeCount)
        assertEquals(null, runtime.activeTarget.value)
        // A launch request (appliance entry, startup autoplay) is new intent: the live
        // player it opens stays open and starts its channel.
        runtime.notePlaybackIntent()
        val entry = requireNotNull(runtime.enterPlayerScreen())
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val start = scope.async { runtime.playLive(liveSelection(), entry) }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { start.isCompleted }
        assertTrue(start.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun enteringARecordingPlayerAfterASessionStopRestoresNothing() = exercise {
        recording()
        val stops = collectSessionStops()
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        assertNull(runtime.enterPlayerScreen())
        assertEquals(null, restoreRecording())
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun enteringWithoutAUserStopNotesIntentThatBeatsAnEarlierQueuedStop() = exercise {
        live(false)
        val entry = requireNotNull(runtime.enterPlayerScreen())
        assertFalse(runtime.isPlaybackIntentStopped(entry))
        assertNotNull(runtime.enterPlayerScreen()) // re-entry
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun automaticStopNeitherClosesAReenteredPlayerNorBlocksReconnectRetryOrRestore() = exercise {
        live(false)
        val stops = collectSessionStops()
        runtime.stopAfterLoss() // connection lost
        settle()
        assertEquals(1, connection.unsubscribeCount)
        assertNotNull(runtime.enterPlayerScreen())
        // Reconnected: the live screen's automatic retry resubscribes.
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val retry = scope.async { runtime.retryLive() }
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        connection.emit(started)
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun recordingRestoreAfterAnAutomaticStopRestores() = exercise {
        recording()
        runtime.stopAfterLoss()
        settle()
        assertEquals(null, runtime.activeTarget.value)
        assertTrue(restoreRecording()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun sessionStopTearsDownTimeshiftAndRecordingTargets() = exercise {
        val stops = collectSessionStops()
        live(true)
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 1 }
        recording()
        assertTrue(wrapper.isCommandAvailable(Player.COMMAND_STOP))
        wrapper.stop()
        await { runtime.activeTarget.value == null && stops.size == 2 }
        settle()
        assertEquals(2, stops.size)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
    }

    @Test fun sessionStopWithoutAScreenCollectingClosesNoLaterPlayer() = exercise {
        recording()
        wrapper.stop()
        await { runtime.activeTarget.value == null }
        settle()
        // A player opened later without new intent closes itself; one opened by a new
        // request (a click) must not close on the earlier stop.
        assertNull(runtime.enterPlayerScreen())
        runtime.notePlaybackIntent()
        assertNotNull(runtime.enterPlayerScreen())
        val stops = collectSessionStops()
        settle()
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun queuedStopAfterCloseDoesNotStop() = exercise {
        live(false)
        val stops = collectSessionStops()
        whileCommandsBlocked {
            wrapper.stop()
            settle()
            observation.cancel()
            wrapper.close()
        }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(0, connection.unsubscribeCount)
        assertEquals(emptyList<Unit>(), stops)
    }

    @Test fun metadataUsesNamesAndPublishesProgrammeAndTargetChanges() = exercise {
        val titles = mutableListOf<String?>()
        wrapper.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                titles += mediaMetadata.title?.toString()
            }
        })
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        live(true)
        await { wrapper.mediaMetadata.title == "Channel One" }
        assertEquals("Channel One", wrapper.mediaMetadata.artist)
        val now = Clock.System.now()
        val previous = session.observation.value
        session.publish(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState, dvrState = previous.dvrState,
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
            events = listOf(EpgEvent.create(EventId(1), ChannelId(1), start = now - 1.hours,
                stop = now + 1.hours, title = "Programme")),
        ))))
        await { wrapper.mediaMetadata.title == "Programme" }
        recording()
        await { wrapper.mediaMetadata.title == "Recording" }
        assertEquals("Recorded Channel", wrapper.mediaMetadata.subtitle)
        assertTrue(titles.containsAll(listOf("Channel One", "Programme", "Recording")))
        assertNull(wrapper.currentMediaItem?.localConfiguration)
        assertNull(wrapper.mediaMetadata.artworkUri)
        assertNull(wrapper.mediaMetadata.extras)
        assertEquals("", wrapper.currentMediaItem?.mediaId)
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted && wrapper.availableCommands == Player.Commands.EMPTY }
        assertNull(wrapper.currentMediaItem)
    }

    @Test fun closeLeavesRawPlayerAliveAndRejectsFurtherCommands() = exercise {
        recording()
        val requests = focusRequests
        observation.cancel()
        wrapper.close()
        wrapper.close()
        wrapper.pause()
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(requests, focusRequests)
        assertEquals(Player.Commands.EMPTY, wrapper.availableCommands)
        player.pause()
        assertFalse(player.playWhenReady)
    }

    // Real Media3 and the SDK's asynchronous subscription machinery require looper pumping.
    /**
     * A Retry (or the automatic reconnect) has begun but waits behind another command when the
     * viewer pauses: the retried channel starts paused and its server hold follows the first picture.
     */
    private suspend fun Fixture.pauseDuringARetryHoldsTheRetriedChannel(
        viewerRetry: Boolean,
        pause: suspend Fixture.() -> Unit,
    ) {
        live(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry) }
            settle()
            pause()
            settle()
        }
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        await { !player.playWhenReady }
        assertEquals(emptyList<Int>(), connection.speeds)
        grant(subscription = 2)
        playerReady()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    private fun exercise(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture(CoroutineScope(coroutineContext + SupervisorJob()))
        try { withTimeout(15_000) { fixture.block() } }
        finally {
            fixture.observation.cancel()
            fixture.wrapper.close()
            fixture.scope.cancel()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class Fixture(val scope: CoroutineScope) {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        var focusRequests = 0
        var focusGranted = true
        var focusListener: ((AudioInterruption) -> Unit)? = null
        private val focus = object : PlaybackAudioFocus {
            override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
                focusRequests++
                focusListener = onInterruption
                return focusGranted
            }
            override fun abandon() = Unit
        }
        val connection = ScriptedSubscriptionConnection()
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Channel One")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(
                id = DvrEntryId(1), state = DvrEntryState.COMPLETED, title = "Recording", channelName = "Recorded Channel",
            )))),
        )).apply { scriptLivePlaybackSuccess(manager); scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val player = ExoPlayer.Builder(context).build()
        private val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        /** Runtime listeners, so tests can drive STATE_READY, which the fake stream never reaches. */
        private val playerListeners = mutableListOf<Player.Listener>()
        val runtime = AppPlaybackRuntime(object : ExoPlayer by player {
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                player.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                player.removeListener(listener)
            }
        }, session, coordinator, settings, profiles, scope, TvheadendAudioOutputProvider(context), focus,
            PlaybackRuntimePolicy.fromPlayerSettings())
        val wrapper = SessionPlaybackPlayer(runtime)
        val observation = scope.launch { wrapper.observe(session.observation) }

        suspend fun live(timeshift: Boolean, firstPicture: Boolean = true) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
            }
            await { connection.subscribeCount == 1 }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            }
            // The first picture is ready: a server pause is only sent after it.
            if (firstPicture) playerReady()
            settle()
            // The session offers Stop before a test presses it, even on a loaded machine.
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        val started = SubscriptionEvent.Started(listOf(
            SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
        ), null, SubscriptionCondition.NO_DETAIL)

        fun liveSelection() = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))

        /** Installs live with timeshift requested ([intent] if given); its grant is delivered later by [grant]. */
        suspend fun startingLive(intent: Long? = null) {
            settings.setTimeshiftEnabled(true)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
            val install = scope.async { runtime.playLive(liveSelection(), intent) }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            await { runtime.livePause.value.availability == LivePauseAvailability.STARTING }
        }

        /** The subscription of the latest start is confirmed and granted. */
        suspend fun grant(subscription: Int = 1) {
            await { connection.subscribeCount == subscription }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
            await { runtime.livePause.value.availability == LivePauseAvailability.READY }
        }

        fun playerReady() {
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            settle()
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        /** Subscribes before returning, as a showing player screen would be. */
        suspend fun restoreRecording(): PlaybackTargetResult? = runtime.restoreRecordingRoute(
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
            RecordingPlaybackStart.START_OVER,
        )

        fun collectSessionStops(): List<Unit> = mutableListOf<Unit>().also { stops ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        }

        /** The runtime's command serializer, as in AutomaticAudioRuntimeTest; no runtime test seam. */
        val commands: PlaybackTargetCommandSerialization
            get() = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
                it.isAccessible = true
                it.get(runtime) as PlaybackTargetCommandSerialization
            }

        /** Answers the [count]th live subscription (scripted Ok, then Started) when it arrives. */
        fun answerNextSubscribe(count: Int) {
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
            scope.launch {
                await { connection.subscribeCount == count }
                connection.awaitCollectionRegistered()
                connection.emit(started)
            }
        }

        /** Pending session Stops registered at arrival; no runtime test seam. */
        fun pendingSessionStops(): Int =
            AppPlaybackRuntime::class.java.getDeclaredField("pendingStops").let {
                it.isAccessible = true
                it.getInt(runtime)
            }

        /** Serializer busy, session Stop arrives, then the viewer's warm opening action. */
        suspend fun warmOpeningAfterAQueuedStopKeepsThePlayerOpen(target: AppPlaybackTarget) {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                val opening = runtime.notePlaybackIntent()
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertEquals(opening + 1, entry) // not joined to the older pending Stop
                stops = collectSessionStops()
            }
            // Drained: the Stop has had its check, and was skipped.
            assertEquals(0, pendingSessionStops())
            assertEquals(0, connection.unsubscribeCount)
            assertEquals(target, runtime.activeTarget.value)
            assertFalse(runtime.isPlaybackIntentStopped(entry))
            assertEquals(emptyList<Unit>(), stops)
        }

        /** Serializer busy, the viewer's warm opening action, then a session Stop. */
        suspend fun warmOpeningBeforeAQueuedStopClosesWithIt() {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                val opening = runtime.notePlaybackIntent()
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertEquals(opening, entry) // joined: the Stop is the viewer's latest action
                assertEquals(entry, runtime.enterPlayerScreen()) // re-entry notes nothing either
                stops = collectSessionStops()
            }
            await { stops.size == 1 }
            assertEquals(null, runtime.activeTarget.value)
            assertTrue(runtime.isPlaybackIntentStopped(entry)) // the entry's own start is withdrawn
            assertEquals(1, stops.size)
        }

        suspend fun whileCommandsBlocked(block: suspend () -> Unit) {
            // Hold the existing serializer; no runtime test seam.
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = scope.launch {
                commands.serialize(onClosed = {}) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            try { block() } finally {
                release.complete(Unit)
                blocker.join()
            }
            // Drain commands queued by the wrapper before checking their effects.
            commands.serialize(onClosed = {}) {}
        }

        suspend fun await(predicate: () -> Boolean) {
            withTimeout(5_000) { while (!predicate()) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
        }
        suspend fun settle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
    }
}
