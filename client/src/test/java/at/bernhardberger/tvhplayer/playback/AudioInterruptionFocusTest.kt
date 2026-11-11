@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
)

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import androidx.media3.common.C
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class AudioInterruptionFocusTest : AudioInterruptionFixture() {
    @Test fun personalOutputDisconnectRestoresPassthroughWithoutResumingNoisyPausedPlayback() = exercise {
        live(timeshift = true)
        outputs.personalOutputConnected.value = true
        await { !output.isPassthroughEnabled }
        val target = runtime.activeTarget.value
        focus.send(AudioInterruption.NOISY)
        await { !player.playWhenReady }
        outputs.personalOutputConnected.value = false
        await { output.isPassthroughEnabled }
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(target, runtime.activeTarget.value)
        assertEquals(1, connection.subscribeCount)
        assertTrue(settings.playerSettings.first().audioPassthroughEnabled)
    }

    @Test fun personalOutputChangesDoNotReapplyStoredTrackPreferences() = exercise {
        live(timeshift = false)
        // Wait for a known settings application before changing a session-local preference.
        settings.setSubtitleLanguage("de")
        await { player.trackSelectionParameters.preferredTextLanguages.toList() == listOf("de") }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredTextLanguages("fr").build()
        outputs.personalOutputConnected.value = true
        await { !output.isPassthroughEnabled }
        assertEquals(listOf("fr"), player.trackSelectionParameters.preferredTextLanguages.toList())
        outputs.personalOutputConnected.value = false
        await { output.isPassthroughEnabled }
        assertEquals(listOf("fr"), player.trackSelectionParameters.preferredTextLanguages.toList())
    }

    @Test fun headphonesSwitchOnlyAudioThroughTheCommandSerializerAndDisconnectRestoresTheSetting() = exercise {
        live(timeshift = false)
        val target = runtime.activeTarget.value
        val item = player.currentMediaItem
        assertTrue(output.isPassthroughEnabled)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { entered.complete(Unit); release.await() } }
        try {
            entered.await()
            outputs.personalOutputConnected.value = true
            settle()
            assertTrue("Output switch must wait for the target command", output.isPassthroughEnabled)
        } finally {
            release.complete(Unit)
            blocker.join()
        }
        await { !output.isPassthroughEnabled }
        assertTrue(settings.playerSettings.first().audioPassthroughEnabled)
        assertEquals(target, runtime.activeTarget.value)
        assertSame(item, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        outputs.personalOutputConnected.value = false
        await { output.isPassthroughEnabled }
        assertEquals(1, connection.subscribeCount)
        assertFalse(runtime.audioPassthroughChangeFailed.value)
        runtime.detach()
        assertEquals(1, outputs.closes)
    }

    @Test fun settingsChangesWithHeadphonesKeepPcmAndQueuedSwitchReadsLatestOutputs() = exercise {
        live(timeshift = false)
        outputs.personalOutputConnected.value = true
        await { !output.isPassthroughEnabled }
        settings.setAudioPassthroughEnabled(false)
        settle()
        settings.setAudioPassthroughEnabled(true)
        settle()
        assertFalse(output.isPassthroughEnabled)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val blocker = scope.launch { commands.serialize(onClosed = {}) { entered.complete(Unit); release.await() } }
        try {
            entered.await()
            outputs.personalOutputConnected.value = false
            settle()
            outputs.personalOutputConnected.value = true
        } finally {
            release.complete(Unit)
            blocker.join()
        }
        settle()
        assertFalse(output.isPassthroughEnabled)
        assertTrue(settings.playerSettings.first().audioPassthroughEnabled)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun timeshiftTransientPausesServerOnceAndGainResumesWithoutRetune() = exercise {
        live(timeshift = true)
        assertFalse(runtime.hasAudioInterruption)
        assertEquals(1, focus.requests.size)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { connection.speeds == listOf(0) }
        assertTrue(runtime.hasAudioInterruption)
        assertFalse(player.playWhenReady)
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        settle()
        assertEquals(listOf(0), connection.speeds)
        advanceRecoveryTime(30_000)
        settle()
        assertTrue(recoveries.isEmpty())
        assertEquals(1, connection.subscribeCount)
        focus.send(AudioInterruption.GAIN)
        await { player.playWhenReady && connection.speeds == listOf(0, 100) }
        assertFalse(runtime.hasAudioInterruption)
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
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady }
        runtime.pause()
        await { !player.playWhenReady }
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
        val backgroundRequests = focus.requests.size
        runtime.play()
        settle()
        assertEquals(backgroundRequests, focus.requests.size)
        assertFalse(player.playWhenReady)
        val subscriptions = connection.subscribeCount
        runtime.onAppForegrounded()
        await { connection.subscribeCount > subscriptions }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value != null && player.playWhenReady }
        val beforeStop = focus.requests.last()
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        val stop = scope.async { runtime.stop() }
        await { stop.isCompleted }
        assertFalse(runtime.hasAudioInterruption)
        assertFalse(audioDisabled())
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
            val previousSpeeds = connection.speeds.toList()
            focus.send(event)
            await { !player.playWhenReady }
            val requests = focus.requests.size
            assertEquals(TimeshiftCommandResult.ACCEPTED, runtime.resumeTimeshift())
            assertTrue(player.playWhenReady)
            assertEquals(previousSpeeds + listOf(0, 100), connection.speeds)
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        assertEquals(TimeshiftCommandResult.SERVER_REJECTED, runtime.resumeTimeshift())
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
        val abandons = focus.abandons
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { player.playWhenReady }
        assertTrue(focus.abandons > abandons)
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

    @Test fun pauseQueuedDuringMutedResumePausesLocallyAndOnServer() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        assertEquals(TimeshiftCommandResult.SERVER_REJECTED, runtime.pauseTimeshiftPlayback())
        assertTrue(player.playWhenReady)
        assertEquals(requests, focus.requests.size)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.TIMEOUT))
        assertEquals(TimeshiftCommandResult.TIMEOUT, runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertEquals(requests, focus.requests.size)
    }

    @Test fun rejectedPauseAfterRuntimeCloseDoesNotRestorePlayback() = exercise {
        live(timeshift = true)
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.TIMEOUT))
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
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
        await { runBlocking { settings.audioChoices.read("test-profile", ChannelId(1)) != null } }
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
        await("replacement and stale preview completed") {
            blocker.isCompleted && replacement.isCompleted && stalePreview.isCompleted
        }
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
        assertNotNull(runtime.recordingSelection.value)
        assertNotNull(runtime.recordingAdmission.value)

        var liveDuringLiveInstall: Boolean? = null
        beforeLiveBinding = { liveDuringLiveInstall = startupBuffer.isLiveTarget }
        val subscriptions = connection.subscribeCount
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { connection.subscribeCount > subscriptions }
        assertEquals(true, liveDuringLiveInstall)
        startSubscription(connection.awaitCollectionRegistered())
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        assertNull(runtime.recordingSelection.value)
        assertNull(runtime.recordingAdmission.value)

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

}
