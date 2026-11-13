package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.testutil.tagBounds
import at.bernhardberger.tvhplayer.testutil.labelToFocusedFillGap
import at.bernhardberger.tvhplayer.testutil.pressKey

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.CaptureOutput
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.ui.TvCardFocusedScale
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.partitionDvrLibrary
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatFileSize
import at.bernhardberger.tvhplayer.ui.common.formatRecordedDuration
import at.bernhardberger.tvhplayer.ui.components.SideRail
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.awaitScheduledWork
import at.bernhardberger.tvhplayer.ui.components.LocalBrowsePreparationContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Offline production screen and shell, with real D-pad focus and no server/player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingsVisualLayoutTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var session: FakeTvheadendSession
    private lateinit var view: View
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private lateinit var oldLocale: Locale
    private lateinit var oldZone: TimeZone
    private var playbackRequests = 0
    private var navigationRequests = 0
    private var screenState = RecordingsScreenState()
    private val railRoute = mutableStateOf(AppDestination.RECORDINGS)
    // The app keeps this state while the player route replaces the Recordings entry.
    private val screenShown = mutableStateOf(true)
    private val endPaddingState = mutableStateOf<Dp?>(null)
    private val connectionState = mutableStateOf<ConnectionUiState>(ConnectionUiState.Ready)
    private var startView by mutableStateOf(DvrArchiveView(grouping = DvrArchiveGrouping.FOLDERS))
    private var nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
    private val viewChanges = mutableListOf<DvrArchiveView>()
    private val preparationContext = mutableStateOf<CoroutineContext>(EmptyCoroutineContext)

    @Before fun prepare() {
        oldLocale = Locale.getDefault()
        oldZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(
            ArtworkId(101) to FixtureArt.art("ridge-light"),
            ArtworkId(102) to FixtureArt.art("still-tide-watch"),
            ArtworkId(103) to FixtureArt.art("ridge-light"),
            ArtworkId(104) to FixtureArt.art("northline-tonight"),
            ArtworkId(105) to FixtureArt.art("harbor-lights-live"),
        )))
    }

    @After fun release() {
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
    }

    @Test fun rootAlignmentAndImmediateChildPreviewPromotesOnlyOnEntry() {
        show()
        focused("recordings-folder-Documentaries")
        assertEquals(540f, bounds("recordings-archive-list").bottom, .1f)
        val grown = bounds("recordings-folder-Documentaries")
        // The options button ends where the rows do and stays clear of the focused first row.
        assertEquals(grown.right, bounds(OPTIONS).right, 1f)
        assertTrue(bounds(OPTIONS).bottom <= grown.top - grown.height * .025f)
        compose.onNodeWithTag("depth-preview", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recording-list-entry-2").assertDoesNotExist()
        compose.onNodeWithTag("recordings-folder-Documentaries/Expeditions").assertDoesNotExist()
        capture("root-folder", 1f)
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        assertGrowthInside(grown, bounds("recordings-archive-list"))
        capture("folder-child-active", 1f)
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        key(Key.Menu)
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("programme-details-page").assertIsDisplayed()
        key(Key.Back)
        focused("recording-list-entry-2")
        key(Key.DirectionLeft)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
    }

    /** The README scene: the previous evening's Ridge Light, partly watched, under a 2026-10-04 20:15 clock, xhdpi. */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    @Category(VisualCapture::class)
    fun presentation() {
        presentationLibrary()
        key(Key.DirectionRight)
        focused("recording-list-entry-1")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture("recordings", 1f, outputs = "build/outputs/presentation")
    }

    /** The same library at the Archive's top level: the server's folders, then the recordings outside them. */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    @Category(VisualCapture::class)
    fun presentationFolders() {
        presentationLibrary()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture("recordings-folders", 1f, outputs = "build/outputs/presentation")
    }

    /** Eight recordings of the README evening's channels, with their logos, focused on the Documentaries folder. */
    private fun presentationLibrary(store: Boolean = false, firstRow: Boolean = false, problems: Boolean = false,
        portrait: Boolean = false, scale: Float = 1f, scheduled: Boolean = false,
        diskSpace: DvrDiskSpaceState = DvrDiskSpaceState.Current(
            DvrDiskSpace(1_240_000_000_000L, 2_760_000_000_000L, 4_000_000_000_000L))) {
        assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
        nowSeconds = { 1_791_144_900L }
        val evening = 1_791_057_600L
        // The channels as Channels and the player number them; each logo is the channel's fixture picon.
        val picons = listOf("ridge-earth", "harbor-sport", "northline-news", "kite-kids", "ion-frame", "amber-stage", "harbor-lights")
        val channels = listOf("Ridge Earth", "Harbor Sport", "Northline News", "Kite Kids", "Ion Frame", "Amber Stage", "Harbor Lights")
            .mapIndexed { index, name -> Channel.create(ChannelId(index + 1L), name = name, number = index + 1L, icon = ChannelIcon.ImageCache(ArtworkId(index + 1))) }
        val genres = mapOf(1L to 0x90L, 2L to 0x90L, 3L to 0x90L, 4L to 0x40L, 5L to 0x40L, 6L to 0x10L, 7L to 0x60L,
            8L to 0x50L, 9L to 0x40L, 10L to 0x10L, 11L to 0x40L, 12L to 0x90L, 13L to 0x60L)
        fun recording(id: Long, title: String, channel: Int, folder: String?, start: Long, minutes: Long,
            art: String?, synopsis: String, position: Long? = null, plays: Long? = null,
            state: DvrEntryState = DvrEntryState.COMPLETED) = DvrEntry.create(
            DvrEntryId(id), title = title, state = state, folder = listOfNotNull(folder),
            start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(start + minutes * 60),
            files = listOf(DvrRecordingFile.create(start = Instant.fromEpochSeconds(start),
                stop = Instant.fromEpochSeconds(start + minutes * 60), sizeBytes = minutes * 40_000_000L)),
            channelId = ChannelId(channel.toLong()), channelName = channels[channel - 1].name,
            image = art?.let { ArtworkId(100 + id.toInt()) }, description = synopsis,
            subtitle = "Winter on the ridge".takeIf { id == 1L }, playPosition = position?.seconds, playCount = plays,
            contentGenre = ContentGenre.fromContentType(genres.getValue(id)))
        val library = listOf(
            recording(1, "Ridge Light", 1, "Documentaries", evening, 60, "ridge-light",
                "Seasonal mountain light, forests and weather moving over a ridge.", position = 1223),
            recording(2, "Tide Watch", 1, "Documentaries", evening - 86400 + 3600, 60, "still-tide-watch",
                "Coastal wildlife along a tidal estuary, wading birds and seals.", plays = 1),
            recording(3, "Coastal Miles", 6, "Documentaries", evening - 2 * 86400, 45, "still-coastal-miles",
                "A walking travel strand along a temperate coast."),
            recording(4, "Harbor Kickoff", 2, "Sport", evening - 86400 - 1800, 135, "harbor-kickoff",
                "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.", plays = 1),
            recording(5, "River Court", 2, "Sport", evening - 3 * 86400, 105, "river-court",
                "Indoor basketball, River Court against North Quay."),
            recording(6, "Glass Harbor", 6, null, evening + 1800, 120, "glass-harbor",
                "A quiet drama about night-shift workers in a harbor glass workshop.", position = 2145.takeIf { store }?.toLong()),
            recording(7, "Harbor Lights Live", 7, null, evening - 86400, 90, "harbor-lights-live",
                "A small-hall concert with a quartet."),
            recording(8, "Paper Kite Tales", 4, null, evening - 2 * 86400 - 7200, 30, "paper-kite-tales",
                "2D animation about a paper kite over painted hills.", plays = 1),
            // Tonight's match, recording now as in the Guide.
            recording(9, "Harbor Kickoff", 2, "Sport", evening + 86400 - 1800, 135, "harbor-kickoff",
                "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.", state = DvrEntryState.RECORDING),
        ) + if (problems) {
            // Two failed recordings: the Problems tab's badge counts them.
            listOf(
                recording(10, "Glass Harbor", 6, null, evening - 5 * 86400, 120, null,
                    "A quiet drama about night-shift workers in a harbor glass workshop.", state = DvrEntryState.COMPLETED_ERROR),
                recording(11, "River Court", 2, null, evening - 6 * 86400, 105, null,
                    "Indoor basketball, River Court against North Quay.", state = DvrEntryState.COMPLETED_ERROR),
            )
        } else {
            emptyList()
        } + if (scheduled) {
            // Two timers for the coming evenings, for the Schedule tab.
            listOf(
                recording(12, "Ridge Light", 1, "Documentaries", evening + 2 * 86400, 60, null,
                    "Seasonal mountain light, forests and weather moving over a ridge.", state = DvrEntryState.SCHEDULED),
                recording(13, "Harbor Lights Live", 7, null, evening + 3 * 86400, 90, null,
                    "A small-hall concert with a quartet.", state = DvrEntryState.SCHEDULED),
            )
        } else {
            emptyList()
        }
        val art = listOf("ridge-light", "still-tide-watch", "still-coastal-miles", "harbor-kickoff", "river-court",
            "glass-harbor", "harbor-lights-live", "paper-kite-tales", "harbor-kickoff")
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), buildMap {
            picons.forEachIndexed { index, name -> put(ArtworkId(index + 1), FixtureArt.picon(name)) }
            art.forEachIndexed { index, name -> put(ArtworkId(101 + index),
                FixtureArt.art(if (portrait && index == 5) "glass-harbor-poster-2x3" else name)) }
        }))
        // A household server's disk, as the Recordings header reports it.
        show(scale = scale, entries = library, channels = channels, diskSpace = diskSpace)
        compose.mainClock.advanceTimeBy(1_500)
        if (firstRow) {
            awaitList()
        } else {
            // The running match fills the Recording now folder that leads the list, and dots its Sport folder.
            focused("recordings-recording-now-folder")
            key(Key.DirectionDown)
            focused("recordings-folder-Sport")
            key(Key.DirectionDown)
            focused("recordings-folder-Documentaries")
        }
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    @Category(VisualCapture::class)
    fun portraitArtwork() {
        presentationLibrary(portrait = true)
        key(Key.DirectionDown)
        focused("recording-list-entry-6")
        compose.waitForIdle()
        capture("recordings-portrait-artwork", 1f, outputs = "build/outputs/presentation")
    }

    /** Store archive: a complete, partly watched drama, distinct from tonight's live scenes. */
    @Test @Config(qualifiers = "en-rUS-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    @Category(VisualCapture::class)
    fun storeRecording() {
        presentationLibrary(store = true)
        key(Key.DirectionDown)
        focused("recording-list-entry-6")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture("store-recording", 1f, outputs = "build/outputs/presentation")
    }

    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    fun storageIndicatorIsQuietInformationAtTheEndOfTheTabLineAndNeverMoves() {
        show(diskSpace = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L)))
        compose.onNodeWithTag("recordings-space").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
        // A ring beside the one-line figure, spoken as one description.
        compose.onNodeWithTag("recordings-space-ring", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recordings-space").assertContentDescriptionContains("79% used", substring = true)
        focused("recordings-folder-Documentaries")
        val resting = bounds("recordings-space")
        // It ends the tab line at the header's edge, centred on the tabs.
        assertEquals(bounds("recordings-header").right, resting.right, .5f)
        assertEquals(bounds("recordings-mode-tabs").center.y, resting.center.y, 2f)
        assertTrue(bounds("recordings-mode-tabs").right < resting.left)
        // Neither the options button, a folder nor another tab moves it.
        upToOptions()
        assertEquals(resting, bounds("recordings-space"))
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        assertEquals(resting, bounds("recordings-space"))
        key(Key.DirectionUp)
        focused("recordings-mode-archive")
        key(Key.DirectionRight)
        focused("recordings-mode-schedule")
        assertEquals(resting, bounds("recordings-space"))
        // Sample the opaque centres of the drawn strokes, not antialiased edge pixels.
        // At normal usage both strokes are long enough to identify their dominant colours.
        fun ringColours(): List<androidx.compose.ui.graphics.Color> {
            val pixels = compose.onNodeWithTag("recordings-space-ring", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            val background = pixels[pixels.width / 2, pixels.height / 2]
            return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
                .filter { it != background }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
        }
        var trackLuminance = 0f
        val contrasts = listOf(70, 95, 99).map { used ->
            compose.runOnIdle {
                session.publish(session.observation.value.let {
                    SessionObservation.create(it.sessionState, it.channelState, it.epgState, it.dvrState,
                        recordingProgressCapability = it.recordingProgressCapability,
                        dvrDiskSpaceState = diskSpacePercentUsed(used))
                })
            }
            compose.waitForIdle()
            val colours = ringColours()
            if (used == 70) trackLuminance = colours.take(2).minOf { it.luminance() }
            val contrast = (colours.first().luminance() + .05f) / (trackLuminance + .05f)
            "$used% used" to contrast
        }
        println("Storage ring drawn arc/track contrast: $contrasts")
        assertTrue("Every storage level needs arc/track contrast >= 3:1: $contrasts",
            contrasts.all { it.second >= 3f })
        assertNoCommands()
    }

    @Test fun storageIndicatorSaysUnknownWithoutARingOrOldNumbers() {
        show(diskSpace = DvrDiskSpaceState.Synchronizing(null))
        compose.onNodeWithTag("recordings-space").assertExists()
        compose.onNodeWithTag("recordings-space-ring", useUnmergedTree = true).assertDoesNotExist()
        focused("recordings-folder-Documentaries")
        assertEquals(bounds("recordings-header").right, bounds("recordings-space").right, .5f)
        capture("header-no-space", 1f)
    }

    @Test fun scheduleDeepLinkScrollsToTheEntryBeforeFocus() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        show(entries = (40L..49L).map { recording(it, "Scheduled expedition ${it - 39}", null,
            DvrEntryState.SCHEDULED, startEpochSeconds = 1_900_000_000L + it * 3600) })
        compose.onNodeWithTag("recording-list-entry-48").assertIsFocused().assertIsDisplayed()
        capture("schedule-deep-linked", 1f, "focused entry=48", outputs = "build/outputs/home")
    }

    @Test fun missingScheduleDeepLinkUsesNormalInitialFocus() {
        scheduleDeepLinkUsesNormalInitialFocus(started = false)
    }

    @Test fun startedScheduleDeepLinkUsesNormalInitialFocus() {
        scheduleDeepLinkUsesNormalInitialFocus(started = true)
    }

    private fun scheduleDeepLinkUsesNormalInitialFocus(started: Boolean) {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(99))
        val entries = if (started) listOf(
            recording(98, "Recording now", null, DvrEntryState.RECORDING, startEpochSeconds = 1_790_000_000),
            recording(99, "Just started", null, DvrEntryState.RECORDING, startEpochSeconds = 1_790_000_100),
        ) else listOf(recording(40, "Scheduled expedition", null, DvrEntryState.SCHEDULED),
            recording(99, "Already finished", null, DvrEntryState.COMPLETED))
        show(entries = entries)
        compose.onNodeWithTag("recording-list-entry-${if (started) 98 else 40}").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("details-cancel").assertDoesNotExist()
    }

    @Test fun scheduleDeepLinkWaitsForTheFirstLoadedSnapshot() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        show(snapshotLoaded = false)
        compose.runOnIdle { assertEquals(DvrEntryId(48), screenState.requestedScheduleEntryId.value) }
        publish((40L..49L).map { recording(it, "Scheduled expedition ${it - 39}", null,
            DvrEntryState.SCHEDULED, startEpochSeconds = 1_900_000_000L + it * 3600) })
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("recording-list-entry-48").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertNull(screenState.requestedScheduleEntryId.value) }
    }

    @Test fun scheduleDeepLinkWaitsForTheMatchingPreparedSnapshot() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE)
        show(empty = true)
        val preparation = StandardTestDispatcher()
        compose.runOnIdle { preparationContext.value = preparation }
        publish((40L..49L).map { recording(it, "Scheduled expedition ${it - 39}", null,
            DvrEntryState.SCHEDULED, startEpochSeconds = 1_900_000_000L + it * 3600) }, awaitPreparation = false)
        compose.runOnIdle {
            screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("The old empty projection cannot judge the new snapshot",
            DvrEntryId(48), screenState.requestedScheduleEntryId.value) }
        compose.awaitScheduledWork(preparation) { screenState.requestedScheduleEntryId.value == null }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("recording-list-entry-48").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle { assertNull(screenState.requestedScheduleEntryId.value) }
    }

    @Test fun scheduleDeepLinkDoesNotStealFocusAfterTheViewerChangesTabs() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        show(snapshotLoaded = false)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        publish(listOf(recording(48, "Scheduled expedition", null, DvrEntryState.SCHEDULED),
            recording(99, "Failed expedition", null, DvrEntryState.COMPLETED_ERROR)))
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Problems").assertIsFocused()
        compose.runOnIdle { assertNull(screenState.requestedScheduleEntryId.value) }
    }

    @Test fun scheduleDeepLinkIsCancelledWhenTheDrawerTakesFocus() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        show(snapshotLoaded = false)
        compose.onNodeWithTag("nav-recordings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertNull(screenState.requestedScheduleEntryId.value) }
        publish(listOf(recording(48, "Scheduled expedition", null, DvrEntryState.SCHEDULED)))
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
    }

    @Test fun scheduleDeepLinkIsCancelledWhenTheScreenLeaves() {
        screenState.open(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE, DvrEntryId(48))
        show(snapshotLoaded = false)
        compose.runOnIdle { screenShown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(screenState.requestedScheduleEntryId.value) }
    }

    @Test fun upFromTheFirstRowReachesTheOptionsButtonThenTheTabsAndDownFromATabReturnsToTheRow() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionDown)
        focused("recordings-folder-News")
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        focused(OPTIONS)
        // Right from the button stays; Up reaches the selected tab, and Down from a tab skips the button.
        key(Key.DirectionRight)
        focused(OPTIONS)
        key(Key.DirectionUp)
        focused("recordings-mode-archive")
        key(Key.DirectionUp)
        focused("recordings-mode-archive")
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        // The button belongs to the root: inside a folder Up goes straight to the tab, Down returns to the row.
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        gone(OPTIONS)
        key(Key.DirectionUp)
        focused("recordings-mode-archive")
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries/Expeditions")
        compose.runOnIdle { assertEquals(listOf("Documentaries"), screenState.archivePath) }
        key(Key.Back)
        compose.runOnIdle { assertTrue(screenState.archivePath.isEmpty()) }
        focused("recordings-folder-Documentaries")
        upToOptions()
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        capture("drawer-expanded", 1f)
        assertNoCommands()
    }

    @Test fun theOptionsPanelOpensGroupSortAndShowAtTheirValuesChoicesApplyAtOnceAndFocusReturnsToTheButton() {
        show()
        focused("recordings-folder-Documentaries")
        upToOptions()
        // The root names each choice with its current value; Folders adds the Recording now switch.
        key(Key.DirectionCenter)
        focused("recordings-view-group")
        compose.onNodeWithTag("recordings-view-group").assertTextContains("Folders")
        compose.onNodeWithTag("recordings-view-recording-now").assertExists()
        key(Key.DirectionCenter)
        focused("recordings-grouping-option-folders")
        key(Key.Back)
        focused("recordings-view-group")
        key(Key.Back)
        gone("recordings-view-panel")
        focused(OPTIONS)
        compose.runOnIdle { assertTrue(viewChanges.isEmpty()) }

        openOptionsLevel("group")
        focused("recordings-grouping-option-folders")
        key(Key.DirectionDown)
        focused("recordings-grouping-option-channels")
        key(Key.DirectionCenter)
        // OK applies at once and the level stays on the row just pressed.
        compose.runOnIdle { assertEquals(listOf(DvrArchiveView(grouping = DvrArchiveGrouping.CHANNELS)), viewChanges) }
        focused("recordings-grouping-option-channels")
        key(Key.Back)
        focused("recordings-view-group")
        compose.onNodeWithTag("recordings-view-group").assertTextContains("Channels")
        compose.onNodeWithTag("recordings-view-recording-now").assertDoesNotExist()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        focused("recordings-sort-option-newest")
        // All six orders are listed, each with its direction.
        DvrArchiveSort.entries.forEach { compose.onNodeWithTag("recordings-sort-option-${it.name.lowercase()}").assertExists() }
        repeat(2) { key(Key.DirectionDown) }
        focused("recordings-sort-option-name")
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(DvrArchiveSort.NAME, viewChanges.last().sort) }
        key(Key.Back)
        key(Key.DirectionDown)
        focused("recordings-view-show")
        key(Key.DirectionCenter)
        focused("recordings-show-option-all")
        DvrArchiveShow.entries.forEach { compose.onNodeWithTag("recordings-show-option-${it.name.lowercase()}").assertExists() }
        key(Key.DirectionDown)
        focused("recordings-show-option-unwatched")
        key(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(DvrArchiveView(DvrArchiveGrouping.CHANNELS, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED), viewChanges.last())
        }
        key(Key.Back)
        key(Key.Back)
        gone("recordings-view-panel")
        focused(OPTIONS)
        // The button speaks the values that differ from the defaults; the headline names the grouping.
        compose.onNodeWithTag(OPTIONS).assertContentDescriptionEquals("View options, Name A–Z, Unwatched")
        assertEquals("Channels", activeHeading().text)
        requireNotNull(valuesLine()).assertTextEquals("Name A–Z · Unwatched")
        assertNoCommands()
    }

    @Test fun choosingAnArrangementResetsTheViewportAndDownEntryToTheFirstRow() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.FLAT)
        show(entries = (1L..30L).map { recording(it, "Recording $it", "recording$it.ts") })
        upToOptions()
        openOptionsLevel("sort")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        closeOptions()
        compose.onNodeWithTag("recording-list-entry-30").assertIsDisplayed()
        key(Key.DirectionDown)
        focused("recording-list-entry-30")
    }

    @Test fun rightAfterResortingGenresEntersThePreviewNotTheButton() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.GENRES)
        presentationLibrary(firstRow = true)
        upToOptions()
        openOptionsLevel("sort")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        closeOptions()
        key(Key.DirectionDown)
        assertTrue(listRowFocused())
        key(Key.DirectionRight)
        assertFalse("Right from a row must not reach the options button", tagFocused(OPTIONS))
        assertTrue("Right enters the folder preview", listRowFocused())
    }

    @Test fun closingTheOptionsPanelGoesStraightToTheButtonWithoutFocusingAnyRow() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.FLAT)
        show(entries = (1L..30L).map { recording(it, "Recording $it", "recording$it.ts") })
        focused("recording-list-entry-1")
        upToOptions()
        openOptionsLevel("sort")
        focused("recordings-sort-option-newest")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.Back)
        focused("recordings-view-sort")
        // Back closes: from here on no frame may show a list row holding the focus before the button has it.
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { backDispatcher.onBackPressed() }
            var frames = 0
            while (!tagFocused(OPTIONS)) {
                assertFalse("A list row held the focus on its way back to the button", listRowFocused())
                assertTrue("The button never took the focus", frames++ < 60)
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertNoCommands()
    }

    @Test fun aFlatListSortsTheRunningRecordingInPlaceAndItsDateDividersTakeNoFocus() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.FLAT)
        show(entries = (1L..5L).map { recording(it, "Recording $it", "recording$it.ts") } +
            recording(6, "Running now", "running.ts", DvrEntryState.RECORDING, startEpochSeconds = 1_791_100_000))
        // The running recording is the newest row of the list, with its dot: no card, no virtual row.
        compose.onNodeWithTag("recordings-now-cards").assertDoesNotExist()
        focused("recording-list-entry-6")
        assertTrailingStateMark(6, "Recording now")
        compose.onAllNodesWithTag("archive-date-divider", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("A day opens with its label", it.isNotEmpty())
            assertTrue("Date labels remain accessible section headings", it.all { node ->
                node.config.contains(SemanticsProperties.Heading) &&
                    node.config.contains(SemanticsProperties.ContentDescription)
            })
        }
        key(Key.DirectionDown)
        focused("recording-list-entry-1")
        key(Key.DirectionUp)
        focused("recording-list-entry-6")
        // The first row's Up stop is the options button, as at every level; Down returns to the first recording.
        key(Key.DirectionUp)
        focused(OPTIONS)
        key(Key.DirectionDown)
        focused("recording-list-entry-6")
        assertNoCommands()
    }

    /**
     * A focused row grows towards the label above it. Every date label keeps the first one's air
     * above a focused row, so the label never touches the row it introduces.
     */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun aFocusedRowKeepsTheFirstDateLabelsAirUnderEveryLabel() {
        startView = DvrArchiveView()
        presentationLibrary(firstRow = true)
        compose.mainClock.advanceTimeBy(600)
        fun labels() = compose.onAllNodesWithTag("archive-date-divider", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.width > 0f && it.top > 0f }.sortedBy { it.top }
        fun focusedRow() = compose.onAllNodes(SemanticsMatcher("List row") {
            it.config.getOrNull(SemanticsProperties.TestTag).orEmpty().startsWith("recording-list-entry-")
        } and isFocused()).fetchSemanticsNodes().single().boundsInRoot
        fun labelAbove(row: Rect) = labels().lastOrNull { it.bottom <= row.top + 1f && row.top - it.bottom < 48f }
        val first = labels().first()
        assertEquals(first, labelAbove(focusedRow()))
        val firstGap = compose.labelToFocusedFillGap(view, first)
        capture("flat-first-label-focused-row", 1f, "label ink to focused fill=$firstGap px", outputs = LABEL_CAPTURES)
        repeat(8) { if (labelAbove(focusedRow()).let { it == null || it == labels().first() }) key(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(600)
        val later = requireNotNull(labelAbove(focusedRow())) { "No later date label above a focused row" }
        val laterGap = compose.labelToFocusedFillGap(view, later)
        capture("flat-later-label-focused-row", 1f, "label ink to focused fill=$laterGap px", outputs = LABEL_CAPTURES)
        assertTrue("A later date label sits $laterGap px above a focused row, the first $firstGap px",
            laterGap >= firstGap)
    }

    @Test fun sortAndShowApplyAtOnceTheListFollowsAndOnlyShowFillsTheButton() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.FLAT)
        show(entries = listOf(
            recording(1, "Bravo", "b.ts"), recording(2, "Alpha", "a.ts", playCount = 1), recording(3, "Charlie", "c.ts"),
            recording(4, "Zulu", "Zulu/z.ts"),
        ))
        focused("recording-list-entry-1")
        compose.onAllNodesWithTag("archive-date-divider", useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("Newest first keeps the date dividers", it.isNotEmpty())
        }
        assertEquals(null, valuesLine())
        val resting = optionsFill()
        openOptionsLevel("sort")
        repeat(2) { key(Key.DirectionDown) }
        focused("recordings-sort-option-name")
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(DvrArchiveSort.NAME, viewChanges.last().sort) }
        closeOptions()
        // A sort alone leaves the button quiet and shows on the values line.
        key(Key.DirectionDown)
        assertEquals(resting, optionsFill())
        requireNotNull(valuesLine()).assertTextEquals("Name A–Z")
        openOptionsLevel("show")
        focused("recordings-show-option-all")
        key(Key.DirectionDown)
        focused("recordings-show-option-unwatched")
        key(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(DvrArchiveView(DvrArchiveGrouping.FLAT, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED), viewChanges.last())
        }
        // Name order without date dividers; the played Alpha is out, the partly watched Bravo is not played to the end.
        compose.onNodeWithTag("recording-list-entry-2").assertDoesNotExist()
        assertTrue(bounds("recording-list-entry-1").top < bounds("recording-list-entry-3").top)
        assertTrue(bounds("recording-list-entry-3").top < bounds("recording-list-entry-4").top)
        assertTrue(compose.onAllNodesWithTag("archive-date-divider", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        // Partially watched is narrower: only what was begun and not finished.
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-list-entry-1").assertExists()
        compose.onNodeWithTag("recording-list-entry-3").assertDoesNotExist()
        // Watched is the opposite of Unwatched.
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-list-entry-2").assertExists()
        compose.onNodeWithTag("recording-list-entry-1").assertDoesNotExist()
        // The choices apply together: grouping to folders keeps the sort and the filter.
        key(Key.Back)
        repeat(2) { key(Key.DirectionUp) }
        focused("recordings-view-group")
        key(Key.DirectionCenter)
        focused("recordings-grouping-option-flat")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(DvrArchiveView(DvrArchiveGrouping.FOLDERS, DvrArchiveSort.NAME, DvrArchiveShow.WATCHED), viewChanges.last())
        }
        closeOptions()
        // A narrowing Show fills the button.
        key(Key.DirectionDown)
        assertNotEquals(resting, optionsFill())
        assertNoCommands()
    }

    @Test fun anArchiveEmptiedByTheShowFilterSaysSoAndTheButtonCanLiftIt() {
        startView = DvrArchiveView(show = DvrArchiveShow.UNWATCHED)
        show(entries = listOf(recording(2, "Alpha", "a.ts", playCount = 1), recording(3, "Bravo", "b.ts", playCount = 2)))
        compose.mainClock.advanceTimeUntil { tagFocused(OPTIONS) }
        compose.waitForIdle()
        compose.onNodeWithText("Everything here has been played.").assertIsDisplayed()
        compose.onNodeWithText("2 recordings are hidden.").assertIsDisplayed()
        openOptionsLevel("show")
        focused("recordings-show-option-unwatched")
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        closeOptions()
        compose.onNodeWithText("Everything here has been played.").assertDoesNotExist()
        compose.onNodeWithTag("recording-list-entry-2").assertExists()
    }

    @Test fun aFolderTheFilterEmptiesReturnsToItsParentAndFocusesTheNearestRow() {
        startView = DvrArchiveView(grouping = DvrArchiveGrouping.FOLDERS, show = DvrArchiveShow.UNWATCHED)
        show(entries = listOf(
            recording(2, "Alpha", "Series/a.ts"), recording(3, "Bravo", "Series/b.ts"),
            recording(4, "Charlie", "Other/c.ts"),
        ))
        awaitList()
        if (!tagFocused("recordings-folder-Series")) key(Key.DirectionDown)
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        // Both get played elsewhere while the folder is open under Unwatched.
        publish(listOf(
            recording(2, "Alpha", "Series/a.ts", playCount = 1), recording(3, "Bravo", "Series/b.ts", playCount = 2),
            recording(4, "Charlie", "Other/c.ts"),
        ))
        compose.runOnIdle { assertEquals(emptyList<String>(), screenState.archivePath) }
        focused("recordings-folder-Other")
        compose.onNodeWithTag("recordings-folder-Series").assertDoesNotExist()
        compose.onNodeWithTag("recordings-folder-Other").assertExists()
        assertNoCommands()
    }

    @Test fun backOnATabInsideAFolderLeavesForTheRailWithoutPoppingTheFolder() {
        show(shellBackEnabled = true)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.DirectionUp)
        focused(BROWSE_TAB)
        key(Key.Back)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        compose.runOnIdle { assertEquals(listOf("Documentaries"), screenState.archivePath) }
        assertNoCommands()
    }

    @Test fun aChangedStoredShowFilterReconcilesTheOpenFolderWithoutAnEntryUpdate() {
        show(entries = listOf(
            recording(2, "Alpha", "Series/a.ts", playCount = 1),
            recording(3, "Other", "Other/b.ts"),
        ))
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        compose.runOnIdle { startView = startView.copy(show = DvrArchiveShow.UNWATCHED) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(emptyList<String>(), screenState.archivePath) }
        focused("recordings-folder-Other")
        assertNoCommands()
    }

    @Test fun deletingTheLastNestedRecordingReturnsToTheNearestPopulatedAncestor() {
        val keep = recording(3, "Other", "Series/other.ts")
        show(entries = listOf(recording(2, "Alpha", "Series/Season/Episode/a.ts"), keep))
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        focused("recordings-folder-Series/Season")
        key(Key.DirectionRight)
        focused("recordings-folder-Series/Season/Episode")
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        publish(listOf(keep))
        compose.runOnIdle { assertEquals(listOf("Series"), screenState.archivePath) }
        focused("recording-list-entry-3")
        assertNoCommands()
    }

    /**
     * At the defaults both columns' first rows are level and the button stays clear of the focused
     * first row; the values line hangs from the headline and never moves the preview column.
     */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun theBrowseHeadlineNamesTheGroupingAndItsColumnsShareTheFirstRow() {
        show()
        focused("recordings-folder-Documentaries")
        val active = activeHeading()
        assertEquals("Folders", active.text)
        val defaultRow = firstListRow()
        assertButtonClearOf(defaultRow)
        assertEquals(null, valuesLine())
        compose.onNodeWithTag(OPTIONS).assertContentDescriptionEquals("View options")
        // With the list unfocused both columns start with a folder row: their icons are level.
        upToOptions()
        val listIcon = firstIconInk(firstListRow().left, active.bounds.bottom)
        val previewIcon = firstIconInk(bounds("depth-preview").left, active.bounds.bottom)
        assertEquals("Both columns start their rows level", listIcon, previewIcon, 1f)
        // Below the root at the defaults: no button, no line, the same headline and rows.
        downToList()
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        gone(OPTIONS)
        assertEquals(null, valuesLine())
        assertEquals(active.baseline, activeHeading().baseline, 1f)
        assertEquals(defaultRow.top, firstListRow().top, 1f)
        key(Key.Back)
        focused("recordings-folder-Documentaries")

        // A non-default sort and filter add the quiet line under the headline; the preview column stays put.
        openOptionsLevel("sort")
        repeat(2) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        key(Key.Back)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        closeOptions()
        val withValues = activeHeading()
        assertEquals(active.baseline, withValues.baseline, 1f)
        val values = requireNotNull(valuesLine())
            .assertTextEquals("Name A–Z · Unwatched").fetchSemanticsNode().boundsInRoot
        assertEquals("The values line never pushes the preview down", previewIcon,
            firstIconInk(bounds("depth-preview").left, active.bounds.bottom), 1f)
        val above = values.top - withValues.baseline
        val below = firstListRow().top - values.bottom
        assertTrue("The values line hangs from the headline ($above above, $below below)", above >= 0f && below > 2 * above)
        assertTrue("The values line stops short of the button", values.right <= bounds(OPTIONS).left)
        downToList()
        assertButtonClearOf(firstListRow())
        val rootRowWithValues = firstListRow()

        // Below the root there is no button and the line reads as at the root, at the same geometry.
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        gone(OPTIONS)
        val inFolder = activeHeading()
        assertEquals(active.baseline, inFolder.baseline, 1f)
        requireNotNull(valuesLine()).assertTextEquals("Name A–Z · Unwatched")
        assertEquals(rootRowWithValues.top, firstListRow().top, 1f)

        // With Show back at All the non-default sort alone still names itself below the root.
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        openOptionsLevel("show")
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        closeOptions()
        requireNotNull(valuesLine()).assertTextEquals("Name A–Z")
        downToList()
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        requireNotNull(valuesLine()).assertTextEquals("Name A–Z")
        assertEquals(active.baseline, activeHeading().baseline, 1f)
        assertEquals(rootRowWithValues.top, firstListRow().top, 1f)
        assertNoCommands()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun theLongestHeaderValuesStayClearOfTheButtonAndTheTabsOfTheStorageInGermanAtLargeText() {
        Locale.setDefault(Locale.GERMANY)
        startView = DvrArchiveView(DvrArchiveGrouping.CHANNELS, DvrArchiveSort.LARGEST, DvrArchiveShow.PARTIALLY_WATCHED)
        presentationLibrary(firstRow = true, scale = 1.3f)
        val storage = bounds("recordings-space")
        assertTrue("Storage and tabs do not overlap", bounds("recordings-mode-tabs").right < storage.left)
        assertEquals(bounds("recordings-header").right, storage.right, .5f)
        val values = requireNotNull(valuesLine()).fetchSemanticsNode().boundsInRoot
        assertTrue("The values line ends before the button", values.right <= bounds(OPTIONS).left)
        assertButtonClearOf(firstListRow())
    }

    @Test fun theTabsOpenScheduleAndProblemsBesideTheStorageWithTheProblemCountOnTheTab() {
        val space = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L))
        show(diskSpace = space)
        focused("recordings-folder-Documentaries")
        val storage = bounds("recordings-space")
        // Schedule is a tab here, not a rail item; the Problems tab counts the one problem.
        compose.onNodeWithTag("nav-schedule").assertDoesNotExist()
        compose.onNode(hasText("1") and hasAnyAncestor(hasTestTag("recordings-problems-badge")), useUnmergedTree = true)
            .assertIsDisplayed()
        upToOptions()
        key(Key.DirectionUp)
        focused("recordings-mode-archive")
        key(Key.DirectionRight)
        focused("recordings-mode-schedule")
        gone(OPTIONS)
        assertEquals(storage, bounds("recordings-space"))
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        key(Key.DirectionUp)
        focused("recordings-mode-schedule")
        key(Key.DirectionRight)
        focused("recordings-mode-problems")
        assertEquals(storage, bounds("recordings-space"))
        key(Key.DirectionDown)
        focused("recording-list-entry-21")
        key(Key.DirectionUp)
        focused("recordings-mode-problems")
        // Problems is the last tab; Left walks back to Browse, whose Down returns to the row it left.
        key(Key.DirectionRight)
        focused("recordings-mode-problems")
        repeat(2) { key(Key.DirectionLeft) }
        focused("recordings-mode-archive")
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
    }

    private fun diskSpacePercentUsed(used: Int) = DvrDiskSpaceState.Current(
        DvrDiskSpace(4_000_000_000_000L * (100 - used) / 100, 4_000_000_000_000L * used / 100, 4_000_000_000_000L))

    /** The final header frames for the owner's review; each frame's .txt lists the measured header bounds. */
    private fun headerFrame(name: String, view: DvrArchiveView, problems: Boolean = false,
        diskSpace: DvrDiskSpaceState = diskSpacePercentUsed(70), steps: () -> Unit = {}) {
        startView = view
        presentationLibrary(firstRow = true, problems = problems, scheduled = true, diskSpace = diskSpace)
        steps()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture(name, 1f, headerReport(), outputs = HEADER_CAPTURES)
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFoldersDefault() = headerFrame("folders-default", DvrArchiveView(DvrArchiveGrouping.FOLDERS))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFoldersSortAndFilter() = headerFrame("folders-sort-and-filter",
        DvrArchiveView(DvrArchiveGrouping.FOLDERS, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFoldersRecordingNowOff() = headerFrame("folders-recording-now-off",
        DvrArchiveView(DvrArchiveGrouping.FOLDERS, recordingNowFolder = false))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFoldersEntered() = headerFrame("folders-entered", DvrArchiveView(DvrArchiveGrouping.FOLDERS)) {
        key(Key.DirectionDown)
        focused("recordings-folder-Sport")
        key(Key.DirectionRight)
        awaitList()
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFoldersEnteredFilter() = headerFrame("folders-entered-filter",
        DvrArchiveView(DvrArchiveGrouping.FOLDERS, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED)) {
        // Name A–Z puts Sport after the other folders.
        repeat(6) { if (!tagFocused("recordings-folder-Sport")) key(Key.DirectionDown) }
        focused("recordings-folder-Sport")
        key(Key.DirectionRight)
        awaitList()
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFlatDefault() = headerFrame("flat-default", DvrArchiveView())

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameFlatSortOnly() = headerFrame("flat-sort-only", DvrArchiveView(sort = DvrArchiveSort.OLDEST))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameChannelsFilterOnly() = headerFrame("channels-filter-only",
        DvrArchiveView(DvrArchiveGrouping.CHANNELS, show = DvrArchiveShow.UNWATCHED))

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameOptionsButtonFocused() = headerFrame("options-button-focused",
        DvrArchiveView(DvrArchiveGrouping.FOLDERS, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED)) { upToOptions() }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameOptionsPanelRoot() = headerFrame("options-panel-root",
        DvrArchiveView(DvrArchiveGrouping.FOLDERS, DvrArchiveSort.NAME, DvrArchiveShow.UNWATCHED)) {
        upToOptions()
        key(Key.DirectionCenter)
        focused("recordings-view-group")
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameScheduleTab() = headerFrame("schedule-tab", DvrArchiveView(DvrArchiveGrouping.FOLDERS), problems = true) {
        upToOptions()
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        focused("recordings-mode-schedule")
        downToList()
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameProblemsTab() = headerFrame("problems-tab", DvrArchiveView(DvrArchiveGrouping.FOLDERS), problems = true) {
        upToOptions()
        key(Key.DirectionUp)
        repeat(2) { key(Key.DirectionRight) }
        focused("recordings-mode-problems")
        downToList()
    }

    @Test @Category(VisualCapture::class) @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun headerFrameStorageWarning() = headerFrame("storage-warning", DvrArchiveView(DvrArchiveGrouping.FOLDERS),
        diskSpace = diskSpacePercentUsed(97))

    /**
     * The options button belongs to Browse's root column: entering a folder carries it off with
     * that column, stepping back brings it home, and it never stands over the level sliding in.
     */
    @Test @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi",
        instrumentedPackages = ["at.bernhardberger.tvhplayer.ui.screens", "at.bernhardberger.tvhplayer.ui.common"])
    fun theOptionsButtonTravelsWithTheRootColumnThroughTheDepthSlide() {
        startView = DvrArchiveView(DvrArchiveGrouping.FOLDERS)
        presentationLibrary(firstRow = true)
        key(Key.DirectionDown)
        focused("recordings-folder-Sport")
        compose.mainClock.advanceTimeBy(1_500)
        val button = bounds(OPTIONS)
        // The root column's own unscaled content under the button: its leading row's end, with the chevron.
        val row = bounds("recordings-recording-now-folder").let { Rect(it.right - 160f, it.top, it.right, it.bottom) }
        val buttonTemplate = compose.runOnIdle { luminance(button) }
        val rowTemplate = compose.runOnIdle { luminance(row) }
        // Frame by frame, wherever the root column's row is still legible (it fades and leaves the
        // screen), the button stands at the same shift, whole: nothing of the deeper level is drawn over it.
        fun step(press: Key, captures: Map<Long, String> = emptyMap()) {
            var tracked = 0
            compose.mainClock.autoAdvance = false
            try {
                compose.onRoot().performKeyInput { pressKey(press) }
                (1..12).map { it * 80L }.forEach { at ->
                    compose.mainClock.advanceTimeBy(80)
                    compose.waitForIdle()
                    val (rowShift, rowMatch) = compose.runOnIdle { trackHorizontally(rowTemplate, row) }
                    val (buttonShift, buttonMatch) = compose.runOnIdle { trackHorizontally(buttonTemplate, button) }
                    captures[at]?.let { capture(it, 1f, "root column shift=$rowShift px; button shift=$buttonShift px",
                        outputs = HEADER_CAPTURES) }
                    if (rowMatch < 0.85f) return@forEach
                    assertEquals("At $at ms the button left the root column", rowShift, buttonShift, 1f)
                    assertTrue("At $at ms the button is not whole (match $buttonMatch)", buttonMatch > 0.9f)
                    if (rowShift != 0f) tracked++
                }
            } finally {
                compose.mainClock.autoAdvance = true
            }
            assertTrue("The slide was not seen in motion ($tracked frames)", tracked >= 3)
        }
        step(Key.DirectionRight, mapOf(160L to "transition-enter-1", 240L to "transition-enter-2",
            320L to "transition-enter-3"))
        awaitList()
        compose.mainClock.advanceTimeBy(1_500)
        step(Key.DirectionLeft)
        compose.mainClock.advanceTimeBy(1_500)
        focused("recordings-folder-Sport")
        assertEquals(button, bounds(OPTIONS))
    }

    /**
     * Inside a folder the parent slides off to the screen edge beside the rail. Once the slide
     * settles it fades out, so its folder arrows do not rest there and read as the rail's.
     */
    @Test fun parentArrowsBesideTheRailFadeOutOnceTheSlideSettles() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recordings-folder-Documentaries/Expeditions")
        compose.waitForIdle()
        val (parent, _, background) = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            fun brightest(xs: IntRange, ys: IntRange) =
                xs.maxOf { x -> ys.maxOf { y -> android.graphics.Color.valueOf(bitmap.getPixel(x, y)).run { maxOf(red(), green(), blue()) } } }
            Triple(brightest(0..30, 116..246), brightest(562..900, 116..186), brightest(0..30, 420..460))
                .also { bitmap.recycle() }
        }
        assertTrue("the parent's arrows still show ($parent over $background)", parent <= background + .01f)
    }

    @Test fun theListFadeKeepsTheScaledFocusedRowWhole() {
        show()
        focused("recordings-folder-Documentaries")
        compose.waitForIdle()
        val list = bounds("depth-active")
        val row = bounds("recordings-folder-Documentaries")
        val (outside, background) = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            fun brightness(x: Float, y: Float) =
                android.graphics.Color.valueOf(bitmap.getPixel(x.toInt(), y.toInt())).run { maxOf(red(), green(), blue()) }
            (brightness(list.left - 3f, row.center.y) to brightness(list.left - 3f, row.bottom + 40f))
                .also { bitmap.recycle() }
        }
        assertTrue("the focused row's scaled end shows left of the list ($outside over $background)", outside > background + .5f)
    }

    @Test fun folderOkEntersInPlaceDetailsAndBackRestoreOrigin() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recordings-folder-Documentaries/Expeditions")
        compose.onNodeWithTag("archive-level-heading").assertTextEquals("Documentaries")
        capture("entered-folder", 1f)
        assertEquals(bounds("recording-list-leading-1").left, bounds("recording-list-leading-3").left, .1f)
        assertEquals(bounds("recording-list-headline-1").left, bounds("recording-list-headline-3").left, .1f)
        assertEquals(bounds("recording-list-metadata-1").left, bounds("recording-list-metadata-3").left, .1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-1")
        assertOverlineAboveTitle(1)
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-pane").assertDoesNotExist()
        val previewText = hasText("A winter expedition beyond the Arctic Circle") and
            hasAnyAncestor(hasTestTag("recording-metadata-pane"))
        compose.onNode(previewText).assertDoesNotExist()
        compose.onNode(previewText, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(340f, bounds("recording-metadata-pane").width, .1f)
        assertEquals(562f, bounds("recording-metadata-pane").left, .1f)
        capture("focused-recording-metadata", 1f)
        key(Key.Menu)
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-resume").assertIsFocused()
        compose.onNodeWithText("Resume")
            .assertIsDisplayed()
        key(Key.Back)
        focused("recording-list-entry-1")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
    }

    @Test fun returningFromThePlayerFocusesThePlayedRecordingBelowFolders() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        focused("recording-list-entry-5")
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(1, playbackRequests) }
        compose.runOnIdle { screenShown.value = false }
        compose.waitForIdle()
        compose.runOnIdle { screenShown.value = true }
        compose.waitForIdle()
        focused("recording-list-entry-5")
        compose.mainClock.advanceTimeBy(1_200)
        focused("recording-list-entry-5")
        compose.runOnIdle { assertEquals("recording:5", screenState.archiveNavigation.stack.active.focusedItemId) }
    }

    @Test fun longTextAtLargeFontGrowsNativeRowsWithoutClipping() {
        show(scale = 1.3f)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.DirectionDown)
        focused("recording-list-entry-1")
        val row = bounds("recording-list-entry-1")
        assertTrue("Native row must grow beyond the retired 56dp row: ${row.height}", row.height > 72f)
        val list = bounds("recordings-archive-list")
        assertGrowthInside(row, list)
        assertOverlineAboveTitle(1)
        val title = textLayout("recording-list-headline-1")
        assertEquals(1, title.lineCount)
        assertTrue("Long title ellipsizes in its own text column", title.isLineEllipsized(0))
        capture("long-font1.3", 1.3f)
        assertNoCommands()
    }

    @Test fun scheduleAndProblemsKeepNativeRowsAndTruthfulStatus() {
        scheduleAndProblemsKeepNativeRowsAndTruthfulStatus(endPadding = null)
    }

    @Test fun largerCallerEndPaddingIsPreservedWithoutDoublingFocusReserve() {
        scheduleAndProblemsKeepNativeRowsAndTruthfulStatus(endPadding = 100.dp)
    }

    private fun scheduleAndProblemsKeepNativeRowsAndTruthfulStatus(endPadding: Dp?) {
        show(endPadding = endPadding)
        focused("recordings-folder-Documentaries")
        compose.mainClock.advanceTimeBy(600)
        val archiveRow = bounds("recordings-folder-Documentaries")
        if (endPadding != null) assertEquals(860f, bounds("recordings-header").right, .1f)
        openScheduleSubnav()
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        assertOverlineAboveTitle(20)
        assertArchiveColumns(20, archiveRow, "Tomorrow's documentary")
        if (endPadding != null) assertTrue(bounds("recording-metadata-pane").right <= 860f)
        // A scheduled row carries its clock at the end, as channel rows do; the overline keeps only times.
        assertTrailingStateMark(20, "Scheduled")
        // The pane is passive: Right stays on the row, as on an Archive recording.
        key(Key.DirectionRight)
        focused("recording-list-entry-20")
        capture("schedule", 1f)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-21")
        assertOverlineAboveTitle(21)
        assertArchiveColumns(21, archiveRow, "Unfinished expedition")
        if (endPadding != null) assertTrue(bounds("recording-metadata-pane").right <= 860f)
        // Without a reason from the server the pane says what happened in its notice's words.
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-failure")) and hasText("Recording failed"),
            useUnmergedTree = true).assertIsDisplayed()
        key(Key.DirectionRight)
        focused("recording-list-entry-21")
        compose.onNodeWithTag("recording-list-state-failed", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasTestTag("recording-list-metadata-21") and hasText("Failed", substring = true),
            useUnmergedTree = true).assertExists()
        // The state no longer leads the row, so the logo keeps the column it has on every other tab.
        assertEquals(32f * 1.05f, bounds("recording-list-leading-21").width, .1f)
        capture("problems", 1f)
        assertNoCommands()
    }

    @Test fun multiItemListsExposeMiddleRowNativeFocusAndGeometry() {
        val entries = (1L..8L).map { recording(it, "Expedition episode $it", "Documentaries/episode$it.ts") } +
            (40L..47L).map { recording(it, "Scheduled expedition ${it - 39}", null, DvrEntryState.SCHEDULED,
                startEpochSeconds = 1_791_086_400) } +
            (60L..67L).map { recording(it, "Unfinished expedition ${it - 59}", null, DvrEntryState.COMPLETED_ERROR,
                startEpochSeconds = 1_791_000_000) }
        show(entries = entries)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        val archivePane = bounds("recording-metadata-pane")
        key(Key.DirectionDown)
        focused("recording-list-entry-2")
        // Archive spacing is unchanged: record its existing overlap, but still require complete focus growth.
        assertMiddleFocus(2, checkNeighbourClearance = false)
        key(Key.DirectionUp)
        openScheduleSubnav()
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-40")
        assertDenseListGeometry()
        // The focused entry's pane takes the column of the Archive's recording preview.
        assertEquals(archivePane.width, bounds("recording-metadata-pane").width, .1f)
        assertEquals(archivePane.left, bounds("recording-metadata-pane").left, .1f)
        assertPaneTitle("Scheduled expedition 1")
        key(Key.DirectionDown)
        focused("recording-list-entry-41")
        assertPaneTitle("Scheduled expedition 2")
        assertMiddleFocus(41)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-60")
        assertDenseListGeometry()
        assertEquals(archivePane.width, bounds("recording-metadata-pane").width, .1f)
        assertEquals(archivePane.left, bounds("recording-metadata-pane").left, .1f)
        assertPaneTitle("Unfinished expedition 1")
        key(Key.DirectionDown)
        focused("recording-list-entry-61")
        assertPaneTitle("Unfinished expedition 2")
        assertMiddleFocus(61)
        assertNoCommands()
    }

    @Test fun schedulePaneGivesThePaddedStopAndRuleAndProblemsPaneWhatWentWrong() {
        show(entries = paneEntries(long = false))
        focused("recordings-folder-Sport")
        openScheduleScreen()
        focused("recording-list-entry-40")
        // The padded stop in the Schedule row's own words, and the series rule that set it up.
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-metadata")) and hasText("• until", substring = true),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-rule")) and hasText("Part of a series recording"),
            useUnmergedTree = true).assertIsDisplayed()
        // Nothing is recorded yet, so the planned length is not given as recorded.
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-metadata")) and hasText("recorded", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
        capture("schedule-pane", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-41")
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-rule")) and hasText("Part of a repeating timer"),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-metadata")) and hasText("until", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-60")
        // The server's reason in plain words, and what is left of the file.
        val size = formatFileSize(1_200_000_000L, ApplicationProvider.getApplicationContext<Application>())
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-failure")) and hasText("Recording storage full"),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-partial-file")) and hasText("Partial file • $size"),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-rule", useUnmergedTree = true).assertDoesNotExist()
        capture("problems-pane", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-61")
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-failure")) and hasText("Recording missed"),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-partial-file", useUnmergedTree = true).assertDoesNotExist()
        assertNoCommands()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeSchedulePaneAndProblemsPaneKeepTheirLinesWhole() {
        Locale.setDefault(Locale.GERMANY)
        show(scale = 1.3f, entries = paneEntries(long = true))
        focused("recordings-folder-Sport")
        openScheduleScreen()
        focused("recording-list-entry-40")
        assertOverlineAboveTitle(40)
        assertPaneLinesWhole("recording-metadata-title", "recording-metadata-metadata", "recording-metadata-rule")
        capture("schedule-pane-de-font1.3", 1.3f)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("recording-list-entry-60")
        assertOverlineAboveTitle(60)
        assertPaneLinesWhole("recording-metadata-title", "recording-metadata-metadata", "recording-metadata-failure",
            "recording-metadata-partial-file")
        capture("problems-pane-de-font1.3", 1.3f)
        assertNoCommands()
    }

    @Test fun emptyScheduleAndProblemsKeepTheirMessageWithoutAPane() {
        show(entries = listOf(recording(1, "Kept match", "Sport/kept.ts")))
        focused("recordings-folder-Sport")
        openScheduleSubnav()
        compose.onNodeWithText("Schedule").assertIsFocused()
        compose.onNodeWithText("Nothing is recording or scheduled.").assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertDoesNotExist()
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        compose.onNodeWithText("No recording problems.").assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertDoesNotExist()
        capture("empty-problems", 1f)
        assertNoCommands()
    }

    @Test fun stoppedEarlyOverlineFitsOneLine() {
        stoppedEarlyHintStaysWholeInTheRow(german = false)
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeStoppedEarlyHintStaysWholeInTheRow() {
        stoppedEarlyHintStaysWholeInTheRow(german = true)
    }

    private fun stoppedEarlyHintStaysWholeInTheRow(german: Boolean) {
        if (german) Locale.setDefault(Locale.GERMANY)
        show(scale = if (german) 1.3f else 1f, entries = listOf(recording(1,
            if (german) "Die außergewöhnliche Reise der Polarlichter: Eine Winterexpedition durch die Arktis" else "Ridge Light",
            if (german) "Dokumentationen/Expedition.ts" else "Documentaries/ridge.ts", recordedMinutes = 20)))
        focused("recordings-folder-${if (german) "Dokumentationen" else "Documentaries"}")
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        assertOverlineAboveTitle(1)
        val size = formatFileSize(2_400_000_000L, ApplicationProvider.getApplicationContext<Application>())
        compose.onNodeWithTag("recording-list-metadata-1", useUnmergedTree = true)
            .assert(hasText(" • $size", substring = true))
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasTestTag("recording-list-metadata-1") and
            hasText(if (german) "Vorzeitig beendet" else "• 20m • $size Stopped early", substring = true), useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val hint = results.single()
        val shown = hint.layoutInput.text.text.substring(0, hint.getLineEnd(hint.lineCount - 1, visibleEnd = true))
        assertTrue("'$shown' lines=${hint.lineCount} size=${hint.size} c=${hint.layoutInput.constraints}",
            shown.endsWith(if (german) "20 Min. • $size Vorzeitig beendet" else "20m • $size Stopped early"))
        compose.onNodeWithTag("recording-list-stopped-early", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, hint.lineCount)
        assertFalse("The stopped-early marker must remain whole", hint.hasVisualOverflow)
        // A plain completed recording does not repeat its state.
        compose.onNode(hasTestTag("recording-list-metadata-1") and
            hasText(if (german) "Abgeschlossen" else "Completed", substring = true), useUnmergedTree = true).assertDoesNotExist()
        val subtitle = compose.onNodeWithTag("recording-list-supporting-1", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The line below the title must stay inside its row",
            subtitle.bottom <= bounds("recording-list-entry-1").bottom + 0.5f)
        assertNoCommands()
    }

    @Test fun runningMetadataPaneShowsElapsedLengthRatherThanUntil() {
        val now = System.currentTimeMillis() / 1000L
        val entry = DvrEntry.create(DvrEntryId(1), title = "Growing recording", state = DvrEntryState.RECORDING,
            start = Instant.fromEpochSeconds(now - 1_200), stop = Instant.fromEpochSeconds(now + 3_600),
            files = listOf(DvrRecordingFile.create(start = Instant.fromEpochSeconds(now - 610))))
        compose.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingMetadataPane(entry, null, null)
            }
        }
        val elapsed = formatRecordedDuration(600, ApplicationProvider.getApplicationContext<Application>().resources)
        compose.onNodeWithText("$elapsed recorded", substring = true).assertIsDisplayed()
        compose.onNodeWithText("until", substring = true).assertDoesNotExist()
    }

    @Test fun missedRecordingIsListedWithTheFailuresAndSaysMissed() {
        show(entries = listOf(recording(1, "Kept match", "Sport/kept.ts"),
            recording(30, "Missed match", null, DvrEntryState.MISSED),
            recording(31, "Broken match", null, DvrEntryState.COMPLETED_ERROR)))
        focused("recordings-folder-Sport")
        openScheduleSubnav()
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-30")
        // One "Failed" section holds both; a missed row carries the failure mark, read out as what happened.
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("Canceled").assertDoesNotExist()
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("Missed", substring = true),
            useUnmergedTree = true).assertExists()
        // Neither left a file, so both rows give their date but no length.
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("90m", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasTestTag("recording-list-metadata-31") and hasText("90m", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithTag("recording-list-state-failed", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag("recording-list-state-cancelled", useUnmergedTree = true).assertDoesNotExist()
        capture("problems-missed", 1f)
    }

    @Test fun missedAndCanceledPaneBadgesNameTheirState() {
        val entry = mutableStateOf(recording(30, "Missed match", null, DvrEntryState.MISSED))
        compose.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingMetadataPane(entry.value, null, null)
            }
        }
        compose.onNodeWithTag("recording-badge-missed", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording missed").assertExists()
        compose.onNodeWithText("recorded", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Canceled", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { entry.value = recording(31, "Dropped entry", null, DvrEntryState.INVALID) }
        compose.onNodeWithTag("recording-badge-cancelled", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Canceled", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Failed", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun crowdedPaneShortensItsTitleAndKeepsSubtitleAndStatus() {
        val start = 1_791_000_000L - 86_400L
        val entry = DvrEntry.create(DvrEntryId(1), title = LONG_TITLE, state = DvrEntryState.COMPLETED, folder = emptyList(),
            start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(start + 215 * 60),
            files = listOf(DvrRecordingFile.create(start = Instant.fromEpochSeconds(start),
                stop = Instant.fromEpochSeconds(start + 151 * 60), sizeBytes = 2_400_000_000L)),
            channelName = "Ridge Earth Mountain Channel HD Extra", subtitle = "A winter expedition beyond the Arctic Circle",
            // The error's reason line fills the pane, which now starts higher, so the title must shorten.
            subscriptionError = DvrSubscriptionError.NO_DISK_SPACE,
            episode = EpgEpisode(null, null, 2, 5, 7, 10, null, null, "S2 E7"), copyrightYear = 2018,
            rating = EpgRating(0, null, null, null, null, null),
            description = "Follow a small team of naturalists through remote landscapes.")
        show(scale = 1.3f, entries = listOf(entry))
        focused("recording-list-entry-1")
        capture("archive-crowded-pane", 1.3f)
        val pane = bounds("recording-metadata-pane")
        listOf("recording-metadata-title", "recording-metadata-subtitle", "recording-metadata-metadata",
            "recording-metadata-stopped-early").forEach {
            compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed()
            assertTrue("$it ends inside the pane", bounds(it).bottom <= pane.bottom + 0.5f)
        }
        // The title gives way to one line first; the subtitle says what the recording is.
        val title = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("recording-metadata-title", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(title) }
        assertEquals(1, title.single().lineCount)
    }

    @Test fun zeroLengthIsAbsentFromRowsAndPane() {
        show(entries = listOf(recording(1, "Empty recording", "empty.ts", recordedMinutes = 0)))
        focused("recording-list-entry-1")
        val zeroLength = formatRecordedDuration(0, ApplicationProvider.getApplicationContext<Application>().resources)
        compose.onNode(hasTestTag("recording-list-metadata-1") and
            hasText(zeroLength, substring = true), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-pane")) and
            hasText(zeroLength, substring = true), useUnmergedTree = true).assertDoesNotExist()
        assertNoCommands()
    }

    @Test fun runningRowMarksItsEndAndKeepsTheLogoAndOverlineAligned() {
        val now = System.currentTimeMillis() / 1000L
        show(entries = listOf(
            recording(1, "Archived", "Documentaries/archived.ts"),
            DvrEntry.create(DvrEntryId(30), title = "Live match", state = DvrEntryState.RECORDING,
                start = Instant.fromEpochSeconds(now - 600), stop = Instant.fromEpochSeconds(now + 3_000),
                files = listOf(DvrRecordingFile.create(sizeBytes = 2_400_000_000L)),
                channelName = "Ridge Earth HD", subtitle = "Second half"),
            DvrEntry.create(DvrEntryId(31), title = "Weak signal", state = DvrEntryState.RECORDING_ERROR,
                start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 3_300),
                channelName = "Ridge Earth HD"),
        ))
        awaitList()
        openScheduleSubnav()
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-30")
        assertOverlineAboveTitle(30)
        // The overline gives the start and the stop; the state is the dot at the row's end.
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("• until", substring = true),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("Recording", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
        val size = formatFileSize(2_400_000_000L, ApplicationProvider.getApplicationContext<Application>())
        compose.onNodeWithTag("recording-list-metadata-30", useUnmergedTree = true)
            .assert(!hasText(size, substring = true))
        assertTrailingStateMark(30, "Recording now")
        // Only the 32dp logo (with the focus scale) leads the row.
        assertEquals(32f * 1.05f, bounds("recording-list-leading-30").width, .1f)
        compose.onNodeWithTag("recording-list-errors-30", useUnmergedTree = true).assertDoesNotExist()
        key(Key.DirectionDown)
        focused("recording-list-entry-31")
        // Errors while recording add the error mark beside the dot.
        assertTrailingStateMark(31, "Recording • errors")
        assertTrue(bounds("recording-list-errors-31").left > bounds("recording-list-state-31").right)
        capture("schedule-running-row", 1f)
        assertNoCommands()
    }

    @Test fun emptyLibraryParksFocusOnTheBrowseTabWithoutCommands() {
        show(empty = true)
        compose.mainClock.advanceTimeUntil(10_000) { tagFocused(BROWSE_TAB) }
        compose.onNodeWithText("No recordings are available.").assertIsDisplayed()
        capture("empty-archive", 1f)
        key(Key.DirectionDown)
        focused(BROWSE_TAB)
        assertNoCommands()
    }

    @Test fun loadingShowsTheArchiveSkeletonUnderTheHeaderItKeepsWhenRecordingsArrive() {
        val space = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L))
        connectionState.value = ConnectionUiState.SyncingChannels
        show(empty = true, diskSpace = space)
        // The loading handoff parks focus on the Browse tab: without a list there is no headline or button; the skeleton takes none.
        compose.mainClock.advanceTimeUntil(10_000) { tagFocused(BROWSE_TAB) }
        compose.onNodeWithTag("recordings-skeleton").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
        compose.onNodeWithText("Loading recordings…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading recordings…").assertIsDisplayed()
        val header = bounds("recordings-header")
        val tab = bounds(BROWSE_TAB)
        val spaceLine = bounds("recordings-space")
        val skeleton = bounds("recordings-skeleton")
        capture("loading-skeleton", 1f)
        key(Key.DirectionDown)
        focused(BROWSE_TAB)

        compose.runOnIdle { connectionState.value = ConnectionUiState.Ready }
        publish(listOf(recording(1, LONG_TITLE, "Documentaries/northern.ts"), recording(5, "Harbor Lights Live", "concert.ts")))
        compose.onNodeWithTag("recordings-archive-list", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("recordings-skeleton").assertDoesNotExist()
        assertEquals(header, bounds("recordings-header"))
        // The tab stands where it did before the recordings arrived.
        assertEquals(tab, bounds(BROWSE_TAB))
        assertEquals(spaceLine, bounds("recordings-space"))
        // The skeleton stood in the Archive column's place; the list fills in below the headline as recordings arrive.
        assertTrue(bounds("recordings-archive-list").top >= skeleton.top)
        assertEquals(bounds("recordings-folder-Documentaries").left, skeleton.left, .1f)
        assertEquals(540f, skeleton.bottom, .1f)
        assertNoCommands()
    }

    @Test fun preparingShowsTheSkeletonWhereTheSyncingLibraryKeepsIt() = preparationSkeletonStaysInPlace(1f)

    @Test fun preparingShowsTheSkeletonWhereTheSyncingLibraryKeepsItAtLargeText() = preparationSkeletonStaysInPlace(1.3f)

    private fun preparationSkeletonStaysInPlace(scale: Float) {
        connectionState.value = ConnectionUiState.SyncingChannels
        show(scale = scale, empty = true)
        compose.mainClock.advanceTimeUntil(10_000) { tagFocused(BROWSE_TAB) }
        val syncing = bounds("recordings-skeleton")
        compose.runOnIdle { screenShown.value = false }
        compose.waitForIdle()
        val preparation = StandardTestDispatcher()
        compose.runOnIdle { preparationContext.value = preparation }
        // A new entry prepares its first frame: the focus anchor shows the same skeleton, not "Loading…".
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { screenShown.value = true }
            var frames = 0
            while (compose.onAllNodesWithTag("browse-preparing", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                assertTrue("The screen shows its preparation within a few frames", ++frames <= 10)
                compose.mainClock.advanceTimeByFrame()
            }
            val preparing = hasTestTag("recordings-skeleton") and hasAnyAncestor(hasTestTag("browse-preparing"))
            val skeleton = compose.onNode(preparing, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            compose.onAllNodesWithText(ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(at.bernhardberger.tvhplayer.R.string.loading)).assertCountEquals(0)
            assertEquals("The preparing skeleton stands where the syncing one does at font $scale", syncing, skeleton)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.awaitScheduledWork(preparation) {
            compose.onAllNodesWithTag("browse-preparing", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(syncing, bounds("recordings-skeleton"))
        assertNoCommands()
    }

    @Test fun parentViewportAndInvokingFolderSurviveRepeatedPushPop() {
        show(entries = (1L..24L).map { recording(it, "Episode $it", "Folder%02d/episode.ts".format(it)) })
        focused("recordings-folder-Folder01")
        repeat(16) { key(Key.DirectionDown) }
        focused("recordings-folder-Folder17")
        val frame = compose.runOnIdle { screenState.archiveNavigation.stack.active }
        assertTrue(frame.firstVisibleIndex > 0)
        repeat(3) {
            key(Key.DirectionRight)
            focused("recording-list-entry-17")
            key(Key.DirectionLeft)
            focused("recordings-folder-Folder17")
            compose.runOnIdle {
                val restored = screenState.archiveNavigation.stack.active
                assertEquals(frame.firstVisibleItemId, restored.firstVisibleItemId)
                assertEquals(frame.scrollOffset, restored.scrollOffset)
            }
        }
        assertNoCommands()
    }

    /** The G10's Sports folder showed half a row above its first whole one after a visit scrolled it. */
    @Test fun previewOfAScrolledFolderStartsWithAWholeRowUnderItsHeading() {
        show(entries = (1L..14L).map { recording(it, "Event $it", "Sports/League%02d/event.ts".format(it)) } +
            recording(30, "Harbor Lights Live", "concert.ts"))
        focused("recordings-folder-Sports")
        val fresh = previewFirstInk()
        key(Key.DirectionRight)
        focused("recordings-folder-Sports/League01")
        repeat(9) { key(Key.DirectionDown) }
        focused("recordings-folder-Sports/League10")
        key(Key.Back)
        focused("recordings-folder-Sports")
        capture("preview-after-scrolled-visit", 1f)
        val returned = previewFirstInk()
        assertEquals("the preview's first row starts where an unvisited one does", fresh.first, returned.first, 1f)
        assertEquals("and is as bright, not under the top fade", fresh.second, returned.second, .05f)
        // Entering again still lands on the row the visit left.
        key(Key.DirectionRight)
        focused("recordings-folder-Sports/League10")
        assertNoCommands()
    }

    @Test fun removedPathReconcilesWithoutStealingTabOrDrawerFocus() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.DirectionRight)
        focused("recording-list-entry-2")
        key(Key.DirectionUp)
        focused(BROWSE_TAB)
        publish(listOf(recording(1, "Remaining", "Documentaries/remaining.ts")))
        compose.runOnIdle { assertEquals(listOf("Documentaries"), screenState.archivePath) }
        focused(BROWSE_TAB)
        downToList()
        focused("recording-list-entry-1")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        publish(emptyList())
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        key(Key.DirectionRight)
        compose.mainClock.advanceTimeUntil(10_000) { tagFocused(BROWSE_TAB) }
        compose.onNodeWithText("No recordings are available.").assertIsDisplayed()
        compose.onNodeWithTag("depth-row-depth-unavailable").assertDoesNotExist()
        assertNoCommands()
    }

    @Test fun serverDeletedRecordingClosesItsDetailsAndFocusesTheNextRow() {
        val entries = (1L..3L).map { recording(it, "Recording $it", "recording$it.ts") }
        show(entries = entries)
        focused("recording-list-entry-1")
        key(Key.DirectionDown)
        focused("recording-list-entry-2")
        key(Key.Menu)
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-play").assertIsFocused()
        publish(entries.map { if (it.id == DvrEntryId(2))
            DvrEntry.create(it.id, title = it.title, state = DvrEntryState.REMOVED, files = emptyList()) else it })
        compose.onNodeWithTag("details-play").assertDoesNotExist()
        compose.onNodeWithTag("details-delete").assertDoesNotExist()
        compose.onNodeWithTag("details-remove_entry").assertDoesNotExist()
        focused("recording-list-entry-3")
        openScheduleSubnav()
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        compose.onNodeWithText("Failed").assertDoesNotExist()
        compose.onNodeWithText("No recording problems.").assertIsDisplayed()
        compose.onNodeWithTag("recording-list-entry-2").assertDoesNotExist()
        assertNoCommands()
    }

    @Test fun removedRecordingUsesNextNeighborWithoutDismissingAnotherDetailsPanel() {
        val entries = (1L..3L).map { recording(it, "Recording $it", "recording$it.ts") }
        show(entries = entries)
        focused("recording-list-entry-1")
        key(Key.DirectionDown)
        focused("recording-list-entry-2")
        publish(entries.filterNot { it.id == DvrEntryId(2) })
        focused("recording-list-entry-3")
        key(Key.Menu)
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("details-play").assertIsFocused()
        publish(entries.filterNot { it.id == DvrEntryId(2) }.map { if (it.id == DvrEntryId(1))
            recording(1, "Updated", "recording1.ts") else it })
        compose.onNodeWithTag("details-play").assertIsFocused()
        key(Key.Back)
        focused("recording-list-entry-3")
        assertNoCommands()
    }

    @Test fun replacementSessionCannotConfirmAnArchiveDeletionWithCollidingId() {
        show(entries = listOf(recording(9, "Opening recording", "opening.ts")))
        focused("recording-list-entry-9")
        key(Key.Menu)
        compose.onNodeWithText("Delete").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        compose.runOnIdle {
            session.replaceGeneration(session.observation.value.let {
                SessionObservation.create(it.sessionState, it.channelState, it.epgState,
                    DvrRepositoryState.Current(DvrSnapshot.create(listOf(recording(9, "Replacement recording", "replacement.ts")))),
                    recordingProgressCapability = it.recordingProgressCapability)
            })
        }
        compose.waitForIdle()
        compose.onNodeWithTag("recording-confirmation-back").assertDoesNotExist()
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        compose.onNodeWithTag("details-play").assertDoesNotExist()
        compose.onNodeWithTag("details-delete").assertDoesNotExist()
        focused("recording-list-entry-9")
        assertNoCommands()
    }

    @Test fun heldRightEntersOneLevelAndRapidReverseKeepsInvokingRows() {
        show()
        focused("recordings-folder-Documentaries")
        compose.mainClock.autoAdvance = false
        try {
            // Repeats are swallowed: on the TV they outran the new level and escaped to the tabs.
            compose.onRoot().performKeyInput { keyDown(Key.DirectionRight) }
            compose.mainClock.advanceTimeBy(96)
            compose.onRoot().performKeyInput { advanceEventTime(800); keyUp(Key.DirectionRight) }
            compose.runOnIdle { assertEquals(listOf("Documentaries"), screenState.archivePath) }
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.mainClock.advanceTimeBy(96)
            compose.runOnIdle { assertTrue(screenState.archivePath.isEmpty()) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        focused("recordings-folder-Documentaries")
        assertNoCommands()
    }

    @Test fun heldLeftWalksBackToTheRootAndOnToTheDrawer() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        compose.runOnIdle { assertEquals(listOf("Documentaries", "Expeditions"), screenState.archivePath) }
        compose.onRoot().performKeyInput { keyDown(Key.DirectionLeft); advanceEventTime(800); keyUp(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(screenState.archivePath.isEmpty()) }
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        assertNoCommands()
    }

    @Test fun aFolderOpensAgainOnTheRowItWasLeftOn() {
        show(entries = List(12) { recording(it + 1L, "Episode ${it + 1}", "Series/episode-$it.ts") } +
            recording(20, "Northline Tonight", "News/report.ts"))
        // Folders come newest first, so Series leads.
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        focused("recording-list-entry-1")
        repeat(9) { key(Key.DirectionDown) }
        focused("recording-list-entry-10")
        key(Key.DirectionLeft)
        focused("recordings-folder-Series")
        // Another folder in between does not make Series forget its row.
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        focused("recording-list-entry-20")
        key(Key.DirectionLeft)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        focused("recording-list-entry-10")
        compose.runOnIdle { assertEquals(listOf("Series"), screenState.archivePath) }
        assertNoCommands()
    }

    @Test fun aRunningRecordingLeadsTheFoldersRootInRecordingNowAndDotsItsFolderIcon() {
        show(entries = listOf(
            recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING),
            recording(12, "Kept", "kept.ts"),
        ))
        awaitList()
        compose.onNodeWithTag("recordings-now-cards").assertDoesNotExist()
        // The Recording now folder leads the root and opens on the running recording.
        focused("recordings-recording-now-folder")
        assertTrue(bounds("recordings-recording-now-folder").top < bounds("recordings-folder-Series").top)
        key(Key.DirectionRight)
        focused("recording-list-entry-10")
        compose.runOnIdle { assertEquals(listOf("recording-now"), screenState.archiveNavigation.stack.frames.drop(1).map { it.levelId }) }
        key(Key.DirectionLeft)
        focused("recordings-recording-now-folder")
        // The running recording also stays in its own folder, whose icon carries the dot.
        key(Key.DirectionDown)
        focused("recordings-folder-Series")
        assertFolderDotOnIcon("Series")
        key(Key.DirectionRight)
        focused("recordings-folder-Series/Season")
        assertFolderDotOnIcon("Series/Season")
        key(Key.DirectionRight)
        focused("recording-list-entry-10")
        assertTrailingStateMark(10, "Recording now")
        key(Key.DirectionLeft)
        key(Key.DirectionLeft)
        focused("recordings-folder-Series")
        // Once it finishes the folder and the dot go.
        publish(listOf(recording(10, "Finished", "Series/Season/growing.ts"), recording(12, "Kept", "kept.ts")))
        compose.onNodeWithTag("recordings-folder-running-Series", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("recordings-recording-now-folder").assertDoesNotExist()
        assertNoCommands()
    }

    @Test fun theRecordingNowSwitchHidesTheFolderButKeepsTheFolderDot() {
        show(entries = listOf(
            recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING),
            recording(12, "Kept", "kept.ts"),
        ))
        focused("recordings-recording-now-folder")
        upToOptions()
        key(Key.DirectionCenter)
        focused("recordings-view-group")
        repeat(3) { key(Key.DirectionDown) }
        focused("recordings-view-recording-now")
        compose.onNodeWithTag("recordings-view-recording-now").assertIsOn()
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(DvrArchiveView(DvrArchiveGrouping.FOLDERS, recordingNowFolder = false), viewChanges.last()) }
        compose.onNodeWithTag("recordings-view-recording-now").assertIsOff()
        key(Key.Back)
        gone("recordings-view-panel")
        focused(OPTIONS)
        compose.onNodeWithTag("recordings-recording-now-folder").assertDoesNotExist()
        key(Key.DirectionDown)
        focused("recordings-folder-Series")
        assertFolderDotOnIcon("Series")
        assertNoCommands()
    }

    @Test fun finishingLastRecordingWithContextMenuOpenKeepsTheMenuOnTheSameRecording() {
        show(entries = listOf(recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING)))
        focused("recordings-recording-now-folder")
        key(Key.DirectionDown)
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        focused("recording-list-entry-10")
        key(Key.Menu)
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.onNodeWithText("Stop recording").requestFocus().assertIsFocused()

        publish(listOf(recording(10, "Finished", "Series/Season/growing.ts")))

        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        compose.onNodeWithText("Stop recording").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNode(hasAnyAncestor(hasTestTag("browse-context-menu")) and isFocused()).assertExists()
        assertTrue(compose.onAllNodesWithText("Finished").fetchSemanticsNodes().isNotEmpty())
        key(Key.Back)
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        focused("recording-list-entry-10")
        assertNoCommands()
    }

    @Test fun runningRecordingFinishesInPlaceAtFullFolderDepth() {
        show(entries = listOf(recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING)))
        focused("recordings-recording-now-folder")
        key(Key.DirectionDown)
        focused("recordings-folder-Series")
        key(Key.DirectionRight)
        focused("recordings-folder-Series/Season")
        key(Key.DirectionRight)
        focused("recording-list-entry-10")

        publish(listOf(recording(10, "Finished", "Series/Season/growing.ts")))

        focused("recording-list-entry-10")
        compose.runOnIdle { assertEquals(listOf("Series", "Season"), screenState.archivePath) }
        assertNoCommands()
    }

    @Test fun folderRowsShowWhatIsWatchedAndTheirMenuOffersTheFolderActions() {
        show(entries = listOf(
            recording(2, "Tide Watch", "Documentaries/blue.ts", playCount = 1),
            recording(3, "Ridge Light", "Documentaries/mountains.ts"),
            recording(6, "Coastal Miles", "Documentaries/coast.ts"),
            recording(4, "Northline Tonight", "News/report.ts", playCount = 1),
            recording(7, "Northline Weekend", "News/weekend.ts", playCount = 3),
        ))
        compose.onNodeWithTag("recordings-folder-watched-News", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recordings-folder-supporting-Documentaries", useUnmergedTree = true)
            .assertTextEquals("3 recordings • 1 watched • 7.2 GB")
        capture("archive-folder-marks", 1f)
        compose.onNodeWithTag("recordings-folder-Documentaries").requestFocus().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithTag("browse-context-menu").assertIsDisplayed()
        // The menu's header counts what the folder holds and spans its days, oldest first.
        compose.onNode(hasText("3 recordings • 1 watched • 7.2 GB") and hasAnyAncestor(hasTestTag("browse-context-item")),
            useUnmergedTree = true).assertIsDisplayed()
        // The range keeps each day together with non-breaking spaces.
        compose.onNode(hasText("Sep\u00a027", substring = true) and hasText("Oct\u00a01", substring = true) and
            hasAnyAncestor(hasTestTag("browse-context-item")), useUnmergedTree = true).assertIsDisplayed()
        capture("archive-folder-menu", 1f)
        compose.onNodeWithText("Delete all").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("folder-delete-back").assertIsFocused()
        capture("archive-folder-delete", 1f)
    }

    @Test fun watchMarksEndTheTitleLineAndThePaneSaysWatchedNotACount() {
        show(entries = listOf(
            recording(1, "Half seen", "half.ts"),
            recording(2, "Seen", "seen.ts", playCount = 147),
            recording(3, "New", "new.ts"),
        ))
        focused("recording-list-entry-1")
        assertWatchMark(1, "Partly watched")
        assertWatchMark(2, "Watched")
        compose.onNodeWithTag("recording-list-watched-3", useUnmergedTree = true).assertDoesNotExist()
        // 1:02:03 into 90 minutes leaves 27:57, rounded up, ending that long from now.
        assertTimeLeft("28m left • Ends at ")
        capture("archive-watch-partly", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-2")
        compose.onNodeWithTag("recording-metadata-watched", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-time-left", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("147", substring = true).assertDoesNotExist()
        capture("archive-watch-seen", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-3")
        compose.onNodeWithTag("recording-metadata-watched", useUnmergedTree = true).assertDoesNotExist()
        assertTimeLeft("Ends at ")
        compose.onNodeWithText("left", substring = true).assertDoesNotExist()
        capture("archive-watch-marks", 1f)
        assertNoCommands()
    }

    private fun assertTimeLeft(start: String) {
        compose.onNode(hasAnyAncestor(hasTestTag("recording-metadata-time-left")) and hasText(start, substring = true),
            useUnmergedTree = true).assertIsDisplayed()
    }

    private fun publish(entries: List<DvrEntry>, awaitPreparation: Boolean = true) {
        compose.runOnIdle {
            session.publish(session.observation.value.let {
                SessionObservation.create(it.sessionState, it.channelState, it.epgState,
                    DvrRepositoryState.Current(DvrSnapshot.create(entries)),
                    recordingProgressCapability = it.recordingProgressCapability,
                    dvrDiskSpaceState = it.dvrDiskSpaceState)
            })
        }
        if (awaitPreparation) compose.waitForIdle()
    }

    private fun show(scale: Float = 1f, empty: Boolean = false, entries: List<DvrEntry>? = null, endPadding: Dp? = null,
        diskSpace: DvrDiskSpaceState = DvrDiskSpaceState.Unknown, channels: List<Channel> = emptyList(), snapshotLoaded: Boolean = true,
        shellBackEnabled: Boolean = false) {
        endPaddingState.value = endPadding
        val recordings = entries ?: if (empty) emptyList() else listOf(
            recording(1, LONG_TITLE, "Documentaries/northern.ts"),
            recording(2, "Tide Watch", "Documentaries/Expeditions/blue.ts"),
            recording(3, "Ridge Light", "Documentaries/mountains.ts"),
            recording(4, "Northline Tonight", "News/report.ts"),
            recording(5, "Harbor Lights Live", "concert.ts"),
            recording(20, "Tomorrow's documentary", null, DvrEntryState.SCHEDULED),
            recording(21, "Unfinished expedition", null, DvrEntryState.COMPLETED_ERROR),
        )
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
            )),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels, emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = if (snapshotLoaded) DvrRepositoryState.Current(DvrSnapshot.create(recordings)) else SessionObservation.create().dvrState,
            dvrDiskSpaceState = diskSpace,
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )
        session = FakeTvheadendSession(observation)
        val currentObservation = session.observation.value
        if (snapshotLoaded) assertNotNull("Exercise live capability/session guards, not cached-only rows", currentObservation.currentSession)
        val actions = DvrMutationActions(session.dvrRepository)
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(0L, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            val liveObservation by session.observation.collectAsState()
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, scale),
                LocalBrowsePreparationContext provides preparationContext.value,
            ) {
                TVHeadendPlayerTheme {
                    var shellBack by androidx.compose.runtime.remember { mutableStateOf<() -> Unit>({}) }
                    androidx.activity.compose.BackHandler(enabled = shellBackEnabled) { shellBack() }
                    view = LocalView.current
                    backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(
                            rootRoute = AppDestination.HOME, availableDestinations = setOf(AppDestination.HOME, AppDestination.CHANNELS, AppDestination.GUIDE, AppDestination.RECORDINGS, AppDestination.SETTINGS),
                            currentRoute = railRoute.value,
                            onRootBack = { navigationRequests++ },
                            onNavigate = { navigationRequests++ },
                            onBackHandlerChanged = { shellBack = it },
                        ) { padding, drawer ->
                            val direction = LocalLayoutDirection.current
                            if (screenShown.value) RecordingsScreenContent(
                                observation = liveObservation,
                                currentObservation = { session.observation.value },
                                state = screenState,
                                contentPadding = endPaddingState.value?.let {
                                    PaddingValues(start = padding.calculateStartPadding(direction),
                                        top = padding.calculateTopPadding(), end = it,
                                        bottom = padding.calculateBottomPadding())
                                } ?: padding,
                                initialFocusEnabled = !drawer,
                                backEnabled = !drawer,
                                connectionUiState = connectionState.value,
                                dvrMutationActions = actions,
                                notices = notices,
                                onRetry = { error("Unexpected retry") },
                                onPlayRecording = { _, _ -> playbackRequests++ },
                                view = startView,
                                onViewChange = { viewChanges += it },
                                nowSecProvider = nowSeconds,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun recording(id: Long, title: String, path: String?, state: DvrEntryState = DvrEntryState.COMPLETED,
        startEpochSeconds: Long = 1_791_000_000 - id * 86400, recordedMinutes: Long? = null, playCount: Long? = null) =
        DvrEntry.create(
            id = DvrEntryId(id), title = title, state = state,
            folder = path?.split('/')?.filter(String::isNotEmpty)?.dropLast(1),
            start = Instant.fromEpochSeconds(startEpochSeconds),
            stop = Instant.fromEpochSeconds(startEpochSeconds + 90 * 60),
            files = path?.let { listOf(DvrRecordingFile.create(
                start = recordedMinutes?.let { Instant.fromEpochSeconds(startEpochSeconds) },
                stop = recordedMinutes?.let { Instant.fromEpochSeconds(startEpochSeconds + it * 60) },
                sizeBytes = 2_400_000_000L)) }.orEmpty(),
            channelName = "Ridge Earth HD",
            image = if (id in 1L..5L) ArtworkId(100 + id.toInt()) else null,
            subtitle = if (id == 1L) "A winter expedition beyond the Arctic Circle" else null,
            description = "Follow a small team of naturalists through remote landscapes as they explore " +
                "the changing seasons, meet local communities and discover wildlife at the edge of the world.",
            playPosition = if (id == 1L) 3723.seconds else null,
            playCount = playCount,
        )

    private fun key(key: Key) {
        if (key == Key.Back) compose.runOnIdle { backDispatcher.onBackPressed() }
        else compose.pressKey(key)
        compose.waitForIdle()
    }

    private fun tabFocused() = compose.onAllNodes(SemanticsMatcher("Mode tab") {
        it.config.getOrNull(SemanticsProperties.TestTag).orEmpty().let { tag -> tag.startsWith("recordings-mode-") && tag != "recordings-mode-tabs" }
    } and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun tagFocused(tag: String) =
        compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun listRowFocused() = compose.onAllNodes(SemanticsMatcher("List row") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        tag.startsWith("recording-list-entry-") || tag.startsWith("recordings-folder-") ||
            tag == "recordings-recording-now-folder"
    } and isFocused()).fetchSemanticsNodes().isNotEmpty()

    /** Up from the first list row reaches the options button. */
    private fun upToOptions() {
        repeat(5) { if (!tagFocused(OPTIONS)) key(Key.DirectionUp) }
        focused(OPTIONS)
    }

    /** From the button or the list, the options panel's [entry] level ("group", "sort", "show"). */
    private fun openOptionsLevel(entry: String) {
        upToOptions()
        key(Key.DirectionCenter)
        focused("recordings-view-group")
        repeat(listOf("group", "sort", "show").indexOf(entry)) { key(Key.DirectionDown) }
        focused("recordings-view-$entry")
        key(Key.DirectionCenter)
    }

    /** Back from a level to the panel's root and Back again closes it, focus back on the button. */
    private fun closeOptions() {
        key(Key.Back)
        if (compose.onAllNodesWithTag("recordings-view-panel").fetchSemanticsNodes().isNotEmpty()) key(Key.Back)
        gone("recordings-view-panel")
        focused(OPTIONS)
    }

    /** The options button's fill beside its glyph: quiet or filled. */
    private fun optionsFill(): Int {
        val map = compose.onNodeWithTag(OPTIONS).captureToImage().toPixelMap()
        return map[map.width / 6, map.height / 2].toArgb()
    }

    /** The running recording's dot sits on the folder's 32 dp icon or logo, not after its title. */
    private fun assertFolderDotOnIcon(folder: String) {
        val dot = compose.onNodeWithTag("recordings-folder-running-$folder", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val row = bounds("recordings-folder-$folder")
        val density = view.resources.displayMetrics.density
        assertTrue("The dot sits on the 32 dp icon (${dot.width} px)", dot.width in 32f * density - 1f..32f * 1.05f * density + 1f)
        assertTrue("The dot is at the row's start", dot.left - row.left < 40f * density)
    }

    /** Reopens the screen on its Schedule tab, as Home's upcoming card does. */
    private fun openScheduleScreen() {
        compose.runOnIdle { screenShown.value = false }
        compose.waitForIdle()
        screenState = RecordingsScreenState(at.bernhardberger.tvhplayer.core.DvrLibraryMode.SCHEDULE)
        compose.runOnIdle { screenShown.value = true }
        compose.mainClock.advanceTimeUntil { tabFocused() || listRowFocused() }
        compose.waitForIdle()
        gone(OPTIONS)
    }

    /** As [openScheduleScreen], then Up from the first entry to the tabs. */
    private fun openScheduleSubnav() {
        openScheduleScreen()
        if (!tabFocused()) key(Key.DirectionUp)
        assertTrue(tabFocused())
    }

    private fun downToList() {
        key(Key.DirectionDown)
        awaitList()
    }

    private fun awaitList() {
        compose.mainClock.advanceTimeUntil { listRowFocused() }
        compose.waitForIdle()
    }

    private fun focused(tag: String) {
        compose.mainClock.advanceTimeUntil {
            compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
    }

    /** Waits for a panel page to finish leaving: its fade keeps it composed for a moment. */
    private fun gone(tag: String) {
        compose.mainClock.advanceTimeUntil { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.tagBounds(tag)

    private fun headingLeft() = compose.onAllNodesWithTag("archive-level-heading")[0].fetchSemanticsNode().boundsInRoot.left
    private fun headlineToFirstRowGap(): Float {
        val heading = compose.onAllNodesWithTag("archive-level-heading").fetchSemanticsNodes()
            .minBy { it.boundsInRoot.left }.boundsInRoot
        return (visibleListRows().first().top - heading.bottom) / 2f
    }

    private fun assertTrailingStateMark(id: Int, description: String) {
        compose.onNodeWithTag("recording-list-state-$id", useUnmergedTree = true)
            .assertIsDisplayed().assertContentDescriptionEquals(description)
        // At the right end of the title line, so the overline and supporting line keep their full width.
        val mark = bounds("recording-list-state-$id")
        val title = bounds("recording-list-headline-$id")
        assertTrue(mark.left > title.right)
        assertTrue((mark.top + mark.bottom) / 2 in title.top..title.bottom)
        compose.onNode(hasTestTag("recording-list-metadata-$id") and hasText(description, substring = true),
            useUnmergedTree = true).assertDoesNotExist()
    }

    private fun assertWatchMark(id: Int, description: String) {
        compose.onNodeWithTag("recording-list-watched-$id", useUnmergedTree = true)
            .assertIsDisplayed().assertContentDescriptionEquals(description)
        val mark = bounds("recording-list-watched-$id")
        val title = bounds("recording-list-headline-$id")
        assertTrue(mark.left > title.right)
        assertTrue((mark.top + mark.bottom) / 2 in title.top..title.bottom)
    }

    private fun assertOverlineAboveTitle(id: Long) {
        val title = bounds("recording-list-headline-$id")
        val metadata = bounds("recording-list-metadata-$id")
        val leading = bounds("recording-list-leading-$id")
        assertEquals(title.left, metadata.left, .1f)
        assertTrue(metadata.bottom <= title.top)
        assertTrue(leading.right < title.left)
        val row = bounds("recording-list-entry-$id")
        assertTrue(metadata.top >= row.top)
        compose.onAllNodesWithTag("recording-list-supporting-$id", useUnmergedTree = true).fetchSemanticsNodes()
            .forEach { supporting ->
                assertEquals(title.left, supporting.boundsInRoot.left, .1f)
                assertTrue(title.bottom <= supporting.boundsInRoot.top)
                assertTrue(supporting.boundsInRoot.bottom <= row.bottom)
            }
    }

    /** The row in Archive's first column, the pane where Archive shows its preview, level with the first row. */
    private fun assertArchiveColumns(id: Long, archiveRow: Rect, paneTitle: String) {
        val row = bounds("recording-list-entry-$id")
        assertEquals(archiveRow.left, row.left, .5f)
        assertEquals(archiveRow.width, row.width, .5f)
        val pane = bounds("recording-metadata-pane")
        assertEquals(row.left + 340f + 92f, pane.left, .5f)
        assertEquals(row.top, pane.top, .5f)
        assertPaneTitle(paneTitle)
    }

    private fun assertPaneTitle(title: String) {
        compose.onNode(hasTestTag("recording-metadata-title") and hasText(title), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /**
     * The pane's [title] and [lines] show and end inside the pane and the screen; the lines' text is
     * whole, while the title may give way to an ellipsis as on every pane.
     */
    private fun assertPaneLinesWhole(title: String, vararg lines: String) {
        val pane = bounds("recording-metadata-pane")
        (listOf(title) + lines).forEach { tag ->
            val rect = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $rect ends inside the pane $pane", rect.bottom <= pane.bottom + .5f && rect.right <= pane.right + .5f)
            assertTrue("$tag $rect stays on screen", rect.right <= 960f)
        }
        lines.forEach { tag ->
            val texts = compose.onAllNodes((hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            val count = texts.fetchSemanticsNodes().size
            assertTrue("$tag has text", count > 0)
            repeat(count) { index ->
                val results = mutableListOf<TextLayoutResult>()
                texts[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertFalse("$tag '${layout.layoutInput.text}' must stay whole", layout.hasVisualOverflow)
            }
        }
    }

    private fun paneEntries(long: Boolean): List<DvrEntry> {
        val start = 1_791_086_400L
        val title = if (long) "Die außergewöhnliche Reise der Polarlichter: Eine Winterexpedition durch die Arktis"
            else "Coastal Final"
        val channel = if (long) "Ridge Earth Mountain Channel HD Extra" else "Ridge Earth HD"
        return listOf(
            recording(1, "Kept match", "Sport/kept.ts"),
            DvrEntry.create(DvrEntryId(40), title = title, state = DvrEntryState.SCHEDULED,
                start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(start + 90 * 60),
                startExtraMinutes = 2.minutes, stopExtraMinutes = 15.minutes,
                autorecRuleId = AutorecRuleId("series"), channelName = channel,
                subtitle = if (long) "Zweiter Teil: Über die Gletscher bis an den Rand der Welt" else "Second leg",
                description = "Follow a small team of naturalists through remote landscapes."),
            DvrEntry.create(DvrEntryId(41), title = "Evening news", state = DvrEntryState.SCHEDULED,
                start = Instant.fromEpochSeconds(start + 7_200), stop = Instant.fromEpochSeconds(start + 9_000),
                timerecRuleId = TimerecRuleId("timer"), channelName = channel),
            DvrEntry.create(DvrEntryId(60), title = title, state = DvrEntryState.COMPLETED_ERROR,
                start = Instant.fromEpochSeconds(start - 86_400), stop = Instant.fromEpochSeconds(start - 86_400 + 5_400),
                subscriptionError = DvrSubscriptionError.NO_DISK_SPACE, dataSizeBytes = 1_200_000_000L,
                channelName = channel, subtitle = if (long) "Zweiter Teil: Über die Gletscher" else null),
            DvrEntry.create(DvrEntryId(61), title = "Missed match", state = DvrEntryState.MISSED,
                start = Instant.fromEpochSeconds(start - 2 * 86_400), stop = Instant.fromEpochSeconds(start - 2 * 86_400 + 5_400),
                channelName = channel),
        )
    }

    private fun assertGrowthInside(row: Rect, list: Rect) {
        // Surface's own semantics stay at layout size; descendants/drawing carry its scale.
        val horizontalGrowth = row.width * .025f
        val verticalGrowth = row.height * .025f
        assertTrue(row.left - horizontalGrowth >= list.left && row.right + horizontalGrowth <= list.right)
        assertTrue(row.top - verticalGrowth >= list.top && row.bottom + verticalGrowth <= list.bottom)
    }

    private fun visibleListRows(): List<Rect> = compose.onAllNodes(SemanticsMatcher("Recording or folder row") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        (tag.startsWith("recording-list-entry-") || tag.startsWith("recordings-folder-") ||
            tag == "recordings-recording-now-folder") && !tag.substringAfter("recordings-folder-").startsWith("running-") &&
            !tag.contains("-logo-") && !tag.contains("-watched-") && !tag.contains("-supporting-")
    }).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }

    private fun assertDenseListGeometry() {
        val rows = visibleListRows().filter { it.height > 63.5f }
        assertEquals(6, rows.size)
        rows.forEachIndexed { index, row ->
            assertEquals(rows.first().top + index * 68f, row.top, .5f)
            assertEquals(64f, row.height, .5f)
        }
        rows.zipWithNext().forEach { (previous, next) -> assertEquals(4f, next.top - previous.bottom, .5f) }
    }

    private fun assertMiddleFocus(id: Long, checkNeighbourClearance: Boolean = true): String {
        val row = bounds("recording-list-entry-$id")
        val rows = visibleListRows()
        val previous = rows.last { it.top < row.top }
        val next = rows.first { it.top > row.top }
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val x = (row.left + 8f).toInt()
                val ys = (row.top.toInt() - 8..row.bottom.toInt() + 8).filter { y ->
                    val pixel = bitmap.getPixel(x, y)
                    android.graphics.Color.red(pixel) >= 180 && android.graphics.Color.green(pixel) >= 180 &&
                        android.graphics.Color.blue(pixel) >= 180
                }
                assertTrue("Native middle-row focus is visible", ys.isNotEmpty())
                val top = ys.first().toFloat()
                val bottom = (ys.last() + 1).toFloat()
                assertEquals("Complete native 1.05 focus height", row.height * 1.05f, bottom - top, 2f)
                assertTrue("Native focus grows above the row", top < row.top)
                assertTrue("Native focus grows below the row", bottom > row.bottom)
                if (checkNeighbourClearance) {
                    assertTrue("Focus must not overlap previous row", top >= previous.bottom)
                    assertTrue("Focus must not overlap next row", bottom <= next.top)
                }
                "middleFocus=$row; nativeFocusY=$top..$bottom; previous=$previous; next=$next; " +
                    "clearanceAbove=${top - previous.bottom}; clearanceBelow=${next.top - bottom}"
            } finally {
                bitmap.recycle()
            }
        }
    }

    private class Heading(val text: String, val bounds: Rect, val baseline: Float)

    /**
     * The active level's heading with its baseline in the root. The preview column clears its
     * semantics, so its heading is measured in the captures' ink, not here.
     */
    private fun activeHeading(): Heading {
        val previewLeft = bounds("depth-preview").left
        return compose.onAllNodesWithTag("archive-level-heading").fetchSemanticsNodes().map { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.first()
            val box = node.boundsInRoot
            Heading(layout.layoutInput.text.text, box, box.top + layout.firstBaseline * box.height / layout.size.height)
        }.filter { it.bounds.width > 0f && it.bounds.left >= 0f && it.bounds.left < previewLeft - 1f }
            .maxBy { it.bounds.left }
    }

    /** The active level's values line, if it has one; earlier levels keep theirs off-screen. */
    private fun valuesLine(): SemanticsNodeInteraction? {
        val previewLeft = bounds("depth-preview").left
        val nodes = compose.onAllNodesWithTag("archive-values-line", useUnmergedTree = true)
        val index = nodes.fetchSemanticsNodes().withIndex()
            .filter { (_, it) -> it.boundsInRoot.width > 0f && it.boundsInRoot.left >= 0f && it.boundsInRoot.left < previewLeft }
            .maxByOrNull { it.value.boundsInRoot.left }?.index ?: return null
        return nodes[index]
    }

    /** The first visible row of the list column; the preview column's rows carry no semantics. */
    private fun firstListRow(): Rect {
        val previewLeft = bounds("depth-preview").left
        return visibleListRows().filter {
            it.width > 100f && it.bottom > 0f && it.left < previewLeft && it.left > bounds("recordings-header").left - 1f
        }.minBy { it.top }
    }

    /** The first line below [fromY] with ink in the 40 dp band where a column starting at [left] draws its row icons. */
    private fun firstIconInk(left: Float, fromY: Float): Float = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        fun brightness(x: Int, y: Int) =
            android.graphics.Color.valueOf(bitmap.getPixel(x, y)).run { maxOf(red(), green(), blue()) }
        val background = brightness(view.width - 4, view.height - 4)
        val band = left.toInt() until left.toInt() + (40f * view.resources.displayMetrics.density).toInt()
        val first = (fromY.toInt() + 1 until view.height).first { y -> band.any { x -> brightness(x, y) > background + .15f } }
        bitmap.recycle()
        first.toFloat()
    }

    /** The button ends where the rows do and keeps clear air above the row grown by its focus. */
    private fun assertButtonClearOf(row: Rect) {
        val button = bounds(OPTIONS)
        assertEquals("The button ends with the rows", row.right, button.right, 1f)
        val gap = row.top - row.height * .025f - button.bottom
        assertTrue("The button keeps clear of the focused first row ($gap px)", gap >= 4f * view.resources.displayMetrics.density)
    }

    /** The header's measured bounds for a frame's evidence file, in pixels. */
    private fun headerReport(): String {
        fun r(rect: Rect) = "%.1f,%.1f-%.1f,%.1f".format(rect.left, rect.top, rect.right, rect.bottom)
        fun tagged(tag: String) = runCatching {
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }.getOrNull()
        return buildList {
            add("tabs=${tagged("recordings-mode-tabs")?.let(::r)}")
            add("storage=${tagged("recordings-space")?.let(::r)} ring=${tagged("recordings-space-ring")?.let(::r)} " +
                "text=${tagged("recordings-space-text")?.let(::r)}")
            add("button=${tagged(OPTIONS)?.let(::r)}")
            add("values=${runCatching { valuesLine() }.getOrNull()?.fetchSemanticsNode()?.boundsInRoot?.let(::r)}")
            runCatching { activeHeading() }.getOrNull()?.let { active ->
                add("headline='${active.text}' ${r(active.bounds)} baseline=${active.baseline}")
            }
            runCatching { firstListRow() }.getOrNull()?.let { list ->
                add("firstListRow=${r(list)}")
                tagged(OPTIONS)?.let { add("buttonToFocusedRowGap=${list.top - list.height * .025f - it.bottom}") }
            }
        }.joinToString("\n")
    }

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertNoCommands() = compose.runOnIdle {
        assertEquals(0, playbackRequests)
        assertEquals(0, navigationRequests)
        assertTrue("Browsing/details must not dispatch any SDK command", session.calls.isEmpty())
    }

    /**
     * The first line from the top of the preview's list band with ink where its rows' folder icons
     * are, and the brightest ink of the row it starts.
     */
    private fun previewFirstInk(): Pair<Float, Float> {
        val preview = bounds("depth-preview")
        // The preview's list starts level with the root's, whose first row is the Sports folder.
        val listTop = bounds("recordings-folder-Sports").top - 4f
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            fun brightness(x: Int, y: Int) =
                android.graphics.Color.valueOf(bitmap.getPixel(x, y)).run { maxOf(red(), green(), blue()) }
            val background = brightness(preview.right.toInt() - 4, preview.bottom.toInt() - 4)
            val icons = preview.left.toInt() until preview.left.toInt() + 80
            val first = (listTop.toInt() until preview.bottom.toInt()).first { y ->
                icons.any { x -> brightness(x, y) > background + .15f }
            }
            val peak = (first until first + 24).maxOf { y -> icons.maxOf { x -> brightness(x, y) } }
            bitmap.recycle()
            first.toFloat() to peak
        }
    }

    /** Luminance of [area] in the frame as drawn now, row by row. */
    private fun luminance(area: Rect): FloatArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val left = area.left.toInt()
        val top = area.top.toInt()
        val width = area.width.toInt()
        val height = area.height.toInt()
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left.coerceAtLeast(0), top, width, height)
        bitmap.recycle()
        return FloatArray(pixels.size) { pixels[it].let { c ->
            0.299f * (c shr 16 and 0xff) + 0.587f * (c shr 8 and 0xff) + 0.114f * (c and 0xff) } }
    }

    /**
     * Where [template], drawn at [home], now stands on the same rows at or left of home: the shift and its
     * normalised correlation, which a fade over the plain background does not change.
     */
    private fun trackHorizontally(template: FloatArray, home: Rect): Pair<Float, Float> {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val width = home.width.toInt()
        val height = home.height.toInt()
        val band = IntArray(view.width * height)
        bitmap.getPixels(band, 0, view.width, 0, home.top.toInt(), view.width, height)
        bitmap.recycle()
        val lum = FloatArray(band.size) { band[it].let { c ->
            0.299f * (c shr 16 and 0xff) + 0.587f * (c shr 8 and 0xff) + 0.114f * (c and 0xff) } }
        val mean = template.average().toFloat()
        val t = FloatArray(template.size) { template[it] - mean }
        val tNorm = kotlin.math.sqrt(t.sumOf { (it * it).toDouble() }).toFloat()
        var best = 0 to -1f
        // The root column only ever moves left of its home.
        for (x in 0..minOf(view.width - width, home.left.toInt())) {
            var sum = 0f
            for (y in 0 until height) for (i in 0 until width) sum += lum[y * view.width + x + i]
            val m = sum / (width * height)
            var dot = 0f
            var norm = 0f
            for (y in 0 until height) for (i in 0 until width) {
                val v = lum[y * view.width + x + i] - m
                dot += v * t[y * width + i]
                norm += v * v
            }
            val score = if (norm == 0f) 0f else dot / (kotlin.math.sqrt(norm) * tNorm)
            if (score > best.second) best = x to score
        }
        return (best.first - home.left) to best.second
    }

    private fun capture(name: String, scale: Float, focusEvidence: String? = null,
        outputs: String = "build/outputs/browse-cohesion/archive-depth") {
        if (!CaptureOutput.enabled) return
        assertNoCommands()
        val rows = visibleListRows()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val density = view.resources.displayMetrics.density
            assertEquals((960 * density).toInt(), bitmap.width)
            assertEquals((540 * density).toInt(), bitmap.height)
            val directory = File(outputs).apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "Production RecordingsScreenContent + SideRail; 960x540dp at density $density; ${view.resources.configuration.locales[0].toLanguageTag()}; UTC; fontScale=$scale\n" +
                    "Robolectric SDK34 native View.draw; native D-pad focus; fixed recording dates; absent artwork\n" +
                    "Opaque theme background, no video; SDK/playback/navigation calls=0\n" +
                    "Static composition only, not physical-TV focus feel, overscan, motion or SurfaceView proof.\n" +
                    "rows=$rows\n" +
                    (focusEvidence?.let { "$it\n" } ?: ""),
            )
            bitmap.recycle()
        }
    }

    private companion object {
        const val HEADER_CAPTURES = "build/outputs/recordings-header-final"
        const val LABEL_CAPTURES = "build/outputs/list-label-spacing"
        const val OPTIONS = "recordings-view-options"
        const val BROWSE_TAB = "recordings-mode-archive"
        const val LONG_TITLE = "Ridge Light: The Remarkable Journey of the Northern Lights Across the Arctic"
    }
}
