@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
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

    @Test fun deniedForegroundFocusKeepsPausedUntilCurrentFocusGain() = exercise {
        live()
        runtime.onAppBackgrounded()
        await { connection.priorityChanges.size == 1 }
        focus.granted = false
        runtime.onAppForegrounded()
        await { focus.requests.size == 2 }
        settle()
        assertFalse(player.playWhenReady)
        assertFalse(connection.speeds.contains(100))
        focus.requests.last()(AudioInterruption.GAIN)
        await { player.playWhenReady }
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
        assertNull(runtime.backgroundNotice.value)
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
        override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
            requests += onInterruption
            return granted
        }
        override fun abandon() { abandons++ }
    }

    private class Fixture {
        val scheduler = TestCoroutineScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        private val context = ApplicationProvider.getApplicationContext<Application>()
        val focus = FakeFocus()
        val connection = ScriptedSubscriptionConnection()
        private val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
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
        private val coordinator = createTvheadendPlaybackCoordinator(player,
            onRecoveryRequired = { recover(it) }).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            audioOutput = TvheadendAudioOutputProvider(context), audioFocus = focus, elapsedRealtime = { scheduler.currentTime })
        private fun recover(reason: PlaybackRecoveryReason) { runtime.onRecoveryRequired(reason) }

        fun controlCalls(from: Int) = connection.calls.drop(from).filter {
            it == ScriptedSubscriptionCall.SPEED || it == ScriptedSubscriptionCall.PRIORITY
        }

        suspend fun live(channel: Int = 1, timeshift: Boolean = true) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val previous = connection.subscribeCount
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
            }
            await { connection.subscribeCount > previous }
            connection.awaitCollectionRegistered()
            startSubscription()
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available }
            }
        }

        suspend fun returnAndRetune() {
            val previous = connection.subscribeCount
            runtime.onAppForegrounded()
            await { connection.subscribeCount > previous }
            connection.awaitCollectionRegistered()
            startSubscription()
            await { runtime.activeTarget.value != null && player.playWhenReady }
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
