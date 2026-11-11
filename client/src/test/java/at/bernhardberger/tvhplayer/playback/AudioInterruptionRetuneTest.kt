@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlaybackException
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
internal class AudioInterruptionRetuneTest : AudioInterruptionFixture() {
    @Test fun anErrorClearedDuringItsDispatchDoesNotFailTheReplacement() = exercise {
        live(timeshift = false)
        live(timeshift = false, channel = 2)
        val replacement = player.currentMediaItem
        val states = mutableListOf<AppPlaybackState>()
        val observing = scope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(scheduler)) {
            runtime.state.collect { states += it }
        }
        val errorCycleFinished = CompletableDeferred<Unit>()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // The SDK repairs the replacement in this dispatch, before onEvents.
                player.prepare()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYER_ERROR)) errorCycleFinished.complete(Unit)
            }
        })
        player.createMessage { _, _ ->
            throw ExoPlaybackException.createForSource(IOException("retired stream"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }.send()
        await("repaired player error cycle") { errorCycleFinished.isCompleted }
        settle()
        observing.cancelAndJoin()
        assertNull(player.playerError)
        assertFalse("A repaired predecessor error must never publish Failed", states.any { it is AppPlaybackState.Failed })
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertSame(replacement, player.currentMediaItem)
        assertEquals(AppPlaybackState.Starting, runtime.state.value)
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertTrue(player.playWhenReady)
    }

    @Test fun anErrorStillSetAfterItsDispatchFailsTheActiveTarget() = exercise {
        live(timeshift = false)
        assertTrue(runtime.liveStartUndisturbed.value)
        player.createMessage { _, _ ->
            throw ExoPlaybackException.createForSource(IOException("stream failed"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }.send()
        await("active target failure") { runtime.state.value is AppPlaybackState.Failed }
        assertNotNull(player.playerError)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), (runtime.state.value as AppPlaybackState.Failed).target)
        assertFalse(runtime.liveStartUndisturbed.value)
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

    @Test fun sessionPauseQueuedDuringAReconnectRetuneHoldsTheRetunedChannel() = exercise {
        live(timeshift = true)
        // The server restarted: a new session of the same profile is current.
        replaceSession()
        // The system's Pause arrives while the reconnect retune is under way, before it commits.
        var pause: Job? = null
        beforeLiveBinding = { if (pause == null) pause = runtime.setSessionPlayWhenReady(false) { true } }
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { connection.subscribeCount == 2 }
        val replacement = connection.awaitCollectionRegistered()
        startSubscription(replacement)
        await { retune.isCompleted && pause?.isCompleted == true }
        assertEquals(PlaybackTargetResult.STARTED, retune.await())
        connection.emit(replacement, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
        markReady()
        // The retuned target is the same channel for the same intent: the Pause holds it.
        await { connection.speeds == listOf(0) }
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(2, connection.subscribeCount)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun aServerSavedWhileAReconnectRetuneReadsItsSettingsRetunesNothing() = exercise {
        live(timeshift = false)
        replaceSession()
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        beforeSettingsRead = {
            beforeSettingsRead = {}
            reading.complete(Unit)
            release.await()
        }
        val retune = scope.async { runtime.retuneAfterReconnect() }
        reading.await()
        // The retune was admitted for this profile; another server is saved before it installs.
        val generation = profiles.configurationGeneration.value
        scope.launch { profiles.saveServer("other.test", 9982) }
        await { profiles.configurationGeneration.value > generation && profiles.serverProfile.value != null }
        release.complete(Unit)
        await { retune.isCompleted }
        settle()
        assertNull(retune.await())
        assertEquals("channel one of another server is not this one", 1, connection.subscribeCount)
    }

    @Test fun aServerSavedWhileAReconnectRetuneReadsItsAudioRetriesNothingThere() = exercise {
        live(timeshift = false)
        installManualAudio()
        restartServer()
        val reading = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        // The first read is the player settings, the second the channel's stored audio.
        beforeSettingsRead = {
            if (++reads == 2) { beforeSettingsRead = {}; reading.complete(Unit); release.await() }
        }
        val retune = scope.async { runtime.retuneAfterReconnect() }
        reading.await()
        // Another server is saved and its session is current before the audio read returns.
        val generation = profiles.configurationGeneration.value
        scope.launch { profiles.saveServer("other.test", 9982) }
        await { profiles.configurationGeneration.value > generation && profiles.serverProfile.value != null }
        replaceSession()
        release.complete(Unit)
        await { retune.isCompleted }
        settle()
        assertFalse(retune.await()?.isStarted == true)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        assertEquals("channel one of another server is not this one", 1, connection.subscribeCount)
    }

    @Test fun aChannelChangeDuringAReconnectRetryEndsTheRetries() = exercise(
        // This fixture reports READY only to the app, not to the SDK's real player. Keep its
        // preparation watchdog outside the stale-retry observation window for the new channel.
        recoveryPolicy = PlaybackRecoveryPolicy(preparationDurationMillis = 60_000L),
    ) {
        live(timeshift = false)
        restartServer()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        // Observe the ended stream's asynchronous error before zapping, without consuming backoff.
        advanceRecoveryTime(100)
        await("ended stream error before channel change") { player.playerError != null }
        // The viewer picks another channel while the retry waits.
        session.scriptLivePlaybackSuccess(manager)
        live(timeshift = false, channel = 2)
        advanceRecoveryTime(40_001)
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering || runtime.state.value is AppPlaybackState.Failed)
        assertEquals("No stale reconnect retry: recoveries=$recoveries", 2, connection.subscribeCount)
    }

    @Test fun theEndedStreamsErrorDuringAReconnectBackoffKeepsTheChannelRecovering() = exercise {
        live(timeshift = false)
        restartServer()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        // The ended stream's error arrives while the retry waits.
        reportStreamError()
        settle()
        assertEquals(2_000L, (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        advanceRecoveryTime(2_001)
        for (delayMillis in listOf(5_000L, 10_000L, 20_000L)) {
            await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == delayMillis }
            advanceRecoveryTime(delayMillis + 1)
        }
        // Out of retries: the failure shows.
        await { runtime.state.value is AppPlaybackState.Failed }
        assertNull(runtime.activeTarget.value)
    }

    @Test fun theEndedStreamsErrorAfterAReconnectRetryFoundAnotherSessionShowsTheFailure() = exercise {
        live(timeshift = false)
        restartServer()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        // The session changes during the backoff: the retry no longer runs.
        replaceSession()
        advanceRecoveryTime(2_001)
        settle()
        reportStreamError()
        await { runtime.state.value is AppPlaybackState.Failed }
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun theEndedStreamsErrorHeldBackDuringABackoffShowsWhenTheRetryDoesNotRun() = exercise {
        live(timeshift = false)
        restartServer()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        reportStreamError()
        settle()
        assertEquals(2_000L, (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
        // The session changes during the backoff: the retry no longer runs.
        replaceSession()
        advanceRecoveryTime(2_001)
        await { runtime.state.value is AppPlaybackState.Failed }
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun aChannelPausedBehindBrowseStopsAtTheReconnectInsteadOfRetuning() = exercise {
        live(timeshift = false)
        runtime.pause()
        await { !player.playWhenReady }
        replaceSession()
        val retune = scope.async { runtime.retuneAfterReconnect(stopIfPaused = true) }
        await { retune.isCompleted }
        assertNull(retune.await())
        await { runtime.activeTarget.value == null }
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun aReconnectRetuneThatKeepsFailingRetriesWithBackoffThenStops() = exercise {
        live(timeshift = false)
        restartServer()
        // The ended stream's player error is already in: the retune and its retries keep their
        // target all the same.
        reportStreamError()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        assertFalse(retune.await()?.isStarted == true)
        // The first retry follows at once; the later ones back off as any live recovery does.
        for (delayMillis in listOf(2_000L, 5_000L, 10_000L, 20_000L)) {
            await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == delayMillis }
            advanceRecoveryTime(delayMillis + 1)
        }
        // Out of retries: the ended stream is stopped, not left frozen on screen.
        await { runtime.state.value is AppPlaybackState.Failed }
        assertNull(runtime.activeTarget.value)
        assertEquals(1, connection.subscribeCount)
    }

    // Like the channel-change case, this observes the real ended-period error, not reportStreamError().
    @Test fun aFailedReconnectRetuneRetriesUntilTheChannelComesBack() = exercise {
        live(timeshift = false)
        restartServer()
        session.scriptLivePlaybackFailure(PlaybackBindingResult.TargetUnavailable)
        val retune = scope.async { runtime.retuneAfterReconnect() }
        await { retune.isCompleted }
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        session.scriptLivePlaybackSuccess(manager)
        advanceRecoveryTime(100)
        await("ended stream error before reconnect retry") { player.playerError != null }
        assertEquals("before retry: ${runtime.state.value}", 2_000L,
            (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis)
        advanceRecoveryTime(2_001)
        await { connection.subscribeCount == 2 }
        startSubscription(connection.awaitCollectionRegistered())
        markReady()
        await { runtime.state.value !is AppPlaybackState.Recovering }
        settle()
        assertFalse(runtime.state.value is AppPlaybackState.Failed)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue(player.playWhenReady)
        assertEquals(2, connection.subscribeCount)
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
        await { zap?.isCompleted == true && pause?.isCompleted == true }
        assertTrue(zap?.await()?.isStarted == true)
        // The retired retune may never subscribe. Feed whichever collectors actually open,
        // without assuming their count or registration order.
        val startup = scope.launch {
            while (isActive) {
                val registration = connection.awaitCollectionRegistered()
                startSubscription(registration)
                connection.emit(registration, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
            }
        }
        try {
            await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null &&
                pendingSessions == 1 }
        } finally {
            startup.cancelAndJoin()
        }
        markReady()
        advanceRecoveryTime(300)
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
        advanceRecoveryTime()
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
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
        await { connection.subscribeCount == 2 }
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // The first picture sends the held pause; the server rejects it and playback goes on.
        connection.scriptSpeed(SubscriptionOperationResult.Failed(TvheadendFailure.SERVER_REJECTED))
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        markReady()
        await { runtime.livePauseNotice.value != null }
        assertEquals(listOf(0), connection.speeds)
        advanceRecoveryTime(300)
        settle()
        // Playing again, the deferred recovery goes on without a Play from the viewer: one
        // retune, admitted as the target's first attempt.
        await { connection.subscribeCount == 2 }
        assertEquals("the recovery the rejected pause deferred stayed stranded", 2, connection.subscribeCount)
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
        startSubscription(connection.awaitCollectionRegistered())
        await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering }
        advanceRecoveryTime(300)
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
        advanceRecoveryTime(300)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
        // The first picture without a grant drops the Pause without any server command.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        markReady()
        await { runtime.livePauseNotice.value != null }
        advanceRecoveryTime(300)
        settle()
        assertEquals(emptyList<Int>(), connection.speeds)
        await { connection.subscribeCount == 2 }
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
        await { installing.size == 2 }
        startSurvivingInstallation()
        assertEquals(2, installing.size)
        assertEquals(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L), installing.last())
        advanceRecoveryTime(300)
        settle()
        // No further install ran, and only the retune's session outlived the retired first one.
        assertEquals(2, installing.size)
        assertEquals(1, pendingSessions)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun escalationOfAChannelChangesNewSourceRetunesTheNewChannelOnce() = exercise {
        live(timeshift = false)
        val installingFrom = mutableListOf<AppPlaybackTarget?>()
        beforeLiveBinding = { installingFrom += runtime.activeTarget.value }
        escalateOnNextSource()
        val zap = scope.async {
            runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(2))))
        }
        await { zap.isCompleted }
        assertTrue(zap.await()?.isStarted == true)
        await { installingFrom.size == 2 }
        startSurvivingInstallation()
        advanceRecoveryTime(300)
        settle()
        assertEquals("state=${runtime.state.value}, subscribes=${connection.subscribeCount}, unsubscribes=${connection.unsubscribeCount}", 1, pendingSessions)
        assertEquals(listOf(AppPlaybackTarget.Live(ChannelId(1)), AppPlaybackTarget.Live(ChannelId(2))), installingFrom)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun escalationOfAnInstallThatANewerChannelChangeReplacedIsDropped() = exercise {
        live(timeshift = false)
        val bindings = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { bindings += runtime.state.value }
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
        assertTrue(zap.await()?.isStarted == true)
        assertTrue(zapBack?.await()?.isStarted == true)
        startSurvivingInstallation()
        advanceRecoveryTime(300)
        settle()
        // Channel 1 committed after channel 2: channel 2's escalation touches it not.
        assertEquals(2, bindings.size)
        assertEquals("state=${runtime.state.value}, subscribes=${connection.subscribeCount}, unsubscribes=${connection.unsubscribeCount}", 1, pendingSessions)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(runtime.state.value is AppPlaybackState.Recovering)
        // Channel 1's own first recovery still finds its whole budget.
        val installing = mutableListOf<AppPlaybackState>()
        beforeLiveBinding = { installing += runtime.state.value }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { installing.isNotEmpty() }
        assertEquals(listOf(AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 0L)), installing)
    }

    @Test fun failedNewerSelectionDoesNotReviveThePreviousIntentsRecovery() = exercise {
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
        // Drain installation callbacks before observing the fallback through the retry window.
        settle()
        advanceRecoveryTime(3_300)
        settle()
        // The old queued report is invalidated, but fresh reports belong to the settled fallback.
        assertEquals(1, installing.size)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { installing.size == 2 }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

}
