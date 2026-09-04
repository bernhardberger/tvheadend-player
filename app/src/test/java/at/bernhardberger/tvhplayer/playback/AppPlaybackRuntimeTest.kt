package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionDiagnostics
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionSource
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppPlaybackRuntimeTest {
    @Test
    fun enabledTimeshiftRequestsTheFixedProductPeriod() {
        assertEquals(2.hours, requestedLiveTimeshiftPeriod(timeshiftEnabled = true))
    }

    @Test
    fun disabledTimeshiftRequestsNoPeriod() {
        assertEquals(Duration.ZERO, requestedLiveTimeshiftPeriod(timeshiftEnabled = false))
    }

    @Test
    fun unavailableSdkTimeshiftStateHasUnavailablePresentation() {
        assertEquals(
            AppTimeshiftState(),
            LiveTimeshiftState.Unavailable.toAppPresentation(),
        )
    }

    @Test
    fun nullMeasuredTimeshiftDoesNotInventSeekableHistory() {
        assertEquals(
            AppTimeshiftState(available = true),
            measuredTimeshiftPresentation(
                bufferedDuration = null,
                positionBehindLive = null,
                serverPaused = false,
            ),
        )
    }

    @Test
    fun measuredTimeshiftPreservesObservedBufferPositionAndPause() {
        assertEquals(
            AppTimeshiftState(
                available = true,
                paused = true,
                bufferStartMs = -120_000L,
                positionMs = -30_000L,
            ),
            measuredTimeshiftPresentation(
                bufferedDuration = 2.minutes,
                positionBehindLive = 30.seconds,
                serverPaused = true,
            ),
        )
    }

    @Test
    fun olderTargetCompletionCannotOverwriteANewerFailedTarget() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        var state: AppPlaybackState = AppPlaybackState.Idle
        val olderLive = epoch.begin()
        val newerRecording = epoch.begin()

        assertFalse(epoch.publishIfCurrent(olderLive) {
            target = AppPlaybackTarget.Live(ChannelId(7))
            state = AppPlaybackState.Playing
        })
        assertTrue(epoch.publishIfCurrent(newerRecording) {
            target = null
            state = AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED)
        })

        assertNull(target)
        assertEquals(
            AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED),
            state,
        )
    }

    @Test
    fun olderTargetCompletionCannotSurviveANewerStop() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        var state: AppPlaybackState = AppPlaybackState.Starting
        val targetCommand = epoch.begin()
        val stopCommand = epoch.begin()

        assertFalse(epoch.publishIfCurrent(targetCommand) {
            target = AppPlaybackTarget.Live(ChannelId(9))
        })
        assertTrue(epoch.publishIfCurrent(stopCommand) {
            target = null
            state = AppPlaybackState.Idle
        })

        assertNull(target)
        assertEquals(AppPlaybackState.Idle, state)
    }

    @Test
    fun onlyTheLatestSuccessfulTargetCanPublishPresentation() {
        val epoch = PlaybackPresentationEpoch()
        var target: AppPlaybackTarget? = null
        val olderRecording = epoch.begin()
        val newerLive = epoch.begin()

        assertTrue(epoch.publishIfCurrent(newerLive) {
            target = AppPlaybackTarget.Live(ChannelId(12))
        })
        assertFalse(epoch.publishIfCurrent(olderRecording) {
            target = AppPlaybackTarget.Recording(DvrEntryId(3))
        })

        assertEquals(AppPlaybackTarget.Live(ChannelId(12)), target)
    }

    @Test
    fun recoveryCannotStartFromATargetSupersededByStop() {
        val epoch = PlaybackPresentationEpoch()
        val activeTarget = epoch.begin()
        epoch.begin()

        assertNull(epoch.beginIfCurrent(activeTarget))
    }

    @Test
    fun liveRecoveryFenceRejectsARecordingReplacement() {
        val channel = Channel.create(id = ChannelId(7), name = "Seven")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val fence = LiveRecoveryFence(
            reason = PlaybackRecoveryReason.LIVE_ENDED,
            selection = LivePlaybackSelection(
                currentSession = observations.captureCurrentSession(),
                channelId = channel.id,
            ),
            targetEpoch = 20L,
        )

        assertFalse(
            fence.matches(
                activeTarget = AppPlaybackTarget.Recording(DvrEntryId(9)),
                activeTargetEpoch = 21L,
                observation = observations.observation.value,
            ),
        )
    }

    @Test
    fun liveRecoveryFenceRejectsANewerLiveTarget() {
        val channel = Channel.create(id = ChannelId(11), name = "Eleven")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val fence = LiveRecoveryFence(
            reason = PlaybackRecoveryReason.LIVE_ENDED,
            selection = LivePlaybackSelection(
                currentSession = observations.captureCurrentSession(),
                channelId = channel.id,
            ),
            targetEpoch = 22L,
        )

        assertFalse(
            fence.matches(
                activeTarget = AppPlaybackTarget.Live(ChannelId(13)),
                activeTargetEpoch = 23L,
                observation = observations.observation.value,
            ),
        )
    }

    @Test
    fun liveRecoveryFenceAcceptsOnlyTheSameTargetEpochGenerationAndReason() {
        val channel = Channel.create(id = ChannelId(17), name = "Seventeen")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val target = AppPlaybackTarget.Live(channel.id)
        val selection = LivePlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            channelId = channel.id,
        )
        val fence = LiveRecoveryFence(
            reason = PlaybackRecoveryReason.AUDIO_RECOVERY_EXHAUSTED,
            selection = selection,
            targetEpoch = 24L,
        )

        assertTrue(
            fence.matches(
                activeTarget = target,
                activeTargetEpoch = 24L,
                observation = observations.observation.value,
            ),
        )
        assertEquals(PlaybackRecoveryReason.AUDIO_RECOVERY_EXHAUSTED, fence.reason)

        observations.publish(currentObservation(channels = listOf(channel)))
        assertFalse(
            fence.matches(
                activeTarget = target,
                activeTargetEpoch = 24L,
                observation = observations.observation.value,
            ),
        )
    }

    @Test
    fun ordinaryPlayerCallbacksPreserveRecoveringUntilItsAttemptResolves() {
        val recovering = AppPlaybackState.Recovering(
            reason = PlaybackRecoveryReason.LIVE_ENDED,
            retryDelayMillis = 0L,
        )

        assertSame(
            recovering,
            playerReportedPlaybackState(
                currentState = recovering,
                recoveryAttemptInProgress = true,
                playbackState = Player.STATE_IDLE,
                isPlaying = false,
            ),
        )
        assertSame(
            recovering,
            playerReportedPlaybackState(
                currentState = recovering,
                recoveryAttemptInProgress = true,
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
            ),
        )
        assertEquals(
            AppPlaybackState.Starting,
            playerReportedPlaybackState(
                currentState = recovering,
                recoveryAttemptInProgress = false,
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
            ),
        )
    }

    @Test
    fun failedRecoveryWithItsHealthyReadyTargetRepublishesAfterOwnershipClears() = runTest {
        val channel = Channel.create(id = ChannelId(23), name = "Twenty-three")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val target = AppPlaybackTarget.Live(channel.id)
        val fence = LiveRecoveryFence(
            reason = PlaybackRecoveryReason.LIVE_ENDED,
            selection = LivePlaybackSelection(
                currentSession = observations.captureCurrentSession(),
                channelId = channel.id,
            ),
            targetEpoch = 25L,
        )
        val recovering = AppPlaybackState.Recovering(
            reason = fence.reason,
            retryDelayMillis = 0L,
        )
        var state: AppPlaybackState = recovering
        lateinit var recoveryAttempts: LiveRecoveryAttemptRunner
        recoveryAttempts = LiveRecoveryAttemptRunner { resolvedFence, result ->
            assertFalse(recoveryAttempts.inProgress)
            if (shouldRepublishPlayerStateAfterRecovery(
                    result = result,
                    fence = resolvedFence,
                    activeTarget = target,
                    activeTargetEpoch = 25L,
                    observation = observations.observation.value,
                    healthyActiveTarget = target,
                )
            ) {
                state = playerStateAfterRecoveryResolution(
                    currentState = state,
                    playbackState = Player.STATE_READY,
                    isPlaying = false,
                )
            }
        }

        recoveryAttempts.run(fence) {
            state = playerReportedPlaybackState(
                currentState = state,
                recoveryAttemptInProgress = recoveryAttempts.inProgress,
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
            )
            assertSame(recovering, state)
            PlaybackTargetResult.NOT_READY
        }

        assertEquals(AppPlaybackState.Starting, state)
    }

    @Test
    fun recoveryCallbackIsQueuedOntoTheApplicationDispatcherWithItsReason() = runTest {
        var observedReason: PlaybackRecoveryReason? = null

        val recovery = dispatchPlaybackRecovery(
            scope = this,
            reason = PlaybackRecoveryReason.AUDIO_RECOVERY_EXHAUSTED,
        ) { reason ->
            observedReason = reason
        }

        assertNull(observedReason)
        runCurrent()
        recovery.join()
        assertEquals(PlaybackRecoveryReason.AUDIO_RECOVERY_EXHAUSTED, observedReason)
    }

    @Test
    fun replacementConcealsThePreviousTargetsFrame() {
        val previous = AppVideoPresentation(epoch = 1L, visible = true)

        assertEquals(
            AppVideoPresentation(epoch = 2L, visible = false),
            previous.beginTarget(epoch = 2L),
        )
    }

    @Test
    fun staleFirstFrameCannotRevealTheCurrentTarget() {
        val current = AppVideoPresentation(epoch = 2L, visible = false)

        assertEquals(
            current,
            current.onFirstFrame(frameEpoch = 1L, activeTargetEpoch = 2L),
        )
    }

    @Test
    fun currentTargetsFirstFrameRevealsVideoOnlyAfterTargetActivation() {
        val current = AppVideoPresentation(epoch = 2L, visible = false)

        assertEquals(
            current,
            current.onFirstFrame(frameEpoch = 2L, activeTargetEpoch = null),
        )
        assertEquals(
            AppVideoPresentation(epoch = 2L, visible = true),
            current.onFirstFrame(frameEpoch = 2L, activeTargetEpoch = 2L),
        )
    }

    @Test
    fun priorGenerationLiveProofCannotAuthorizeAnUnobservedReconnectTarget() {
        val channel = Channel.create(id = ChannelId(7), name = "Seven")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val staleSelection = LivePlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            channelId = channel.id,
        )

        observations.publish(currentObservation())

        assertNull(
            resolveLivePlaybackSelection(
                observation = observations.observation.value,
                channelId = channel.id,
                requestedSelection = staleSelection,
            ),
        )
    }

    @Test
    fun reconnectReauthorizesLiveIntentOnlyWithTheGenerationThatObservedIt() {
        val channel = Channel.create(id = ChannelId(11), name = "Eleven")
        val observations = FakeSessionObservation(currentObservation(channels = listOf(channel)))
        val staleSelection = LivePlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            channelId = channel.id,
        )
        observations.publish(currentObservation(channels = listOf(channel)))

        val currentObservation = observations.observation.value
        val resolved = requireNotNull(
            resolveLivePlaybackSelection(
                observation = currentObservation,
                channelId = channel.id,
                requestedSelection = staleSelection,
            ),
        )

        assertNotSame(staleSelection, resolved)
        assertNotSame(staleSelection.currentSession, resolved.currentSession)
        assertSame(currentObservation.currentSession, resolved.currentSession)
    }

    @Test
    fun priorGenerationRecordingProofCannotAuthorizeAnUnobservedReconnectTarget() {
        val recording = DvrEntry.create(id = DvrEntryId(12))
        val observations = FakeSessionObservation(
            currentObservation(recordings = listOf(recording)),
        )
        val staleProof = observations.captureCurrentSession()

        observations.publish(currentObservation())

        assertNotSame(staleProof, observations.observation.value.currentSession)
        assertNull(
            currentRecordingPlaybackSelection(
                observation = observations.observation.value,
                recordingId = recording.id,
            ),
        )
    }

    @Test
    fun failedReplacementDoesNotDemoteAHealthyTargetOrConcealItsVideo() = runTest {
        val installation = CompletableDeferred<PlaybackTargetResult>()
        val healthyTarget = AppPlaybackTarget.Live(ChannelId(13))
        var activeTarget: AppPlaybackTarget? = healthyTarget
        var state: AppPlaybackState = AppPlaybackState.Playing
        var presentation = AppVideoPresentation(epoch = 31L, visible = true)

        val request = async {
            completePlaybackTargetInstallation(
                installTarget = { installation.await() },
                presentationStillCurrent = { true },
                activeTarget = { activeTarget },
                onStarted = {
                    activeTarget = AppPlaybackTarget.Live(ChannelId(17))
                    state = AppPlaybackState.Starting
                    presentation = presentation.beginTarget(epoch = 32L)
                },
                onFailed = { state = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER) },
            )
        }
        runCurrent()

        assertEquals(healthyTarget, activeTarget)
        assertEquals(AppPlaybackState.Playing, state)
        assertEquals(AppVideoPresentation(epoch = 31L, visible = true), presentation)

        installation.complete(PlaybackTargetResult.TARGET_UNAVAILABLE)

        assertEquals(PlaybackTargetResult.TARGET_UNAVAILABLE, request.await())
        assertEquals(healthyTarget, activeTarget)
        assertEquals(AppPlaybackState.Playing, state)
        assertEquals(AppVideoPresentation(epoch = 31L, visible = true), presentation)
    }

    @Test
    fun failedTargetStatePreservesTheSdkOutcomeAndItsStableCategories() {
        val result = PlaybackTargetResult.GROWING_RECORDING_DEFERRED
        val state = AppPlaybackState.Failed(
            reason = AppPlaybackFailureReason.OTHER,
            targetResult = result,
        )

        assertSame(result, state.targetResult)
        assertTrue(requireNotNull(state.targetResult).isTransient)
        assertTrue(requireNotNull(state.targetResult).isUnsupported)
    }

    @Test
    fun orderedServerPauseReconcilesOnlyAnActiveLiveTarget() {
        val liveTarget = AppPlaybackTarget.Live(ChannelId(23))

        assertEquals(false, observedLivePlayIntent(liveTarget, serverPaused = true))
        assertEquals(true, observedLivePlayIntent(liveTarget, serverPaused = false))
        assertNull(observedLivePlayIntent(liveTarget, serverPaused = null))
        assertNull(
            observedLivePlayIntent(
                AppPlaybackTarget.Recording(DvrEntryId(29)),
                serverPaused = true,
            ),
        )
    }

    @OptIn(SubscriptionInfrastructureApi::class)
    @Test
    fun liveDiagnosticsAreExcludedFromNonLiveTargets() {
        val diagnostics = requireNotNull(
            LiveSubscriptionDiagnostics.update(
                previous = null,
                event = SubscriptionEvent.Started(
                    streams = null,
                    codecMetadata = null,
                    condition = SubscriptionCondition.NO_DETAIL,
                    issue = null,
                    source = LiveSubscriptionSource.create(
                        adapterName = "DVB adapter",
                        muxName = null,
                        networkName = null,
                        providerName = null,
                        serviceName = null,
                    ),
                ),
            ),
        )

        assertSame(
            diagnostics,
            liveDiagnosticsForTarget(AppPlaybackTarget.Live(ChannelId(31)), diagnostics),
        )
        assertNull(
            liveDiagnosticsForTarget(
                AppPlaybackTarget.Recording(DvrEntryId(37)),
                diagnostics,
            ),
        )
        assertNull(liveDiagnosticsForTarget(activeTarget = null, diagnostics))
    }

    @Test
    fun retainedTargetHealthUsesCurrentPlayerConditionAfterInstallation() {
        assertTrue(
            activePlayerTargetIsHealthy(
                playerErrorPresent = false,
                playbackState = Player.STATE_READY,
            ),
        )
        assertTrue(
            activePlayerTargetIsHealthy(
                playerErrorPresent = false,
                playbackState = Player.STATE_BUFFERING,
            ),
        )
        assertFalse(
            activePlayerTargetIsHealthy(
                playerErrorPresent = true,
                playbackState = Player.STATE_READY,
            ),
        )
        assertFalse(
            activePlayerTargetIsHealthy(
                playerErrorPresent = false,
                playbackState = Player.STATE_IDLE,
            ),
        )
        assertFalse(
            activePlayerTargetIsHealthy(
                playerErrorPresent = false,
                playbackState = Player.STATE_ENDED,
            ),
        )
    }

    @Test
    fun staleInstallCompletionCannotPoisonANewerPresentation() = runTest {
        val installation = CompletableDeferred<PlaybackTargetResult>()
        var ownsPresentation = true
        var activeTarget: AppPlaybackTarget? = null
        var state: AppPlaybackState = AppPlaybackState.Starting
        var callbacks = 0
        val request = async {
            completePlaybackTargetInstallation(
                installTarget = { installation.await() },
                presentationStillCurrent = { ownsPresentation },
                activeTarget = { activeTarget },
                onStarted = { callbacks++ },
                onFailed = {
                    callbacks++
                    state = AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER)
                },
            )
        }
        runCurrent()

        activeTarget = AppPlaybackTarget.Recording(DvrEntryId(19))
        state = AppPlaybackState.Playing
        ownsPresentation = false
        installation.complete(PlaybackTargetResult.TARGET_UNAVAILABLE)

        assertEquals(PlaybackTargetResult.TARGET_UNAVAILABLE, request.await())
        assertEquals(AppPlaybackTarget.Recording(DvrEntryId(19)), activeTarget)
        assertEquals(AppPlaybackState.Playing, state)
        assertEquals(0, callbacks)
    }

    @Test
    fun liveBackgroundStopsAndForegroundRetunesExactlyOnce() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Live(ChannelId(7))

        assertEquals(
            ForegroundPlaybackAction.StopLive,
            lifecycle.onBackgrounded(
                activeTarget = target,
                activeTargetEpoch = 11L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeLive(ChannelId(7)),
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun recordingBackgroundPausesAndResumesTheSameTargetInPlace() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Recording(DvrEntryId(19))

        assertEquals(
            ForegroundPlaybackAction.PauseRecording,
            lifecycle.onBackgrounded(
                activeTarget = target,
                activeTargetEpoch = 12L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeRecording,
            lifecycle.onForegrounded(activeTarget = target, activeTargetEpoch = 12L),
        )
    }

    @Test
    fun foregroundDoesNotRestartARecordingThatWasAlreadyPaused() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val target = AppPlaybackTarget.Recording(DvrEntryId(23))

        lifecycle.onBackgrounded(
            activeTarget = target,
            activeTargetEpoch = 13L,
            recordingPlayWhenReady = false,
        )

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = target, activeTargetEpoch = 13L),
        )
    }

    @Test
    fun successfulTargetReplacementReplacesThePendingRecordingResume() {
        val lifecycle = ForegroundPlaybackLifecycle()
        val previousTarget = AppPlaybackTarget.Recording(DvrEntryId(29))
        val replacementTarget = AppPlaybackTarget.Recording(DvrEntryId(31))

        lifecycle.onBackgrounded(
            activeTarget = previousTarget,
            activeTargetEpoch = 14L,
            recordingPlayWhenReady = true,
        )
        assertEquals(
            ForegroundPlaybackAction.PauseRecording,
            lifecycle.onTargetStarted(
                activeTarget = replacementTarget,
                activeTargetEpoch = 15L,
                recordingPlayWhenReady = true,
            ),
        )

        assertEquals(
            ForegroundPlaybackAction.ResumeRecording,
            lifecycle.onForegrounded(
                activeTarget = replacementTarget,
                activeTargetEpoch = 15L,
            ),
        )
    }

    @Test
    fun staleRecordingIdentityCannotResumeAfterBackground() {
        val lifecycle = ForegroundPlaybackLifecycle()

        lifecycle.onBackgrounded(
            activeTarget = AppPlaybackTarget.Recording(DvrEntryId(37)),
            activeTargetEpoch = 16L,
            recordingPlayWhenReady = true,
        )

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(
                activeTarget = AppPlaybackTarget.Recording(DvrEntryId(37)),
                activeTargetEpoch = 17L,
            ),
        )
    }

    @Test
    fun liveTargetThatFinishesStartingInBackgroundIsStoppedThenRetunedOnce() {
        val lifecycle = ForegroundPlaybackLifecycle()

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onBackgrounded(
                activeTarget = null,
                activeTargetEpoch = null,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.StopLive,
            lifecycle.onTargetStarted(
                activeTarget = AppPlaybackTarget.Live(ChannelId(41)),
                activeTargetEpoch = 18L,
                recordingPlayWhenReady = true,
            ),
        )
        assertEquals(
            ForegroundPlaybackAction.ResumeLive(ChannelId(41)),
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun explicitStopInvalidatesAPendingLiveRetune() {
        val lifecycle = ForegroundPlaybackLifecycle()

        lifecycle.onBackgrounded(
            activeTarget = AppPlaybackTarget.Live(ChannelId(43)),
            activeTargetEpoch = 19L,
            recordingPlayWhenReady = true,
        )
        lifecycle.onExplicitStop()

        assertEquals(
            ForegroundPlaybackAction.None,
            lifecycle.onForegrounded(activeTarget = null, activeTargetEpoch = null),
        )
    }

    @Test
    fun recordingRetryReadsItsRequestInsideTargetCommandSerialization() = runTest {
        val commands = PlaybackTargetCommandSerialization()
        val blockerStarted = CompletableDeferred<Unit>()
        val releaseBlocker = CompletableDeferred<Unit>()
        var request = "old"
        val blocker = launch {
            commands.serialize(onClosed = {}) {
                blockerStarted.complete(Unit)
                releaseBlocker.await()
                request = "new"
            }
        }
        blockerStarted.await()

        val retried = async {
            commands.retryRecording(
                onClosed = { "closed" },
                currentRequest = { request },
                retry = { it },
            )
        }
        runCurrent()

        assertFalse(retried.isCompleted)
        releaseBlocker.complete(Unit)
        blocker.join()
        assertEquals("new", retried.await())
    }

    @Test
    fun recordingRetryWithoutARequestDoesNothing() = runTest {
        val commands = PlaybackTargetCommandSerialization()

        val result = commands.retryRecording<String, String>(
            onClosed = { "closed" },
            currentRequest = { null },
            retry = { error("retry must not run") },
        )

        assertNull(result)
    }

    @Test
    fun detachFencesACommandQueuedBehindTargetSerialization() = runTest {
        val commands = PlaybackTargetCommandSerialization()
        val blockerStarted = CompletableDeferred<Unit>()
        val releaseBlocker = CompletableDeferred<Unit>()
        var queuedCommandRuns = 0
        val blocker = launch {
            commands.serialize(onClosed = {}) {
                blockerStarted.complete(Unit)
                releaseBlocker.await()
            }
        }
        blockerStarted.await()
        val queued = async {
            commands.serialize(onClosed = { "closed" }) {
                queuedCommandRuns += 1
                "ran"
            }
        }
        runCurrent()

        assertTrue(commands.close())
        releaseBlocker.complete(Unit)
        blocker.join()

        assertEquals("closed", queued.await())
        assertEquals(0, queuedCommandRuns)
    }

    @Test
    fun detachedInFlightCommandCannotResumeIntoPlayerAccess() = runTest {
        val commands = PlaybackTargetCommandSerialization()
        val commandStarted = CompletableDeferred<Unit>()
        val resumeCommand = CompletableDeferred<Unit>()
        var playerTouches = 0
        val command = launch {
            commands.serialize<Unit>(onClosed = {}) {
                commandStarted.complete(Unit)
                resumeCommand.await()
                commands.runIfOpen { playerTouches += 1 }
            }
        }
        commandStarted.await()

        commands.close()
        resumeCommand.complete(Unit)
        command.join()

        assertEquals(0, playerTouches)
    }

    @Test
    fun detachDrainWaitsForAnInFlightSerializedCommand() = runTest {
        val commands = PlaybackTargetCommandSerialization()
        val commandStarted = CompletableDeferred<Unit>()
        val resumeCommand = CompletableDeferred<Unit>()
        var detachActionRan = false
        val command = launch {
            commands.serialize(onClosed = {}) {
                commandStarted.complete(Unit)
                resumeCommand.await()
            }
        }
        commandStarted.await()
        commands.close()

        val detach = launch {
            commands.awaitIdle { detachActionRan = true }
        }
        runCurrent()
        assertFalse(detachActionRan)

        resumeCommand.complete(Unit)
        command.join()
        detach.join()
        assertTrue(detachActionRan)
    }

    @Test
    fun recordingRouteRestorationIsNeededOnlyForAnUnmatchedRuntime() {
        val recordingId = DvrEntryId(42)
        val recording = DvrEntry.create(id = recordingId)
        val observations = FakeSessionObservation(
            currentObservation(recordings = listOf(recording)),
        )
        val currentSelection = RecordingPlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            recordingId = recordingId,
        )

        assertTrue(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = null,
                selectedRecording = null,
            )
        )
        assertTrue(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = AppPlaybackTarget.Recording(recordingId),
                selectedRecording = null,
            )
        )
        assertTrue(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = null,
                selectedRecording = currentSelection,
            )
        )
        assertFalse(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = AppPlaybackTarget.Recording(recordingId),
                selectedRecording = currentSelection,
            )
        )
        assertTrue(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = AppPlaybackTarget.Live(ChannelId(7)),
                selectedRecording = RecordingPlaybackSelection(
                    currentSession = currentSelection.currentSession,
                    recordingId = DvrEntryId(41),
                ),
            )
        )
    }

    @Test
    fun recordingRouteRestorationRequiresCurrentGenerationAuthority() {
        val recording = DvrEntry.create(id = DvrEntryId(47))
        val observations = FakeSessionObservation(
            currentObservation(recordings = listOf(recording)),
        )
        val staleSelection = RecordingPlaybackSelection(
            currentSession = observations.captureCurrentSession(),
            recordingId = recording.id,
        )
        observations.publish(currentObservation(recordings = listOf(recording)))
        val currentSelection = requireNotNull(
            currentRecordingPlaybackSelection(observations.observation.value, recording.id),
        )

        assertTrue(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = AppPlaybackTarget.Recording(recording.id),
                selectedRecording = staleSelection,
            ),
        )
        assertFalse(
            recordingRouteNeedsRestoration(
                routeSelection = currentSelection,
                activeTarget = AppPlaybackTarget.Recording(recording.id),
                selectedRecording = currentSelection,
            ),
        )
    }

    @Test
    fun concurrentRecordingRouteRestorationsAdmitPlaybackOnce() = runTest {
        val commands = PlaybackTargetCommandSerialization()
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        var selectedRecordingId: DvrEntryId? = null
        var starts = 0

        suspend fun restore(): String? = commands.restoreRecordingIfNeeded(
            onClosed = { "closed" },
            targetMatches = { selectedRecordingId == DvrEntryId(42) },
            restore = {
                starts += 1
                firstStarted.complete(Unit)
                finishFirst.await()
                selectedRecordingId = DvrEntryId(42)
                "started"
            },
        )

        val first = async { restore() }
        firstStarted.await()
        val duplicate = async { restore() }
        runCurrent()
        assertFalse(duplicate.isCompleted)

        finishFirst.complete(Unit)

        assertEquals("started", first.await())
        assertNull(duplicate.await())
        assertEquals(1, starts)
    }

    @Test
    fun nonRecordingForegroundActionsInvokeOnlyTheirOwnedEffect() = runTest {
        val cases = listOf(
            ForegroundPlaybackAction.None to emptyList(),
            ForegroundPlaybackAction.StopLive to listOf("stop-live"),
            ForegroundPlaybackAction.PauseRecording to listOf("pause-recording"),
            ForegroundPlaybackAction.ResumeLive(ChannelId(7)) to listOf("resume-live:7"),
        )

        cases.forEach { (action, expected) ->
            val events = mutableListOf<String>()
            executeForegroundPlaybackAction(
                action = action,
                stopLive = { events += "stop-live" },
                pauseRecording = { events += "pause-recording" },
                resumeLive = { events += "resume-live:${it.value}" },
                resumeRecording = { events += "resume-recording" },
            )

            assertEquals(action.toString(), expected, events)
        }
    }

    @Test
    fun recordingForegroundResumeUsesOnlyTheExistingPlayerTarget() = runTest {
        val events = mutableListOf<String>()

        executeForegroundPlaybackAction(
            action = ForegroundPlaybackAction.ResumeRecording,
            stopLive = { events += "stop-live" },
            pauseRecording = { events += "pause-recording" },
            resumeLive = { events += "resume-live:$it" },
            resumeRecording = { events += "resume-recording" },
        )

        assertEquals(listOf("resume-recording"), events)
    }

    private fun currentObservation(
        channels: List<Channel> = emptyList(),
        recordings: List<DvrEntry> = emptyList(),
    ): SessionObservation = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED,
            ),
        ),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = channels)),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
    )
}
