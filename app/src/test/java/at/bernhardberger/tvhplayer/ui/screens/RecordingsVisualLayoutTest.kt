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
import at.bernhardberger.tvhplayer.ui.AppDestination
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.SideRail
import coil3.ImageLoader
import java.io.File
import java.util.Locale
import java.util.TimeZone
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

/** Offline production screen and shell, with real D-pad focus and no server/player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingsVisualLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    private lateinit var session: FakeTvheadendSession
    private lateinit var view: View
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private lateinit var oldLocale: Locale
    private lateinit var oldZone: TimeZone
    private var playbackRequests = 0
    private var navigationRequests = 0
    private val screenState = RecordingsScreenState()

    @Before fun prepare() {
        oldLocale = Locale.getDefault()
        oldZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        loader = FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(
            ArtworkId(101) to FixtureArt.art("ridge-light"),
            ArtworkId(102) to FixtureArt.art("still-tide-watch"),
            ArtworkId(103) to FixtureArt.art("ridge-light"),
            ArtworkId(104) to FixtureArt.art("northline-tonight"),
            ArtworkId(105) to FixtureArt.art("harbor-lights-live"),
        ))
    }

    @After fun release() {
        loader.shutdown()
        Locale.setDefault(oldLocale)
        TimeZone.setDefault(oldZone)
    }

    @Test fun rootAlignmentAndImmediateChildPreviewPromotesOnlyOnEntry() {
        show()
        focused("recordings-folder-Documentaries")
        assertEquals(130f, bounds("recordings-header").left, .1f)
        assertEquals(28f, bounds("recordings-header").top, .1f)
        assertEquals(902f, bounds("recordings-header").right, .1f)
        assertEquals(512f, bounds("recordings-archive-list").bottom, .1f)
        assertEquals(130f, bounds("recordings-mode-tabs").left, .1f)
        assertEquals(76f, bounds("recordings-mode-tabs").top, .1f)
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
        compose.onNodeWithTag("recording-details-panel").assertDoesNotExist()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        key(Key.Back)
        focused("recording-list-entry-2")
        key(Key.DirectionLeft)
        focused("recordings-folder-Documentaries/Expeditions")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
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
        assertMetadataBelowTitle(1)
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("recording-metadata-pane").assertDoesNotExist()
        val previewText = hasText("A winter expedition beyond the Arctic Circle") and
            hasAnyAncestor(hasTestTag("recording-metadata-pane"))
        compose.onNode(previewText).assertDoesNotExist()
        compose.onNode(previewText, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(340f, bounds("recording-metadata-pane").width, .1f)
        assertEquals(562f, bounds("recording-metadata-pane").left, .1f)
        capture("focused-recording-metadata", 1f)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-details-resume").assertIsFocused()
        compose.onNodeWithContentDescription("Resume from 1 hour, 2 minutes, 3 seconds")
            .assertIsDisplayed()
        key(Key.Back)
        focused("recording-list-entry-1")
        key(Key.Back)
        focused("recordings-folder-Documentaries")
        assertNoCommands()
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
        assertMetadataBelowTitle(1)
        val title = textLayout("recording-list-headline-1")
        assertEquals(1, title.lineCount)
        assertTrue("Long title ellipsizes in its own text column", title.isLineEllipsized(0))
        capture("long-font1.3", 1.3f)
        assertNoCommands()
    }

    @Test fun scheduleAndProblemsKeepNativeRowsAndTruthfulStatus() {
        show()
        focused("recordings-folder-Documentaries")
        key(Key.DirectionUp)
        compose.onNodeWithText("Archive").assertIsFocused()
        key(Key.DirectionRight)
        compose.onNodeWithText("Schedule").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        assertMetadataBelowTitle(20)
        assertFullWidthFocusInside("recordings-schedule-list", 20)
        capture("schedule", 1f, assertFullWidthFocusSafeArea(20))
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-21")
        assertMetadataBelowTitle(21)
        assertFullWidthFocusInside("recordings-problems-list", 21)
        compose.onNodeWithContentDescription("Recording problem").assertIsDisplayed()
        capture("problems", 1f, assertFullWidthFocusSafeArea(21))
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
        capture("schedule-multi-item", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-41")
        capture("schedule-middle-focus", 1f, assertMiddleFocus(41))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        compose.onNodeWithText("Problems").assertIsFocused()
        key(Key.DirectionDown)
        focused("recording-list-entry-60")
        assertDenseListGeometry(firstRowTop)
        capture("problems-multi-item", 1f)
        key(Key.DirectionDown)
        focused("recording-list-entry-61")
        capture("problems-middle-focus", 1f, assertMiddleFocus(61))
        assertNoCommands()
    }

    @Test fun largerCallerEndPaddingIsPreservedWithoutDoublingFocusReserve() {
        show(endPadding = 100.dp)
        focused("recordings-folder-Documentaries")
        assertEquals(860f, bounds("recordings-header").right, .1f)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("recording-list-entry-20")
        assertEquals(848f, bounds("recording-list-entry-20").right, .1f)
        assertFullWidthFocusInside("recordings-schedule-list", 20)
        capture("schedule-end100", 1f, assertFullWidthFocusSafeArea(20))
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        focused("recording-list-entry-21")
        assertEquals(848f, bounds("recording-list-entry-21").right, .1f)
        assertFullWidthFocusInside("recordings-problems-list", 21)
        assertFullWidthFocusSafeArea(21)
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
        assertMetadataBelowTitle(1)
        compose.onNodeWithTag("recording-metadata-pane", useUnmergedTree = true).assertIsDisplayed()
        capture("metadata-de-font1.3", 1.3f)
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
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-details-play").assertIsFocused()
        publish(entries.filterNot { it.id == DvrEntryId(2) }.map { if (it.id == DvrEntryId(1))
            recording(1, "Updated", "recording1.ts") else it })
        compose.onNodeWithTag("recording-details-play").assertIsFocused()
        key(Key.Back)
        focused("recording-list-entry-3")
        assertNoCommands()
    }

    @Test fun replacementSessionCannotConfirmAnArchiveDeletionWithCollidingId() {
        show(entries = listOf(recording(9, "Opening recording", "opening.ts")))
        focused("recording-list-entry-9")
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-details-play").assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        compose.runOnIdle {
            session.replaceGeneration(session.observation.value.let {
                SessionObservation.create(it.sessionState, it.channelState, it.epgState,
                    DvrRepositoryState.Current(DvrSnapshot.create(listOf(recording(9, "Replacement recording", "replacement.ts")))))
            })
        }
        compose.waitForIdle()
        compose.onNodeWithTag("recording-confirmation-back").assertDoesNotExist()
        focused("recording-details-panel")
        compose.onNodeWithTag("recording-details-play").assertDoesNotExist()
        compose.onNodeWithTag("recording-details-delete").assertDoesNotExist()
        key(Key.Back)
        focused("recording-list-entry-9")
        assertNoCommands()
    }

    @Test fun heldRightEntersOnlyOneLevelAndRapidReverseKeepsInvokingRow() {
        show()
        focused("recordings-folder-Documentaries")
        compose.mainClock.autoAdvance = false
        try {
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

    private fun publish(entries: List<DvrEntry>) {
        compose.runOnIdle {
            session.publish(session.observation.value.let {
                SessionObservation.create(it.sessionState, it.channelState, it.epgState,
                    DvrRepositoryState.Current(DvrSnapshot.create(entries)))
            })
        }
        compose.waitForIdle()
    }

    private fun show(scale: Float = 1f, empty: Boolean = false, entries: List<DvrEntry>? = null, endPadding: Dp? = null) {
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
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList(), emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(recordings)),
            recordingProgressCapability = RecordingProgressCapability.UNSUPPORTED,
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
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
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
                            RecordingsScreenContent(
                                observation = liveObservation,
                                currentObservation = { session.observation.value },
                                state = screenState,
                                contentPadding = endPadding?.let {
                                    PaddingValues(start = padding.calculateStartPadding(direction),
                                        top = padding.calculateTopPadding(), end = it,
                                        bottom = padding.calculateBottomPadding())
                                } ?: padding,
                                initialFocusEnabled = !drawer,
                                backEnabled = !drawer,
                                imageLoader = loader,
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
        startEpochSeconds: Long = 1_791_000_000 - id * 86400) =
        DvrEntry.create(
            id = DvrEntryId(id), title = title, path = path, state = state,
            start = Instant.fromEpochSeconds(startEpochSeconds),
            stop = Instant.fromEpochSeconds(startEpochSeconds + 90 * 60),
            files = path?.let { listOf(DvrRecordingFile(null, it, null, null, 2_400_000_000L)) }.orEmpty(),
            channelName = "Ridge Earth HD",
            image = if (id in 1L..5L) "imagecache/${100 + id}" else null,
            subtitle = if (id == 1L) "A winter expedition beyond the Arctic Circle" else null,
            description = "Follow a small team of naturalists through remote landscapes as they explore " +
                "the changing seasons, meet local communities and discover wildlife at the edge of the world.",
            playPosition = if (id == 1L) 3723.seconds else null,
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

    private fun assertMetadataBelowTitle(id: Long) {
        val title = bounds("recording-list-headline-$id")
        val metadata = bounds("recording-list-metadata-$id")
        val leading = bounds("recording-list-leading-$id")
        assertEquals(title.left, metadata.left, .1f)
        assertTrue(title.bottom <= metadata.top)
        assertTrue(leading.right < title.left)
        val row = bounds("recording-list-entry-$id")
        assertTrue(metadata.bottom <= row.bottom)
    }

    private fun assertFullWidthFocusInside(listTag: String, id: Long) {
        val list = bounds(listTag)
        val row = bounds("recording-list-entry-$id")
        assertGrowthInside(row, list)
    }

    private fun assertFullWidthFocusSafeArea(id: Long): String {
        val centerY = bounds("recording-list-entry-$id").center.y.toInt()
        return compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                // Sample the native light focused surface beyond its text/icons, after graphicsLayer scale.
                val maxX = (bitmap.width / 2 until bitmap.width).lastOrNull { x ->
                    val pixel = bitmap.getPixel(x, centerY)
                    android.graphics.Color.red(pixel) >= 180 &&
                        android.graphics.Color.green(pixel) >= 180 && android.graphics.Color.blue(pixel) >= 180
                }
                assertNotNull("Native focused surface must be visible", maxX)
                val gap = bitmap.width - (requireNotNull(maxX) + 1)
                val evidence = "Native focus recording:$id maxPixelX=$maxX trailingGap=${gap}px"
                println(evidence)
                assertTrue("$evidence; required >=48px", gap >= 48)
                evidence
            } finally {
                bitmap.recycle()
            }
        }
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
        assertEquals(5, rows.size)
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

    private fun capture(name: String, scale: Float, focusEvidence: String? = null) {
        if (System.getProperty("tvhplayer.writeMotionCaptures") == "false") return
        assertNoCommands()
        val rows = visibleListRows()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(960, bitmap.width)
            assertEquals(540, bitmap.height)
            val directory = File("build/outputs/browse-cohesion/archive-depth").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "Production RecordingsScreenContent + SideRail; 960x540 mdpi; ${view.resources.configuration.locales[0].toLanguageTag()}; UTC; fontScale=$scale\n" +
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
