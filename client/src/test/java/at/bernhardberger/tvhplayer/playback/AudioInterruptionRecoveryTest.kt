@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
)

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class AudioInterruptionRecoveryTest : AudioInterruptionFixture() {
    @Test fun interruptionDuringAdmittedRecoveryBackoffRetunesOnceAfterGain() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        // The backoff passes while the interruption holds playback: nothing retunes.
        advanceRecoveryTime()
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
        advanceRecoveryTime()
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun rejectedInterruptionHoldKeepsTheAdmittedRecoveryAndItsMute() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        val requests = focus.requests.size
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        focus.send(AudioInterruption.TRANSIENT_LOSS)
        await { connection.subscribeCount == subscriptions + 1 }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue(audioDisabled())
        assertEquals(requests, focus.requests.size)
        advanceRecoveryTime()
        settle()
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun permanentLossDuringRecoveryBackoffRetunesOnlyOnExplicitPlay() = exercise {
        awaitSecondRecoveryBackoff()
        val subscriptions = connection.subscribeCount
        focus.send(AudioInterruption.PERMANENT_LOSS)
        await { !player.playWhenReady && connection.speeds == listOf(0) }
        advanceRecoveryTime()
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime()
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime()
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
        advanceRecoveryTime()
        settle()
        assertEquals(subscriptions, connection.subscribeCount)
        runtime.onAppForegrounded()
        await { connection.subscribeCount == subscriptions + 1 }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) && player.playWhenReady }
        advanceRecoveryTime(300)
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
        val firstInstall = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { firstInstall += runtime.state.value }
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val failed = runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1))))
        assertEquals(listOf(AppPlaybackState.Starting), firstInstall)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
        settle()
        assertEquals(1, connection.subscribeCount)
        runtime.onAppForegrounded()
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        await { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) && player.playWhenReady }
        advanceRecoveryTime(300)
        settle()
        assertEquals(2, connection.subscribeCount)
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
                stopped to LiveRetuneDecision.Admitted(attempt = 2, delay = 60.seconds)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime()
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
        advanceRecoveryTime(300)
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

}
