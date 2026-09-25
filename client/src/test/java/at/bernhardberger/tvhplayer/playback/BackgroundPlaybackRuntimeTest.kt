@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
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
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackgroundPlaybackRuntimeTest {
    @Test fun keepAndReturnOrdersCommandsAndReacquiresFocusWithoutRetune() = exercise {
        live()
        val commands = connection.calls.size
        runtime.onAppBackgrounded()
        await { connection.priorityChanges == listOf(LiveSubscriptionPriority.YIELD) }
        assertFalse(player.playWhenReady)
        assertTrue(focus.abandons > 0)
        assertEquals(listOf(ScriptedSubscriptionCall.SPEED, ScriptedSubscriptionCall.PRIORITY), controlCalls(commands))
        scheduler.advanceTimeBy(60_000)
        runtime.onAppForegrounded()
        await { player.playWhenReady && connection.speeds == listOf(0, 100) }
        assertEquals(listOf(LiveSubscriptionPriority.YIELD, LiveSubscriptionPriority.NORMAL), connection.priorityChanges)
        assertEquals(listOf(ScriptedSubscriptionCall.SPEED, ScriptedSubscriptionCall.PRIORITY,
            ScriptedSubscriptionCall.PRIORITY, ScriptedSubscriptionCall.SPEED), controlCalls(commands))
        assertEquals(1, connection.subscribeCount)
        assertEquals(2, focus.requests.size)
        assertNull(runtime.backgroundNotice.value)
        scheduler.advanceTimeBy(1_200_000)
        settle()
        assertNotNull(runtime.activeTarget.value)
    }

    @Test fun serverPausedChannelRemainsPausedOnReturn() = exercise {
        live()
        connection.emit(SubscriptionEvent.Speed(0))
        await { !player.playWhenReady }
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        val speeds = connection.speeds.toList()
        runtime.onAppForegrounded()
        await { connection.priorityChanges.size == 2 }
        settle()
        assertEquals(speeds, connection.speeds)
        assertFalse(player.playWhenReady)
        assertEquals(1, focus.requests.size)
    }

    @Test fun expiryUsesVirtualTimeReleasesThenRetunesExactlyOnceWithNotice() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        scheduler.advanceTimeBy(1_199_999)
        settle()
        assertNotNull(runtime.activeTarget.value)
        scheduler.advanceTimeBy(1)
        await { runtime.activeTarget.value == null }
        assertEquals(1, connection.subscribeCount)
        assertEquals(1, connection.unsubscribeCount)
        assertNull(runtime.backgroundNotice.value)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.LIMIT_EXPIRED, runtime.backgroundNotice.value)
        runtime.consumeBackgroundNotice(BackgroundPlaybackNotice.LIMIT_EXPIRED)
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
        assertNull(runtime.backgroundNotice.value)
    }

    @Test fun standbyReleasesWithoutNoticeAndNoninteractiveBackgroundNeverYields() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        runtime.onDeviceStandby()
        await { runtime.activeTarget.value == null }
        returnAndRetune()
        assertNull(runtime.backgroundNotice.value)
        runtime.onAppBackgrounded(interactive = false)
        await { runtime.activeTarget.value == null }
        assertEquals(listOf(LiveSubscriptionPriority.YIELD), connection.priorityChanges)
    }

    @Test fun rejectedPauseAndUnsupportedYieldReleaseWithoutNotice() = exercise {
        for (rejectPause in listOf(true, false)) {
            live()
            if (rejectPause) connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
            else {
                connection.scriptSpeed(SubscriptionOperationResult.Ok(Unit))
                connection.scriptPriority(SubscriptionOperationResult.NotSupported)
            }
            val priorities = connection.priorityChanges.size
            runtime.onAppBackgrounded()
            await { runtime.activeTarget.value == null }
            assertEquals(priorities + if (rejectPause) 0 else 1, connection.priorityChanges.size)
            returnAndRetune()
            assertNull(runtime.backgroundNotice.value)
        }
    }

    @Test fun offAndNoTimeshiftKeepLegacyStopBehaviour() = exercise {
        for (timeshift in listOf(true, false)) {
            settings.setKeepChannelMinutes(if (timeshift) 0 else 20)
            live(timeshift = timeshift)
            runtime.onAppBackgrounded()
            await { runtime.activeTarget.value == null }
            returnAndRetune()
            assertNull(runtime.backgroundNotice.value)
        }
        assertTrue(connection.priorityChanges.isEmpty())
    }

    @Test fun serverStopReleasesKeptTunerAndDoesNotRecoverInBackground() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.emit(SubscriptionEvent.Stopped(SubscriptionCondition.STATUS_REPORTED, SubscriptionIssue.SUBSCRIPTION_OVERRIDDEN))
        await { runtime.activeTarget.value == null }
        scheduler.advanceTimeBy(120_000)
        settle()
        assertEquals(1, connection.subscribeCount)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
    }

    @Test fun recoveryRequestWhileKeptReleasesInsteadOfRetuning() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { runtime.activeTarget.value == null }
        scheduler.advanceTimeBy(120_000)
        settle()
        assertEquals(1, connection.subscribeCount)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
    }

    @Test fun foregroundQueuedBehindBackgroundRestoresAndOldFocusCannotResumeKeptTarget() = exercise {
        live()
        val oldFocus = focus.requests.last()
        runtime.onAppBackgrounded()
        runtime.onAppForegrounded()
        await { connection.priorityChanges.size == 2 && player.playWhenReady }
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 3 }
        oldFocus(AudioInterruption.GAIN)
        settle()
        assertFalse(player.playWhenReady)
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun deniedForegroundFocusKeepsPausedUntilExplicitResumeIsGranted() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        focus.granted = false
        runtime.onAppForegrounded()
        await { focus.requestCount == 2 }
        settle()
        assertFalse(player.playWhenReady)
        assertFalse(connection.speeds.contains(100))
        assertNull(focus.callback)
        assertEquals(listOf(LiveSubscriptionPriority.YIELD, LiveSubscriptionPriority.NORMAL), connection.priorityChanges)
        assertEquals(1, connection.subscribeCount)
        focus.granted = true
        val resume = scope.async { runtime.resumeTimeshift() }
        await { resume.isCompleted && player.playWhenReady }
        assertEquals(TimeshiftCommandResult.ACCEPTED, resume.await())
        assertEquals(3, focus.requestCount)
        assertEquals(1, connection.speeds.count { it == 100 })
        assertEquals(100, connection.speeds.last())
        assertEquals(1, connection.subscribeCount)
    }

    @Test fun rejectedNormalPriorityRetunesOnceRatherThanResumingYieldedTarget() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.scriptPriority(SubscriptionOperationResult.NotSupported)
        returnAndRetune()
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
        assertTrue(player.playWhenReady)
        assertEquals(2, focus.requestCount)
        assertNull(runtime.backgroundNotice.value)
    }

    @Test fun unavailableReplacementPauseStopsWithoutFocusOrAutoplayDespiteNormalSpeedObservation() = exercise {
        live()
        connection.emit(SubscriptionEvent.Speed(0))
        await { !player.playWhenReady }
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        val playTransitions = mutableListOf<Boolean>()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playTransitions += playWhenReady
            }
        })
        connection.scriptPriority(SubscriptionOperationResult.NotSupported)
        // The fresh subscription has no timeshift grant: pause must fail closed, not
        // treat a locally paused player as evidence that the server accepted it.
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
        // The replacement is paused as soon as it is installed. Open and start its subscription
        // first, so the pause doesn't race the subscription manager's own thread.
        val previousMediaItem = player.currentMediaItem
        afterPause = {
            if (player.currentMediaItem !== previousMediaItem && runtime.activeTarget.value != null) {
                afterPause = {}
                runBlocking {
                    await { connection.subscribeCount == 2 }
                    connection.awaitCollectionRegistered()
                    startSubscription()
                    connection.emit(SubscriptionEvent.Speed(100))
                }
            }
        }
        runtime.onAppForegrounded()
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        settle()
        assertNull(runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertFalse(playTransitions.contains(true))
        assertEquals(1, focus.requestCount)
        assertEquals(2, connection.subscribeCount)
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun acceptedReplacementPauseKeepsRetunedChannelPausedWithoutFocusOrNotice() = exercise {
        live()
        connection.emit(SubscriptionEvent.Speed(0))
        await { !player.playWhenReady }
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.scriptPriority(SubscriptionOperationResult.NotSupported)
        // Make the real SDK grant available before the replacement pause, rather than
        // exercising the sibling test's Pending/unavailable fail-closed path.
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
            }
        }
        val speeds = connection.speeds.size
        runtime.onAppForegrounded()
        await { connection.speeds.size > speeds }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertEquals(0, connection.speeds.last())
        assertEquals(2, connection.subscribeCount)
        assertEquals(1, focus.requestCount)
        assertNull(runtime.backgroundNotice.value)
    }

    @Test fun recoveryQueuedAfterBackgroundBehindLongCommandReleasesKeptTarget() = exercise {
        live()
        val resumeEntered = CompletableDeferred<Unit>()
        val finishResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100) {
                resumeEntered.complete(Unit)
                finishResume.await()
            }
        }
        val resume = scope.async { runtime.resumeTimeshift() }
        await { resumeEntered.isCompleted }
        runtime.onAppBackgrounded()
        scheduler.runCurrent() // Background queues for the occupied command lock first.
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        scheduler.runCurrent() // Recovery is queued, but not yet admitted.
        finishResume.complete(Unit)
        await { resume.isCompleted && runtime.activeTarget.value == null }
        assertEquals(1, connection.subscribeCount)
        assertEquals(listOf(LiveSubscriptionPriority.YIELD), connection.priorityChanges)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun stoppedAdmittedRecoveryCannotCancelNextTargetsQueuedRecovery() = exercise {
        live()
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { player.playWhenReady }
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
        // Retires the admitted recovery while it waits for its backoff.
        val stopped = scope.async { runtime.stop() }
        await { stopped.isCompleted }
        live(channel = 2)
        val subscriptions = connection.subscribeCount
        val resumeEntered = CompletableDeferred<Unit>()
        val finishResume = CompletableDeferred<Unit>()
        beforeSpeed = { speed ->
            if (speed == 100) {
                resumeEntered.complete(Unit)
                finishResume.await()
            }
        }
        val resume = scope.async { runtime.resumeTimeshift() }
        await { resumeEntered.isCompleted }
        runtime.onAppBackgrounded()
        scheduler.runCurrent()
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        scheduler.runCurrent()
        finishResume.complete(Unit)
        await { resume.isCompleted && runtime.activeTarget.value == null }
        assertEquals(subscriptions, connection.subscribeCount)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
        assertEquals(subscriptions + 1, connection.subscribeCount)
    }

    @Test fun rejectedServerResumeRetunesOnceWithTunerLostNotice() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.scriptSpeed(SubscriptionOperationResult.ServerRejected)
        returnAndRetune()
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        assertEquals(listOf(LiveSubscriptionPriority.YIELD, LiveSubscriptionPriority.NORMAL), connection.priorityChanges)
        assertEquals(1, connection.speeds.count { it == 100 })
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun unsupportedServerResumeIsNotMistakenForFocusDenial() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        connection.scriptSpeed(SubscriptionOperationResult.NotSupported)
        returnAndRetune()
        await { runtime.backgroundNotice.value == BackgroundPlaybackNotice.TUNER_LOST }
        assertTrue(player.playWhenReady)
        runtime.onAppForegrounded()
        settle()
        assertEquals(2, connection.subscribeCount)
    }

    @Test fun backgroundReleasesAdmittedRecoveryAndRetunesOnlyOnForeground() = exercise {
        for (returnBeforeBackoff in listOf(false, true)) {
            live()
            val initialSubscriptions = connection.subscribeCount
            runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            await { connection.subscribeCount == initialSubscriptions + 1 }
            connection.awaitCollectionRegistered()
            startSubscription()
            await { player.playWhenReady }
            connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
            await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available }
            runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
            await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
            val priorities = connection.priorityChanges.size
            runtime.onAppBackgrounded()
            await { runtime.activeTarget.value == null }
            assertEquals(priorities, connection.priorityChanges.size)
            if (!returnBeforeBackoff) {
                scheduler.advanceTimeBy(2_001)
                settle()
                assertFalse(player.playWhenReady)
                assertEquals(initialSubscriptions + 1, connection.subscribeCount)
            }
            returnAndRetune()
            assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
            scheduler.advanceTimeBy(2_001)
            settle()
            assertEquals(initialSubscriptions + 2, connection.subscribeCount)
            assertNotNull(runtime.activeTarget.value)
        }
    }

    @Test fun recoveryRaisedDuringInstallationIsNotDiscardedByThePublishedEpoch() = exercise {
        live(timeshift = false, beforeStart = { runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED) })
        await { connection.subscribeCount == 2 }
        connection.awaitCollectionRegistered()
        startSubscription()
        await { player.playWhenReady }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun existingSubscriptionIssueReleasesWithoutWaitingForAnotherObservation() = exercise {
        live()
        connection.emit(SubscriptionEvent.Status(SubscriptionCondition.STATUS_REPORTED, SubscriptionIssue.NO_FREE_ADAPTER))
        await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)?.subscriptionIssue != null }
        runtime.onAppBackgrounded()
        await { runtime.activeTarget.value == null }
        assertEquals(1, connection.subscribeCount)
        returnAndRetune()
        assertEquals(BackgroundPlaybackNotice.TUNER_LOST, runtime.backgroundNotice.value)
    }

    @Test fun explicitStopCancelsTimerAndPendingRetune() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        val stopped = scope.async { runtime.stop() }
        await { stopped.isCompleted }
        scheduler.advanceTimeBy(1_200_000)
        runtime.onAppForegrounded()
        settle()
        assertEquals(1, connection.subscribeCount)
        assertNull(runtime.activeTarget.value)
        assertNull(runtime.backgroundNotice.value)
    }

    @Test fun replacementDropsOldTimerAndDoesNotResumeOldChannel() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        live(channel = 2, timeshift = false)
        assertNull(runtime.activeTarget.value) // New background target uses the legacy release path.
        scheduler.advanceTimeBy(1_200_000)
        returnAndRetune()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertNull(runtime.backgroundNotice.value)
    }

    private fun exercise(block: suspend Fixture.() -> Unit) = runBlocking {
        val fixture = Fixture()
        try { withTimeout(20_000) { fixture.block() } }
        finally {
            fixture.scope.cancel()
            fixture.scheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    private class FakeFocus : PlaybackAudioFocus {
        val requests = mutableListOf<(AudioInterruption) -> Unit>()
        var abandons = 0
        var granted = true
        var requestCount = 0
        var callback: ((AudioInterruption) -> Unit)? = null
        override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
            requestCount++
            callback = onInterruption.takeIf { granted }
            if (granted) requests += onInterruption
            return granted
        }
        override fun abandon() { abandons++; callback = null }
    }

    private class Fixture {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        private val context = ApplicationProvider.getApplicationContext<Application>()
        val focus = FakeFocus()
        val connection = ScriptedSubscriptionConnection()
        var beforeSpeed: suspend (Int) -> Unit = {}
        private val manager = createSubscriptionManager(object : SubscriptionConnection by connection {
            override suspend fun speed(id: SubscriptionId, speed: Int): SubscriptionOperationResult<Unit> {
                beforeSpeed(speed)
                return connection.speed(id, speed)
            }
        }, Dispatchers.Default).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2))))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).apply { scriptLivePlaybackSuccess(manager) }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val player = ExoPlayer.Builder(context).build()
        var afterPause: () -> Unit = {}
        private val coordinator = createTvheadendPlaybackCoordinator(player,
            onRecoveryRequired = { recover(it) }).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(object : ExoPlayer by player {
            override fun pause() {
                player.pause()
                afterPause()
            }
        }, session, coordinator, settings, profiles, scope,
            audioOutput = TvheadendAudioOutputProvider(context), audioFocus = focus, elapsedRealtime = { scheduler.currentTime })
        private fun recover(reason: PlaybackRecoveryReason) { runtime.onRecoveryRequired(reason) }

        fun controlCalls(from: Int) = connection.calls.drop(from).filter {
            it == ScriptedSubscriptionCall.SPEED || it == ScriptedSubscriptionCall.PRIORITY
        }

        suspend fun live(channel: Int = 1, timeshift: Boolean = true, beforeStart: () -> Unit = {}) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val previous = connection.subscribeCount
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
            }
            // A background replacement can be released before ExoPlayer opens its source.
            await { connection.subscribeCount > previous || (install.isCompleted && runtime.activeTarget.value == null) }
            if (connection.subscribeCount > previous) {
                connection.awaitCollectionRegistered()
                beforeStart()
                startSubscription()
            }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available }
            }
        }

        suspend fun returnAndRetune(playWhenReady: Boolean = true) {
            val previous = connection.subscribeCount
            runtime.onAppForegrounded()
            await { connection.subscribeCount > previous }
            connection.awaitCollectionRegistered()
            startSubscription()
            await { runtime.activeTarget.value != null && player.playWhenReady == playWhenReady }
        }

        suspend fun startSubscription() {
            connection.emit(SubscriptionEvent.Started(listOf(
                SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                    language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                    frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
            ), null, SubscriptionCondition.NO_DETAIL))
        }

        suspend fun await(predicate: () -> Boolean) {
            withTimeout(5_000) {
                while (!predicate()) { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle(); delay(10) }
            }
        }

        suspend fun settle() { repeat(5) { scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
    }
}
