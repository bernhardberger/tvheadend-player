@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AudioInterruptionRuntimeTest {
    @Test fun timeshiftTransientPausesServerOnceAndGainResumesWithoutRetune() = exercise {
        live(timeshift = true)
        assertEquals(1, focus.requests.size)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) }
        assertFalse(player.playWhenReady)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        settle()
        assertEquals(listOf(0), connection.speeds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        settle()
        assertTrue(recoveries.isEmpty())
        assertEquals(1, connection.subscribeCount)
        focus.send(AudioInterruption.GAIN)
        await { player.playWhenReady && connection.speeds == listOf(0, 100) }
        assertEquals(1, connection.subscribeCount)
        assertNull(player.playerError)
    }

    @Test fun permanentAndNoisyStayPausedUntilExplicitPlay() = exercise {
        for (event in listOf(AudioInterruption.PERMANENT_LOSS, AudioInterruption.NOISY)) {
            live(timeshift = true)
            focus.send(event)
            await { !player.playWhenReady }
            focus.send(AudioInterruption.GAIN)
            settle()
            assertFalse(player.playWhenReady)
            val requests = focus.requests.size
            runtime.play()
            await { player.playWhenReady }
            assertEquals(requests + 1, focus.requests.size)
        }
    }

    @Test fun noTimeshiftMutesTrackTypeKeepsSubscriptionAndSurvivesSettings() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() }
        assertTrue(player.playWhenReady)
        val target = runtime.activeTarget.value
        settings.setAudioPassthroughEnabled(false)
        await { !output.isPassthroughEnabled }
        assertTrue(audioDisabled())
        // An audio-selection restore cannot take ownership away from the interruption.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
        assertTrue(audioDisabled())
        focus.send(AudioInterruption.GAIN)
        await { !audioDisabled() }
        assertTrue(player.playWhenReady)
        assertEquals(target, runtime.activeTarget.value)
        assertEquals(1, connection.subscribeCount)
        assertTrue(connection.speeds.isEmpty())
    }

    @Test fun persistentMuteRequiresViewerToggleAndDuckDoesNothing() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.TRANSIENT_LOSS_CAN_DUCK)
        settle()
        assertTrue(player.playWhenReady)
        assertFalse(audioDisabled())
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        focus.send(AudioInterruption.GAIN)
        settle()
        assertTrue(audioDisabled())
        runtime.pause() // Existing UI toggle sees video playing and calls pause.
        await { !audioDisabled() }
        assertTrue(player.playWhenReady)
        assertEquals(2, focus.requests.size)
    }

    @Test fun recordingsPauseResumeAndUserPauseCancelsAutoResume() = exercise {
        recording()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        focus.send(AudioInterruption.GAIN)
        await { player.playWhenReady }
        runtime.pause()
        await { !player.playWhenReady }
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        focus.send(AudioInterruption.GAIN)
        settle()
        assertFalse(player.playWhenReady)
    }

    @Test fun staleEpochBackgroundAndStopCannotBeResumedByOldGain() = exercise {
        live(timeshift = false)
        val old = focus.requests.last()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() }
        live(timeshift = false, channel = 2)
        assertFalse(audioDisabled())
        old(AudioInterruption.PERMANENT_LOSS)
        settle()
        assertFalse(audioDisabled())
        val current = focus.requests.last()
        val abandoned = focus.abandons
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        assertTrue(focus.abandons > abandoned)
        current(AudioInterruption.GAIN)
        settle()
        assertFalse(player.playWhenReady)
        val subscriptions = connection.subscribeCount
        runtime.onAppForegrounded()
        await { connection.subscribeCount > subscriptions }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value != null && player.playWhenReady }
        val beforeStop = focus.requests.last()
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted }
        beforeStop(AudioInterruption.GAIN)
        settle()
        assertNull(runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
    }

    @Test fun deniedStartKeepsLiveConsumingMutedUntilExplicitRequest() = exercise(granted = false) {
        live(timeshift = false)
        assertTrue(audioDisabled())
        assertTrue(player.playWhenReady)
        focus.send(AudioInterruption.GAIN)
        settle()
        assertTrue(audioDisabled())
        focus.granted = true
        runtime.play()
        await { !audioDisabled() }
        assertEquals(2, focus.requests.size)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun deniedExplicitPlayAfterPermanentLossRequiresAnotherRequest() = exercise {
        recording()
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady }
        focus.granted = false
        runtime.play()
        await { focus.requests.size == 2 }
        assertFalse(player.playWhenReady)
        focus.send(AudioInterruption.GAIN)
        settle()
        assertFalse(player.playWhenReady)
        focus.granted = true
        runtime.play()
        await { player.playWhenReady }
        assertEquals(3, focus.requests.size)
    }

    @Test fun failedReplacementPreservesPlayingTargetAndItsFocus() = exercise {
        live(timeshift = false)
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { install.isCompleted }
        assertFalse(install.await()?.isStarted == true)
        assertTrue(player.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(1, focus.requests.size)
    }

    @Test fun recordingInterruptedBeforeBackgroundDoesNotAutoResumeInForeground() = exercise {
        recording()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        val abandoned = focus.abandons
        runtime.onAppBackgrounded()
        await { focus.abandons > abandoned }
        focus.send(AudioInterruption.GAIN)
        runtime.onAppForegrounded()
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, focus.requests.size)
    }

    @Test fun rejectedServerPauseKeepsConsumingMutedAndGainRetriesServerResume() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() && player.playWhenReady }
        assertEquals(listOf(0), connection.speeds)
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        focus.send(AudioInterruption.GAIN)
        await { !audioDisabled() }
        assertTrue(player.playWhenReady)
        assertEquals(listOf(0, 100), connection.speeds)
    }

    @Test fun explicitTimeshiftResumeResolvesPermanentAndNoisyInterruptions() = exercise {
        for (event in listOf(AudioInterruption.PERMANENT_LOSS, AudioInterruption.NOISY)) {
            live(timeshift = true)
            focus.send(event)
            await { !player.playWhenReady }
            val requests = focus.requests.size
            assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.resumeTimeshift())
            assertTrue(player.playWhenReady)
            assertEquals(100, connection.speeds.last())
            assertEquals(requests + 1, focus.requests.size)
        }
    }

    @Test fun cancelledReplacementRestoresRetainedTargetAndFocus() = exercise {
        live(timeshift = false)
        val callback = focus.requests.last()
        val abandons = focus.abandons
        lateinit var replacement: Deferred<PlaybackTargetResult?>
        beforeRecordingBinding = {
            replacement.cancel()
            throw CancellationException("Cancelled replacement")
        }
        replacement = scope.async(start = CoroutineStart.LAZY) {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }
        replacement.start()
        await { replacement.isCompleted }
        assertTrue(replacement.isCancelled)
        assertTrue(player.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(abandons, focus.abandons)
        assertSame(callback, focus.requests.last())
        callback(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() }
    }

    @Test fun rejectedPauseIgnoresPausedObservationAndRejectedResumeUnmutes() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() && player.playWhenReady }
        connection.emit(SubscriptionEvent.Speed(0))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused == true }
        settle()
        assertTrue(audioDisabled())
        assertTrue(player.playWhenReady)
        focus.send(AudioInterruption.GAIN)
        await { !audioDisabled() }
        assertTrue(player.playWhenReady)
        assertEquals(listOf(0, 100), connection.speeds)
    }

    @Test fun exceptionalReplacementRestoresRetainedTargetButShutdownDoesNot() = exercise {
        live(timeshift = false)
        beforeRecordingBinding = { throw IllegalStateException("Synthetic binding failure") }
        val exceptional = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }
        await { exceptional.isCompleted }
        assertTrue(runCatching { exceptional.await() }.exceptionOrNull() is IllegalStateException)
        assertTrue(player.playWhenReady)
        assertEquals(1, focus.requests.size)
        lateinit var shutdown: Job
        beforeRecordingBinding = {
            shutdown = scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.detach() }
            throw CancellationException("Shutdown during replacement")
        }
        val cancelled = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }
        await { cancelled.isCompleted && shutdown.isCompleted }
        assertFalse(player.playWhenReady)
        assertEquals(1, focus.requests.size)
    }

    @Test fun rejectedExplicitResumeClearsPauseOwnershipAndReportsServerResult() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady }
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        assertNotEquals(TimeshiftCommandResult.ACCEPTED, runtime.resumeTimeshift())
        assertTrue(player.playWhenReady)
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.resumeTimeshift())
        assertEquals(listOf(0, 100, 100), connection.speeds)
    }

    @Test fun mutedLiveStillAdmitsRecovery() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady }
        assertTrue(audioDisabled())
        assertEquals(1, focus.requests.size)
        runtime.play()
        await { !audioDisabled() }
    }

    @Test fun mutedThroughRecoveryToggleOnlyRestoresSoundWithoutServerPause() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState is LiveTimeshiftState.Available }
        assertTrue(audioDisabled())
        assertTrue(runtime.isInterruptionMuted)
        // The backstop remains safe; the atomic operation restores only sound.
        assertEquals(TimeshiftCommandResult.UNAVAILABLE, runtime.pauseTimeshift())
        assertNull(runtime.pauseTimeshiftPlayback())
        await { !audioDisabled() }
        assertFalse(runtime.isInterruptionMuted)
        assertTrue(player.playWhenReady)
        assertTrue(connection.speeds.isEmpty())
        assertEquals(2, focus.requests.size)
    }

    @Test fun oneTimeshiftPlayActionRequestsFocusAndResumesServerOnce() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady }
        val requests = focus.requests.size
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.resumeTimeshift())
        assertTrue(player.playWhenReady)
        assertEquals(requests + 1, focus.requests.size)
        assertEquals(listOf(0, 100), connection.speeds)
    }

    @Test fun pauseQueuedDuringMutedResumePausesLocallyAndOnServer() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.NOISY)
        await { runtime.isInterruptionMuted }
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        val resumeEntered = CompletableDeferred<Unit>()
        val finishResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100) {
                resumeEntered.complete(Unit)
                finishResume.await()
            } else withContext(scope.coroutineContext) { assertFalse(player.playWhenReady) }
        }
        val resume = scope.async { runtime.resumeTimeshift() }
        resumeEntered.await()
        assertTrue(runtime.isInterruptionMuted)
        val pause = scope.async(start = CoroutineStart.UNDISPATCHED) { runtime.pauseTimeshiftPlayback() }
        assertFalse(pause.isCompleted)
        finishResume.complete(Unit)
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        assertEquals(TimeshiftCommandResult.ACCEPTED, pause.await())
        assertFalse(player.playWhenReady)
        assertFalse(runtime.isInterruptionMuted)
        assertEquals(listOf(0, 100, 0), connection.speeds)
        assertEquals(2, focus.requests.size)
    }

    @Test fun atomicPauseRollsBackRejectedIntentWithoutFocusAndKeepsTimeoutPaused() = exercise {
        live(timeshift = true)
        beforeSpeed = { withContext(scope.coroutineContext) { assertFalse(player.playWhenReady) } }
        val requests = focus.requests.size
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        assertEquals(TimeshiftCommandResult.SERVER_REJECTED, runtime.pauseTimeshiftPlayback())
        assertTrue(player.playWhenReady)
        assertEquals(requests, focus.requests.size)
        connection.scriptSpeed(SubscriptionOperationResult.Timeout)
        assertEquals(TimeshiftCommandResult.TIMEOUT, runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertEquals(requests, focus.requests.size)
    }

    @Test fun rejectedPauseAfterRuntimeCloseDoesNotRestorePlayback() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        beforeSpeed = {
            withContext(scope.coroutineContext) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.detach() }
            }
        }
        assertEquals(TimeshiftCommandResult.SERVER_REJECTED, runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
    }

    @Test fun mutedSoundRestorationReleasesUnconfirmedInterruptionHoldOnce() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.Timeout)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { runtime.isInterruptionMuted && player.playWhenReady }
        connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
        assertNull(runtime.pauseTimeshiftPlayback())
        assertFalse(runtime.isInterruptionMuted)
        assertTrue(player.playWhenReady)
        assertEquals(listOf(0, 100), connection.speeds)
        // The released hold is gone: the next Pause is an ordinary pause.
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertEquals(listOf(0, 100, 0), connection.speeds)
    }

    @Test fun mutedSoundRestorationNeverSendsServerCommandsEvenWithDeniedFocus() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.NOISY)
        await { runtime.isInterruptionMuted }
        val speeds = connection.speeds.toList()
        focus.granted = false
        assertNull(runtime.pauseTimeshiftPlayback())
        assertTrue(runtime.isInterruptionMuted)
        focus.granted = true
        assertNull(runtime.pauseTimeshiftPlayback())
        assertFalse(runtime.isInterruptionMuted)
        assertTrue(player.playWhenReady)
        assertEquals(speeds, connection.speeds)
    }

    @Test fun deniedResumeWithRejectedServerPauseExposesRuntimeInterruptionOwnership() = exercise {
        live(timeshift = true)
        runtime.pause()
        await { !player.playWhenReady }
        focus.granted = false
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        val requests = focus.requests.size
        assertEquals(TimeshiftCommandResult.UNAVAILABLE, runtime.resumeTimeshift())
        assertTrue(runtime.hasAudioInterruption)
        assertTrue(runtime.isInterruptionMuted)
        assertEquals(requests + 1, focus.requests.size)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(requests + 1, focus.requests.size)
    }

    @Test fun deniedFocusDuringTimeshiftRecoveryStillSendsServerPause() = exercise {
        live(timeshift = true)
        focus.granted = false
        // Let the replacement subscription publish its timeshift grant before focus returns.
        // Normally ExoPlayer prepares asynchronously, so the new observation can still be Pending.
        focus.beforeRequest = {
            runBlocking {
                await { connection.subscribeCount == 2 }
                startSubscription(connection.awaitCollectionRegistered())
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState is LiveTimeshiftState.Available }
            }
        }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { focus.requests.size == 2 }
        await { connection.speeds.contains(0) }
        assertFalse(player.playWhenReady)
    }

    @Test fun deniedResumePreservesServerHoldWhenTimeshiftDisappears() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady }
        manager.closeAndJoin()
        await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState !is LiveTimeshiftState.Available }
        focus.granted = false
        runtime.play()
        await { focus.requests.size == 2 }
        assertFalse(player.playWhenReady)
        assertFalse(audioDisabled())
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun queuedAutomaticPreviewThenBackRestoresExactManualAndRememberedChoice() = exercise {
        live(timeshift = false)
        val manual = installManualAudio()
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { entered.complete(Unit); release.await() } }
        entered.await()
        val epoch = runtime.videoPresentation.value.epoch
        val preview = runtime.selectQuickListAudio(epoch, null)
        val back = runtime.selectQuickListAudio(epoch, manual)
        settle()
        assertFalse(preview.isCompleted)
        release.complete(Unit)
        blocker.join(); preview.join(); back.join()
        settle()
        assertSame(manual.mediaTrackGroup, player.trackSelectionParameters.overrides.values.single().mediaTrackGroup)
        assertNotNull(settings.audioChoices.read("test-profile", ChannelId(1)))
        // Retain the session choice, not just the immediate player parameters.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        audioSelection.restore(runtimePlayer)
        assertSame(manual.mediaTrackGroup, player.trackSelectionParameters.overrides.values.single().mediaTrackGroup)
    }

    @Test fun queuedAutomaticFromPredecessorCannotClearSuccessorManualChoice() = exercise {
        live(timeshift = false)
        val manual = installManualAudio()
        settle()
        settings.audioChoices.write("test-profile", ChannelId(2), requireNotNull(settings.audioChoices.read("test-profile", ChannelId(1))))
        val oldEpoch = runtime.videoPresentation.value.epoch
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { entered.complete(Unit); release.await() } }
        entered.await()
        val replacement = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
            markReady()
        }
        settle()
        val stalePreview = runtime.selectQuickListAudio(oldEpoch, null)
        settle()
        assertFalse(stalePreview.isCompleted)
        release.complete(Unit)
        blocker.join(); replacement.await(); stalePreview.join()
        // Consume the replacement's stream registration so live() below targets its successor.
        connection.awaitCollectionRegistered()
        settle()
        assertSame(manual.mediaTrackGroup, player.trackSelectionParameters.overrides.values.single().mediaTrackGroup)
        assertNotNull(settings.audioChoices.read("test-profile", ChannelId(2)))
        assertFalse(runtime.isQuickListTargetCurrent(oldEpoch))
        val epoch = runtime.videoPresentation.value.epoch
        assertTrue(runtime.isQuickListTargetCurrent(epoch))
        live(timeshift = false, channel = 2)
        assertFalse(runtime.isQuickListTargetCurrent(epoch))
    }

    @Test fun audioBackAfterFocusGainKeepsAudioEnabledAndAutomaticBackForgetsPreview() = exercise {
        live(timeshift = false)
        val manual = installManualAudio()
        val epoch = runtime.videoPresentation.value.epoch
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() }
        focus.send(AudioInterruption.GAIN)
        await { !audioDisabled() }
        runtime.selectQuickListAudio(epoch, manual).join()
        assertFalse(audioDisabled())
        runtime.selectQuickListAudio(epoch, null).join()
        settle()
        audioSelection.restore(runtimePlayer)
        assertTrue(player.trackSelectionParameters.overrides.isEmpty())
        assertNull(settings.audioChoices.read("test-profile", ChannelId(1)))
    }

    @Test fun startupBufferKnowsTheTargetKindBeforePreparation() = exercise {
        assertFalse(startupBuffer.isLiveTarget)
        live(timeshift = false)
        assertTrue(startupBuffer.isLiveTarget)
        var liveDuringRecordingInstall: Boolean? = null
        beforeRecordingBinding = { liveDuringRecordingInstall = startupBuffer.isLiveTarget }
        recording()
        assertEquals(false, liveDuringRecordingInstall)
        assertFalse(startupBuffer.isLiveTarget)

        val subscriptions = connection.subscribeCount
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { connection.subscribeCount > subscriptions }
        assertTrue("Live before the source starts", startupBuffer.isLiveTarget)
        startSubscription(connection.awaitCollectionRegistered())
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)

        // A failed recording replacement leaves the live target, and the live threshold, in place.
        beforeRecordingBinding = { throw IllegalStateException("Synthetic binding failure") }
        val failed = scope.async {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }
        await { failed.isCompleted }
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertTrue(startupBuffer.isLiveTarget)
    }

    @Test fun interruptionDuringAdmittedRecoveryBackoffRetunesOnceAfterGain() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        // The backoff passes while the interruption holds playback: nothing retunes.
        delay(2_500)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        assertFalse(player.playWhenReady)
        focus.send(AudioInterruption.GAIN)
        await { connection.subscribeCount == subscriptions + 1 }
        assertEquals(listOf(0, 100), connection.speeds)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(audioDisabled())
        delay(2_500)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun rejectedInterruptionHoldKeepsTheAdmittedRecoveryAndItsMute() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        val requests = focus.requests.size
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { connection.subscribeCount == subscriptions + 1 }
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue(audioDisabled())
        assertEquals(requests, focus.requests.size)
        delay(2_500)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun permanentLossDuringRecoveryBackoffRetunesOnlyOnExplicitPlay() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        delay(2_500)
        focus.send(AudioInterruption.GAIN)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(subscriptions, connection.subscribeCount)
        runtime.play()
        await { connection.subscribeCount == subscriptions + 1 }
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun viewerPauseDuringTheInterruptionKeepsTheRecoveryOwedUntilPlay() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.pauseTimeshiftPlayback()
        focus.send(AudioInterruption.GAIN)
        delay(300)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(subscriptions, connection.subscribeCount)
        runtime.play()
        await { connection.subscribeCount == subscriptions + 1 }
    }

    @Test fun interruptedRecoveryNeverRetunesAReplacementTarget() = exercise {
        awaitSecondRecoveryBackoff()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        live(timeshift = true, channel = 2)
        val subscriptions = connection.subscribeCount
        runtime.play()
        focus.send(AudioInterruption.GAIN)
        delay(300)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)

        // Another recovery interrupted, then replaced by a recording.
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == subscriptions + 1 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        recording()
        runtime.play()
        delay(2_500)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
    }

    @Test fun interruptedRecoveryOfAReplacedSessionIsDropped() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        val previous = session.observation.value
        session.replaceGeneration(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState,
            epgState = previous.epgState, dvrState = previous.dvrState,
        ))
        focus.send(AudioInterruption.GAIN)
        delay(300)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

    @Test fun stopAndDetachEndAnInterruptedRecovery() = exercise {
        awaitSecondRecoveryBackoff()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        val beforeStop = focus.requests.last()
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted }
        beforeStop(AudioInterruption.GAIN)
        live(timeshift = true)
        val subscriptions = connection.subscribeCount
        delay(300)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)

        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == subscriptions + 1 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        val beforeDetach = focus.requests.last()
        runtime.detach()
        beforeDetach(AudioInterruption.GAIN)
        delay(2_500)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun backgroundTurnsAnInterruptedRecoveryIntoOneForegroundRetune() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        val callback = focus.requests.last()
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        callback(AudioInterruption.GAIN)
        delay(2_500)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        runtime.onAppForegrounded()
        await { connection.subscribeCount == subscriptions + 1 }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) && player.playWhenReady }
        delay(300)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun viewerRetryOfAMutedChannelStartsFreshWithFocusAndSound() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertFalse(audioDisabled())
        assertTrue(player.playWhenReady)
        assertEquals(2, focus.requests.size)
        // The new request owns focus: a later loss mutes again.
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { audioDisabled() }
    }

    @Test fun viewerRetryWithDeniedFocusStaysSafelyMuted() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        focus.granted = false
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { retry.isCompleted }
        assertEquals(2, focus.requests.size)
        assertTrue(audioDisabled())
        assertTrue(player.playWhenReady)
    }

    @Test fun automaticRetryKeepsTheInterruptionMute() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        val retry = scope.async { runtime.retryLive() }
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertTrue(audioDisabled())
        assertTrue(player.playWhenReady)
        assertEquals(1, focus.requests.size)
    }

    @Test fun viewerRetryFromFailedShowsStartingWhileTheTunerIsAcquired() = exercise {
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val failed = runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
        assertFalse(failed?.isStarted == true)
        assertTrue(runtime.state.value is AppPlaybackState.Failed)
        session.scriptLivePlaybackSuccess(manager)
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        val subscriptions = connection.subscribeCount
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { connection.subscribeCount > subscriptions }
        // The viewer sees the retry start while the channel is still being bound and admitted.
        assertEquals(listOf(AppPlaybackState.Starting), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun failedViewerRetryKeepsTheMutedTargetItsStateAndFocus() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        settle()
        val state = runtime.state.value
        val abandons = focus.abandons
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { retry.isCompleted }
        assertFalse(retry.await()?.isStarted == true)
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(state, runtime.state.value)
        assertTrue(audioDisabled())
        assertTrue(player.playWhenReady)
        assertEquals(1, focus.requests.size)
        assertEquals(abandons, focus.abandons)
    }

    @Test fun pauseQueuedBehindTheFocusGainKeepsTheResumedRecoveryOwedUntilPlay() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        // The gain's server resume is still in flight when the viewer presses Pause.
        val resumeSent = CompletableDeferred<Unit>()
        val releaseResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100 && resumeSent.complete(Unit)) releaseResume.await()
        }
        focus.send(AudioInterruption.GAIN)
        await { resumeSent.isCompleted }
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        settle()
        assertFalse(pause.isCompleted)
        releaseResume.complete(Unit)
        await { pause.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, pause.await())
        delay(300)
        settle()
        // The resumed recovery ran after the Pause: it neither retuned nor played over it.
        assertFalse(player.playWhenReady)
        assertEquals(subscriptions, connection.subscribeCount)
        assertEquals(listOf(0, 100, 0), connection.speeds)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // It is still owed: the viewer's Play retunes the target once.
        runtime.play()
        await { connection.subscribeCount == subscriptions + 1 }
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        delay(300)
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun pauseQueuedBehindTheResumedRetuneHoldsTheNewSubscription() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        focus.send(AudioInterruption.GAIN)
        await { connection.subscribeCount == subscriptions + 1 }
        // The viewer pauses while the retune is under way: its commit cannot interleave with the
        // Pause, so the Pause serializes after it and addresses the retuned target.
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { pause.isCompleted }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        // The Pause belongs to the retuned target and is held on its server once its picture is up.
        await { connection.speeds == listOf(0, 100, 0) }
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(subscriptions + 1, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun recoveryRequestedWhileAnInterruptionHoldsPlaybackIsAdmittedAfterGain() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        // The SDK escalates while the interruption holds playback, even repeatedly.
        repeat(3) { runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED) }
        delay(300)
        settle()
        assertEquals(1, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        assertFalse(player.playWhenReady)
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        focus.send(AudioInterruption.GAIN)
        await { connection.subscribeCount == 2 }
        // Admitted as the target's first attempt: waiting claimed no attempt and no backoff.
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun recoveryQueuedBehindAnInterruptionPauseIsAdmittedAfterGain() = exercise {
        live(timeshift = true)
        val release = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { release.await() } }
        settle()
        // The focus loss queues first, the escalation behind it.
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        settle()
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        release.complete(Unit)
        blocker.join()
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        delay(300)
        settle()
        assertEquals(1, connection.subscribeCount)
        focus.send(AudioInterruption.GAIN)
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun stopAndANewChannelEndADeferredRecovery() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        val beforeStop = focus.requests.last()
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted }
        beforeStop(AudioInterruption.GAIN)
        live(timeshift = true)
        var subscriptions = connection.subscribeCount
        runtime.play()
        delay(300)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)

        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        live(timeshift = true, channel = 2)
        subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.GAIN)
        runtime.play()
        delay(300)
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

    @Test fun backgroundTurnsADeferredRecoveryIntoOneForegroundRetune() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        val callback = focus.requests.last()
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        callback(AudioInterruption.GAIN)
        delay(300)
        settle()
        assertEquals(1, connection.subscribeCount)
        runtime.onAppForegrounded()
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) && player.playWhenReady }
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun resumedRecoveryStaysPresentedAsRecoveringUntilItRetunes() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        // A rejected hold goes on muted and resumes the stopped attempt in the same command,
        // while that attempt is still unwinding.
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { connection.subscribeCount == subscriptions + 1 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun stoppedAttemptUnwindingAfterTheResumeDoesNotRepublishOverIt() = exercise {
        val events = mutableListOf<String>()
        val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
        val stopped = LiveRecoveryFence(PlaybackRecoveryReason.LIVE_ENDED, selection, targetEpoch = 1L)
        var admitted = false
        val recovery = LiveRecoveryController(scope, PlaybackTargetCommandSerialization()) { fence, _ ->
            events += if (fence === stopped) "stopped attempt resolved" else "resumed attempt resolved"
        }
        recovery.dispatch(
            PlaybackRecoveryReason.LIVE_ENDED,
            admitLocked = {
                recovery.admitLocked(stopped)
                admitted = true
                stopped to LiveRecoveryAttempt(attempt = 2, delayMillis = 60_000L)
            },
            retryLocked = { events += "stopped attempt retuned"; null },
        )
        await { admitted }
        recovery.cancelOnInterruptionLocked(Job())
        assertEquals(stopped, recovery.interruptedRecovery)
        // Playback may continue before the stopped attempt has unwound from its backoff.
        recovery.resumeInterruptedLocked({ events += "resumed attempt retuned"; null }) {
            events += "Recovering published"
        }
        await { "resumed attempt resolved" in events }
        settle()
        assertEquals(listOf("Recovering published", "resumed attempt retuned", "resumed attempt resolved"), events)
    }

    @Test fun pauseQueuedBehindTheFocusGainKeepsADeferredRecoveryUnadmittedUntilPlay() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        // The gain's server resume is still in flight when the viewer presses Pause.
        val resumeSent = CompletableDeferred<Unit>()
        val releaseResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100 && resumeSent.complete(Unit)) releaseResume.await()
        }
        focus.send(AudioInterruption.GAIN)
        await { resumeSent.isCompleted }
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        settle()
        assertFalse(pause.isCompleted)
        releaseResume.complete(Unit)
        await { pause.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, pause.await())
        delay(300)
        settle()
        // The deferred request reached admission after the Pause: it neither retuned nor played.
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertEquals(listOf(0, 100, 0), connection.speeds)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // Still unadmitted: the viewer's Play admits it as the target's first attempt.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.play()
        await { connection.subscribeCount == 2 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun pauseDuringTheBackoffOfAnAdmittedDeferredRecoveryKeepsItOwedWithoutAnotherAttempt() = exercise {
        live(timeshift = true)
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        focus.send(AudioInterruption.GAIN)
        // Admitted after the gain as the second attempt, it waits out its backoff.
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.pauseTimeshiftPlayback())
        delay(2_500)
        settle()
        // Its retune found the viewer's Pause: nothing retuned or played over it.
        assertFalse(player.playWhenReady)
        assertEquals(2, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // It stays owed: the viewer's Play retunes at once, claiming no further attempt.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.play()
        await { connection.subscribeCount == 3 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun channelChangeQueuedBehindTheFocusGainNeitherRetunesNorSpendsItsBudget() = exercise {
        live(timeshift = true)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        settle()
        val resumeSent = CompletableDeferred<Unit>()
        val releaseResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100 && resumeSent.complete(Unit)) releaseResume.await()
        }
        focus.send(AudioInterruption.GAIN)
        await { resumeSent.isCompleted }
        // The viewer changes channel while the gain's server resume is in flight.
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        settle()
        releaseResume.complete(Unit)
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { zap.isCompleted }
        assertTrue(zap.await()?.isStarted == true)
        delay(300)
        settle()
        // The request deferred for channel 1 does not retune channel 2.
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // Channel 2's own first recovery still finds its whole budget: admitted without backoff.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 3 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun sessionPauseQueuedDuringARecoveryRetuneHoldsTheRecoveredChannel() = exercise {
        live(timeshift = true)
        // The system's Pause arrives while the automatic retune is under way, before it commits.
        var pause: Job? = null
        beforeLiveBinding = { if (pause == null) pause = runtime.setSessionPlayWhenReady(false) { true } }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { pause?.isCompleted == true }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        // The recovered target is the same channel for the same intent: the Pause holds it.
        await { connection.speeds == listOf(0) }
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun channelChangeDuringARecoveryRetuneDropsTheSessionPauseQueuedBeforeIt() = exercise {
        live(timeshift = true)
        // The system's Pause arrives during the retune, and a channel change right after it.
        var pause: Job? = null
        var zap: Deferred<PlaybackTargetResult?>? = null
        beforeLiveBinding = {
            if (pause == null) {
                pause = runtime.setSessionPlayWhenReady(false) { true }
                zap = scope.async {
                    runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
                }
            }
        }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        // The retune and then the channel change both install (subscribe order: 2 and 3).
        await { connection.subscribeCount == 3 && zap?.isCompleted == true && pause?.isCompleted == true }
        connection.awaitCollectionRegistered()
        val channel2 = connection.awaitCollectionRegistered()
        startSubscription(channel2)
        assertTrue(zap?.await()?.isStarted == true)
        connection.emit(channel2, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        delay(300)
        settle()
        // The newer channel wins: the Pause touches neither the recovered nor the new target.
        assertTrue(player.playWhenReady)
        assertEquals(emptyList<Int>(), connection.speeds)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun pauseDuringTheBackoffOfAnOrdinaryRecoveryKeepsItOwedUntilPlay() = exercise {
        awaitSecondRecoveryBackoff()
        // The viewer pauses while the second attempt waits out its backoff.
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.pauseTimeshiftPlayback())
        delay(2_500)
        settle()
        // Its retune found the viewer's Pause: nothing retuned or played over it.
        assertFalse(player.playWhenReady)
        assertEquals(2, connection.subscribeCount)
        assertEquals(0, connection.speeds.last())
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // It stays owed: the viewer's Play retunes once, at once, claiming no further attempt.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.play()
        await { connection.subscribeCount == 3 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        delay(300)
        settle()
        assertEquals(3, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        // The paused attempt was budgeted once: the next escalation is the third attempt.
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 5_000L }
    }

    @Test fun recoveryRequestedWhileTheViewerIsPausedWaitsUnadmittedUntilPlay() = exercise {
        live(timeshift = true)
        assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.pauseTimeshiftPlayback())
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        delay(300)
        settle()
        // Deferred: no retune, no startup Play, no Recovering state over the viewer's Pause.
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // The viewer's Play admits it as the target's first attempt.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.play()
        await { connection.subscribeCount == 2 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun escalationQueuedBehindAChannelChangeNeitherRetunesNorSpendsItsBudget() = exercise {
        live(timeshift = true)
        // Channel 1 escalates while channel 2's install holds the command lock.
        var escalated = false
        beforeLiveBinding = {
            if (!escalated) {
                escalated = true
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            }
        }
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        // Channel 2's install completes before its stream starts: nothing started yet that a
        // stale retune could replace, so the count below decides before the fixture emits.
        await { zap.isCompleted }
        assertTrue(escalated)
        assertTrue(zap.await()?.isStarted == true)
        delay(300)
        settle()
        // Channel 1's escalation is stale once channel 2 committed: dropped silently.
        assertEquals("channel 1's stale escalation retuned channel 2", 2, connection.subscribeCount)
        startSubscription(connection.awaitCollectionRegistered())
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        assertTrue(player.playWhenReady)
        // Channel 2's own first recovery still finds its whole budget: admitted without backoff.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 3 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun rejectedPendingPauseGoesOnWithTheRecoveryItDeferred() = exercise {
        live(timeshift = true, firstPicture = false)
        // Before the first picture the viewer's Pause waits locally; a recovery arriving now
        // waits unadmitted behind it.
        assertNull(runtime.pauseTimeshiftPlayback())
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        delay(300)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // The first picture sends the held pause; the server rejects it and playback goes on.
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        markReady()
        await { runtime.livePauseNotice.value != null }
        assertEquals(listOf(0), connection.speeds)
        delay(300)
        settle()
        // Playing again, the deferred recovery goes on without a Play from the viewer: one
        // retune, admitted as the target's first attempt.
        assertEquals("the recovery the rejected pause deferred stayed stranded", 2, connection.subscribeCount)
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        // One attempt was spent: the next escalation is the second attempt.
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
    }

    @Test fun pendingPauseWithoutAGrantGoesOnWithTheRecoveryItDeferred() = exercise {
        live(timeshift = true, granted = false, firstPicture = false)
        // The grant is still undecided: the Pause waits locally, and so does the recovery.
        assertNull(runtime.pauseTimeshiftPlayback())
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        delay(300)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        // The first picture without a grant drops the Pause without any server command.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        markReady()
        await { runtime.livePauseNotice.value != null }
        delay(300)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertEquals("the recovery the dropped pause deferred stayed stranded", 2, connection.subscribeCount)
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun escalationOfTheFirstInstallsOwnSourceRetunesItOnceItCommits() = exercise {
        // The SDK reports the new source's escalation (here: at once) before the install that
        // set the source has committed in the runtime.
        escalateOnNextSource()
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
        }
        // The install completes before its stream starts; nothing is emitted before the count.
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        // Bounded, so a dropped escalation still fails on the count below.
        withTimeoutOrNull(3_000) { await { connection.subscribeCount >= 2 } }
        delay(300)
        settle()
        assertEquals("the first install's own escalation was dropped", 2, connection.subscribeCount)
        assertEquals(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L), installing.last())
        connection.awaitCollectionRegistered()
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        delay(300)
        settle()
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun escalationOfAChannelChangesNewSourceRetunesTheNewChannelOnce() = exercise {
        live(timeshift = false)
        escalateOnNextSource()
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted }
        assertTrue(zap.await()?.isStarted == true)
        // Bounded, so a dropped escalation still fails on the count below.
        withTimeoutOrNull(3_000) { await { connection.subscribeCount >= 3 } }
        delay(300)
        settle()
        assertEquals("channel 2's own escalation was dropped", 3, connection.subscribeCount)
        connection.awaitCollectionRegistered()
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        delay(300)
        settle()
        assertEquals(3, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun escalationOfAnInstallThatANewerChannelChangeReplacedIsDropped() = exercise {
        live(timeshift = false)
        // Channel 2's source escalates at once; a change back to channel 1 is already queued.
        var zapBack: Deferred<PlaybackTargetResult?>? = null
        escalateOnNextSource {
            zapBack = scope.async(start = CoroutineStart.UNDISPATCHED) {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
            }
        }
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted && zapBack?.isCompleted == true }
        delay(300)
        settle()
        // Channel 1 committed after channel 2: channel 2's escalation touches it not.
        assertEquals(3, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // Channel 1's own first recovery still finds its whole budget.
        connection.awaitCollectionRegistered()
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady }
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 4 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
    }

    @Test fun escalationOfATargetRestoredAfterAFailedReplacementRetunesThatTarget() = exercise {
        live(timeshift = false)
        // Channel 2's install sets its source, then fails; the SDK restores channel 1's source
        // and delivers channel 1's pending escalation while that install is still unwinding.
        failNextPrepare = true
        var sources = 0
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || timeline.isEmpty) return
                if (++sources == 2) runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            }
        })
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted }
        // Channel 2's source, then channel 1's restored one (a retune may add its own after).
        assertTrue(sources >= 2)
        assertFalse(zap.await()?.isStarted == true)
        // Bounded, so a dropped escalation still fails on the bindings below.
        withTimeoutOrNull(3_000) { await { installing.size >= 2 } }
        delay(300)
        settle()
        // Channel 1 stays and its escalation retunes it once, as its first attempt. (Bindings
        // count the retunes: the restored source may subscribe once more on its own.)
        assertEquals("the restored channel's escalation was dropped", 2, installing.size)
        assertEquals(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L), installing.last())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        // Charged to channel 1 only: its next escalation is the second attempt, and nothing
        // else retuned meanwhile.
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        assertEquals(2, installing.size)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun installsEscalationIsDroppedWhenTheSessionIsReplacedBeforeItsAdmission() = exercise {
        live(timeshift = false)
        // Channel 2's source escalates during its install; a command queued first holds the
        // admission after the commit, while the session is replaced.
        val admissionHeld = CompletableDeferred<Unit>()
        var holder: Job? = null
        escalateOnNextSource {
            holder = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                commands.serialize(onClosed = {}) { admissionHeld.await() }
            }
        }
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted }
        assertTrue(zap.await()?.isStarted == true)
        assertNotNull(holder)
        val previous = session.observation.value
        session.replaceGeneration(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState,
            epgState = previous.epgState, dvrState = previous.dvrState,
        ))
        settle()
        admissionHeld.complete(Unit)
        await { holder?.isCompleted == true }
        delay(300)
        settle()
        // Reported under the replaced session: dropped, neither retuning nor charging channel 2.
        assertEquals("the replaced session's escalation retuned channel 2", 2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

    /**
     * Reports an escalation when the SDK sets the next target's source, as its recovery may do
     * for a source that ended at once: before that install commits in the runtime.
     */
    private fun Fixture.escalateOnNextSource(beforeReport: () -> Unit = {}) {
        var reported = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reported || reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || timeline.isEmpty) return
                reported = true
                beforeReport()
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            }
        })
    }

    /** Live timeshift whose second recovery attempt is admitted and waits out its 2 s backoff. */
    private suspend fun Fixture.awaitSecondRecoveryBackoff() {
        live(timeshift = true)
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
    }

    private fun exercise(granted: Boolean = true, block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture(CoroutineScope(coroutineContext + SupervisorJob()), granted)
        try { withTimeout(15_000) { fixture.block() } }
        finally {
            fixture.scope.cancel()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class FakeFocus(var granted: Boolean) : PlaybackAudioFocus {
        val requests = mutableListOf<(AudioInterruption) -> Unit>()
        private var callback: ((AudioInterruption) -> Unit)? = null
        var beforeRequest: () -> Unit = {}
        var abandons = 0
        override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
            beforeRequest()
            requests += onInterruption
            callback = onInterruption.takeIf { granted }
            return granted
        }
        override fun abandon() { abandons++; callback = null }
        fun send(event: AudioInterruption) { callback?.invoke(event) }
    }

    private class Fixture(val scope: CoroutineScope, granted: Boolean) {
        private val context = ApplicationProvider.getApplicationContext<Application>()
        val focus = FakeFocus(granted)
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        }
        var beforeSpeed: suspend (Int) -> Unit = {}
        val manager = createSubscriptionManager(object : SubscriptionConnection by connection {
            override suspend fun speed(id: SubscriptionId, speed: Int): SubscriptionOperationResult<Unit> {
                beforeSpeed(speed)
                return connection.speed(id, speed)
            }
        }, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(id = DvrEntryId(1), state = DvrEntryState.COMPLETED)))),
        )).apply {
            scriptLivePlaybackSuccess(manager)
            scriptRecordingPlaybackSuccess()
        }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val output = TvheadendAudioOutputProvider(context)
        val player = ExoPlayer.Builder(context).build()
        val recoveries = mutableListOf<PlaybackRecoveryReason>()
        /** Fails the SDK's next prepare, after it set the new source: it restores the previous one. */
        var failNextPrepare = false
        private val coordinatorPlayer = object : ExoPlayer by player {
            override fun prepare() {
                if (failNextPrepare) {
                    failNextPrepare = false
                    throw IllegalStateException("scripted prepare failure")
                }
                player.prepare()
            }
        }
        private val coordinator = createTvheadendPlaybackCoordinator(coordinatorPlayer, onRecoveryRequired = {
            recoveries += it
            recover(it)
        }).also { it.launchIn(scope) }
        var beforeRecordingBinding: () -> Unit = {}
        /** Runs inside a live target install, before the session binds the channel. */
        var beforeLiveBinding: () -> Unit = {}
        private val runtimeSession = object : TvheadendSession by session {
            override fun bindLivePlayback(currentSession: CurrentSessionObservation, channelId: ChannelId): PlaybackBindingResult<PlaybackBinding.Live> {
                beforeLiveBinding()
                return session.bindLivePlayback(currentSession, channelId)
            }
            override fun bindRecordingPlayback(currentSession: CurrentSessionObservation, recordingId: DvrEntryId): PlaybackBindingResult<PlaybackBinding.Recording> {
                beforeRecordingBinding()
                return session.bindRecordingPlayback(currentSession, recordingId)
            }
        }
        /** Runtime listeners, so tests can drive STATE_READY, which the fake stream never reaches. */
        private val playerListeners = mutableListOf<Player.Listener>()
        var audioTracks: Tracks? = null
        var reportedPlaybackState: Int? = null
        val runtimePlayer = object : ExoPlayer by player {
            override fun getCurrentTracks(): Tracks = audioTracks ?: player.currentTracks
            override fun getPlaybackState(): Int = reportedPlaybackState ?: player.playbackState
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                player.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                player.removeListener(listener)
            }
        }
        val startupBuffer = StartupBufferController(StartupBufferLoadControl(androidx.media3.exoplayer.DefaultLoadControl()),
            settings, profiles.startupBufferIdentity(), scope)
        val runtime = AppPlaybackRuntime(runtimePlayer, runtimeSession, coordinator, settings, profiles, scope, output, focus,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            startupBuffer = startupBuffer)
        val commands: PlaybackTargetCommandSerialization get() = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
            it.isAccessible = true; it.get(runtime) as PlaybackTargetCommandSerialization
        }
        val audioSelection: SessionAudioSelection get() = AppPlaybackRuntime::class.java.getDeclaredField("audioSelection").let {
            it.isAccessible = true; it.get(runtime) as SessionAudioSelection
        }
        fun installManualAudio(channel: Long = 1): TrackSelectionOverride {
            // The fake stream has no frames; expose a ready target with the test track groups.
            reportedPlaybackState = Player.STATE_READY
            markReady()
            // An opaque storage identity avoids any server address or credentials in this fixture.
            AppProfileOwner::class.java.getDeclaredField("audioProfileId").apply {
                isAccessible = true; set(profiles, "test-profile")
            }
            val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("de").build())
            audioTracks = Tracks(listOf(Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
            audioSelection.useProfile(requireNotNull(profiles.serverProfile.value), runtimePlayer)
            audioSelection.activate(ChannelId(channel), runtimePlayer)
            return TrackSelectionOverride(group, listOf(0)).also {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().addOverride(it).build()
            }
        }
        private fun recover(reason: PlaybackRecoveryReason) { runtime.onRecoveryRequired(reason) }

        fun audioDisabled() = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes

        suspend fun live(timeshift: Boolean, channel: Int = 1, granted: Boolean = timeshift, firstPicture: Boolean = true) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (granted) 120 else 0)))
            val subscriptions = connection.subscribeCount
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
            }
            await { connection.subscribeCount > subscriptions }
            val registration = connection.awaitCollectionRegistered()
            startSubscription(registration)
            await { install.isCompleted }
            assertTrue("Live target installed", install.await()?.isStarted == true)
            if (granted) {
                connection.emit(registration, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                // The subscription grant alone already publishes Available (playbackPaused = null).
                // Wait for this status: the runtime mirrors its playbackPaused into the local play
                // intent, so arriving after a test's local pause it would resume playback.
                await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
            }
            // The first picture is ready: a server pause is only sent after it.
            if (firstPicture) markReady()
        }

        fun markReady() { playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) } }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue("Recording target installed", install.await()?.isStarted == true)
        }

        // Target the exact stream: a broadcast would also reach a predecessor whose channel
        // unsubscribe already closed but whose collector has not yet deregistered it.
        suspend fun startSubscription(registration: ScriptedSubscriptionRegistration) {
            connection.emit(registration, SubscriptionEvent.Started(listOf(
                SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                    language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                    frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
            ), null, SubscriptionCondition.NO_DETAIL))
        }

        suspend fun await(predicate: () -> Boolean) {
            withTimeout(5_000) {
                while (!predicate()) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
        }

        suspend fun settle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
    }
}
