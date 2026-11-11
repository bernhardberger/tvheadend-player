@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopOutcome
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
internal class AudioInterruptionCleanupTest : AudioInterruptionFixture() {
    @Test fun exhaustedSuppressedLiveRetunesExactlyOnceAfterSuppressionClears() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        settle()
        assertEquals(1, connection.subscribeCount)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        suppress(false)
        signalPlayIntent()
        signalPlayIntent()
        await { connection.subscribeCount == 2 }
        settle()
        assertEquals(2, connection.subscribeCount)
        assertTrue(player.playWhenReady)
    }

    @Test fun failedSwitchAllowsFreshExhaustionAndRetirementReportsForRetainedChannel() = exercise {
        fun retainedSubscriptions() = subscribedChannels.count { it == 1L }
        for (reason in listOf(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED,
            PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED)) {
            live(timeshift = false)
            // The rollback and the recovery open collectors. Feed whichever
            // open, so the next live() receives its own collector rather than a stale one.
            val startup = scope.launch {
                while (isActive) startSubscription(connection.awaitCollectionRegistered())
            }
            try {
                val retained = retainedSubscriptions()
                val previousItem = player.currentMediaItem
                failNextPrepare = true
                val failed = scope.async {
                    runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
                }
                await { failed.isCompleted }
                assertFalse(failed.await()?.isStarted == true)
                assertSame(previousItem, player.currentMediaItem)
                // The restored source re-subscribes the retained channel after the switch returned,
                // and the switch target may have subscribed first: count the retained channel only.
                await { retainedSubscriptions() == retained + 1 }
                val count = retainedSubscriptions()
                if (reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED) exhaust()
                else runtime.onRecoveryRequired(reason)
                await { retainedSubscriptions() == count + 1 }
                settle()
            } finally {
                startup.cancelAndJoin()
            }
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        }
    }

    @Test fun newerIntentBeforeCleanupReportStillBlocksInstallationUntilCleanup() = exercise {
        live(timeshift = false)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED) {
            runtime.noteLiveSelection(runtime.notePlaybackIntent())
        }
        val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2)))
        val blocked = scope.async { runtime.playLive(selection) }
        await { blocked.isCompleted }
        assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, blocked.await())
        assertTrue(runtime.state.value is AppPlaybackState.Failed)
        assertTrue(coordinator.playerCleanupPending.value)
        reportedPlaybackState = Player.STATE_READY
        markReady()
        signalIsPlaying()
        settle()
        assertTrue(runtime.state.value is AppPlaybackState.Failed)
        reportedPlaybackState = null
        assertEquals(1, connection.subscribeCount)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        val resumed = scope.async { runtime.playLive(selection) }
        await { resumed.isCompleted }
        assertTrue(resumed.await()?.isStarted == true)
        assertFalse(coordinator.playerCleanupPending.value)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun blockedSwitchKeepsTheRetirementRetryFence() = exercise {
        live(timeshift = false)
        suppress(true)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED)
        val blocked = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { blocked.isCompleted }
        assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, blocked.await())
        assertTrue(runtime.state.value is AppPlaybackState.Failed)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        signalPlayIntent()
        await { !coordinator.playerCleanupPending.value }
        assertEquals(0, player.mediaItemCount)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun suppressionDuringRecoverySettingsReadKeepsTheAttemptOwed() = exercise {
        live(timeshift = false)
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeSettingsRead = { beforeSettingsRead = {}; reading.complete(Unit); release.await() }
        exhaust()
        await { reading.isCompleted }
        suppress(true)
        release.complete(Unit)
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        suppress(false)
        await { connection.subscribeCount == 2 }
    }

    @Test fun newerIntentBeforeLateCleanupReportPreservesUnrelatedCurrentItem() = exercise {
        live(timeshift = false)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED) {
            runtime.noteLiveSelection(runtime.notePlaybackIntent())
            player.addMediaItem(replacement)
            player.seekTo(1, 0L)
            player.play()
            failCoordinatorStop = false
            failCoordinatorRemoval = false
        }
        assertEquals(1, player.mediaItemCount)
        assertEquals(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertFalse(coordinator.playerCleanupPending.value)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun replacementDuringRecoveryAudioReadIsNotPausedOrReclaimed() = exercise {
        live(timeshift = false)
        installManualAudio()
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        beforeSettingsRead = {
            if (++reads == 2) { beforeSettingsRead = {}; reading.complete(Unit); release.await() }
        }
        exhaust()
        await { reading.isCompleted }
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        player.setMediaItem(replacement)
        player.play()
        val parameters = player.trackSelectionParameters
        release.complete(Unit)
        settle()
        assertEquals(replacement, player.currentMediaItem)
        assertEquals(parameters, player.trackSelectionParameters)
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertNull(runtime.activeTarget.value)
    }

    @Test fun exhaustedRecoveryUsesTheSharedBudgetAndDeduplicatesCallbacks() = exercise {
        live(timeshift = false)
        for ((index, delayMillis) in listOf(0L, 2_000L, 5_000L, 10_000L, 20_000L).withIndex()) {
            exhaust()
            runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
            signalPlayIntent()
            if (delayMillis > 0) {
                await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == delayMillis }
                advanceRecoveryTime(delayMillis + 1)
            }
            await { connection.subscribeCount == index + 2 }
            startSubscription(connection.awaitCollectionRegistered())
            settle()
        }
        exhaust()
        await { runtime.state.value is AppPlaybackState.Failed }
        assertEquals(6, connection.subscribeCount)
    }

    @Test fun predecessorExhaustionBeforeSourceSwapDoesNotRetuneOrStopTheNewChannel() = exercise {
        live(timeshift = false)
        val predecessor = player.currentMediaItem
        val stops = coordinatorStops
        var predecessorRemoved = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && timeline.isEmpty) {
                    predecessorRemoved = true
                }
            }
        })
        beforeLiveBinding = {
            beforeLiveBinding = {}
            // The runtime has begun channel 2's install, but the SDK has not swapped its source.
            // Model the predecessor's exhaustion stop/removal and callback at this boundary.
            assertSame(predecessor, player.currentMediaItem)
            player.stop()
            player.clearMediaItems()
            assertTrue(predecessorRemoved)
            runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
        }
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted }
        assertTrue(zap.await()?.isStarted == true)
        await { connection.subscribeCount == 2 }
        advanceRecoveryTime(300)
        settle()
        assertEquals(2, connection.subscribeCount)
        assertEquals(stops, coordinatorStops)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertTrue(player.playWhenReady)
        assertEquals(1, player.mediaItemCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

    @Test fun recoveryInstallExhaustionBeforeCommitIsOwedOnceForTheNewInstallation() = exercise {
        live(timeshift = false)
        var cleanup: Job? = null
        escalateOnNextSource(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED, reports = 2) {
            suppress(true)
            cleanup = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                commands.serialize(onClosed = {}) { coordinator.stop() }
            }
        }
        exhaust()
        await { cleanup?.isCompleted == true && player.mediaItemCount == 0 && pendingSessions == 0 }
        val subscriptions = connection.subscribeCount
        assertEquals(0, player.mediaItemCount)
        suppress(false)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        assertEquals(subscriptions, connection.subscribeCount)
        assertEquals(2_000L, (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
        advanceRecoveryTime(2_001)
        await { connection.subscribeCount == subscriptions + 1 }
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
        assertEquals(1, pendingSessions)
    }

    @Test fun recoveryInstallRetirementFailureBeforeCommitRetiresTheNewInstallationWhileSuppressed() = exercise {
        live(timeshift = false)
        var escalated = false
        escalateOnNextSource(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED, reports = 2) {
            escalated = true
            suppress(true)
        }
        exhaust()
        await { escalated && player.mediaItemCount == 0 && pendingSessions == 0 }
        val subscriptions = connection.subscribeCount
        assertEquals(0, player.mediaItemCount)
        suppress(false)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        assertEquals(subscriptions, connection.subscribeCount)
        assertEquals(2_000L, (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
        advanceRecoveryTime(2_001)
        await { connection.subscribeCount == subscriptions + 1 }
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
        assertEquals(1, pendingSessions)
    }

    @Test fun recoveryInstallCleanupFailureBeforeCommitKeepsCleanupOwedAndVisible() {
        for (duplicateReport in listOf(false, true)) exercise {
            live(timeshift = false)
            var cleanup: Job? = null
            escalateOnNextSource(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED, reports = 2) {
                suppress(true)
                failCoordinatorStop = true
                failCoordinatorRemoval = true
                cleanup = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    commands.serialize(onClosed = {}) { coordinator.stop() }
                }
            }
            exhaust()
            await { cleanup?.isCompleted == true && runtime.state.value is AppPlaybackState.Failed }
            assertEquals(1, player.mediaItemCount)
            assertEquals(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER,
                target = AppPlaybackTarget.Live(ChannelId(1))), runtime.state.value)
            failCoordinatorStop = false
            failCoordinatorRemoval = false
            if (duplicateReport) {
                runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED)
                await { player.mediaItemCount == 0 && pendingSessions == 0 }
                assertEquals(2, connection.subscribeCount)
            }
            suppress(false)
            await { player.mediaItemCount == 0 && pendingSessions == 0 &&
                (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
            val subscriptions = connection.subscribeCount
            assertEquals(0, player.mediaItemCount)
            assertEquals(2_000L, (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
            advanceRecoveryTime(2_001)
            await { connection.subscribeCount == subscriptions + 1 }
            assertFalse(coordinator.playerCleanupPending.value)
        }
    }

    @Test fun failedRecoveryRetuneRestoresTheSameOwnedItemForLaterCleanup() = exercise {
        live(timeshift = false)
        val previousItem = player.currentMediaItem
        failNextPrepare = true
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { !failNextPrepare && player.currentMediaItem === previousItem && player.playWhenReady }
        settle()
        assertSame(previousItem, player.currentMediaItem)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        suppress(true)
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED)
        assertTrue(coordinator.playerCleanupPending.value)
        assertSame(previousItem, player.currentMediaItem)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        signalPlayIntent()
        await { player.mediaItemCount == 0 }
        assertEquals(0, player.mediaItemCount)
        assertFalse(coordinator.playerCleanupPending.value)
    }

    @Test fun departureKeepsReplacementUntouchedButRestoresAppMuteOnNewLiveCommit() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri("file:///replacement"))
        await { runtime.activeTarget.value == null }
        assertTrue(audioDisabled())
        assertFalse(runtime.hasAudioInterruption)
        assertFalse(runtime.isInterruptionMuted)
        live(timeshift = false, channel = 2)
        assertFalse(audioDisabled())
    }

    @Test fun departureKeepsReplacementUntouchedButRestoresAppMuteOnRecordingCommit() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri("file:///replacement"))
        await { runtime.activeTarget.value == null }
        assertTrue(audioDisabled())
        recording()
        assertFalse(audioDisabled())
    }

    @Test fun newCommitDoesNotUndoAnAudioDisableTheInterruptionDidNotApply() = exercise {
        live(timeshift = false)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        focus.send(AudioInterruption.NOISY)
        await { runtime.isInterruptionMuted }
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri("file:///replacement"))
        await { runtime.activeTarget.value == null }
        recording()
        assertTrue(audioDisabled())
    }

    @Test fun exhaustedLiveRequiresPlayIntentAsWellAsNoSuppression() = exercise {
        live(timeshift = false)
        player.pause()
        val requests = focus.requests.size
        val ignored = runtime.setSessionPlayWhenReady(true) { true }
        await { ignored.isCompleted }
        assertEquals(requests, focus.requests.size)
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        suppress(true)
        exhaust()
        suppress(false)
        settle()
        assertEquals(1, connection.subscribeCount)
        runtime.setSessionPlayWhenReady(true) { true }
        // The retune subscribes before it starts playing.
        await { connection.subscribeCount == 2 && player.playWhenReady }
    }

    @Test fun playingWhileExhaustedButSuppressedWaitsForSuppression() = exercise {
        live(timeshift = false)
        player.pause()
        suppress(true)
        exhaust()
        runtime.play()
        settle()
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        suppress(false)
        await { connection.subscribeCount == 2 }
    }

    @Test fun newerPendingSelectionCancelsExhaustionBeforeItCommits() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        val intent = runtime.notePlaybackIntent()
        runtime.noteLiveSelection(intent)
        suppress(false)
        settle()
        assertEquals(1, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

    @Test fun stopCancelsSuppressedExhaustion() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        val stopped = scope.async { runtime.stop() }
        await { stopped.isCompleted }
        suppress(false)
        player.play()
        settle()
        assertNull(runtime.activeTarget.value)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun recordingCancelsSuppressedExhaustion() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        recording()
        suppress(false)
        settle()
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), runtime.activeTarget.value)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun changedSessionCancelsSuppressedExhaustion() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        val old = session.observation.value
        session.replaceGeneration(SessionObservation.create(sessionState = old.sessionState,
            channelState = old.channelState, epgState = old.epgState, dvrState = old.dvrState))
        suppress(false)
        settle()
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun detachCancelsSuppressedExhaustion() = exercise {
        live(timeshift = false)
        suppress(true)
        exhaust()
        installManualAudio()
        assertFalse(player.trackSelectionParameters.overrides.isEmpty())
        runtime.detach()
        assertTrue(player.trackSelectionParameters.overrides.isEmpty())
        suppress(false)
        player.play()
        settle()
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun exhaustedRecoveryPreservesInterruptionMute() = exercise {
        live(timeshift = false)
        focus.send(AudioInterruption.NOISY)
        await { audioDisabled() }
        exhaust()
        await { connection.subscribeCount == 2 }
        assertTrue(runtime.isInterruptionMuted)
        assertTrue(audioDisabled())
    }

    @Test fun exhaustionRetirementFailureRetriesStopWhileSuppressed() = exercise {
        live(timeshift = false)
        suppress(true)
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED)
        // The unsubscribe follows the cleared player asynchronously.
        await { player.mediaItemCount == 0 && connection.unsubscribeCount > 0 }
        assertEquals(1, connection.subscribeCount)
        suppress(false)
        await { connection.subscribeCount == 2 }
    }

    @Test fun newerPendingIntentDoesNotDiscardRetirementOfTheOldInstallation() = exercise {
        live(timeshift = false)
        runtime.noteLiveSelection(runtime.notePlaybackIntent())
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED)
        await { player.mediaItemCount == 0 && connection.unsubscribeCount > 0 }
        signalPlayIntent()
        settle()
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun retirementRetryAfterDeparturePreservesReplacementPlayback() = exercise {
        live(timeshift = false)
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        player.setMediaItem(replacement)
        player.play()
        await { runtime.activeTarget.value == null }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED)
        await { connection.unsubscribeCount > 0 }
        settle()
        assertEquals(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertNull(runtime.activeTarget.value)
    }

    @Test fun exhaustedCleanupFailureBlocksRetuneUntilOwnedItemIsRemoved() = exercise {
        live(timeshift = false)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED)
        settle()
        assertEquals(1, connection.subscribeCount)
        assertEquals(1, player.mediaItemCount)
        assertEquals(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER,
            target = AppPlaybackTarget.Live(ChannelId(1))), runtime.state.value)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        await { connection.subscribeCount == 2 }
        assertFalse(coordinator.playerCleanupPending.value)
    }

    @Test fun cleanupFailureDoesNotPublishFailureForAnUnrelatedReplacement() = exercise {
        live(timeshift = false)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        var replacementState: AppPlaybackState? = null
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED) {
            player.addMediaItem(replacement)
            player.seekTo(1, 0L)
            player.play()
            replacementState = runtime.state.value
        }
        assertEquals(replacementState, runtime.state.value)
        assertFalse(runtime.state.value is AppPlaybackState.Failed)
        assertSame(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertEquals(2, player.mediaItemCount)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        signalPlayIntent()
        await { runtime.state.value == AppPlaybackState.Idle }
        assertEquals(1, player.mediaItemCount)
        assertSame(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun exhaustedCleanupPreservesAnApplicationReplacement() = exercise {
        live(timeshift = false)
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        suppress(true)
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        exhaust(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED) {
            player.addMediaItem(replacement)
            player.seekTo(1, 0L)
            player.play()
        }
        suppress(false)
        settle()
        assertEquals(replacement, player.currentMediaItem)
        assertEquals(2, player.mediaItemCount)
        assertTrue(coordinator.playerCleanupPending.value)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        signalPlayIntent()
        await { player.mediaItemCount == 1 }
        assertFalse(coordinator.playerCleanupPending.value)
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun failedInstallationRetriesCoordinatorCleanupWithoutExternalPlaylistClear() = exercise {
        failNextPrepare = true
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
        val failed = scope.async { runtime.playLive(selection) }
        await { failed.isCompleted }
        assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, failed.await())
        val remaining = player.currentMediaItem
        assertNotNull(remaining)
        assertTrue(coordinator.playerCleanupPending.value)
        val subscriptions = connection.subscribeCount
        val retry = scope.async { runtime.retryLive(viewerRetry = true) }
        await { retry.isCompleted }
        assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, retry.await())
        assertSame(remaining, player.currentMediaItem)
        assertEquals(subscriptions, connection.subscribeCount)
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        val recovered = scope.async { runtime.retryLive(viewerRetry = true) }
        await { recovered.isCompleted }
        assertTrue(recovered.await()?.isStarted == true)
        await { connection.subscribeCount == subscriptions + 1 }
        assertEquals(subscriptions + 1, connection.subscribeCount)
        assertFalse(coordinator.playerCleanupPending.value)
        assertEquals(1, player.mediaItemCount)
        assertFalse(remaining === player.currentMediaItem)
    }

    @Test fun failedRecordingInstallationRetriesCoordinatorCleanupWithoutExternalPlaylistClear() {
        for (interruptedLive in listOf(false, true)) exercise {
            var acquiring: AppPlaybackState? = null
            beforeRecordingBinding = { acquiring = runtime.state.value }
            if (interruptedLive) {
                live(timeshift = false)
                focus.send(AudioInterruption.NOISY)
                await { runtime.isInterruptionMuted }
            }
            failNextPrepare = true
            failCoordinatorStop = true
            failCoordinatorRemoval = true
            val failed = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { failed.isCompleted }
            assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, failed.await())
            if (!interruptedLive) assertEquals(AppPlaybackState.Starting, acquiring)
            assertTrue(runtime.state.value is AppPlaybackState.Failed)
            assertEquals(AppPlaybackTarget.Recording(DvrEntryId(1)), (runtime.state.value as AppPlaybackState.Failed).target)
            assertFalse(runtime.hasAudioInterruption)
            assertFalse(audioDisabled())
            val remaining = player.currentMediaItem
            assertNotNull(remaining)
            assertTrue(coordinator.playerCleanupPending.value)
            val blocked = scope.async { runtime.retryRecording() }
            await { blocked.isCompleted }
            assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, blocked.await())
            assertSame(remaining, player.currentMediaItem)
            assertEquals(1, player.mediaItemCount)
            failCoordinatorStop = false
            failCoordinatorRemoval = false
            failNextPrepare = false
            val recovered = scope.async { runtime.retryRecording() }
            await { recovered.isCompleted }
            assertTrue(recovered.await()?.isStarted == true)
            assertFalse(coordinator.playerCleanupPending.value)
            assertEquals(1, player.mediaItemCount)
            assertFalse(remaining === player.currentMediaItem)
        }
    }

    @Test fun stopRetriesFailedInstallationCleanupAndPreservesReplacementPlayback() = exercise {
        failNextPrepare = true
        failCoordinatorStop = true
        failCoordinatorRemoval = true
        val failed = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
        }
        await { failed.isCompleted }
        assertEquals(PlaybackTargetResult.PLAYER_UNAVAILABLE, failed.await())
        assertNull(runtime.activeTarget.value)
        val subscriptions = connection.subscribeCount
        val blocked = scope.async { runtime.stop() }
        await { blocked.isCompleted }
        assertTrue(blocked.await().outcome == PlaybackStopOutcome.PLAYER_UNAVAILABLE)
        assertTrue(coordinator.playerCleanupPending.value)
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        player.addMediaItem(replacement)
        player.seekTo(1, 12_345L)
        player.play()
        failCoordinatorStop = false
        failCoordinatorRemoval = false
        val stopped = scope.async { runtime.stop() }
        await { stopped.isCompleted }
        assertFalse(stopped.await().outcome == PlaybackStopOutcome.PLAYER_UNAVAILABLE)
        assertFalse(coordinator.playerCleanupPending.value)
        assertEquals(1, player.mediaItemCount)
        assertSame(replacement, player.currentMediaItem)
        assertEquals(12_345L, player.currentPosition)
        assertTrue(player.playWhenReady)
        assertEquals(subscriptions, connection.subscribeCount)
        val again = scope.async { runtime.stop() }
        await { again.isCompleted }
        assertEquals(PlaybackStopResult.ALREADY_STOPPED, again.await())
        assertSame(replacement, player.currentMediaItem)
        assertEquals(12_345L, player.currentPosition)
        assertTrue(player.playWhenReady)
    }

    @Test fun departedLivePermanentlyWithdrawsAttributionAndDoesNotReclaimReplacement() = exercise {
        live(timeshift = false)
        assertTrue(runtime.liveStartUndisturbed.value)
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        player.setMediaItem(replacement)
        player.play()
        await { runtime.livePlaybackObservation.value == LivePlaybackObservation.Departed }
        await { runtime.activeTarget.value == null }
        assertFalse(runtime.liveStartUndisturbed.value)
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        signalPlayIntent()
        settle()
        assertEquals(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun departureDuringInstallDoesNotCommitOrRestoreThePreviousTarget() = exercise {
        live(timeshift = false)
        reportedPlaybackState = Player.STATE_READY
        val replacement = androidx.media3.common.MediaItem.fromUri("file:///replacement")
        var replaced = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!replaced && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !timeline.isEmpty) {
                    replaced = true
                    player.setMediaItem(replacement)
                    player.play()
                }
            }
        })
        val install = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { install.isCompleted }
        assertEquals(PlaybackTargetResult.DEPARTED, install.await())
        settle()
        assertNull(runtime.activeTarget.value)
        assertEquals(replacement, player.currentMediaItem)
        assertTrue(player.playWhenReady)
        assertFalse(runtime.videoPresentation.value.visible)
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
        // The playback thread opens channel 2's subscription after the install; a session replaced
        // before that refuses the open itself instead of testing the escalation.
        await { connection.subscribeCount == 2 }
        val previous = session.observation.value
        session.replaceGeneration(SessionObservation.create(
            sessionState = previous.sessionState, channelState = previous.channelState,
            epgState = previous.epgState, dvrState = previous.dvrState,
        ))
        settle()
        admissionHeld.complete(Unit)
        await { holder?.isCompleted == true }
        advanceRecoveryTime(300)
        settle()
        // Reported under the replaced session: dropped, neither retuning nor charging channel 2.
        assertEquals("the replaced session's escalation retuned channel 2", 2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
    }

}
