@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.*

internal val labels = object : SessionMetadataLabels {
    override fun genre(genre: ContentGenre): String = "Category"
    override fun channelNumber(channel: Channel): String = "Test number"
    override fun error(kind: SessionPlaybackErrorKind): String = when (kind) {
        SessionPlaybackErrorKind.RECORDING -> "Recording can't be read"
        SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE -> "Channel unavailable"
        SessionPlaybackErrorKind.CHANNEL -> "Playback failed"
    }
}

internal open class SessionPlaybackFixture {
    internal suspend fun Fixture.assertCancelledStartSettled(states: List<AppPlaybackState>, stops: List<Unit>) {
        assertEquals(null, runtime.activeTarget.value)
        assertEquals(null, runtime.recordingSelection.value)
        assertEquals(AppPlaybackState.Idle, runtime.state.value)
        assertFalse(states.toString(), states.any { it is AppPlaybackState.Failed })
        assertFalse(runtime.videoPresentation.value.visible)
        assertEquals(playerLog.toString(), 0, player.mediaItemCount)
        // No subscription stays open, neither the stopped channel's nor a cancelled channel's.
        await { connection.unsubscribeCount == connection.subscribeCount }
        // Not the viewer's stop: no session Stop, and the next player entry is admitted.
        assertEquals(emptyList<Unit>(), stops)
        assertNotNull(runtime.enterPlayerScreen())
    }

