@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
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
        connection.awaitCollectionRegistered()
        startSubscription()
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
        connection.awaitCollectionRegistered()
        startSubscription()
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
        connection.awaitCollectionRegistered()
        startSubscription()
        await { player.playWhenReady }
        connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
        await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState is LiveTimeshiftState.Available }
        assertTrue(audioDisabled())
        assertTrue(runtime.isInterruptionMuted)
        // Backstop remains safe, but the UI's muted toggle only uses the local sound-restoring path.
        assertEquals(TimeshiftCommandResult.UNAVAILABLE, runtime.pauseTimeshift())
        runtime.pause()
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
                connection.awaitCollectionRegistered()
                startSubscription()
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
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
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
        private val coordinator = createTvheadendPlaybackCoordinator(player, onRecoveryRequired = {
            recoveries += it
            recover(it)
        }).also { it.launchIn(scope) }
        var beforeRecordingBinding: () -> Unit = {}
        private val runtimeSession = object : TvheadendSession by session {
            override fun bindRecordingPlayback(currentSession: CurrentSessionObservation, recordingId: DvrEntryId): PlaybackBindingResult<PlaybackBinding.Recording> {
                beforeRecordingBinding()
                return session.bindRecordingPlayback(currentSession, recordingId)
            }
        }
        val runtime = AppPlaybackRuntime(player, runtimeSession, coordinator, settings, profiles, scope, output, focus)
        private fun recover(reason: PlaybackRecoveryReason) { runtime.onRecoveryRequired(reason) }

        fun audioDisabled() = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes

        suspend fun live(timeshift: Boolean, channel: Int = 1) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val subscriptions = connection.subscribeCount
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
            }
            await { connection.subscribeCount > subscriptions }
            connection.awaitCollectionRegistered()
            startSubscription()
            await { install.isCompleted }
            assertTrue("Live target installed", install.await()?.isStarted == true)
            if (timeshift) {
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available }
            }
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue("Recording target installed", install.await()?.isStarted == true)
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
                while (!predicate()) {
                    shadowOf(Looper.getMainLooper()).idle()
                    delay(10)
                }
            }
        }

        suspend fun settle() { repeat(5) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
    }
}
