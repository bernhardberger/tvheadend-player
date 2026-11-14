package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.testutil.testDvrMutationActions
import android.app.Application
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.setContentWithArtworkSampling
import at.bernhardberger.tvhplayer.ui.player.*
import org.junit.Assert.assertEquals
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.core.programmeDetailsActions
import at.bernhardberger.tvhplayer.core.recordingDetailsActions
import at.bernhardberger.tvhplayer.ui.common.AppClock
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi

/** Matched before/after production details hosts; fictional fixture programme, no services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgrammeDetailsCaptureTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val oldLocale = Locale.getDefault()
    private val oldZone = TimeZone.getDefault()
    private val oldClock = AppClock.systemUses24Hour
    private val oldFormat = AppClock.format
    private lateinit var view: View
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ChannelIcon.ImageCache(ArtworkId(1)))
    private val description = ("A winter expedition beyond the Arctic Circle. The crew follows the changing light across the ridge, discovering the wildlife and people of the far north. ").repeat(8)

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        AppClock.format = at.bernhardberger.tvhplayer.settings.ClockFormat.SYSTEM
        AppClock.systemUses24Hour = true
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light"),
            ArtworkId(3) to FixtureArt.art("still-ridge-light"))))
    }
    @After fun release() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
        AppClock.systemUses24Hour = oldClock
        AppClock.format = oldFormat
    }

    @Test fun guideArtLongDescription() = capture("guide-art-long", guide = true)
    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun portraitArtwork() = capture("portrait-artwork", guide = true, portrait = true)
    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun portraitArtworkFramed() = capture("portrait-artwork-framed", guide = true, portrait = true, player = true)
    @Test fun guideScheduled() = capture("guide-scheduled", guide = true, scheduled = true)
    @Test fun guideWithoutArt() = capture("guide-no-art", guide = true, art = false)
    @Test fun recordingResume() = capture("recording-resume", guide = false)
    @Test fun recordingFailed() = capture("recording-failed", guide = false, failed = true)
    @Test fun playerGeometryMatchesModalHosts() = capture("player-geometry", guide = false, player = true)
    @Test fun guideWithoutButtonsTrapsPanelFocusAndBackCloses() = emptyPanel(guide = true)
    @Test fun recordingWithoutButtonsTrapsPanelFocusAndBackCloses() = emptyPanel(guide = false)

    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun warmRecordingFromBrowse() = warmRecording(player = false)
    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun warmRecordingFromPlayer() = warmRecording(player = true)

    @OptIn(TvheadendTestingApi::class)
    private fun warmRecording(player: Boolean) {
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, channelName = channel.name,
            title = "Ridge Light", subtitle = "Beyond the Arctic Circle", description = description,
            start = start, stop = start + 7200.seconds, image = ArtworkId(2), playPosition = 3723.seconds,
            folder = emptyList(), state = DvrEntryState.COMPLETED)
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels = listOf(channel))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(entry))),
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )
        val mutations = testDvrMutationActions(scheduleEntry = { _, _ -> error("Unexpected mutation") },
            stopEntry = { _, _ -> error("Unexpected mutation") }, cancelEntry = { _, _ -> error("Unexpected mutation") },
            deleteEntry = { _, _ -> error("Unexpected mutation") }, updateWatchStateCommand = { _, _, _ -> error("Unexpected mutation") })
        val notices = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) {
            at.bernhardberger.tvhplayer.notices.NoticeContext(0, observation.currentSession?.generationIdentity)
        }
        val state = RecordingsScreenState().apply { mode.value = at.bernhardberger.tvhplayer.core.DvrLibraryMode.ARCHIVE }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f),
                // Browse normally inherits this from AppRoot's navigation shell.
                LocalWarmPlayback provides true,
                at.bernhardberger.tvhplayer.ui.components.LocalWatchingTarget provides
                    at.bernhardberger.tvhplayer.playback.AppPlaybackTarget.Recording(entry.id),
                at.bernhardberger.tvhplayer.ui.components.LocalRecordingPlaybackSelection provides
                    at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection(requireNotNull(observation.currentSession), entry.id)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    if (player) RecordingPlayerDetails(entry, observation, observation, { observation },
                        start.epochSeconds + 8000, mutations, notices, androidx.compose.runtime.rememberCoroutineScope(),
                        onPlayRecording = { _, _ -> error("Unexpected playback") }, onClose = {})
                    else RecordingsScreenContent(observation, state = state, dvrMutationActions = mutations,
                        notices = notices, onPlayRecording = { _, _ -> error("Unexpected playback") },
                        playingRecordingId = entry.id, onReturnToRecording = { _, beforeRequest -> beforeRequest(); true })
                }
            }
        }
        if (!player) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("recording-list-entry-1")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("recording-list-entry-1").requestFocus().performKeyInput { pressKey(Key.Menu) }
            compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        }
        compose.onNodeWithTag("details-continue_watching").assertIsFocused()
        compose.onNodeWithTag("details-start_over").assertExists()
        compose.onNodeWithTag("details-mark_watched").assertExists()
        compose.onNodeWithTag("details-delete").assertExists()
        compose.onNodeWithTag("details-timeline").assertExists()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        CaptureOutput.writePng(File("build/outputs/details-unify", "warm-recording-${if (player) "player" else "browse"}-en-font1.0.png"), bitmap)
        bitmap.recycle()
    }

    @Test fun guideBackFromMoreInfoReturnsToDetailsThenCloses() {
        var shown by mutableStateOf(true)
        lateinit var dispatcher: OnBackPressedDispatcher
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
            title = "Ridge Light", subtitle = "Beyond the Arctic Circle", description = description)
        compose.setContent {
            TVHeadendPlayerTheme {
                dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                if (shown) ProvideArtworkSession(session) { ProgrammeDetailsPage(event, null, channel, start.epochSeconds - 60,
                    programmeDetailsActions(event, start.epochSeconds - 60, null, null, true, true),
                    {}, { shown = false }, related = listOf(EpgEvent.create(EventId(2),
                        channel.id, event.stop, event.stop + 3600.seconds, title = "Next"))) }
            }
        }
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("details-about-description").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("player-info-reading").assertIsFocused()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("details-about-description").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-watch").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("details-card-2").assertIsFocused()
        val back = { compose.runOnIdle { dispatcher.onBackPressed() } }
        back()
        compose.onNodeWithTag("details-watch").assertIsFocused()
        back()
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, shown) }
    }

    @Test fun guideActionThatSurvivesAnUpdateKeepsFocusAfterAnEmptyStart() {
        var canModify by mutableStateOf(false)
        var now by mutableStateOf(start.epochSeconds - 60)
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds, title = "Ridge Light")
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) { ProgrammeDetailsPage(event, null, channel, now,
                    programmeDetailsActions(event, now, null, null, canModify, now >= start.epochSeconds),
                    {}, {}) }
            }
        }
        compose.onNodeWithTag("programme-details-page").assertIsFocused()
        compose.runOnIdle { canModify = true }
        compose.onNodeWithTag("details-record-choice").assertIsFocused()
        // The programme starts: Watch joins ahead of Record…, and the focused Record… stays focused.
        compose.runOnIdle { now = start.epochSeconds + 60 }
        compose.onNodeWithText("Watch").assertExists()
        compose.onNodeWithTag("details-record-choice").assertIsFocused()
    }

    private fun emptyPanel(guide: Boolean) {
        var shown by mutableStateOf(true)
        var canModify by mutableStateOf(false)
        lateinit var backDispatcher: OnBackPressedDispatcher
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds, title = "Ridge Light")
        val entry = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title, state = DvrEntryState.SCHEDULED)
        compose.setContent {
            TVHeadendPlayerTheme {
                backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = {}) { Text("Underlying action") }
                    if (shown) {
                        if (guide) ProvideArtworkSession(session) { ProgrammeDetailsPage(event, null, channel, start.epochSeconds - 60,
                            programmeDetailsActions(event, start.epochSeconds - 60, null, null, canModify, false),
                            {}, { shown = false }) }
                        else ProvideArtworkSession(session) { ProgrammeDetailsPage(null, entry, channel, 0L,
                            recordingDetailsActions(entry, null, canModify, false, nowSec = 0),
                            {}, { shown = false }) }
                    }
                }
            }
        }
        val panel = compose.onNodeWithTag("programme-details-page")
        panel.assertIsFocused()
        compose.onNodeWithText("Close").assertDoesNotExist()
        // A newly available action takes over, then its removal returns focus to the panel.
        compose.runOnIdle { canModify = true }
        (if (guide) compose.onNodeWithTag("details-record-choice") else compose.onNodeWithText("Cancel recording"))
            .assertIsFocused()
        compose.runOnIdle { canModify = false }
        panel.assertIsFocused()
        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight).forEach { direction ->
            panel.performKeyInput { pressKey(direction) }
            panel.assertIsFocused()
        }
        // Robolectric key injection does not dispatch platform Back to the activity/dialog.
        compose.runOnIdle {
            backDispatcher.onBackPressed()
        }
        panel.assertDoesNotExist()
        compose.runOnIdle { assertEquals(false, shown) }
    }

    /** The README scene: Ridge Light from the Guide, on air 20:00-21:00 on 2026-10-04 under a 20:15 clock. */
    @Test fun presentation() = presentationScene(store = false)

    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi")
    fun storeDetails() = presentationScene(store = true)

    private fun presentationScene(store: Boolean) {
        if (store) images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            // The title-free still leaves the page's own channel, status and title legible.
            ArtworkId(1) to FixtureArt.picon("ion-frame"), ArtworkId(2) to FixtureArt.art("still-ion-wake"))))
        val start = Instant.parse(if (store) "2026-10-04T19:20:00Z" else "2026-10-04T20:00:00Z")
        val now = Instant.parse("2026-10-04T20:15:00Z").epochSeconds
        val channel = Channel.create(ChannelId(if (store) 5 else 1), name = if (store) "Ion Frame" else "Ridge Earth",
            number = if (store) 5 else 1, icon = ChannelIcon.ImageCache(ArtworkId(1)))
        val event = if (store) EpgEvent.create(EventId(5), channel.id, start, start + 3900.seconds,
            title = "Ion Wake", subtitle = "A signal beyond the city", genre = "Animation",
            summary = "A night courier follows a mysterious signal across a city of light.",
            description = "A night courier follows a mysterious signal across a city of light. " +
                "With the last train gone, she takes the rooftops towards the old relay tower, " +
                "where an unexpected voice is waiting on the other end.",
            copyrightYear = 2025L, image = ArtworkId(2))
        else EpgEvent.create(EventId(1), channel.id, start, start + 3600.seconds,
            title = "Ridge Light", subtitle = "Winter on the ridge", genre = "Documentary",
            summary = "Seasonal mountain light, forests and weather moving over a ridge.",
            description = "Seasonal mountain light, forests and weather moving over a ridge. As the first snow settles, " +
                "the crew follows deer down to the valley woods and waits out a storm at the hut below the summit.",
            episode = EpgEpisode(null, null, 2, 5, 3, 10, null, null, "S2 E3"), copyrightYear = 2024L,
            image = ArtworkId(2))
        val repeat = EpgEvent.create(EventId(2), channel.id, start + 86400.seconds, event.stop + 86400.seconds,
            title = event.title, subtitle = event.subtitle, image = event.image)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ProvideArtworkSession(session) {
                            ProgrammeDetailsPage(event, null, channel, now,
                                programmeDetailsActions(event, now, null, null, true, true), {}, {},
                                related = listOf(repeat), channelOf = { channel })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        // The backdrop stays dimmed until its picture is sampled off the main thread.
        repeat(10) {
            Thread.sleep(100)
            compose.mainClock.advanceTimeBy(200)
            compose.waitForIdle()
        }
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        CaptureOutput.writePng(File("build/outputs/presentation", if (store) "store-details.png" else "details.png"), bitmap)
        bitmap.recycle()
    }

    /** The action row's five accepted cases, the Record… choice and More's menu. */
    @OptIn(TvheadendTestingApi::class)
    @Test @org.junit.experimental.categories.Category(at.bernhardberger.tvhplayer.testutil.VisualCapture::class)
    fun actionRowCases() {
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds, title = "Ridge Light",
            subtitle = "Beyond the Arctic Circle", description = description, image = ArtworkId(2),
            seriesLinkUri = "crid://ridge-light")
        val onAir = start.epochSeconds + 600
        val upcoming = start.epochSeconds - 3600
        fun entry(state: DvrEntryState, rule: Boolean = false, position: Long = 0) = DvrEntry.create(DvrEntryId(1),
            eventId = event.id, channelId = channel.id, title = event.title, subtitle = event.subtitle,
            description = description, channelName = channel.name, start = event.start, stop = event.stop,
            image = event.image, playPosition = position.seconds, state = state,
            autorecRuleId = AutorecRuleId("series").takeIf { rule },
            files = if (state == DvrEntryState.SCHEDULED) null
                else listOf(TvheadendTestResultFactory.recordingFile(fileId = 1, serverPath = "/rec/ridge-light.ts",
                    start = event.start, stop = event.stop)))
        class Case(val name: String, val programme: Boolean, val recording: DvrEntry?, val now: Long,
            val actions: List<at.bernhardberger.tvhplayer.core.BrowseContextAction>,
            val kind: at.bernhardberger.tvhplayer.core.AutomaticRecordingKind? = null)
        val ruleRecording = entry(DvrEntryState.RECORDING, rule = true)
        val finished = entry(DvrEntryState.COMPLETED, position = 3723)
        val ruleScheduled = entry(DvrEntryState.SCHEDULED, rule = true)
        val cases = listOf(
            Case("case1-on-air-rule-behind-live", true, ruleRecording, onAir, programmeDetailsActions(event, onAir,
                ruleRecording, admitted(ruleRecording), true, true, playing = true, behindLive = true, ruleAvailable = true),
                at.bernhardberger.tvhplayer.core.AutomaticRecordingKind.SERIES),
            Case("case2-upcoming", true, null, upcoming, programmeDetailsActions(event, upcoming, null, null, true, true)),
            Case("case3-finished-partly-watched", false, finished, onAir,
                recordingDetailsActions(finished, admitted(finished), true, true, nowSec = onAir)),
            Case("case4-scheduled-by-rule", true, ruleScheduled, upcoming, programmeDetailsActions(event, upcoming,
                ruleScheduled, null, true, true, ruleAvailable = true),
                at.bernhardberger.tvhplayer.core.AutomaticRecordingKind.SERIES),
            Case("case5-on-air", true, null, onAir, programmeDetailsActions(event, onAir, null, null, true, true)),
        )
        var shown by androidx.compose.runtime.mutableStateOf(cases.first())
        compose.setContentWithArtworkSampling {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        androidx.compose.runtime.key(shown) {
                            ProvideArtworkSession(session) {
                                ProgrammeDetailsPage(event.takeIf { shown.programme }, shown.recording, channel, shown.now,
                                    shown.actions, {}, {}, recordingAdmission = admitted(shown.recording),
                                    recordingKind = shown.kind)
                            }
                        }
                    }
                }
            }
        }
        fun write(name: String) {
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            CaptureOutput.writePng(File("build/outputs/details-action-row", "$name.png"), bitmap)
            bitmap.recycle()
        }
        cases.forEach { case ->
            compose.runOnIdle { shown = case }
            compose.waitForIdle()
            write(case.name)
            when (case.name) {
                "case2-upcoming" -> {
                    compose.onNodeWithTag("details-record-choice").requestFocus()
                    compose.pressKey(Key.DirectionCenter)
                    compose.onNodeWithTag("record-choice-record").assertIsFocused()
                    write("case2-record-choice")
                }
                "case3-finished-partly-watched" -> {
                    compose.onNodeWithTag("details-more").requestFocus()
                    compose.pressKey(Key.DirectionCenter)
                    compose.onNodeWithTag("browse-context-action-mark_watched").assertIsFocused()
                    write("case3-more-menu")
                }
            }
        }
    }

    private fun capture(name: String, guide: Boolean, scheduled: Boolean = false, art: Boolean = true, failed: Boolean = false, player: Boolean = false, portrait: Boolean = false) {
        val channel = if (portrait) Channel.create(ChannelId(1), name = "Amber Stage", number = 6,
            icon = ChannelIcon.ImageCache(ArtworkId(1))) else this.channel
        if (portrait) images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("amber-stage"), ArtworkId(2) to FixtureArt.art("glass-harbor-poster-2x3"))))
        val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
            title = if (portrait) "Glass Harbor" else "Ridge Light", subtitle = if (portrait) null else "Beyond the Arctic Circle",
            description = if (portrait) "A quiet drama about night-shift workers in a harbour glass workshop." else description,
            image = if (art) ArtworkId(2) else null)
        val entry = DvrEntry.create(DvrEntryId(1), eventId = event.id, channelId = channel.id,
            title = event.title, subtitle = event.subtitle, description = description, channelName = channel.name,
            start = event.start, stop = event.stop, image = event.image, playPosition = 3723.seconds,
            subscriptionError = if (failed) DvrSubscriptionError.NO_DISK_SPACE else null,
            state = if (scheduled) DvrEntryState.SCHEDULED else if (failed) DvrEntryState.COMPLETED_ERROR else DvrEntryState.COMPLETED)
        compose.setContentWithArtworkSampling {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (player) ProvideArtworkSession(session) { PlayerDetailsSurface {
                            ProgrammeDetailsPage(event, null, channel, start.epochSeconds + 600,
                                listOf(at.bernhardberger.tvhplayer.core.BrowseContextAction.CONTINUE_WATCHING), {}, {})
                        } }
                        else if (guide) ProvideArtworkSession(session) { ProgrammeDetailsPage(event, entry.takeIf { scheduled }, channel,
                            start.epochSeconds + if (scheduled) -60 else 600,
                            programmeDetailsActions(event, start.epochSeconds + if (scheduled) -60 else 600,
                                entry.takeIf { scheduled }, admitted(entry.takeIf { scheduled }), true, true), {}, {}) }
                        else ProvideArtworkSession(session) { ProgrammeDetailsPage(null, entry, channel, 0L,
                            recordingDetailsActions(entry, admitted(entry), true, true, nowSec = 0), {}, {},
                            recordingAdmission = admitted(entry)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (!player) compose.onNodeWithText("Close").assertDoesNotExist()
        if (failed) {
            compose.onNodeWithTag("details-remove_entry").assertIsFocused()
            compose.onNodeWithTag("details-description").assertDoesNotExist()
        }
        // All origins now use the shared full-page geometry.
        val reading = compose.onNodeWithTag("details-information", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("details-actions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        run {
            assertEquals(116f, reading.left, .1f)
            assertEquals(reading.left, actions.left, .1f)
            // The block ends at one bottom anchor (540 - 96 peek - 32 gap) and grows upward; a page
            // without lower sections that outgrows it (the failure text) extends to the safe edge.
            if (failed) org.junit.Assert.assertTrue(actions.bottom in 824f..984f) else assertEquals(824f, actions.bottom, .1f)
            org.junit.Assert.assertTrue(reading.top >= 96f)
            org.junit.Assert.assertTrue(actions.top >= reading.bottom + 56f)
            // The title spans the page while the other header rows retain their narrow column.
            assertEquals(1688f, reading.width, .1f)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            val canvas = Canvas(bitmap)
            view.draw(canvas)
        }
        CaptureOutput.writePng(File("build/outputs/details-unify", "$name.png"), bitmap)
        bitmap.recycle()
    }
}

@OptIn(ExperimentalTvheadendApi::class)
private fun admitted(entry: DvrEntry?) = entry?.let {
    SessionObservation.create(recordingProgressCapability = RecordingProgressCapability.SUPPORTED).recordingPlaybackAdmission(it)
}