    // Real Media3 and the SDK's asynchronous subscription machinery require looper pumping.
    /**
     * A Retry (or the automatic reconnect) has begun but waits behind another command when the
     * viewer pauses: the retried channel starts paused and its server hold follows the first picture.
     */
    internal suspend fun Fixture.pauseDuringARetryHoldsTheRetriedChannel(
        viewerRetry: Boolean,
        pause: suspend Fixture.() -> Unit,
    ) {
        live(true)
        connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        lateinit var retry: Deferred<PlaybackTargetResult?>
        whileCommandsBlocked {
            retry = scope.async { runtime.retryLive(viewerRetry) }
            settle()
            pause()
            settle()
        }
        await { retry.isCompleted }
        assertTrue(retry.await()?.isStarted == true)
        await { !player.playWhenReady }
        assertEquals(emptyList<Int>(), connection.speeds)
        grant(subscription = 2)
        playerReady()
        await { connection.speeds == listOf(0) }
        settle()
        assertEquals(listOf(0), connection.speeds)
        assertFalse(player.playWhenReady)
        assertFalse(wrapper.playWhenReady)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    internal fun exercise(realThreads: Boolean = false, block: suspend Fixture.() -> Unit) = runTest(timeout = 15.seconds) {
        val fixture = Fixture(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()), testScheduler)
        try {
            // Only the artwork cases run SessionArtworkLoader on production Dispatchers.IO.
            fixture.driver.exercise(this, timeoutMillis = if (realThreads) 15_000 else null,
                settle = fixture::settle) { fixture.block() }
        }
        finally {
            fixture.observation.cancel()
            fixture.wrapper.close()
            fixture.scope.cancel()
            testScheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    internal class Fixture(val scope: CoroutineScope, val scheduler: TestCoroutineScheduler) {
        val driver = PlaybackTestDriver(scheduler)
        private val work = StandardTestDispatcher(scheduler)
        private val context = ApplicationProvider.getApplicationContext<Application>()
        var focusRequests = 0
        var focusGranted = true
        var focusListener: ((AudioInterruption) -> Unit)? = null
        private val focus = object : PlaybackAudioFocus {
            override fun request(onInterruption: (AudioInterruption) -> Unit): Boolean {
                focusRequests++
                focusListener = onInterruption
                return focusGranted
            }
            override fun abandon() = Unit
        }
        val connection = ScriptedSubscriptionConnection()
        private val subscriptions = MutableStateFlow(0)
        val manager = createSubscriptionManager(object : SubscriptionConnection by connection {
            override suspend fun subscribe(
                id: SubscriptionId,
                channelId: SubscriptionChannelId,
                options: SubscriptionOptions,
            ): SubscriptionOperationResult<SubscriptionConfirmation> =
                connection.subscribe(id, channelId, options).also {
                    subscriptions.value = connection.subscribeCount
                }
        }, work).apply { startAdmission() }
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Channel One")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(DvrEntry.create(
                id = DvrEntryId(1), state = DvrEntryState.COMPLETED, title = "Recording", channelName = "Recorded Channel",
            )))),
        )).apply { scriptLivePlaybackSuccess(manager); scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        private val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, work,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        private val clock = FakeClock(false)
        val player = ExoPlayer.Builder(context).setClock(clock).build()
        /** The SDK's Player stops and the playlists they leave, in order: a stop releases the decoders. */
        val playerLog: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        /** Runs as each [playerLog] entry is made, so a test can cancel a start at that point. */
        var onPlayerLog: (String) -> Unit = {}
        private fun logPlayer(entry: String) {
            playerLog += entry
            onPlayerLog(entry)
        }
        private val coordinator = createTvheadendPlaybackCoordinator(object : ExoPlayer by player {
            override fun stop() {
                logPlayer("stop")
                player.stop()
            }
        }, workDispatcher = work).also { it.launchIn(scope) }
        init {
            player.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) logPlayer("items=${player.mediaItemCount}")
                }
            })
        }
        /** Runtime listeners, so tests can drive STATE_READY, which the fake stream never reaches. */
        private val playerListeners = mutableListOf<Player.Listener>()
        var reportedDuration: Long? = null
        var sessionSeeks = 0
        val runtime = AppPlaybackRuntime(object : ExoPlayer by player {
            override fun seekTo(positionMs: Long) {
                sessionSeeks++
                player.seekTo(positionMs)
            }
            override fun getDuration(): Long = reportedDuration ?: player.duration
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                player.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                player.removeListener(listener)
            }
        }, session, coordinator, settings, profiles, scope, TvheadendAudioOutputProvider(context), focus,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            elapsedRealtime = { scheduler.currentTime },
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val wrapper = SessionPlaybackPlayer(runtime, programmeWindowEnabled = true, labels = labels)
        val observation = scope.launch { wrapper.observe(session.observation) }

        @Suppress("UNCHECKED_CAST")
        fun retireWithState(state: AppPlaybackState) {
            val attributed = if (state is AppPlaybackState.Failed) {
                state.copy(target = state.target ?: runtime.activeTarget.value)
            } else state
            AppPlaybackRuntime::class.java.getDeclaredField("activeTargetEpoch").let {
                it.isAccessible = true
                it.set(runtime, null)
            }
            AppPlaybackRuntime::class.java.getDeclaredField("_activeTarget").let {
                it.isAccessible = true
                (it.get(runtime) as kotlinx.coroutines.flow.MutableStateFlow<AppPlaybackTarget?>).value = null
            }
            publishState(attributed)
        }

        @Suppress("UNCHECKED_CAST")
        fun publishState(state: AppPlaybackState) {
            AppPlaybackRuntime::class.java.getDeclaredField("presentation").let {
                it.isAccessible = true
                val publisher = it.get(runtime) as PlaybackPresentationPublisher
                when (state) {
                    is AppPlaybackState.Failed -> publisher.publishFailed(state)
                    is AppPlaybackState.Recovering -> publisher.publishRecovering(state.reason, state.retryDelayMillis)
                    AppPlaybackState.Starting -> publisher.publishStarting()
                    AppPlaybackState.Buffering -> PlaybackPresentationPublisher::class.java.getDeclaredField("_state").let { field ->
                        field.isAccessible = true
                        (field.get(publisher) as kotlinx.coroutines.flow.MutableStateFlow<AppPlaybackState>).value = state
                    }
                    else -> error("Unsupported fixture state")
                }
            }
        }

        suspend fun live(timeshift: Boolean, firstPicture: Boolean = true, channelId: ChannelId = ChannelId(1)) {
            settings.setTimeshiftEnabled(timeshift)
            val previousSubscriptions = connection.subscribeCount
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (timeshift) 120 else 0)))
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, channelId)))
            }
            await { connection.subscribeCount > previousSubscriptions }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            if (timeshift) {
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState is LiveTimeshiftState.Available }
                connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
                await { wrapper.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) }
            }
            // The first picture is ready: a server pause is only sent after it.
            if (firstPicture) playerReady()
            settle()
            // The session offers Stop before a test presses it, even on a loaded machine.
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        val started = SubscriptionEvent.Started(listOf(
            SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
        ), null)

        fun liveSelection() = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))

        /** Installs live with timeshift requested ([intent] if given); its grant is delivered later by [grant]. */
        suspend fun startingLive(intent: Long? = null) {
            settings.setTimeshiftEnabled(true)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
            val install = scope.async { runtime.playLive(liveSelection(), intent) }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            await { runtime.livePause.value.availability == LivePauseAvailability.STARTING }
        }

        /** The subscription of the latest start is confirmed and granted. */
        suspend fun grant(subscription: Int = 1) {
            await { connection.subscribeCount == subscription }
            connection.awaitCollectionRegistered()
            connection.emit(started)
            await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState is LiveTimeshiftState.Available }
            connection.emit(SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
            await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
            await { runtime.livePause.value.availability == LivePauseAvailability.READY }
        }

        fun playerReady() {
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue(install.await()?.isStarted == true)
            settle()
            await { wrapper.isCommandAvailable(Player.COMMAND_STOP) }
        }

        /** A recording start that runs once started, so a test can arrange its cancellation first. */
        fun recordingStart(): Deferred<PlaybackTargetResult?> = scope.async(start = CoroutineStart.LAZY) {
            runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                RecordingPlaybackStart.START_OVER)
        }

        /** Subscribes before returning, as a showing player screen would be. */
        suspend fun restoreRecording(): PlaybackTargetResult? = runtime.restoreRecordingRoute(
            requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
            RecordingPlaybackStart.START_OVER,
        )

        fun collectStates(): List<AppPlaybackState> = java.util.Collections.synchronizedList(
            mutableListOf<AppPlaybackState>(),
        ).also { states ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.state.collect { states += it } }
        }

        fun collectSessionStops(): List<Unit> = mutableListOf<Unit>().also { stops ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.sessionStops.collect { stops += it } }
        }

        /** The runtime's command serializer, as in AutomaticAudioRuntimeTest; no runtime test seam. */
        val commands: PlaybackTargetCommandSerialization
            get() = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
                it.isAccessible = true
                it.get(runtime) as PlaybackTargetCommandSerialization
            }

        /** Answers the [count]th live subscription (scripted Ok, then Started) when it arrives. */
        fun answerNextSubscribe(count: Int) {
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 0)))
            scope.launch {
                subscriptions.first { it >= count }
                assertEquals("expected subscription before answering its start", count, subscriptions.value)
                connection.awaitCollectionRegistered()
                connection.emit(started)
            }
        }

        /** Pending session Stops registered at arrival; no runtime test seam. */
        fun pendingSessionStops(): Int =
            AppPlaybackRuntime::class.java.getDeclaredField("pendingStops").let {
                it.isAccessible = true
                it.getInt(runtime)
            }

        /** Serializer busy, session Stop arrives, then the viewer's warm opening action. */
        suspend fun warmOpeningAfterAQueuedStopKeepsThePlayerOpen(target: AppPlaybackTarget) {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                val opening = runtime.notePlaybackIntent()
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertTrue(entry > opening) // not joined to the older pending Stop
                stops = collectSessionStops()
            }
            // Drained: the Stop has had its check, and was skipped.
            assertEquals(0, pendingSessionStops())
            assertEquals(0, connection.unsubscribeCount)
            assertEquals(target, runtime.activeTarget.value)
            assertFalse(runtime.isPlaybackIntentStopped(entry))
            assertEquals(emptyList<Unit>(), stops)
        }

        /** Serializer busy, the viewer's warm opening action, then a session Stop. */
        suspend fun warmOpeningBeforeAQueuedStopClosesWithIt() {
            lateinit var stops: List<Unit>
            var entry = 0L
            whileCommandsBlocked {
                val opening = runtime.notePlaybackIntent()
                wrapper.stop()
                assertEquals(1, pendingSessionStops())
                entry = requireNotNull(runtime.enterPlayerScreen())
                assertEquals(opening, entry) // joined: the Stop is the viewer's latest action
                assertEquals(entry, runtime.enterPlayerScreen()) // re-entry notes nothing either
                stops = collectSessionStops()
            }
            await { stops.size == 1 }
            assertEquals(null, runtime.activeTarget.value)
            assertTrue(runtime.isPlaybackIntentStopped(entry)) // the entry's own start is withdrawn
            assertEquals(1, stops.size)
        }

        suspend fun whileCommandsBlocked(block: suspend () -> Unit) {
            // Hold the existing serializer; no runtime test seam.
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val blocker = scope.launch {
                commands.serialize(onClosed = {}) {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            try { block() } finally {
                release.complete(Unit)
                blocker.join()
            }
            // Drain commands queued by the wrapper before checking their effects.
            commands.serialize(onClosed = {}) {}
        }

        fun await(label: String = "session playback condition", predicate: () -> Boolean) {
            settle()
            driver.awaitCurrent(label, predicate)
        }

        fun settle() = driver.settleLive(player)

        fun advanceTimeBy(millis: Long) = driver.advanceLive(player, clock, millis)
    }
}
