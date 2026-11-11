@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package at.bernhardberger.tvhplayer.playback

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.test.utils.FakeClock
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.*
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopOutcome
import at.bernhardberger.tvheadend.sdk.playback.*
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*

internal open class AudioInterruptionFixture {
    /**
     * Reports an escalation when the SDK sets the next target's source, as its recovery may do
     * for a source that ended at once: before that install commits in the runtime.
     */
    internal fun Fixture.escalateOnNextSource(
        recoveryReason: PlaybackRecoveryReason = PlaybackRecoveryReason.LIVE_ENDED,
        reports: Int = 1,
        beforeReport: () -> Unit = {},
    ) {
        var reported = false
        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reported || reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED || timeline.isEmpty) return
                reported = true
                beforeReport()
                repeat(reports) { runtime.onRecoveryRequired(recoveryReason) }
            }
        })
    }

    /** A replaced source may be cancelled before subscribe or register in either order. */
    internal suspend fun Fixture.startSurvivingInstallation() {
        val startup = scope.launch {
            while (isActive) startSubscription(connection.awaitCollectionRegistered())
        }
        try {
            settle()
            await { player.playWhenReady && runtime.state.value !is AppPlaybackState.Recovering &&
                pendingSessions == 1 }
        } finally {
            startup.cancelAndJoin()
        }
    }

    /** Live timeshift whose second recovery attempt is admitted and waits out its 2 s backoff. */
    internal suspend fun Fixture.awaitSecondRecoveryBackoff() {
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
        runtime.onRecoveryRequired(PlaybackRecoveryReason.LIVE_ENDED)
        await { (runtime.state.value as? AppPlaybackState.Recovering)?.retryDelayMillis == 2_000L }
    }

    internal fun exercise(
        granted: Boolean = true,
        recoveryPolicy: PlaybackRecoveryPolicy = PlaybackRecoveryPolicy(),
        block: suspend Fixture.() -> Unit,
    ) = runTest(timeout = 15.seconds) {
        val scheduler = testScheduler
        val dispatcher = StandardTestDispatcher(scheduler)
        val fixture = Fixture(CoroutineScope(dispatcher + SupervisorJob()), granted, scheduler,
            dispatcher, recoveryPolicy)
        try {
            fixture.driver.exercise(this, settle = fixture::settle) { fixture.block() }
        }
        finally {
            fixture.scope.cancel()
            scheduler.runCurrent()
            fixture.runtime.detach()
            fixture.player.release()
            fixture.session.shutdown()
        }
    }

    internal class StalledDataSource : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            try { CountDownLatch(1).await() } catch (_: InterruptedException) { throw InterruptedIOException() }
            return C.LENGTH_UNSET.toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = null
        override fun close() = Unit
    }

    internal class FakeFocus(var granted: Boolean) : PlaybackAudioFocus {
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

    internal class FakeOutputs : PlaybackAudioOutputs {
        override val personalOutputConnected = MutableStateFlow(false)
        var closes = 0
        override fun close() { closes++ }
    }

    internal class Fixture(
        val scope: CoroutineScope,
        granted: Boolean,
        val scheduler: TestCoroutineScheduler,
        private val workDispatcher: CoroutineDispatcher,
        recoveryPolicy: PlaybackRecoveryPolicy,
    ) {
        val driver = PlaybackTestDriver(scheduler)
        private val context = ApplicationProvider.getApplicationContext<Application>()
        val focus = FakeFocus(granted)
        val outputs = FakeOutputs()
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 120)))
        }
        var beforeSpeed: suspend (Int) -> Unit = {}
        /**
         * Sessions whose event collection registered and whose cleanup (unsubscribe) has not finished.
         * The SDK cleans up a replaced session even when it was cancelled before its subscribe ran,
         * so subscribeCount - unsubscribeCount neither counts survivors nor ever settles on that order.
         */
        private val registeredSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<SubscriptionId>()
        private val cleanedSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<SubscriptionId>()
        val pendingSessions: Int get() = registeredSessions.count { it !in cleanedSessions }
        /** The channel of each subscribe: a failed switch may subscribe its target before rolling back. */
        val subscribedChannels = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        val manager = createSubscriptionManager(object : SubscriptionConnection by connection {
            override fun events(id: SubscriptionId): Flow<SubscriptionEvent> {
                registeredSessions += id
                return connection.events(id)
            }
            override suspend fun subscribe(
                id: SubscriptionId,
                channelId: SubscriptionChannelId,
                options: SubscriptionOptions,
            ): SubscriptionOperationResult<SubscriptionConfirmation> {
                subscribedChannels += channelId.value
                return connection.subscribe(id, channelId, options)
            }
            override suspend fun unsubscribe(id: SubscriptionId): SubscriptionOperationResult<Unit> =
                try { connection.unsubscribe(id) } finally { cleanedSessions += id }
            override suspend fun speed(id: SubscriptionId, speed: Int): SubscriptionOperationResult<Unit> {
                beforeSpeed(speed)
                return connection.speed(id, speed)
            }
        }, workDispatcher).apply { startAdmission() }
        val session = FakeTvheadendSession(currentPlaybackObservation(
            channels = listOf(Channel.create(ChannelId(1)), Channel.create(ChannelId(2))),
            recordings = listOf(DvrEntry.create(id = DvrEntryId(1), state = DvrEntryState.COMPLETED)),
        )).apply {
            scriptLivePlaybackSuccess(manager)
            scriptRecordingPlaybackSuccess()
        }
        var beforeSettingsRead: suspend () -> Unit = {}
        private val settingsData = InMemoryPreferencesDataStore()
        val settings = PlayerSettingsStore(settingsData.let { store ->
            object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by store {
                override val data = kotlinx.coroutines.flow.flow {
                    beforeSettingsRead()
                    emitAll(store.data)
                }
            }
        })
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, workDispatcher,
            readProfileForEditing = { ServerProfileEditReadResult.Missing }).also { owner -> scope.launch { owner.run() } }
        val output = TvheadendAudioOutputProvider(context)
        // Application-owned items such as file:///replacement stay loading: a real load failure
        // would reach the runtime as an unrelated player error at a wall-clock dependent moment.
        val clock = FakeClock(false)
        val player = ExoPlayer.Builder(context)
            // Stalled streams must not advance Robolectric time past SDK Handler watchdogs.
            .setClock(clock)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { StalledDataSource() }))
            .build()
        val recoveries = mutableListOf<PlaybackRecoveryReason>()
        /** Fails the SDK's next prepare, after it set the new source: it restores the previous one. */
        var failNextPrepare = false
        var failCoordinatorStop = false
        var coordinatorStops = 0
        var failCoordinatorRemoval = false
        private val coordinatorPlayer = object : ExoPlayer by player {
            override fun removeMediaItem(index: Int) {
                if (failCoordinatorRemoval) throw IllegalStateException("scripted coordinator removal failure")
                player.removeMediaItem(index)
            }
            override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
                if (failCoordinatorRemoval) throw IllegalStateException("scripted coordinator removal failure")
                player.removeMediaItems(fromIndex, toIndex)
            }
            override fun stop() {
                coordinatorStops++
                if (failCoordinatorStop) throw IllegalStateException("scripted stop failure")
                player.stop()
            }
            override fun prepare() {
                if (failNextPrepare) {
                    failNextPrepare = false
                    throw IllegalStateException("scripted prepare failure")
                }
                // Match Media3's error reset on preparation after a source failure.
                reportedPlayerError = null
                player.prepare()
            }
        }
        val coordinator = createTvheadendPlaybackCoordinator(coordinatorPlayer, recoveryPolicy = recoveryPolicy, onRecoveryRequired = {
            recoveries += it
            recover(it)
        }, workDispatcher = workDispatcher).also { it.launchIn(scope) }
        var beforeRecordingBinding: () -> Unit = {}
        /** Runs inside a live target install, before the session binds the channel. */
        var beforeLiveBinding: () -> Unit = {}
        private val runtimeSession = object : TvheadendSession by session {
            override fun bindLivePlayback(currentSession: CurrentSessionObservation, channelId: ChannelId): PlaybackBindingResult<PlaybackBinding.Live> {
                beforeLiveBinding()
                return session.bindLivePlayback(currentSession, channelId)
            }
            override fun bindRecordingPlayback(currentSession: CurrentSessionObservation, recordingId: DvrEntryId): PlaybackBindingResult<PlaybackBinding.Recording> {
                beforeRecordingBinding()
                return session.bindRecordingPlayback(currentSession, recordingId)
            }
        }
        /** Runtime listeners, so tests can drive STATE_READY, which the fake stream never reaches. */
        private val playerListeners = mutableListOf<Player.Listener>()
        var audioTracks: Tracks? = null
        var reportedPlaybackState: Int? = null
        /** Stands in for the ended stream's own error, which reaches the player at no predictable time. */
        private var reportedPlayerError: ExoPlaybackException? = null
        private var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        val runtimePlayer = object : ExoPlayer by player {
            override fun getPlayerError(): ExoPlaybackException? = reportedPlayerError ?: player.playerError
            override fun getPlaybackSuppressionReason(): Int = suppression
            override fun getCurrentTracks(): Tracks = audioTracks ?: player.currentTracks
            override fun getPlaybackState(): Int = reportedPlaybackState ?: player.playbackState
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                player.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                player.removeListener(listener)
            }
        }
        val startupBuffer = StartupBufferController(at.bernhardberger.tvheadend.sdk.media3.TvheadendLiveStartBuffer(),
            settings, profiles.startupBufferIdentity(), scope)
        val runtime = AppPlaybackRuntime(runtimePlayer, runtimeSession, coordinator, settings, profiles, scope, output, focus,
            PlaybackRuntimePolicy.fromPlayerSettings(),
            elapsedRealtime = { scheduler.currentTime },
            startupBuffer = startupBuffer, audioOutputs = outputs,
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val commands: PlaybackTargetCommandSerialization get() = AppPlaybackRuntime::class.java.getDeclaredField("targetCommands").let {
            it.isAccessible = true; it.get(runtime) as PlaybackTargetCommandSerialization
        }
        val audioSelection: SessionAudioSelection get() = AppPlaybackRuntime::class.java.getDeclaredField("audioSelection").let {
            it.isAccessible = true; it.get(runtime) as SessionAudioSelection
        }
        fun installManualAudio(channel: Long = 1): TrackSelectionOverride {
            // The fake stream has no frames; expose a ready target with the test track groups.
            reportedPlaybackState = Player.STATE_READY
            markReady()
            // An opaque storage identity avoids any server address or credentials in this fixture.
            AppProfileOwner::class.java.getDeclaredField("audioProfileId").apply {
                isAccessible = true; set(profiles, "test-profile")
            }
            runBlocking {
                settingsData.updateData { current -> current.toMutablePreferences().apply {
                    this[androidx.datastore.preferences.core.stringPreferencesKey("activeServerProfileId")] = "test-profile"
                } }
            }
            val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("de").build())
            audioTracks = Tracks(listOf(Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
            audioSelection.useProfile(requireNotNull(profiles.serverProfile.value), runtimePlayer)
            audioSelection.activate(ChannelId(channel), runtimePlayer)
            return TrackSelectionOverride(group, listOf(0)).also {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().addOverride(it).build()
            }
        }
        private fun recover(reason: PlaybackRecoveryReason) { runtime.onRecoveryRequired(reason) }

        /** A new session of the same profile becomes current, as after a server restart. */
        fun replaceSession() {
            val previous = session.observation.value
            session.replaceGeneration(SessionObservation.create(
                sessionState = previous.sessionState, channelState = previous.channelState,
                epgState = previous.epgState, dvrState = previous.dvrState,
            ))
        }

        /**
         * The server restarts: its session ends, then the stream it carried, and a new session
         * of the same profile becomes current.
         */
        suspend fun restartServer() {
            val ready = session.observation.value
            session.retire()
            connection.emit(SubscriptionEvent.Stopped(SubscriptionCondition.NO_DETAIL, null))
            settle()
            // Stopped replaces the source epoch but does not end the transport. A server
            // restart also closes the old streams; make their EOF explicit before advancing.
            connection.completeStreams()
            settle()
            session.replaceGeneration(SessionObservation.create(
                sessionState = ready.sessionState, channelState = ready.channelState,
                epgState = ready.epgState, dvrState = ready.dvrState,
            ))
        }

        fun audioDisabled() = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes

        suspend fun live(timeshift: Boolean, channel: Int = 1, granted: Boolean = timeshift, firstPicture: Boolean = true) {
            settings.setTimeshiftEnabled(timeshift)
            connection.scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, if (granted) 120 else 0)))
            val subscriptions = connection.subscribeCount
            val install = scope.async {
                runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(channel.toLong()))))
            }
            try {
                await { connection.subscribeCount > subscriptions }
            } catch (failure: TimeoutCancellationException) {
                val installed = if (install.isCompleted) runCatching { install.await() }.toString() else "<pending>"
                throw AssertionError(
                    "Live channel $channel did not subscribe: install=$installed, " +
                        "playerState=${player.playbackState}, playerError=${player.playerError?.errorCodeName}",
                    failure,
                )
            }
            val registration = connection.awaitCollectionRegistered()
            startSubscription(registration)
            await { install.isCompleted }
            assertTrue("Live target installed", install.await()?.isStarted == true)
            if (granted) {
                // Source opening publishes the grant after Started; wait before sending status.
                await { (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState is LiveTimeshiftState.Available }
                connection.emit(registration, SubscriptionEvent.Timeshift(0, 0, 0, 120_000_000, 100))
                // The subscription grant alone already publishes Available (playbackPaused = null).
                // Wait for this status: the runtime mirrors its playbackPaused into the local play
                // intent, so arriving after a test's local pause it would resume playback.
                await { ((runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState as? LiveTimeshiftState.Available)?.playbackPaused != null }
            }
            // The first picture is ready: a server pause is only sent after it.
            if (firstPicture) markReady()
        }

        fun markReady() { playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) } }

        fun signalIsPlaying() { playerListeners.toList().forEach { it.onIsPlayingChanged(true) } }

        fun reportStreamError() {
            val error = ExoPlaybackException.createForSource(
                IOException("stream ended"), PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )
            dispatchPlayerError(runtimePlayer, playerListeners, error) { reportedPlayerError = it }
        }

        fun suppress(suppressed: Boolean) {
            suppression = if (suppressed) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                else Player.PLAYBACK_SUPPRESSION_REASON_NONE
            playerListeners.toList().forEach { it.onPlaybackSuppressionReasonChanged(suppression) }
        }

        fun signalPlayIntent() {
            playerListeners.toList().forEach {
                it.onPlayWhenReadyChanged(player.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            }
        }

        suspend fun exhaust(
            reason: PlaybackRecoveryReason = PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED,
            beforeReport: () -> Unit = {},
        ) {
            // Reproduce the SDK callback boundary with its real coordinator: retirement has
            // finished (or failed Player cleanup) before the application receives the reason.
            val stop = scope.async { coordinator.stop() }
            await { stop.isCompleted }
            if (reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED) {
                assertTrue(stop.await().outcome == PlaybackStopOutcome.STOPPED)
                // STOPPED clears the player before the SDK finishes retiring its subscription.
                await { pendingSessions == 0 }
            } else assertTrue(stop.await().outcome == PlaybackStopOutcome.PLAYER_UNAVAILABLE)
            beforeReport()
            runtime.onRecoveryRequired(reason)
            settle()
        }

        suspend fun recording() {
            val install = scope.async {
                runtime.playRecording(requireNotNull(currentRecordingPlaybackSelection(session.observation.value, DvrEntryId(1))),
                    RecordingPlaybackStart.START_OVER)
            }
            await { install.isCompleted }
            assertTrue("Recording target installed", install.await()?.isStarted == true)
        }

        // Target the exact stream: a broadcast would also reach a predecessor whose channel
        // unsubscribe already closed but whose collector has not yet deregistered it.
        suspend fun startSubscription(registration: ScriptedSubscriptionRegistration) {
            connection.emit(registration, SubscriptionEvent.Started(listOf(
                SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                    language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                    frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
            ), null))
        }

        fun await(label: String = "audio interruption condition", predicate: () -> Boolean) {
            settle()
            driver.awaitCurrent("$label: state=${runtime.state.value}, playerState=${player.playbackState}, " +
                "playerError=${player.playerError}, live=${runtime.livePlaybackObservation.value}", predicate)
        }

        // Media3's real playback thread must acknowledge commands before controlled state is asserted.
        fun settle() = driver.settleLive(player)

        fun advanceRecoveryTime(millis: Long = 2_500) = driver.advanceLive(player, clock, millis)
    }
}
