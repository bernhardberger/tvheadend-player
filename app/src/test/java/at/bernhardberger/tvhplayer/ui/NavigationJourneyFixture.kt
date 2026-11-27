@file:UnstableApi
@file:OptIn(FakePlaybackApi::class, SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.navigation3.runtime.NavBackStack
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOpener
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.stores.*
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.testutil.observePlayerListeners
import at.bernhardberger.tvhplayer.testutil.playerStatsTestModule
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.components.LocalBrowsePreparationContext
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import at.bernhardberger.tvhplayer.viewmodels.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.serializer
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Offline AppRoot fixture shared by Guide and Home return journeys. */
internal class NavigationJourneyFixture(
    private val compose: ComposeContentTestRule,
    private val server: ServerSettings,
) {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var coordinator: TvheadendPlaybackCoordinator
    private val playerListeners = mutableListOf<Player.Listener>()
    fun presentFirstFrame() {
        compose.runOnIdle {
            recordingEnded = false
            recordingPresented = true
            playerListeners.toList().forEach {
                it.onPlaybackStateChanged(Player.STATE_READY)
                it.onIsPlayingChanged(true)
                it.onRenderedFirstFrame()
            }
        }
        settle()
    }
    private lateinit var session: FakeTvheadendSession
    val playbackNotices = NoticeCenter({ 0L }) { NoticeContext(0, null) }
    lateinit var runtime: AppPlaybackRuntime
        private set
    private var recordingEnded = false
    private var simulatedRecordingPositionMs: Long? = null
    val recordingPosition: Long get() = runtime.player.currentPosition
    // The observed seek is only the fallback when the recovery-seek offset was not pending at install.
    val recordingStartOffset get() = lastRecordingStartOffset ?: lastRecordingSeekMs?.milliseconds
    var endNextRecordingAtInstall = false
    private var recordingPresented = false
    var recordingInstalls = 0
        private set
    private var holdCoordinator = false
    private var lastRecordingSeekMs: Long? = null
    private var lastRecordingStartOffset: Duration? = null
    private var beforeRecordingPause: (() -> Unit)? = null
    private val heldCoordinatorWork = mutableListOf<Runnable>()
    val playbackCommandHeld: Boolean get() = heldCoordinatorWork.isNotEmpty()

    fun holdPlaybackCommands() = compose.runOnIdle { holdCoordinator = true }

    fun holdPlaybackCommandsBeforeAdmission() {
        holdPlaybackCommands()
        // The recording has no timeshift, but this round trip holds the runtime serializer
        // without changing the installed target or its viewing intent.
        compose.runOnIdle { scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.goLive() } }
        settle()
        compose.runOnIdle { check(playbackCommandHeld) }
    }

    fun queuePlayNextDuringPause(
        press: () -> Unit,
        advanceTo: Long? = null,
        removeOffer: DvrEntryId? = null,
        updateOffer: ((DvrEntry) -> DvrEntry)? = null,
    ) {
        compose.runOnIdle {
            beforeRecordingPause = {
                press()
                if (advanceTo != null) simulatedRecordingPositionMs = advanceTo
                if (removeOffer != null || updateOffer != null) {
                    val current = session.observation.value
                    session.publish(SessionObservation.create(current.sessionState, current.channelState, current.epgState,
                        DvrRepositoryState.Current(DvrSnapshot.create(
                            current.dvrSnapshotForDisplay!!.entries.filterNot { it.id == removeOffer }
                                .map { updateOffer?.invoke(it) ?: it })),
                        recordingProgressCapability = RecordingProgressCapability.SUPPORTED))
                }
            }
            runtime.setSessionPlayWhenReady(false) { true }
        }
        settle()
    }

    fun queuePlayNextAndSystemStopDuringPause() {
        compose.runOnIdle {
            val selection = requireNotNull(runtime.recordingSelection.value)
            beforeRecordingPause = {
                val shown = requireNotNull(recordingPlayNextOffer(
                    session.observation.value, selection.recordingId,
                    runtime.recordingBroadcastTime(selection, recordingPosition), Clock.System.now(),
                ))
                val press = requireNotNull(runtime.captureRecordingPlayNextPress(selection, shown))
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    runtime.playRecordingNext(press) { false }
                }
                runtime.stopFromSession { true }
            }
            runtime.setSessionPlayWhenReady(false) { true }
        }
        settle()
    }

    fun retryRecording() {
        compose.runOnIdle { scope.launch { runtime.retryRecording() } }
        settle()
    }

    fun releasePlaybackCommands() = compose.runOnIdle { releaseCoordinatorWork() }

    private fun releaseCoordinatorWork() {
        holdCoordinator = false
        heldCoordinatorWork.toList().also { heldCoordinatorWork.clear() }.forEach { it.run() }
    }

    fun finishRecording() {
        compose.runOnIdle {
            recordingEnded = true
            playerListeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_ENDED) }
        }
        settle()
    }

    fun advanceRecordingTo(positionMs: Long, settleAfter: Boolean = true) {
        compose.runOnIdle { simulatedRecordingPositionMs = positionMs }
        if (settleAfter) settle()
    }

    fun rejectNextRecording() {
        session.scriptRecordingPlaybackFailure(PlaybackBindingResult.TargetUnavailable)
    }

    fun retirePlayback() {
        compose.runOnIdle { scope.launch { runtime.stopAfterLoss() } }
        settle()
    }

    fun allowRecording() {
        recordingEnded = false
        session.scriptRecordingPlaybackSuccess()
    }

    fun loseSession() {
        compose.runOnIdle {
            val current = session.observation.value
            session.publish(SessionObservation.create(SessionState.Disconnected, current.channelState,
                current.epgState, current.dvrState))
        }
        settle()
    }

    lateinit var back: OnBackPressedDispatcher
        private set
    val guidePosition = GuidePositionStore()
    var playerVisible = false
        private set
    private lateinit var navState: SaveableStateRegistry

    fun topRoute(): AppNavKey = compose.runOnIdle {
        navState.performSave().values.flatten().filterIsInstance<Bundle>()
            .mapNotNull { runCatching { decodeFromSavedState(serializer<NavBackStack<AppNavKey>>(), it) }.getOrNull() }
            .single().last()
    }
    var startupKeyMode: MainStartupKeyMode = MainStartupKeyMode.Inactive
        private set
    val launchRequests = ApplianceLaunchRequests()
    private val restoration = StateRestorationTester(compose)
    private var guideOnNextDispose = false

    fun restoreWithPendingGuide() {
        // Match a retained ViewModel request arriving between graph save and recomposition.
        guideOnNextDispose = true
        restoration.emulateSavedInstanceStateRestore()
        settle()
    }

    fun close() {
        releaseCoordinatorWork()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    fun focus(tag: String) {
        compose.mainClock.advanceTimeUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    fun openDestination(from: String, step: Key, to: String) {
        focus(from)
        key(step)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(to) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        key(Key.Enter)
    }

    @OptIn(TvheadendTestingApi::class, ExperimentalTvheadendApi::class)
    fun app(withRecording: Boolean = false, emptyEpg: Boolean = false, scheduled: List<DvrEntry> = emptyList(), autoStartPlayback: Boolean = false,
        guideEntry: Boolean = false, emptyChannels: Boolean = false, driveRecordingEnd: Boolean = false,
        autoContinue: Boolean = false, programmeEndMs: Long? = null, recordingStart: Instant? = null,
        warmPlaybackMode: WarmPlaybackMode? = null) {
        val now = Clock.System.now().epochSeconds
        val fileStart = recordingStart ?: Instant.fromEpochSeconds(now - 7200)
        val programmeStop = fileStart + (programmeEndMs ?: 3_600_000L).milliseconds
        val catalog = ChannelCatalog.create((1..if (emptyChannels) 0L else if (withRecording) 8L else 3L).map {
            Channel.create(ChannelId(it), name = "Name $it", number = it)
        })
        fun event(channel: Long, genre: Long) = EpgEvent.create(EventId(channel), ChannelId(channel), AIRS_FROM, AIRS_UNTIL,
            title = "Programme $channel", contentGenre = ContentGenre.fromContentType(genre))
        val events = catalog.channels.map { channel ->
            if (withRecording && channel.id == ChannelId(6)) EpgEvent.create(EventId(6), channel.id,
                Instant.fromEpochSeconds(now - 1800), AIRS_UNTIL, title = "Programme 6")
            else event(channel.id.value, if (channel.id == ChannelId(1)) 0x40L else 0x50L)
        } + if (withRecording) listOf(EpgEvent.create(EventId(66), ChannelId(6), Instant.fromEpochSeconds(now - 7200),
            Instant.fromEpochSeconds(now - 3600), title = "Recorded programme")) else emptyList()
        session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(catalog),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = if (emptyEpg) emptyList() else events,
                coverages = catalog.channels.map { EpgCoverage.create(it.id, AIRS_FROM, AIRS_UNTIL) })),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create((if (withRecording) listOf(
                DvrEntry.create(id = DvrEntryId(7), eventId = EventId(66), channelId = ChannelId(6),
                    state = DvrEntryState.COMPLETED, title = "Recorded programme",
                    autorecRuleId = if (driveRecordingEnd) AutorecRuleId("journey") else null,
                    start = fileStart.takeIf { driveRecordingEnd },
                    stop = programmeStop.takeIf { driveRecordingEnd },
                    stopExtraMinutes = programmeEndMs?.let { 1.minutes },
                    files = if (recordingStart != null) listOf(TvheadendTestResultFactory.recordingFile(
                        serverPath = "original.ts", start = fileStart, stop = programmeStop,
                    )) else emptyList()),
            ) else emptyList()) + scheduled)),
        )).apply {
            if (autoStartPlayback) scriptLivePlaybackSuccess(SubscriptionOpener { _, _, _ -> awaitCancellation() })
            else scriptLivePlaybackSuccess()
            if (withRecording) scriptRecordingPlaybackSuccess()
        }
        val settings = PlayerSettingsStore(InMemoryData())
        if (driveRecordingEnd) runBlocking {
            settings.setContinueAutomatically(autoContinue)
            warmPlaybackMode?.let { settings.setWarmPlaybackMode(it) }
        }
        val profiles = AppProfileOwner(session, FakeServerProfileStore(ServerProfileReadResult.anonymous(ServerProfileId.generate(), server.host, server.htspPort)),
            settings, Dispatchers.IO, readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        player = ExoPlayer.Builder(context).build()
        val observedPlayer = observePlayerListeners(player, playerListeners)
        val playbackPlayer = if (!driveRecordingEnd) observedPlayer else object : ExoPlayer by observedPlayer {
            override fun getPlaybackState(): Int = when {
                recordingEnded -> Player.STATE_ENDED
                recordingPresented -> Player.STATE_READY
                else -> observedPlayer.playbackState
            }

            override fun isPlaying() = !recordingEnded && (recordingPresented || observedPlayer.isPlaying)

            override fun pause() {
                beforeRecordingPause?.also { beforeRecordingPause = null }?.invoke()
                observedPlayer.pause()
            }

            override fun getCurrentPosition() = simulatedRecordingPositionMs ?: observedPlayer.currentPosition

            override fun seekTo(positionMs: Long) {
                lastRecordingSeekMs = positionMs
                if (simulatedRecordingPositionMs != null) simulatedRecordingPositionMs = positionMs
                observedPlayer.seekTo(positionMs)
            }

            override fun getDuration() = if (simulatedRecordingPositionMs != null) 3_600_000L else observedPlayer.duration

            override fun isCurrentMediaItemSeekable() =
                simulatedRecordingPositionMs != null || observedPlayer.isCurrentMediaItemSeekable

            override fun isCommandAvailable(command: Int) =
                (simulatedRecordingPositionMs != null &&
                    command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    observedPlayer.isCommandAvailable(command)

            private fun silence(source: MediaSource) =
                object : WrappingMediaSource(
                    SilenceMediaSource(3_600_000_000L),
                ) {
                    override fun getMediaItem() = source.mediaItem
                }.also {
                    recordingInstalls++
                    lastRecordingSeekMs = null
                    // The SDK publishes this installation's offset before calling setMediaSource.
                    lastRecordingStartOffset = coordinator.pendingRecordingRecoveryPosition.value
                    recordingPresented = false
                    simulatedRecordingPositionMs = null
                    recordingEnded = endNextRecordingAtInstall
                    endNextRecordingAtInstall = false
                }

            override fun setMediaSource(source: MediaSource) =
                observedPlayer.setMediaSource(silence(source))

            override fun setMediaSource(source: MediaSource, resetPosition: Boolean) =
                observedPlayer.setMediaSource(silence(source), resetPosition)

            override fun setMediaSource(source: MediaSource, startPositionMs: Long) =
                observedPlayer.setMediaSource(silence(source), startPositionMs)
        }
        val coordinatorScope = if (!driveRecordingEnd) scope else CoroutineScope(
            scope.coroutineContext + object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) {
                    Dispatchers.Main.dispatch(context) {
                        if (holdCoordinator) heldCoordinatorWork += block else block.run()
                    }
                }
            },
        )
        coordinator = createTvheadendPlaybackCoordinator(playbackPlayer).also { it.launchIn(coordinatorScope) }
        runtime = AppPlaybackRuntime(playbackPlayer, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = playbackNotices)
        val notices = NoticeCenter(SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val dependencies = module {
            includes(playerStatsTestModule())
            single<CoroutineScope>(qualifier = named("application")) { scope }
            single<TvheadendSession> { session }
            single { runtime }
            single { profiles }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryData()) }
            single { UiSettingsStore(InMemoryData()).apply { runBlocking { setChannelsView(ChannelsView(ChannelsLayout.LIST)); setGuideView(GuideView(GuideRows.REGULAR)) } } }
            single { LastPlayedChannelStore(context) }
            single { ChannelSelectionStore() }
            single { guidePosition }
            single { notices }
            single { ConnectionNoticeSource(session.observation, profiles.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get(), get<AppProfileOwner>().liveProfile) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) {} }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        if (guideEntry) launchRequests.request(ApplianceEntryDestination.GUIDE)
        launchRequests.requestStartup(autoStartPlayback)
        // A TV is not in touch mode: only then does a view hand focus over as it attaches.
        @Suppress("UNCHECKED_CAST")
        (compose as AndroidComposeTestRule<*, ComponentActivity>).leaveTouchModeBeforeComposing()
        restoration.setContent {
            navState = requireNotNull(LocalSaveableStateRegistry.current)
            DisposableEffect(Unit) {
                onDispose {
                    if (guideOnNextDispose) {
                        guideOnNextDispose = false
                        launchRequests.request(ApplianceEntryDestination.GUIDE)
                    }
                }
            }
            CompositionLocalProvider(LocalBrowsePreparationContext provides EmptyCoroutineContext) {
                KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                    TVHeadendPlayerTheme {
                        back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                        AppRoot(startupState = MainStartupState.Ready(server, autoStartPlayback = autoStartPlayback),
                            runtimeServerSettings = server, applianceLaunchRequests = launchRequests,
                            onPlayerVisibilityChanged = { playerVisible = it }, onRequestExit = {},
                            registerActivityKeyContract = { contract ->
                                startupKeyMode = contract.mode
                                {}
                            })
                    }
                }
            }
        }
        if (guideEntry) {
            compose.mainClock.advanceTimeUntil(15_000) {
                compose.onAllNodes(hasTestTag("guide-date-button")).fetchSemanticsNodes().isNotEmpty()
            }
            settle()
            return
        }
        if (autoStartPlayback) {
            settle()
            // The coordinator installs the target on the real ExoPlayer playback thread.
            compose.waitUntil(15_000) {
                playerVisible && runtime.activeTarget.value is AppPlaybackTarget.Live
            }
            return
        }
        compose.mainClock.advanceTimeUntil(15_000) { compose.onAllNodes(hasTestTag("nav-channels")).fetchSemanticsNodes().isNotEmpty() }
        if (emptyChannels) {
            settle()
            return
        }
        compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeUntil(15_000) { compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    fun key(key: Key) {
        compose.pressKey(key)
        settle()
    }

    /** Back as the device delivers it: a press, time passing, then the release, through the root. */
    fun backKeyCycle(framesHeld: Int = 3) {
        compose.onRoot().performKeyInput { keyDown(Key.Back) }
        compose.waitForIdle()
        repeat(framesHeld) { compose.mainClock.advanceTimeByFrame() }
        compose.onRoot().performKeyInput { keyUp(Key.Back) }
        compose.waitForIdle()
        settle()
    }

    /**
     * What the platform does once nothing in the page holds focus (a closing screen's node going away):
     * the view drops its focus and, off touch mode, searches the whole page again from the top.
     */
    fun viewRefocusesWithNothingFocused() {
        compose.runOnUiThread {
            val activity = (compose as AndroidComposeTestRule<*, ComponentActivity>).activity
            fun find(group: android.view.ViewGroup): android.view.View? {
                for (i in 0 until group.childCount) {
                    val child = group.getChildAt(i)
                    if (child.javaClass.simpleName == "AndroidComposeView") return child
                    if (child is android.view.ViewGroup) find(child)?.let { return it }
                }
                return null
            }
            val view = find(activity.window.decorView as android.view.ViewGroup)!!
            view.clearFocus()
            view.requestFocus(android.view.View.FOCUS_DOWN)
        }
        compose.waitForIdle()
        settle()
    }

    fun publishRecordings(entries: List<DvrEntry>) {
        compose.runOnIdle {
            val current = session.observation.value
            session.publish(SessionObservation.create(current.sessionState, current.channelState, current.epgState,
                DvrRepositoryState.Current(DvrSnapshot.create(entries))))
        }
        compose.waitForIdle()
    }

    fun settle() {
        compose.settleFrames(10, 100)
    }

    private companion object {
        val AIRS_FROM = Instant.parse("2000-01-01T00:00:00Z")
        val AIRS_UNTIL = Instant.parse("2100-01-01T00:00:00Z")
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
