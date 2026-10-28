package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext

import android.app.Application
import at.bernhardberger.tvhplayer.testutil.FixtureArt
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule

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
    private val screenState = RecordingsScreenState()
    // The app keeps this state while the player route replaces the Recordings entry.
    private val screenShown = mutableStateOf(true)
    private val endPaddingState = mutableStateOf<Dp?>(null)
    private val connectionState = mutableStateOf<ConnectionUiState>(ConnectionUiState.Ready)

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
        assertEquals(130f, bounds("recordings-header").left, .1f)
        assertEquals(28f, bounds("recordings-header").top, .1f)
        assertEquals(902f, bounds("recordings-header").right, .1f)
        assertEquals(540f, bounds("recordings-archive-list").bottom, .1f)
        // The tabs are the header row's leading content, centred in it, with the list area below.
        assertEquals(130f, bounds("recordings-mode-tabs").left, .1f)
        assertEquals(bounds("recordings-header").center.y, bounds("recordings-mode-tabs").center.y, .5f)
        assertEquals("content starts at y80, as the channel list does", 80f, bounds("recordings-archive-list").top, .1f)
        val grown = bounds("recordings-folder-Documentaries")
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
    fun presentationFolders() {
        presentationLibrary()
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        capture("recordings-folders", 1f, outputs = "build/outputs/presentation")
    }

    /** Eight recordings of the README evening's channels, with their logos, focused on the Documentaries folder. */
    private fun presentationLibrary() {
        assertTrue(android.os.SystemClock.setCurrentTimeMillis(1_791_144_900_000L))
        val evening = 1_791_057_600L
        // The channels as Channels and the player number them; each logo is the channel's fixture picon.
        val picons = listOf("ridge-earth", "harbor-sport", "northline-news", "kite-kids", "ion-frame", "amber-stage", "harbor-lights")
        val channels = listOf("Ridge Earth", "Harbor Sport", "Northline News", "Kite Kids", "Ion Frame", "Amber Stage", "Harbor Lights")
            .mapIndexed { index, name -> Channel.create(ChannelId(index + 1L), name = name, number = index + 1L, icon = ArtworkId(index + 1)) }
        fun recording(id: Long, title: String, channel: Int, folder: String?, start: Long, minutes: Long,
            art: String?, synopsis: String, position: Long? = null, plays: Long? = null,
            state: DvrEntryState = DvrEntryState.COMPLETED) = DvrEntry.create(
            DvrEntryId(id), title = title, state = state, folder = listOfNotNull(folder),
            start = Instant.fromEpochSeconds(start), stop = Instant.fromEpochSeconds(start + minutes * 60),
            files = listOf(DvrRecordingFile.create(start = Instant.fromEpochSeconds(start),
                stop = Instant.fromEpochSeconds(start + minutes * 60), sizeBytes = minutes * 40_000_000L)),
            channelId = ChannelId(channel.toLong()), channelName = channels[channel - 1].name,
            image = art?.let { ArtworkId(100 + id.toInt()) }, description = synopsis,
            subtitle = "Winter on the ridge".takeIf { id == 1L }, playPosition = position?.seconds, playCount = plays)
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
                "A quiet drama about night-shift workers in a harbor glass workshop."),
            recording(7, "Harbor Lights Live", 7, null, evening - 86400, 90, "harbor-lights-live",
                "A small-hall concert with a quartet."),
            recording(8, "Paper Kite Tales", 4, null, evening - 2 * 86400 - 7200, 30, "paper-kite-tales",
                "2D animation about a paper kite over painted hills.", plays = 1),
            // Tonight's match, recording now as in the Guide.
            recording(9, "Harbor Kickoff", 2, "Sport", evening + 86400 - 1800, 135, "harbor-kickoff",
                "Match coverage from a floodlit stadium as Harbor FC meet Amber Town.", state = DvrEntryState.RECORDING),
        )
        val art = listOf("ridge-light", "still-tide-watch", "still-coastal-miles", "harbor-kickoff", "river-court",
            "glass-harbor", "harbor-lights-live", "paper-kite-tales", "harbor-kickoff")
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), buildMap {
            picons.forEachIndexed { index, name -> put(ArtworkId(index + 1), FixtureArt.picon(name)) }
            art.forEachIndexed { index, name -> put(ArtworkId(101 + index), FixtureArt.art(name)) }
        }))
        show(entries = library, channels = channels)
        compose.mainClock.advanceTimeBy(1_500)
        // The running match's Recording now folder comes first, then Sport, which holds it, then Documentaries.
        focused("recordings-recording-now-folder")
        key(Key.DirectionDown)
        focused("recordings-folder-Sport")
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
    }

    @Test fun recordingSpaceLineIsQuietInformationInTheHeader() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        show(diskSpace = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L)))
        val expected = "${formatFileSize(412_000_000_000L, context)} free of ${formatFileSize(2_000_000_000_000L, context)}"
        compose.onNodeWithTag("recordings-space").assertTextEquals(expected)
        compose.onNodeWithTag("recordings-space").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
        // It ends the tabs' row, level with them and clear of them.
        assertEquals(bounds("recordings-mode-tabs").center.y, bounds("recordings-space").center.y, .5f)
        assertTrue(bounds("recordings-space").left >= bounds("recordings-mode-tabs").right)
        assertEquals(902f, bounds("recordings-space").right, .5f)
        assertEquals(130f, bounds("recordings-header").left, .1f)
        assertEquals(902f, bounds("recordings-header").right, .1f)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
    }

    @Test fun recordingSpaceLineIsAbsentWithoutAFigure() {
        show(diskSpace = DvrDiskSpaceState.Synchronizing(null))
        compose.onNodeWithTag("recordings-space").assertDoesNotExist()
        focused("recordings-folder-Documentaries")
        capture("header-no-space", 1f)
    }

    @Test fun recordingSpaceLineGivesWayToTheTabs() {
        show(diskSpace = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L)))
        focused("recordings-folder-Documentaries")
        val tabs = bounds("recordings-mode-tabs")
        val full = bounds("recordings-space").width
        assertTrue("the fixture's text must be wider than the hiding threshold ($full)", full > 120f)
        // The header row runs from x130 to the trailing inset; its actions start 12dp after the tabs.
        fun leaveForSpace(width: Float) = compose.runOnIdle {
            endPaddingState.value = (960f - tabs.right - 12f - width).dp
        }
        leaveForSpace((full + 96f) / 2f)
        compose.waitForIdle()
        assertTrue("narrower than its text, it ellipsizes", textLayout("recordings-space").hasVisualOverflow)
        assertTrue(bounds("recordings-space").left >= tabs.right)
        assertEquals("the tabs keep their width", tabs, bounds("recordings-mode-tabs"))
        leaveForSpace(80f)
        compose.waitForIdle()
        compose.onNodeWithTag("recordings-space").assertDoesNotExist()
        assertEquals("the tabs keep their width", tabs, bounds("recordings-mode-tabs"))
        listOf("Archive", "Schedule", "Problems").forEach {
            val label = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(it, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { layout -> layout(label) }
            assertTrue("$it is not shortened", !label.single().hasVisualOverflow)
        }
        focused("recordings-folder-Documentaries")
    }

    @Test fun upFromEachTabsFirstItemReachesThatTabAndDownReturnsToTheList() {
        show(diskSpace = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L)))
        focused("recordings-folder-Documentaries")
        capture("header-archive", 1f)
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        capture("header-archive-tab-focused", 1f)
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionUp)
        listOf("Schedule" to 20, "Problems" to 21).forEach { (tab, first) ->
            key(Key.DirectionRight)
            compose.onNodeWithText(tab).assertIsFocused()
            key(Key.DirectionDown)
            focused("recording-list-entry-$first")
            capture("header-${tab.lowercase().replace(' ', '-')}", 1f)
            // The active tab, not the one above the item.
            key(Key.DirectionUp)
            compose.onNodeWithText(tab).assertIsFocused()
        }
        // The disk-space text takes no focus: the row ends at Problems.
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        assertNoCommands()
    }

    /**
     * Inside a folder the parent's folder arrows peek out at the screen edge beside the rail.
     * They stay visible but much quieter than the preview, so they do not read as the rail's.
     */
    @Test fun parentArrowsBesideTheRailStayQuieterThanThePreview() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recordings-folder-Documentaries/Expeditions")
        compose.waitForIdle()
        val (parent, preview, background) = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            fun brightest(xs: IntRange, ys: IntRange) =
                xs.maxOf { x -> ys.maxOf { y -> android.graphics.Color.valueOf(bitmap.getPixel(x, y)).run { maxOf(red(), green(), blue()) } } }
            Triple(brightest(0..30, 116..246), brightest(562..900, 116..186), brightest(0..30, 420..460))
                .also { bitmap.recycle() }
        }
        assertTrue("the parent's arrows still show ($parent over $background)", parent > background + .05f)
        assertTrue("the parent's arrows are well below the preview ($parent vs $preview)", parent < preview * .3f)
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
        show()
        focused("recordings-folder-Documentaries")
        val archiveRow = bounds("recordings-folder-Documentaries")
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        assertOverlineAboveTitle(20)
        assertArchiveColumns(20, archiveRow, "Tomorrow's documentary")
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
        // Every tab's first heading shares the Archive heading band, so first rows line up.
        val firstRowTop = bounds("recordings-folder-Documentaries").top
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        val archivePane = bounds("recording-metadata-pane")
        capture("archive-multi-item", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-2")
        // Archive spacing is unchanged: record its existing overlap, but still require complete focus growth.
        capture("archive-middle-focus", 1f, assertMiddleFocus(2, checkNeighbourClearance = false))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-40")
        assertDenseListGeometry(firstRowTop)
        // The focused entry's pane takes the place and size of the Archive's recording preview.
        assertEquals(archivePane, bounds("recording-metadata-pane"))
        assertPaneTitle("Scheduled expedition 1")
        capture("schedule-multi-item", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-41")
        assertPaneTitle("Scheduled expedition 2")
        capture("schedule-middle-focus", 1f, assertMiddleFocus(41))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-60")
        assertDenseListGeometry(firstRowTop)
        assertEquals(archivePane, bounds("recording-metadata-pane"))
        assertPaneTitle("Unfinished expedition 1")
        capture("problems-multi-item", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-61")
        assertPaneTitle("Unfinished expedition 2")
        capture("problems-middle-focus", 1f, assertMiddleFocus(61))
        assertNoCommands()
    }

    @Test fun largerCallerEndPaddingIsPreservedWithoutDoublingFocusReserve() {
        show(endPadding = 100.dp)
        focused("recordings-folder-Documentaries")
        val archiveRow = bounds("recordings-folder-Documentaries")
        assertEquals(860f, bounds("recordings-header").right, .1f)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        assertArchiveColumns(20, archiveRow, "Tomorrow's documentary")
        assertTrue(bounds("recording-metadata-pane").right <= 860f)
        // A scheduled row carries its clock at the end, as channel rows do; the overline keeps only times.
        assertTrailingStateMark(20, "Scheduled")
        capture("schedule-end100", 1f)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("recording-list-entry-21")
        assertArchiveColumns(21, archiveRow, "Unfinished expedition")
        assertTrue(bounds("recording-metadata-pane").right <= 860f)
        assertNoCommands()
    }

    @Test fun schedulePaneGivesThePaddedStopAndRuleAndProblemsPaneWhatWentWrong() {
        show(entries = paneEntries(long = false))
        focused("recordings-folder-Sport")
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
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
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
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
        key(Key.DirectionUp)
        key(Key.DirectionRight)
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

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeMetadataRetainsNativeTypographyAndWholeLineEllipsis() {
        Locale.setDefault(Locale.GERMANY)
        show(scale = 1.3f, entries = listOf(recording(1,
            "Die außergewöhnliche Reise der Polarlichter: Eine Winterexpedition durch die Arktis",
            "Dokumentationen/Expedition.ts")))
        focused("recordings-folder-Dokumentationen")
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        assertOverlineAboveTitle(1)
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
        capture("metadata-de-font1.3", 1.3f)
        assertNoCommands()
    }

    @Test fun stoppedEarlyOverlineFitsOneLine() {
        show(entries = listOf(recording(1, "Ridge Light", "Documentaries/ridge.ts", recordedMinutes = 20)))
        focused("recordings-folder-Documentaries")
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        compose.onNode(hasTestTag("recording-list-metadata-1") and hasText("• 20m Stopped early", substring = true),
            useUnmergedTree = true).assertIsDisplayed()
        // The early stop shows as a marker after the length; its words are only for accessibility.
        compose.onNodeWithTag("recording-list-stopped-early", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(1, textLayout("recording-list-metadata-1").lineCount)
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeStoppedEarlyHintStaysWholeInTheRow() {
        Locale.setDefault(Locale.GERMANY)
        show(scale = 1.3f, entries = listOf(recording(1,
            "Die außergewöhnliche Reise der Polarlichter: Eine Winterexpedition durch die Arktis",
            "Dokumentationen/Expedition.ts", recordedMinutes = 20)))
        focused("recordings-folder-Dokumentationen")
        key(Key.DirectionCenter)
        focused("recording-list-entry-1")
        assertOverlineAboveTitle(1)
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasTestTag("recording-list-metadata-1") and
            hasText("Vorzeitig beendet", substring = true), useUnmergedTree = true)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val hint = results.single()
        val shown = hint.layoutInput.text.text.substring(0, hint.getLineEnd(hint.lineCount - 1, visibleEnd = true))
        assertTrue("'$shown' lines=${hint.lineCount} size=${hint.size} c=${hint.layoutInput.constraints}",
            shown.endsWith("20 Min. Vorzeitig beendet"))
        assertFalse("The stopped-early marker must remain whole", hint.hasVisualOverflow)
        // A plain completed recording does not repeat its state.
        compose.onNode(hasTestTag("recording-list-metadata-1") and
            hasText("Abgeschlossen", substring = true), useUnmergedTree = true).assertDoesNotExist()
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
        key(Key.DirectionUp)
        key(Key.DirectionRight)
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

    @Test fun missedRecordingPaneBadgeSaysMissedInTheErrorTone() {
        val entry = recording(30, "Missed match", null, DvrEntryState.MISSED)
        compose.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingMetadataPane(entry, null, null)
            }
        }
        compose.onNodeWithTag("recording-badge-missed", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording missed").assertExists()
        compose.onNodeWithText("recorded", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Canceled", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun invalidRecordingPaneBadgeSaysCanceledInTheNeutralTone() {
        val entry = recording(31, "Dropped entry", null, DvrEntryState.INVALID)
        compose.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingMetadataPane(entry, null, null)
            }
        }
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
                channelName = "Ridge Earth HD", subtitle = "Second half"),
            DvrEntry.create(DvrEntryId(31), title = "Weak signal", state = DvrEntryState.RECORDING_ERROR,
                start = Instant.fromEpochSeconds(now - 300), stop = Instant.fromEpochSeconds(now + 3_300),
                channelName = "Ridge Earth HD"),
        ))
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-30")
        assertOverlineAboveTitle(30)
        // The overline gives the start and the stop; the state is the dot at the row's end.
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("• until", substring = true),
            useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasTestTag("recording-list-metadata-30") and hasText("Recording", substring = true),
            useUnmergedTree = true).assertDoesNotExist()
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

    @Test fun channelPagingAndInterruptedModeSwitchCannotReactivateArchive() {
        show(entries = (1L..30L).map { recording(it, "Recording $it", "recording$it.ts") } +
            recording(40, "Scheduled", null, DvrEntryState.SCHEDULED))
        focused("recording-list-entry-1")
        key(Key(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN))
        val pagedId = compose.runOnIdle { screenState.archiveNavigation.stack.active.focusedItemId }
        assertNotEquals("recording:1", pagedId)
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key(android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)) }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("Archive").requestFocus()
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(256)
            val saved = compose.runOnIdle { screenState.archiveNavigation.stack.active }
            compose.mainClock.advanceTimeBy(1_200)
            compose.runOnIdle { assertEquals(saved, screenState.archiveNavigation.stack.active) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        focused("recording-list-entry-40")
        assertNoCommands()
    }

    @Test fun emptyLibraryKeepsModeTabsReachableWithoutCommands() {
        show(empty = true)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Archive") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No recordings are available.").assertIsDisplayed()
        capture("empty-archive", 1f)
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText("Schedule").assertIsFocused()
        capture("empty-schedule", 1f)
        assertNoCommands()
    }

    @Test fun loadingShowsTheArchiveSkeletonUnderTheHeaderItKeepsWhenRecordingsArrive() {
        val space = DvrDiskSpaceState.Current(DvrDiskSpace(412_000_000_000L, 1_588_000_000_000L, 2_000_000_000_000L))
        connectionState.value = ConnectionUiState.SyncingChannels
        show(empty = true, diskSpace = space)
        // The loading handoff still parks focus on the tabs; the skeleton takes none.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Archive") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("recordings-skeleton").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
        compose.onNodeWithText("Loading recordings…").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading recordings…").assertIsDisplayed()
        val header = bounds("recordings-header")
        val tabs = bounds("recordings-mode-tabs")
        val spaceLine = bounds("recordings-space")
        val skeleton = bounds("recordings-skeleton")
        capture("loading-skeleton", 1f)
        key(Key.DirectionDown)
        compose.onNodeWithText("Archive").assertIsFocused()

        compose.runOnIdle { connectionState.value = ConnectionUiState.Ready }
        publish(listOf(recording(1, LONG_TITLE, "Documentaries/northern.ts"), recording(5, "Harbor Lights Live", "concert.ts")))
        compose.onNodeWithTag("recordings-skeleton").assertDoesNotExist()
        assertEquals(header, bounds("recordings-header"))
        assertEquals(tabs, bounds("recordings-mode-tabs"))
        assertEquals(spaceLine, bounds("recordings-space"))
        // The skeleton stood in the Archive column's place, under the root's blank heading.
        assertEquals(bounds("recordings-archive-list").top, skeleton.top, .1f)
        assertEquals(bounds("recordings-folder-Documentaries").left, skeleton.left, .1f)
        assertEquals(540f, skeleton.bottom, .1f)
        assertNoCommands()
    }

    @Test fun preparingShowsTheSkeletonWhereTheSyncingLibraryKeepsIt() = preparationSkeletonStaysInPlace(1f)

    @Test fun preparingShowsTheSkeletonWhereTheSyncingLibraryKeepsItAtLargeText() = preparationSkeletonStaysInPlace(1.3f)

    private fun preparationSkeletonStaysInPlace(scale: Float) {
        connectionState.value = ConnectionUiState.SyncingChannels
        show(scale = scale, empty = true)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Archive") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        val syncing = bounds("recordings-skeleton")
        compose.runOnIdle { screenShown.value = false }
        compose.waitForIdle()
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
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browse-preparing", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(syncing, bounds("recordings-skeleton"))
        assertNoCommands()
    }

    @Test fun nestedUpReturnsToTabsAndModeChangesRetainArchiveLocation() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionRight)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionLeft)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries/Expeditions")
        compose.runOnIdle { assertEquals(listOf("Documentaries"), screenState.archivePath) }
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        key(Key.Back)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionDown)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        capture("drawer-expanded", 1f)
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
        compose.onNodeWithText("Archive").assertIsFocused()
        publish(listOf(recording(1, "Remaining", "Documentaries/remaining.ts")))
        compose.waitUntil(10_000) { screenState.archivePath == listOf("Documentaries") }
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-1")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        publish(emptyList())
        compose.onNodeWithTag("nav-recordings").assertIsFocused()
        key(Key.DirectionRight)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Archive") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No recordings are available.").assertIsDisplayed()
        compose.onNodeWithTag("depth-row-depth-unavailable").assertDoesNotExist()
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

    @OptIn(TvheadendTestingApi::class)
    @Test fun recordingNowIsFirstEntersFlatListAndBackRestoresItsRow() {
        show(entries = listOf(
            DvrEntry.create(DvrEntryId(10), title = "Later", state = DvrEntryState.RECORDING,
                folder = listOf("Series", "Season"), start = Instant.fromEpochSeconds(200),
                stop = Instant.fromEpochSeconds(500),
                files = listOf(TvheadendTestResultFactory.recordingFile(serverPath = "Series/Season/later.ts"))),
            recording(11, "Earlier", "Recording now/earlier.ts", DvrEntryState.RECORDING_ERROR, startEpochSeconds = 100),
        ))
        focused("recordings-recording-now-folder")
        compose.onNodeWithText("2 recordings in progress").assertIsDisplayed()
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionDown)
        focused("recordings-recording-now-folder")
        capture("recording-now-root", 1f)
        key(Key.DirectionCenter)
        focused("recording-list-entry-11")
        compose.onNodeWithTag("archive-level-heading").assertTextEquals("Recording now")
        compose.onNodeWithTag("recordings-folder-Series").assertDoesNotExist()
        capture("recording-now-flat", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-10")
        key(Key.Menu)
        compose.onNodeWithText("Details").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("programme-details-page").assertIsDisplayed()
        key(Key.Back)
        focused("recording-list-entry-10")
        key(Key.Back)
        focused("recordings-recording-now-folder")
        key(Key.DirectionDown)
        focused("recordings-folder-Series")
        key(Key.DirectionDown)
        focused("recordings-folder-Recording now")
        key(Key.DirectionCenter)
        focused("recording-list-entry-11")
        key(Key.Back)
        focused("recordings-folder-Recording now")
        assertNoCommands()
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        focused("recordings-recording-now-folder")
        key(Key.DirectionRight)
        // Recording now opens again on the recording it was left on.
        focused("recording-list-entry-10")
        key(Key.DirectionUp)
        focused("recording-list-entry-11")
        key(Key.DirectionDown)
        focused("recording-list-entry-10")
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(1, playbackRequests) }
    }

    @Test fun finishingLastRecordingInsideMetaFolderReturnsFocusToSurvivingRootFolder() {
        val running = recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING)
        show(entries = listOf(running))
        focused("recordings-recording-now-folder")
        compose.onNodeWithText("1 recording in progress").assertIsDisplayed()
        key(Key.DirectionRight)
        focused("recording-list-entry-10")

        publish(listOf(recording(10, "Finished", "Series/Season/growing.ts")))

        focused("recordings-folder-Series")
        compose.onNodeWithTag("recordings-recording-now-folder").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("archive:"), screenState.archiveNavigation.stack.path) }
        key(Key.DirectionRight)
        focused("recordings-folder-Series/Season")
        key(Key.DirectionRight)
        focused("recording-list-entry-10")
        assertNoCommands()
    }

    @Test fun finishingLastRecordingWithContextMenuOpenReturnsToSurvivingRootFolder() {
        show(entries = listOf(recording(10, "Growing", "Series/Season/growing.ts", DvrEntryState.RECORDING)))
        focused("recordings-recording-now-folder")
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
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("archive:"), screenState.archiveNavigation.stack.path) }
        key(Key.Back)
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        focused("recordings-folder-Series")
        compose.onNodeWithTag("recordings-recording-now-folder").assertDoesNotExist()
        assertNoCommands()
    }

    @Test fun disappearingMetaFolderAtRootHandsFocusToNextRow() {
        show(entries = listOf(recording(10, "Growing", "Series/growing.ts", DvrEntryState.RECORDING)))
        focused("recordings-recording-now-folder")

        publish(listOf(recording(10, "Finished", "Series/growing.ts")))

        focused("recordings-folder-Series")
        compose.onNodeWithTag("recordings-recording-now-folder").assertDoesNotExist()
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
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

    private fun publish(entries: List<DvrEntry>) {
        compose.runOnIdle {
            session.publish(session.observation.value.let {
                SessionObservation.create(it.sessionState, it.channelState, it.epgState,
                    DvrRepositoryState.Current(DvrSnapshot.create(entries)),
                    recordingProgressCapability = it.recordingProgressCapability,
                    dvrDiskSpaceState = it.dvrDiskSpaceState)
            })
        }
        compose.waitForIdle()
    }

    private fun show(scale: Float = 1f, empty: Boolean = false, entries: List<DvrEntry>? = null, endPadding: Dp? = null,
        diskSpace: DvrDiskSpaceState = DvrDiskSpaceState.Unknown, channels: List<Channel> = emptyList()) {
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
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(recordings)),
            dvrDiskSpaceState = diskSpace,
            recordingProgressCapability = RecordingProgressCapability.SUPPORTED,
        )
        session = FakeTvheadendSession(observation)
        val currentObservation = session.observation.value
        assertNotNull("Exercise live capability/session guards, not cached-only rows", currentObservation.currentSession)
        val actions = DvrMutationActions(session.dvrRepository)
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(0L, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            val liveObservation by session.observation.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        SideRail(
                            currentRoute = AppDestination.RECORDINGS,
                            showEpgMenu = true,
                            onRootBack = { navigationRequests++ },
                            onNavigate = { navigationRequests++ },
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
        else compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun focused(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsFocused().assertIsDisplayed()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

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
        assertEquals(archiveRow.top, pane.top, .5f)
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
        tag.startsWith("recording-list-entry-") || tag.startsWith("recordings-folder-")
    }).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }

    private fun assertDenseListGeometry(firstRowTop: Float) {
        val rows = visibleListRows().filter { it.height > 63.5f }
        assertEquals(6, rows.size)
        rows.forEachIndexed { index, row ->
            assertEquals(firstRowTop + index * 68f, row.top, .5f)
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

    private fun capture(name: String, scale: Float, focusEvidence: String? = null,
        outputs: String = "build/outputs/browse-cohesion/archive-depth") {
        if (System.getProperty("tvhplayer.writeMotionCaptures") == "false") return
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
        const val LONG_TITLE = "Ridge Light: The Remarkable Journey of the Northern Lights Across the Arctic"
    }
}
