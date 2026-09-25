@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlaybackException
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionPriority
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionCall
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Live Pause while the timeshift grant is undecided: remembered locally, sent once on the grant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LivePauseRuntimeTest {
    @Test fun availabilityFollowsTargetSettingGrantAndReadiness() {
        assertEquals(LivePauseAvailability.NONE, livePauseAvailability(false, true, true, true))
        assertEquals(LivePauseAvailability.OFF, livePauseAvailability(true, false, false, true))
        assertEquals(LivePauseAvailability.READY, livePauseAvailability(true, true, true, true))
        assertEquals(LivePauseAvailability.UNAVAILABLE, livePauseAvailability(true, true, false, true))
        assertEquals(LivePauseAvailability.STARTING, livePauseAvailability(true, true, false, false))
    }

    @Test fun startingPressSendsExactlyOneServerPauseOnlyOnceGrantedAndFirstPictureReady() = exercise {
        val gate = startingLive()
        assertTrue(player.playWhenReady)
        assertNull(runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertEquals(LivePauseState(LivePauseAvailability.STARTING, pending = true), runtime.livePause.value)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)

        // A server hold before the first picture would leave the viewer on "Tuning…".
        grant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY, pending = true), runtime.livePause.value)
        assertFalse(player.playWhenReady)

        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun grantedPressBeforeFirstPicturePendsUntilReady() = exercise {
        grant(startingLive())
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY) }
        assertNull(runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertEquals(LivePauseState(LivePauseAvailability.READY, pending = true), runtime.livePause.value)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)

        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun pressAfterGrantAndFirstPicturePausesTheServerAtOnce() = exercise {
        live()
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        await { pause.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, pause.await())
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.livePause.value.pending)
        assertFalse(player.playWhenReady)
    }

    @Test fun readyWithoutGrantDropsPendingPauseRestoresPlayAndNotifiesOnce() = exercise {
        undecidedLiveWithoutGrant()
        assertNull(runtime.pauseTimeshiftPlayback())
        assertFalse(player.playWhenReady)
        assertTrue(runtime.livePause.value.pending)

        playerReady()
        await { runtime.livePauseNotice.value != null }
        settle()
        assertEquals(LivePauseState(LivePauseAvailability.UNAVAILABLE), runtime.livePause.value)
        assertTrue(player.playWhenReady)
        assertEquals(emptyList<Int>(), connection.speeds)
        val notice = runtime.livePauseNotice.value!!
        runtime.consumeLivePauseNotice(notice)
        playerReady()
        settle()
        assertNull(runtime.livePauseNotice.value)
        assertEquals(1, focus.requestCount)
    }

    @Test fun resumeWhilePendingPlaysLocallyWithoutServerCommand() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        runtime.resumeTimeshift()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        grant(gate)
        await { runtime.livePause.value.availability == LivePauseAvailability.READY }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
    }

    @Test fun channelChangeClearsPendingPauseWithoutServerCommand() = exercise {
        undecidedLiveWithoutGrant()
        assertNull(runtime.pauseTimeshiftPlayback())
        assertTrue(runtime.livePause.value.pending)

        live(channel = 2)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY) }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun timeshiftOffInSettingsIsOffAndNeverPends() = exercise {
        live(timeshift = false)
        assertEquals(LivePauseState(LivePauseAvailability.OFF), runtime.livePause.value)
        val pause = scope.async { runtime.pauseTimeshiftPlayback() }
        await { pause.isCompleted }
        settle()
        assertFalse(runtime.livePause.value.pending)
        assertEquals(emptyList<Int>(), connection.speeds)
    }

    @Test fun backgroundWithPendingPauseKeepsPausedIntent() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        // The grant arrives while the app leaves, so the keep decision sees timeshift available.
        afterPause = {
            afterPause = {}
            runBlocking { grant(gate) }
        }
        val commands = connection.calls.size
        runtime.onAppBackgrounded()
        await { connection.priorityChanges == listOf(LiveSubscriptionPriority.YIELD) }
        assertEquals(listOf(ScriptedSubscriptionCall.SPEED, ScriptedSubscriptionCall.PRIORITY), controlCalls(commands))
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.livePause.value.pending)

        runtime.onAppForegrounded()
        await { connection.priorityChanges.size == 2 }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertNull(runtime.backgroundNotice.value)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun keptPausedRetuneStaysPausedWhenItsGrantArrives() = exercise {
        val gate = keptPausedRetune()
        val speeds = connection.speeds.size

        grant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        settle()
        assertEquals(speeds, connection.speeds.size)
        playerReady()
        await { connection.speeds.size > speeds }
        settle()
        assertEquals(listOf(0), connection.speeds.drop(speeds))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertEquals(1, focus.requestCount)
        assertNull(runtime.backgroundNotice.value)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun keptPausedRetuneFailsClosedWhenTheServerRejectsItsPause() = exercise {
        keptPausedRetuneFailsClosedOnGrantedPause(SubscriptionOperationResult.ServerRejected)
    }

    @Test fun keptPausedRetuneFailsClosedWhenItsPauseTimesOut() = exercise {
        keptPausedRetuneFailsClosedOnGrantedPause(SubscriptionOperationResult.Timeout)
    }

    @Test fun playerErrorDropsPendingPauseSoTheGrantSendsNoServerPause() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        assertTrue(runtime.livePause.value.pending)

        playerError()
        await { runtime.state.value is AppPlaybackState.Failed }
        assertFalse(runtime.livePause.value.pending)
        grant(gate)
        await { runtime.livePause.value.availability == LivePauseAvailability.READY }
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertFalse(runtime.livePause.value.pending)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun playerErrorStopsPausedKeptRetuneWithoutAutoplay() = exercise {
        val gate = keptPausedRetune()
        val speeds = connection.speeds.size

        playerError()
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        assertNull(runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertEquals(LivePauseState(), runtime.livePause.value)
        beforeSubscribe = {}
        gate.complete(Unit)
        settle()
        assertEquals(speeds, connection.speeds.size)
        assertFalse(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun keptPausedRetuneWhosePlayerFailedDuringInstallationStopsWithoutServerPause() = exercise {
        keptPausedInBackground()
        val gate = holdSubscribe()
        val speeds = connection.speeds.size
        val playRequested = trackPlayRequests()
        forcedPlayerError = ExoPlaybackException.createForUnexpected(
            RuntimeException("test"), PlaybackException.ERROR_CODE_UNSPECIFIED)
        // Stopping at commit never leaves a pending pause for a later observation to resolve.
        val published = mutableListOf<LivePauseState>()
        val collector = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            runtime.livePause.collect { published += it }
        }

        runtime.onAppForegrounded()
        assertFailedClosed(speeds, playRequested)
        collector.cancel()
        assertFalse(published.any { it.pending })
        assertEquals(speeds, connection.speeds.size)
        forcedPlayerError = null
        beforeSubscribe = {}
        gate.complete(Unit)
        settle()
        assertEquals(speeds, connection.speeds.size)
        assertFalse(player.playWhenReady)
    }

    @Test fun playerErrorDuringPendingRetuneServerPauseStopsDespiteAcceptance() = exercise {
        val gate = keptPausedRetune()
        val speeds = connection.speeds.size
        val playRequested = trackPlayRequests()
        val entered = CompletableDeferred<Unit>()
        val release = holdSpeed(entered)

        releaseGrant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        playerReady()
        await { entered.isCompleted }
        playerError()
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        release.complete(Unit)
        assertFailedClosed(speeds, playRequested)
        assertEquals(listOf(0), connection.speeds.drop(speeds))
    }

    @Test fun playerErrorDuringDirectRetuneServerPauseStopsDespiteAcceptance() = exercise {
        keptPausedInBackground()
        val playRequested = trackPlayRequests()
        val entered = CompletableDeferred<Unit>()
        val release = holdSpeed(entered)
        // Grant and first picture are both there before the paused retune decides: a direct server pause.
        grantDuringRetunePause(firstPicture = true)
        val speeds = connection.speeds.size

        runtime.onAppForegrounded()
        await { entered.isCompleted }
        assertFalse(runtime.livePause.value.pending)
        playerError()
        release.complete(Unit)
        assertFailedClosed(speeds, playRequested)
        assertEquals(listOf(0), connection.speeds.drop(speeds))
    }

    @Test fun keptPausedRetuneGrantedAtCommitWaitsForFirstPictureBeforeServerPause() = exercise {
        keptPausedInBackground()
        val playRequested = trackPlayRequests()
        grantDuringRetunePause(firstPicture = false)
        val speeds = connection.speeds.size

        runtime.onAppForegrounded()
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        settle()
        assertEquals(speeds, connection.speeds.size)
        assertFalse(player.playWhenReady)

        playerReady()
        await { connection.speeds.size > speeds }
        settle()
        assertEquals(listOf(0), connection.speeds.drop(speeds))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertFalse(playRequested())
        assertNull(runtime.backgroundNotice.value)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun grantedPauseWithoutFirstPictureHoldsTheServerOnceTheBudgetExpires() = exercise {
        grant(startingLive())
        assertNull(runtime.pauseTimeshiftPlayback())
        settle()
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS - 1)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY, pending = true), runtime.livePause.value)

        scheduler.advanceTimeBy(1)
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)

        playerReady()
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
    }

    @Test fun startingPausePastTheBudgetHoldsTheServerAtTheGrant() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        settle()
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.STARTING, pending = true), runtime.livePause.value)

        // No second wait for the first picture: the grant resolves the expired pause.
        grant(gate)
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)

        playerReady()
        settle()
        assertEquals(listOf(0), connection.speeds)
    }

    @Test fun resumeCancelsTheFirstPictureBudgetWithoutAnyServerCommand() = exercise {
        grant(startingLive())
        assertNull(runtime.pauseTimeshiftPlayback())
        runtime.resumeTimeshift()
        assertTrue(player.playWhenReady)
        assertFalse(runtime.livePause.value.pending)

        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        playerReady()
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun firstPictureBeforeTheBudgetSendsTheOnlyServerPause() = exercise {
        grant(startingLive())
        assertNull(runtime.pauseTimeshiftPlayback())
        settle()
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS / 2)
        playerReady()
        await { connection.speeds.isNotEmpty() }

        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
    }

    @Test fun channelChangeCancelsTheFirstPictureBudget() = exercise {
        grant(startingLive())
        assertNull(runtime.pauseTimeshiftPlayback())
        assertTrue(runtime.livePause.value.pending)

        live(channel = 2)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY) }
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(emptyList<Int>(), connection.speeds)
        assertTrue(player.playWhenReady)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun backgroundCancelsTheFirstPictureBudget() = exercise {
        grant(startingLive())
        assertNull(runtime.pauseTimeshiftPlayback())
        assertTrue(runtime.livePause.value.pending)

        runtime.onAppBackgrounded()
        await { connection.priorityChanges == listOf(LiveSubscriptionPriority.YIELD) }
        assertFalse(runtime.livePause.value.pending)
        val speeds = connection.speeds.toList()
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        settle()
        assertEquals(speeds, connection.speeds)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertNull(runtime.backgroundNotice.value)
    }

    @Test fun keptPausedRetuneWithoutFirstPictureHoldsTheServerAtTheBudget() = exercise {
        keptPausedInBackground()
        val playRequested = trackPlayRequests()
        grantDuringRetunePause(firstPicture = false)
        val speeds = connection.speeds.size
        runtime.onAppForegrounded()
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        settle()
        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS - 1)
        settle()
        assertEquals(speeds, connection.speeds.size)

        scheduler.advanceTimeBy(1)
        await { connection.speeds.size > speeds }
        settle()
        assertEquals(listOf(0), connection.speeds.drop(speeds))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(LivePauseState(LivePauseAvailability.READY), runtime.livePause.value)
        assertFalse(player.playWhenReady)
        assertFalse(playRequested())
        assertNull(runtime.backgroundNotice.value)
        assertNull(runtime.livePauseNotice.value)

        playerReady()
        settle()
        assertEquals(listOf(0), connection.speeds.drop(speeds))
    }

    @Test fun keptPausedRetuneRejectedAtTheBudgetStopsWithTunerLost() = exercise {
        keptPausedInBackground()
        val playRequested = trackPlayRequests()
        grantDuringRetunePause(firstPicture = false)
        val speeds = connection.speeds.size
        runtime.onAppForegrounded()
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        settle()
        assertEquals(speeds, connection.speeds.size)

        scheduler.advanceTimeBy(FIRST_PICTURE_BUDGET_MILLIS)
        assertFailedClosed(speeds, playRequested)
        assertEquals(listOf(0), connection.speeds.drop(speeds))
    }

    @Test fun transientLossWhilePausePendsKeepsThePausedIntentThroughGrantAndGain() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        val callback = focus.requests.last()
        val requests = focus.requestCount

        callback(AudioInterruption.TRANSIENT_LOSS)
        settle()
        assertFalse(runtime.isInterruptionMuted)
        assertFalse(player.playWhenReady)
        assertTrue(runtime.livePause.value.pending)

        grant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.livePause.value.pending)
        assertFalse(runtime.isInterruptionMuted)
        assertFalse(player.playWhenReady)

        callback(AudioInterruption.GAIN)
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.isInterruptionMuted)
        assertFalse(player.playWhenReady)
        assertEquals(requests, focus.requestCount)

        val resume = scope.async { runtime.resumeTimeshift() }
        await { resume.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertTrue(player.playWhenReady)
        assertEquals(requests + 1, focus.requestCount)
        assertNull(runtime.livePauseNotice.value)
    }

    @Test fun permanentLossWhilePausePendsKeepsThePausedIntentThroughGrant() = exercise {
        val gate = startingLive()
        assertNull(runtime.pauseTimeshiftPlayback())
        val requests = focus.requestCount

        focus.requests.last()(AudioInterruption.PERMANENT_LOSS)
        settle()
        assertFalse(runtime.isInterruptionMuted)
        assertFalse(player.playWhenReady)
        assertTrue(runtime.livePause.value.pending)

        grant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        playerReady()
        await { connection.speeds.isNotEmpty() }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(runtime.livePause.value.pending)
        assertFalse(runtime.isInterruptionMuted)
        assertFalse(player.playWhenReady)
        assertEquals(requests, focus.requestCount)

        val resume = scope.async { runtime.resumeTimeshift() }
        await { resume.isCompleted }
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        settle()
        assertEquals(listOf(0, 100), connection.speeds)
        assertTrue(player.playWhenReady)
        assertEquals(requests + 1, focus.requestCount)
        assertNull(runtime.livePauseNotice.value)
    }

    private fun exercise(block: suspend BackgroundPlaybackRuntimeTest.Fixture.() -> Unit) =
        BackgroundPlaybackRuntimeTest.exercise(PlaybackRuntimePolicy.fromPlayerSettings(), block)

    /** Holds the next subscription before its confirmation; completing the result releases it. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.holdSubscribe(): CompletableDeferred<Unit> {
        val gate = CompletableDeferred<Unit>()
        beforeSubscribe = { gate.await() }
        return gate
    }

    /** A live target installed with timeshift requested whose subscription is not yet confirmed. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.startingLive(): CompletableDeferred<Unit> {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        val gate = holdSubscribe()
        val install = scope.async { runtime.playLive(selection(1)) }
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        await { runtime.livePause.value.availability == LivePauseAvailability.STARTING && player.playWhenReady }
        return gate
    }

    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.grant(gate: CompletableDeferred<Unit>) {
        releaseGrant(gate)
        await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState is LiveTimeshiftState.Available }
    }

    /** Lets the held subscription confirm and start, which publishes its grant. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.releaseGrant(gate: CompletableDeferred<Unit>) {
        val previous = connection.subscribeCount
        beforeSubscribe = {}
        gate.complete(Unit)
        await { connection.subscribeCount > previous }
        connection.awaitCollectionRegistered()
        startSubscription()
    }

    /** A started live target whose subscription came without a grant, before the player is ready. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.undecidedLiveWithoutGrant() {
        settings.setTimeshiftEnabled(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        val install = scope.async { runtime.playLive(selection(1)) }
        await { connection.subscribeCount > 0 }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { install.isCompleted }
        assertTrue(install.await()?.isStarted == true)
        settle()
        assertEquals(LivePauseAvailability.STARTING, runtime.livePause.value.availability)
        assertTrue(player.playWhenReady)
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.selection(channel: Long) =
        requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel)))

    /** A paused live channel kept in the background whose foreground retune waits for its grant. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.keptPausedRetune(): CompletableDeferred<Unit> {
        keptPausedInBackground()
        val gate = holdSubscribe()
        val speeds = connection.speeds.size
        runtime.onAppForegrounded()
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.STARTING, pending = true) }
        assertFalse(player.playWhenReady)
        assertEquals(speeds, connection.speeds.size)
        return gate
    }

    /** A paused live channel kept in the background that cannot return to normal priority. */
    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.keptPausedInBackground() {
        live()
        connection.emit(SubscriptionEvent.Speed(0))
        await { !player.playWhenReady }
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.scriptPriority(SubscriptionOperationResult.NotSupported)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
    }

    /** Makes the retune's grant (and optionally its first picture) arrive inside its paused start. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.grantDuringRetunePause(firstPicture: Boolean) {
        val previousMediaItem = player.currentMediaItem
        afterPause = {
            if (player.currentMediaItem !== previousMediaItem && runtime.activeTarget.value != null) {
                afterPause = {}
                runBlocking {
                    await { connection.subscribeCount == 2 }
                    connection.awaitCollectionRegistered()
                    startSubscription()
                    await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                        ?.timeshiftState is LiveTimeshiftState.Available }
                }
                if (firstPicture) playerReady()
            }
        }
    }

    /** Records whether anything asks the player to play from now on. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.trackPlayRequests(): () -> Boolean {
        var playRequested = false
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) playRequested = true
            }
        })
        return { playRequested }
    }

    /** Holds the next server speed command after it was sent; completing the result releases it. */
    private fun BackgroundPlaybackRuntimeTest.Fixture.holdSpeed(entered: CompletableDeferred<Unit>): CompletableDeferred<Unit> {
        val release = CompletableDeferred<Unit>()
        beforeSpeed = {
            beforeSpeed = {}
            entered.complete(Unit)
            release.await()
        }
        return release
    }

    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.assertFailedClosed(speeds: Int, playRequested: () -> Boolean) {
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        settle()
        assertNull(runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertFalse(playRequested())
        assertEquals(LivePauseState(), runtime.livePause.value)
        assertNull(runtime.livePauseNotice.value)
        assertTrue(connection.speeds.drop(speeds).all { it == 0 })
    }

    private suspend fun BackgroundPlaybackRuntimeTest.Fixture.keptPausedRetuneFailsClosedOnGrantedPause(
        reply: SubscriptionOperationResult<Unit>,
    ) {
        val gate = keptPausedRetune()
        val speeds = connection.speeds.size
        connection.scriptSpeed(reply)
        val playRequested = trackPlayRequests()

        releaseGrant(gate)
        await { runtime.livePause.value == LivePauseState(LivePauseAvailability.READY, pending = true) }
        assertEquals(speeds, connection.speeds.size)
        playerReady()
        await { connection.speeds.size > speeds }
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        settle()
        assertEquals(listOf(0), connection.speeds.drop(speeds))
        assertNull(runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertFalse(playRequested())
        assertNull(runtime.livePauseNotice.value)
        assertEquals(1, focus.requestCount)
    }

    private fun BackgroundPlaybackRuntimeTest.Fixture.playerError() {
        val error = PlaybackException("test", null, PlaybackException.ERROR_CODE_UNSPECIFIED)
        playerListeners.toList().forEach { it.onPlayerError(error) }
    }
}
