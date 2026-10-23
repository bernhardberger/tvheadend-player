package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.stores.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi",
    instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideHistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var view: View
    private lateinit var zone: TimeZone
    private lateinit var back: androidx.activity.OnBackPressedDispatcher
    @Before fun before() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun after() { models.clear(); scope.cancel(); if (::player.isInitialized) player.release(); TimeZone.setDefault(zone) }
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }

    @Test fun screenPastFrontierNowAndSessionReplacement() = exercise("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun screenPastFrontierGermanLargeText() = exercise("de", 1.3f)
    @Test fun historicalRecordingDetailsCloseOnSessionReplacement() = exercise("en", 1f, "details")
    @Test fun pendingCancelDismissesWhenRecordingStarts() = exercise("en", 1f, "cancel")
    @Test fun pendingStopDismissesWhenRecordingCompletes() = exercise("en", 1f, "stop")
    @Test fun pendingCancelConsumesInterruptedEnter() = exercise("en", 1f, "cancel", activationKeyCode = android.view.KeyEvent.KEYCODE_ENTER)
    @Test fun pendingStopConsumesInterruptedEnter() = exercise("en", 1f, "stop", activationKeyCode = android.view.KeyEvent.KEYCODE_ENTER)
    @Test fun pendingCancelConsumesInterruptedNumpadEnter() = exercise("en", 1f, "cancel", activationKeyCode = android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
    @Test fun pendingStopConsumesInterruptedNumpadEnter() = exercise("en", 1f, "stop", activationKeyCode = android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
    @Test fun airingRecordSchedulesWithoutConfirmation() = exercise("en", 1f, "airing-record")
    @Test fun airingStopUsesExistingConfirmationAndKeepsPartialRecording() = exercise("en", 1f, "airing-stop")
    @Test fun airingScheduledEntryUsesExistingCancelConfirmation() = exercise("en", 1f, "airing-cancel")
    @Test fun detailsStackBackRestoresGuideOriginAndSuppressesOpeningRelease() = exercise("en", 1f, "stack")
    @Test fun recordAfterPoppingDetailsKeepsActionFocus() = exercise("en", 1f, "stack-record")
    @Test fun stackedAiringShowInGuideFocusesItsChannelAndFutureProgramme() = exercise("en", 1f, "stack-guide")
    @Test fun menuRecordExecutesDirectlyAndRestoresGuideCell() = exercise("en", 1f, "menu-record")
    @Test fun menuDetailsBackRestoresGuideCellWithoutReopeningMenuEveryFrame() = exercise("en", 1f, "menu-details")

    @Test fun programmeHostSchedulesDirectlyWithStoredProfileAndGuardsDoubleClick() = recordWithStoredProfile(false)
    @Test fun guideMenuSchedulesDirectlyWithStoredProfileAndGuardsDoubleClick() = recordWithStoredProfile(true)

    private fun recordWithStoredProfile(menu: Boolean) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val event = EpgEvent.create(EventId(77), channelId = ChannelId(1), title = "Mountain expedition",
            start = Instant.fromEpochSeconds(100), stop = Instant.fromEpochSeconds(200))
        val profiles = listOf(DvrConfiguration(DvrConfigId("default"), "", ""),
            DvrConfiguration(DvrConfigId("archive"), "Archive", ""))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Ridge Earth")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(emptyList())),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(profiles))
        var request: DvrScheduleRequest? = null
        var requests = 0
        val release = CompletableDeferred<Unit>()
        val settings = UiSettingsStore(preferences())
        runBlocking { settings.setRecordingProfile(profiles[1].id) }
        val mutations = DvrMutationActions(
            scheduleEntry = { _, value -> requests++; request = value; release.await(); DvrMutationResult.Confirmed(DvrEntryId(1)) },
            stopEntry = { _, _ -> error("unexpected stop") },
            cancelEntry = { _, _ -> error("unexpected cancel") },
            deleteEntry = { _, _ -> error("unexpected delete") },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") })
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(0, observation.currentSession?.generationIdentity)
        }
        val session = FakeTvheadendSession(observation)
        val opening = session.observation.value
        compose.setContent {
            back = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            TVHeadendPlayerTheme {
                val current by session.observation.collectAsState()
                ProgrammeDetailsHost(event, opening, current, { session.observation.value }, { 50 }, fromGuide = true,
                    showContextMenu = menu, onShowDetails = {}, mutations = mutations,
                    mutationScope = androidx.compose.runtime.rememberCoroutineScope(),
                    mutationInFlight = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
                    notices = notices, onPlay = { _, _ -> }, onPlayRecording = null, onClose = {}, settingsStore = settings)
            }
        }
        val record = if (menu) compose.onNodeWithText("Record") else compose.onNodeWithTag("details-record")
        record.requestFocus()
        val click = record.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        compose.waitForIdle()
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        if (!menu) compose.onNodeWithTag("details-record").assertIsFocused()
        assertEquals(1, requests)
        compose.runOnIdle { release.complete(Unit) }
        compose.waitForIdle()
        assertEquals(DvrScheduleRequest(DvrSchedule.Programme(event.id), configId = profiles[1].id, title = event.title), request)
        if (!menu) {
            compose.runOnIdle {
                session.publish(SessionObservation.create(sessionState = observation.sessionState,
                    channelState = observation.channelState, epgState = observation.epgState,
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(
                        DvrEntry.create(DvrEntryId(1), eventId = event.id, state = DvrEntryState.SCHEDULED)))),
                    dvrConfigurationsState = observation.dvrConfigurationsState))
            }
            compose.onNodeWithTag("details-cancel").assertIsFocused()
        }
    }

    private fun exercise(locale: String, scale: Float, transition: String? = null, replaceSession: Boolean = false,
        activationKeyCode: Int = android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val hour = Instant.parse("2026-09-15T19:00:00Z").epochSeconds
        check(android.os.SystemClock.setCurrentTimeMillis(hour * 1000))
        val channels = listOf(Channel.create(ChannelId(1), name = "Documentary", number = 1),
            Channel.create(ChannelId(2), name = "Culture", number = 2))
        fun event(channel: Long, offset: Int) = EpgEvent.create(EventId(channel * 100 + offset + 10),
            channelId = ChannelId(channel), title = "Channel $channel hour $offset", seriesLinkUri = "crid://$channel/$offset",
            start = Instant.fromEpochSeconds(hour + offset * 3600), stop = Instant.fromEpochSeconds(hour + (offset + 1) * 3600))
        // A later airing of "Channel 1 hour 0" for the details page's Upcoming airings row.
        val repeat = EpgEvent.create(EventId(900), channelId = ChannelId(2), title = "Repeat of channel 1 hour 0",
            seriesLinkUri = "crid://1/0", start = Instant.fromEpochSeconds(hour + 26 * 3600),
            stop = Instant.fromEpochSeconds(hour + 27 * 3600))
        val current = channels.flatMap { channel -> (0..3).map { event(channel.id.value, it) } } + repeat
        val history = channels.flatMap { channel -> (-6..-1).map { event(channel.id.value, it) } }
        var archiveFuture = false
        var recordingState = if (transition == "cancel" || transition == "airing-cancel") DvrEntryState.SCHEDULED else DvrEntryState.RECORDING
        fun observation(withHistory: Boolean) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = current.filterNot { archiveFuture && it.id == event(1, 1).id },
                historicalEvents = (if (withHistory) history else emptyList()) + if (archiveFuture) listOf(event(1, 1)) else emptyList(), coverages = channels.map {
                    EpgCoverage.create(channelId = it.id, coveredFrom = Instant.fromEpochSeconds(hour),
                        coveredTo = Instant.fromEpochSeconds(hour + 7 * 86400)) })),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = if (transition == "details") listOf(
                DvrEntry.create(id = DvrEntryId(42), eventId = event(1, -1).id, state = DvrEntryState.COMPLETED))
                else if (transition in listOf("cancel", "stop", "airing-cancel", "airing-stop")) listOf(
                    DvrEntry.create(id = DvrEntryId(42), eventId = event(1, if (transition?.startsWith("airing-") == true) 0 else 1).id, state = recordingState))
                else emptyList())),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(if (transition in listOf("config", "details-config", "menu-config")) listOf(
                DvrConfiguration(DvrConfigId("one"), "One", ""), DvrConfiguration(DvrConfigId("two"), "Two", "")) else emptyList()),
        )
        val session = FakeTvheadendSession(observation(true))
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player)
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider(context),
            at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus.None,
            at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy.fromPlayerSettings())
        val selection = ChannelSelectionStore()
        val position = GuidePositionStore()
        val lastPlayed = LastPlayedChannelStore(context)
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        // Programme details own a dialog-window notice host using the shell dependencies.
        val noticeModule = module {
            single { UiSettingsStore(preferences()) }
            single { notices }
            single<AppProfileOwner> { profiles }
            single<TvheadendSession> { session }
        }
        val noticeApplication = org.koin.dsl.koinApplication { modules(noticeModule) }
        compose.setContent {
            view = LocalView.current
            back = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            org.koin.compose.KoinIsolatedContext(noticeApplication) {
                androidx.compose.runtime.DisposableEffect(noticeApplication) {
                    onDispose { noticeApplication.close() }
                }
                CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                    TVHeadendPlayerTheme { EpgGridScreen(channelViewModel = model, session = session,
                        playerSession = runtime, selection = selection, guidePositionStore = position,
                        lastPlayedStore = lastPlayed, onPlayRecording = {}, onPlay = { _, _ -> }) }
                }
            }
        }
        fun focused(title: String) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(title) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(title).assertIsFocused()
        }
        fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour 0")).fetchSemanticsNodes().isNotEmpty() }
        key(Key.DirectionDown)
        focused("Channel 1 hour 0")
        if (transition == "menu-details") {
            key(Key.Menu)
            compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithTag("details-watch").assertIsFocused()
            compose.mainClock.autoAdvance = false
            try {
                compose.runOnIdle { back.onBackPressed() }
                repeat(24) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
                }
                compose.onNodeWithText("Channel 1 hour 0").assertIsFocused()
                compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
            } finally {
                compose.mainClock.autoAdvance = true
            }
            assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
            return
        }
        if (transition?.startsWith("menu-") == true) {
            session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(42)))
            key(Key.Menu)
            compose.onNodeWithText("Record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitForIdle()
            compose.waitUntil(5_000) { session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY } == 1 }
            compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
            compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
            focused("Channel 1 hour 0")
            return
        }
        if (transition == "stack" || transition == "stack-record" || transition == "stack-guide") {
            key(Key.DirectionCenter)
            compose.onNodeWithTag("details-watch").assertIsFocused()
            compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER, 0)))
            compose.onNodeWithTag("details-watch").assertIsFocused()
            key(Key.DirectionDown)
            compose.onNodeWithTag("details-card-900").assertIsFocused()
            key(Key.DirectionCenter)
            compose.onNodeWithTag("details-watch").assertIsFocused()
            compose.onNodeWithTag("details-card-900").assertDoesNotExist()
            if (transition == "stack-guide") {
                compose.onNodeWithTag("details-show_guide").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
                focused("Repeat of channel 1 hour 0")
                compose.onNodeWithTag("details-watch").assertDoesNotExist()
                return
            }
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithTag("details-card-900").assertIsFocused()
            compose.runOnIdle { back.onBackPressed() }
            compose.onNodeWithTag("details-watch").assertIsFocused()
            if (transition == "stack-record") {
                session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(42)))
                compose.onNodeWithTag("details-record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
                assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
                compose.onNodeWithTag("details-record").assertIsFocused()
            }
            compose.runOnIdle { back.onBackPressed() }
            focused("Channel 1 hour 0")
            return
        }
        if (transition?.startsWith("airing-") == true) {
            key(Key.DirectionCenter)
            compose.onNodeWithText(context.getString(R.string.watch)).assertIsFocused()
            val label = context.getString(when (transition) {
                "airing-stop" -> R.string.stop_recording
                "airing-cancel" -> R.string.cancel_recording
                else -> R.string.record
            })
            when (transition) {
                "airing-stop" -> session.dvrRepository.scriptStopEntry(DvrMutationResult.Confirmed(Unit))
                "airing-cancel" -> session.dvrRepository.scriptCancelEntry(DvrMutationResult.Confirmed(Unit))
                else -> session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(42)))
            }
            val call = when (transition) {
                "airing-stop" -> FakeSessionCall.DVR_STOP_ENTRY
                "airing-cancel" -> FakeSessionCall.DVR_CANCEL_ENTRY
                else -> FakeSessionCall.DVR_SCHEDULE_ENTRY
            }
            compose.onNodeWithText(label).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            if (transition == "airing-record") compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
            else {
                compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals(context.getString(R.string.keep_recording))
                assertEquals(0, session.calls.count { it == call })
                // Keep is safe and restores the invoking action.
                compose.onNodeWithTag("two-column-first").performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithText(label).assertIsFocused()
                assertEquals(0, session.calls.count { it == call })
                compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithText(label).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            }
            compose.waitForIdle()
            compose.waitUntil(5_000) { session.calls.count { it == call } == 1 }
            if (transition == "airing-stop") assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
            return
        }
        if (transition != null) {
            key(if (transition == "details") Key.DirectionLeft else Key.DirectionRight)
            focused(if (transition == "details") "Channel 1 hour -1" else "Channel 1 hour 1")
            key(Key.DirectionCenter)
            if (transition == "cancel" || transition == "stop") {
                val label = context.getString(if (transition == "cancel") R.string.cancel_recording else R.string.stop_recording)
                compose.onNodeWithText(label).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
                compose.onNodeWithText(context.getString(R.string.keep_recording)).assertIsFocused()
                val downTime = android.os.SystemClock.uptimeMillis()
                fun activationEvent(action: Int, repeat: Int = 0) = androidx.compose.ui.input.key.KeyEvent(
                    android.view.KeyEvent(downTime, android.os.SystemClock.uptimeMillis(), action, activationKeyCode, repeat))
                compose.onNodeWithText(label).requestFocus().performKeyPress(activationEvent(android.view.KeyEvent.ACTION_DOWN))
                val before = session.calls.size
                compose.runOnIdle {
                    recordingState = if (transition == "cancel") DvrEntryState.RECORDING else DvrEntryState.COMPLETED
                    session.publish(observation(true))
                }
                compose.onNodeWithText(context.getString(R.string.keep_recording)).assertDoesNotExist()
                compose.onNodeWithText(label).assertDoesNotExist()
                val nextLabel = context.getString(if (transition == "cancel") R.string.stop_recording else R.string.record)
                compose.onNodeWithText(nextLabel).assertIsFocused()
                // The refreshed Dialog must consume the entire interrupted cycle, not just avoid a click.
                for (repeat in 1..2) {
                    assertTrue("interrupted repeat $repeat must be consumed", compose.onNodeWithText(nextLabel)
                        .performKeyPress(activationEvent(android.view.KeyEvent.ACTION_DOWN, repeat)))
                    compose.onNodeWithText(nextLabel).assertIsFocused()
                    compose.onNodeWithText(context.getString(R.string.keep_recording)).assertDoesNotExist()
                    assertEquals(before, session.calls.size)
                }
                assertTrue("interrupted release must be consumed", compose.onNodeWithText(nextLabel)
                    .performKeyPress(activationEvent(android.view.KeyEvent.ACTION_UP)))
                compose.onNodeWithText(nextLabel).assertIsFocused()
                compose.onNodeWithText(context.getString(R.string.keep_recording)).assertDoesNotExist()
                assertEquals(before, session.calls.size)
                compose.onNodeWithText(nextLabel).performKeyPress(activationEvent(android.view.KeyEvent.ACTION_DOWN))
                compose.onNodeWithText(nextLabel).performKeyPress(activationEvent(android.view.KeyEvent.ACTION_UP))
                if (transition == "cancel") {
                    compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals(context.getString(R.string.keep_recording))
                    compose.onNodeWithText(context.getString(R.string.stop_recording_confirm_title)).assertExists()
                    assertEquals(before, session.calls.size)
                } else {
                    compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
                    assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
                }
                return
            }
            if (transition == "details") {
                compose.onNodeWithTag("details-play").assertExists()
                compose.runOnIdle { session.replaceGeneration(observation(false)) }
                compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("details-play")).fetchSemanticsNodes().isEmpty() }
                compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
                compose.waitUntil(10_000) {
                    compose.onAllNodes(hasText(context.getString(R.string.now)) and isFocusable()).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
                key(Key.DirectionCenter)
                focused("Channel 1 hour 0")
            }
            return
        }
        val calls = session.calls.size
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        assertEquals("past navigation must not acquire future coverage", calls, session.calls.size)
        key(Key.DirectionDown)
        focused("Channel 2 hour -1")
        key(Key.DirectionUp)
        focused("Channel 1 hour -1")
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/guide-history-evidence").apply { mkdirs() }
            File(directory, "$locale-font$scale-full-past-guide.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "$locale-font$scale-full-past-guide.txt").writeText("canvas=960x540\ndensity=1\nlocale=$locale\nfontScale=$scale\nzone=UTC\nfocus=Channel 1 hour -1\nfull EpgGridScreen; real runtime/ExoPlayer/coordinator; SDK fake session\n")
            bitmap.recycle()
        }
        for (hour in 2..6) {
            key(Key.DirectionLeft)
            focused("Channel 1 hour -$hour")
        }
        key(Key.DirectionLeft)
        focused("Channel 1 hour -6")
        assertEquals("earliest past window stays local", calls, session.calls.size)
        compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        compose.runOnIdle { session.publish(observation(false)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour -1")).fetchSemanticsNodes().isEmpty() }
        focused(context.getString(R.string.now))
        compose.onNodeWithText(context.getString(R.string.now)).requestFocus()
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
        compose.runOnIdle { session.publish(observation(true)) }
        key(Key.DirectionLeft)
        focused("Channel 1 hour -1")
        compose.runOnIdle { session.replaceGeneration(observation(false)) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Channel 1 hour -1")).fetchSemanticsNodes().isEmpty() }
        focused(context.getString(R.string.now))
        key(Key.DirectionCenter)
        focused("Channel 1 hour 0")
    }
}
