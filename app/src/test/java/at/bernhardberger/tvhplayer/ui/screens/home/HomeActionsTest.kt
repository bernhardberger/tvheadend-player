package at.bernhardberger.tvhplayer.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import org.koin.dsl.module

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeActionsTest {
    @get:Rule val compose = createComposeRule()
    private val now = 1_800_000_000L
    private val channel = Channel.create(ChannelId(1), name = "Test channel")
    private lateinit var back: androidx.activity.OnBackPressedDispatcher

    @Test fun viewOptionsSelectsTheSharedScopeWithoutStartingPlayback() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        var selected: ChannelTagId? = null
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("scope selection tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {},
                groups = listOf(at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup(ChannelTagId(8), "Favorites", listOf(channel))),
                onSelectTag = { selected = it })
        } }
        compose.onNodeWithTag("home-loading").assertDoesNotExist()
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter) }
        repeat(HomeSectionKind.entries.size) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag("home-tag-8").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(ChannelTagId(8), selected) }
    }

    @Test fun optionsToggleAllSectionsAndBackReturnsToHeaderWithoutTuning() {
        var enabled by mutableStateOf(true)
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(),
            listOf(channel.id), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), enabled,
                onPlay = { _, _ -> fail("options tuned") }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> },
                onGenre = {}, onRecordings = {},
                onHiddenChange = { input = input.copy(hiddenSections = it) })
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.Enter) }
        compose.onNodeWithTag("home-view-CURRENT_ACTIVITY").assertIsFocused()
        repeat(HomeSectionKind.entries.size) { index ->
            compose.onNodeWithTag("home-view-${HomeSectionKind.entries[index].name}")
                .performKeyInput { pressKey(Key.Enter); if (index < HomeSectionKind.entries.lastIndex) pressKey(Key.DirectionDown) }
        }
        compose.runOnIdle { assertEquals(HomeSectionKind.entries.toSet(), input.hiddenSections) }
        compose.onNodeWithTag("home-view-UPCOMING").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-view-button").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-recovery").assertIsFocused()
        compose.runOnIdle { enabled = false }
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("home-recovery").assertIsFocused()
    }

    @Test fun channelsWithoutHistoryRecordingsOrEpgAreNotReportedAsNoChannels() {
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null }, null, now)
        val observation = SessionObservation.create(
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(emptyList())),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(emptyList())),
        )
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithText("Recently watched channels will appear here.").assertExists()
        compose.onNodeWithText("No recordings yet.").assertExists()
        compose.onNodeWithTag("home-recovery").assertIsFocused()
        compose.onNodeWithTag("home-view-button").assertExists()
    }

    @Test fun delayedEntryRestoreDoesNotTakeFocusFromAnOpeningDrawer() {
        var enabled by mutableStateOf(false)
        lateinit var drawer: androidx.tv.material3.DrawerState
        val drawerFocus = androidx.compose.ui.focus.FocusRequester()
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent {
            drawer = androidx.tv.material3.rememberDrawerState(androidx.tv.material3.DrawerValue.Closed)
            CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalBrowseDrawerState provides drawer) {
                TVHeadendPlayerTheme {
                    androidx.compose.foundation.layout.Box {
                        HomeScreenContent(input, SessionObservation.create(), PaddingValues(), enabled,
                            onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                            onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
                        androidx.tv.material3.Button(onClick = {}, modifier = androidx.compose.ui.Modifier
                            .then(androidx.compose.ui.Modifier.focusRequester(drawerFocus)).testTag("test-drawer")) {
                            androidx.tv.material3.Text("Drawer")
                        }
                    }
                }
            }
        }
        compose.runOnIdle { drawerFocus.requestFocus() }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            drawer.setValue(androidx.tv.material3.DrawerValue.Open)
            drawerFocus.requestFocus()
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("test-drawer").assertIsFocused()
    }

    @Test fun staleHomeOwnerRejectsOkAndMenuBeforeRecomposition() {
        var current = true
        val owner = at.bernhardberger.tvhplayer.ui.components.TabOwner { current }
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { CompositionLocalProvider(at.bernhardberger.tvhplayer.ui.components.LocalTabOwner provides owner) {
            TVHeadendPlayerTheme {
                HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                    onPlay = { _, _ -> fail("stale frame tuned") }, onPlayRecording = { _, _ -> fail("stale frame played") },
                    onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> fail("stale frame navigated") },
                    onGenre = { fail("stale frame navigated") }, onRecordings = { fail("stale frame navigated") })
            }
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { current = false }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
    }

    @Test fun removingEarlierCardKeepsTheFocusedChannelIdentity() {
        val second = Channel.create(ChannelId(2), name = "Second")
        val third = Channel.create(ChannelId(3), name = "Third")
        var input by mutableStateOf(HomeInput(listOf(channel, second, third), emptyMap(), emptyList(),
            listOf(channel.id, second.id, third.id), emptyList(), { null }, null, now))
        val tuned = mutableListOf<ChannelId>()
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-recent-1").assertIsFocused()
        compose.runOnIdle { input = input.copy(recentChannelIds = listOf(second.id, third.id)) }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(second.id), tuned) }
    }

    @Test fun removingFocusedSectionRestoresTheFirstRemainingCard() {
        var input by mutableStateOf(HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id),
            emptyList(), { null }, HomePlaying.LiveChannel(channel.id), now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
        compose.runOnIdle { input = input.copy(playing = null) }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
    }

    @Test fun removingLastVisitedCardDoesNotStealHeaderFocus() {
        val second = Channel.create(ChannelId(2), name = "Second")
        var input by mutableStateOf(HomeInput(listOf(channel, second), emptyMap(), emptyList(),
            listOf(channel.id, second.id), emptyList(), { null }, null, now))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> }, onReturnToRecording = { _, _ -> false },
                onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        compose.runOnIdle { input = input.copy(recentChannelIds = listOf(second.id)) }
        compose.onNodeWithTag("home-view-button").assertIsFocused()
    }

    @Test fun outOfScopeContinueWatchingOpensFromOkAndMenu() {
        recordingHome(false, { fail("must not tune") }, { }, outsideScope = true)
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
    }

    @Test fun outOfScopeActivityRecordingFallsBackFromOkAndOpensMenu() {
        recordingHome(false, { fail("must not tune") }, { }, playing = true, outsideScope = true)
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
    }

    @Test fun recordingShelfMenuOpensWithoutPlayingAndBackRestoresFocus() {
        recordingHome(false, { fail("menu tuned") }, { fail("menu played") })
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home-shelf-0").assertIsFocused()
    }

    @Test fun missingWarmRecordingFallsBackToItsCurrentEntry() {
        recordingHome(true, { }, { }, playing = true)
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-watch-live").assertIsFocused()
    }

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun onNowMenuOpensWithoutTuningAndBackRestoresFocus() {
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(now - 60),
            Instant.fromEpochSeconds(now + 600), title = "Now")
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create())))
        val observation = session.observation.value
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val preferences = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            override val data = kotlinx.coroutines.flow.MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
            override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences) =
                transform(data.value).also { data.value = it }
        }
        val dependencies = org.koin.dsl.koinApplication { modules(module {
            single<TvheadendSession> { session }; single { notices }
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(preferences) }
        }) }
        val input = HomeInput(listOf(channel), mapOf(channel.id to event), listOf(event), emptyList(), emptyList(),
            observation::recordingPlaybackAdmission, null, now)
        compose.setContent { org.koin.compose.KoinIsolatedContext(dependencies) {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DisposableEffect(dependencies) { onDispose { dependencies.close() } }
            TVHeadendPlayerTheme {
                HomeScreenContent(input, observation, PaddingValues(), true,
                    onPlay = { _, _ -> fail("menu tuned") }, onPlayRecording = { _, _ -> fail("menu played") },
                    onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
            }
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused().performKeyInput { pressKey(Key.Menu) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
    }

    @Test fun currentLiveOkUsesExistingLiveEntryPath() {
        val tuned = mutableListOf<ChannelId>()
        val observation = SessionObservation.create(channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))))
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.LiveChannel(channel.id), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun activeRecordingOkOffersChoiceAndWatchLiveTunes() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { tuned += it }, onPlay = { fail("must not start recording") })
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("recording-watch-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun outOfScopeRunningRecordingWatchLiveTunesThroughTheRecordingPath() {
        val tuned = mutableListOf<ChannelId>()
        recordingHome(running = true, onWatch = { fail("must not use the scoped channel path") },
            onPlay = { fail("must not start recording") }, outsideScope = true, onWatchRecording = { tuned += it })
        compose.onNodeWithTag("home-activity-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("recording-watch-live").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun continueWatchingOkOffersResumeAndBackRestoresCard() {
        val starts = mutableListOf<RecordingPlaybackStart>()
        recordingHome(running = false, onWatch = { fail("must not tune") }, onPlay = { starts += it })
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-resume").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(RecordingPlaybackStart.RESUME), starts) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    private fun recordingHome(running: Boolean, onWatch: (ChannelId) -> Unit, onPlay: (RecordingPlaybackStart) -> Unit, playing: Boolean = false, outsideScope: Boolean = false,
        onWatchRecording: ((ChannelId) -> Unit)? = null) {
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = "Test recording",
            state = if (running) DvrEntryState.RECORDING else DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(if (running) now + 1800 else now),
            playPosition = 120.seconds,
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 1800), stop = Instant.fromEpochSeconds(now))))
        val initialObservation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED)
        val session = FakeTvheadendSession(initialObservation)
        val observation = session.observation.value
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val dependencies = module { single<TvheadendSession> { session }; single { notices } }
        val application = org.koin.dsl.koinApplication { modules(dependencies) }
        val input = HomeInput(if (outsideScope) emptyList() else listOf(channel), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission,
            if (playing) HomePlaying.Recording(entry.id) else null, now)
        compose.setContent { org.koin.compose.KoinIsolatedContext(application) {
            back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            androidx.compose.runtime.DisposableEffect(application) { onDispose { application.close() } }
            TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { id, _ -> onWatch(id) }, onPlayRecording = { _, start -> onPlay(start) },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {},
                onWatchRecordingChannel = { id, _ -> (onWatchRecording ?: onWatch)(id) })
        } } }
    }

    @Test fun currentRecordingOkReturnsToWarmSession() {
        val returns = mutableListOf<DvrEntryId>()
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), emptyList(), { null },
            HomePlaying.Recording(DvrEntryId(9)), now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> fail("must not tune") }, onPlayRecording = { _, _ -> fail("must not restart") },
                onReturnToRecording = { id, _ -> returns += id; true }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-activity-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrEntryId(9)), returns) }
    }

    @Test fun upcomingSummaryOpensSchedule() {
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Tomorrow", state = DvrEntryState.SCHEDULED,
            start = Instant.fromEpochSeconds(now + 3600), stop = Instant.fromEpochSeconds(now + 7200))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { modes += it })
        } }
        compose.onNodeWithTag("home-upcoming").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(DvrLibraryMode.SCHEDULE), modes) }
    }

    @OptIn(TvheadendTestingApi::class, at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun completedRecordingStartsAndEndCardOpensArchive() {
        val starts = mutableListOf<at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart>()
        val modes = mutableListOf<DvrLibraryMode>()
        val entry = DvrEntry.create(DvrEntryId(1), title = "Yesterday", state = DvrEntryState.COMPLETED,
            start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800),
            files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "test.ts",
                start = Instant.fromEpochSeconds(now - 3600), stop = Instant.fromEpochSeconds(now - 1800))))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))))
        val input = HomeInput(emptyList(), emptyMap(), emptyList(), emptyList(), listOf(entry), observation::recordingPlaybackAdmission, null, now)
        assertNotNull(observation.currentSession)
        assertEquals(RecordingOpenAction.START_OVER, recordingOpenAction(observation.recordingPlaybackAdmission(entry)))
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, observation, PaddingValues(), true,
                onPlay = { _, _ -> }, onPlayRecording = { _, start -> starts += start },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = { modes += it })
        } }
        compose.onNodeWithTag("home-shelf-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, starts.size) }
        compose.onNodeWithTag("home-shelf-0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("home-all-recordings").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(listOf(at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart.START_OVER), starts)
            assertEquals(listOf(DvrLibraryMode.ARCHIVE), modes)
        }
    }

    @Test fun fakeInputRendersRecentAndOkTunesWithoutFocusTuning() {
        val tuned = mutableListOf<ChannelId>()
        val input = HomeInput(listOf(channel), emptyMap(), emptyList(), listOf(channel.id), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = {}, onRecordings = {})
        } }
        compose.onNodeWithTag("home-recent-0").assertIsFocused()
        compose.runOnIdle { assertTrue(tuned.isEmpty()) }
        compose.onNodeWithTag("home-recent-0").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned) }
    }

    @Test fun onNowOkAndGenreOkDispatchTheirCallbacks() {
        val tuned = mutableListOf<ChannelId>()
        val genres = mutableListOf<ProgrammeCategory>()
        val event = EpgEvent.create(EventId(1), channelId = channel.id,
            start = Instant.fromEpochSeconds(now - 600), stop = Instant.fromEpochSeconds(now + 600), title = "Test programme",
            contentGenre = ContentGenre.fromContentType(0x40))
        val input = HomeInput(listOf(channel), mapOf(channel.id to event), listOf(event), emptyList(), emptyList(), { null }, null, now)
        compose.setContent { TVHeadendPlayerTheme {
            HomeScreenContent(input, SessionObservation.create(), PaddingValues(), true,
                onPlay = { id, _ -> tuned += id }, onPlayRecording = { _, _ -> },
                onReturnToRecording = { _, _ -> false }, onShowGuide = { _, _ -> }, onGenre = { genres += it }, onRecordings = {})
        } }
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("home-genre-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(listOf(channel.id), tuned); assertEquals(listOf(ProgrammeCategory.SPORT), genres) }
    }
}
