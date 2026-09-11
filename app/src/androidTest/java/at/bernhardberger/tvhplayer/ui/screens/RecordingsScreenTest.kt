package at.bernhardberger.tvhplayer.ui.screens

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.DvrLibraryMode
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.captureBrowseFrame
import coil3.ImageLoader
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class RecordingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun TestRecordingsScreen(
        entries: List<DvrEntry> = emptyList(),
        contentPadding: PaddingValues = PaddingValues(),
        initialFocusEnabled: Boolean = true,
        backEnabled: Boolean = true,
        onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
        state: RecordingsScreenState? = null,
        recordingProgressCapability: RecordingProgressCapability = RecordingProgressCapability.UNKNOWN,
        sessionObservation: SessionObservation? = null,
        connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
        onRetry: () -> Unit = {},
        onCancelRecording: suspend (
            CurrentSessionObservation,
            DvrEntryId,
        ) -> DvrMutationResult<Unit> = { _, _ -> DvrMutationResult.NotReady },
        onDeleteRecording: suspend (
            CurrentSessionObservation,
            DvrEntryId,
        ) -> DvrMutationResult<Unit> = { _, _ -> DvrMutationResult.NotReady },
    ) {
        val context = LocalContext.current
        val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
        val generatedObservation = remember(entries, recordingProgressCapability) {
            testSessionObservation(
                entries = entries,
                recordingProgressCapability = recordingProgressCapability,
            )
        }
        val dvrMutationActions = remember(onCancelRecording, onDeleteRecording) {
            DvrMutationActions(
                scheduleEntry = { _, _ -> DvrMutationResult.NotReady },
                cancelEntry = onCancelRecording,
                deleteEntry = onDeleteRecording,
            )
        }
        RecordingsScreenContent(
            observation = sessionObservation ?: generatedObservation,
            contentPadding = contentPadding,
            initialFocusEnabled = initialFocusEnabled,
            backEnabled = backEnabled,
            imageLoader = imageLoader,
            connectionUiState = connectionUiState,
            onRetry = onRetry,
            onPlayRecording = onPlayRecording,
            state = state,
            dvrMutationActions = dvrMutationActions,
        )
    }

    @Test
    fun emptyRetryableFailureHasInitialAndDpadTabEntryFocus() {
        var retries = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    connectionUiState = ConnectionUiState.Error(
                        ConnectionFailureKind.UNREACHABLE,
                        SessionRecoveryDisposition.EXPLICIT_RETRY,
                    ),
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithText("Retry").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Archive").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Retry").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, retries) }
        composeRule.onNodeWithText("Retry").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Archive").assertIsFocused().pressCenter()
        composeRule.onNodeWithText("Retry").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, retries) }
        composeRule.onNodeWithText("Retry").pressCenter()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun loadingAndNonRetryableEmptyStatesKeepTabsReachable() {
        var connection by mutableStateOf<ConnectionUiState>(ConnectionUiState.Connecting)
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(connectionUiState = connection) }
        }

        composeRule.onNodeWithText("Archive").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Archive").assertIsFocused()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
        composeRule.runOnIdle { connection = ConnectionUiState.Ready }
        composeRule.onNodeWithText("Archive").assertIsFocused().pressCenter()
        composeRule.onNodeWithText("Archive").assertIsFocused()
        composeRule.runOnIdle {
            connection = ConnectionUiState.Error(
                ConnectionFailureKind.UNREACHABLE,
                SessionRecoveryDisposition.EXPLICIT_RETRY,
            )
        }
        composeRule.onNodeWithText("Archive").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Retry").assertIsFocused()
    }

    @Test
    fun emptyNonRetryableFailureHasNoDetachedRetryTarget() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    connectionUiState = ConnectionUiState.Error(
                        ConnectionFailureKind.AUTHENTICATION,
                        SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Archive").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Archive").assertIsFocused().pressCenter()
        composeRule.onNodeWithText("Archive").assertIsFocused()
        composeRule.onAllNodesWithText("Retry").assertCountEquals(0)
    }

    @Test
    fun modeTabsUseTheTitleLeadingAnchorOnTheirOwnRow() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
                )
            }
        }

        val title = composeRule.onNodeWithTag("recordings-header")
            .fetchSemanticsNode().boundsInRoot
        val tabs = composeRule.onNodeWithTag("recordings-mode-tabs")
            .fetchSemanticsNode().boundsInRoot
        val tabsRow = composeRule.onNodeWithTag("recordings-mode-tabs-row")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(title.left, tabs.left, 0.5f)
        assertTrue(tabs.right < title.right)
        assertTrue(tabsRow.width > tabs.width)
        assertTrue(tabsRow.right > title.right)
        assertTrue(tabs.top >= title.bottom)
    }

    @Test
    fun completedRecordingOpensDetailsBeforeDeleteConfirmation() {
        val entries = listOf(
            recording(
                id = 7,
                title = "Evening News",
                path = "News/evening-news.ts",
                fileSizeBytes = 500L,
            )
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(entries = entries)
            }
        }

        composeRule.onNodeWithTag("recordings-folder-News").assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused().pressCenter()
        waitForFocus("recording-list-entry-7")
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsDisplayed()
        composeRule.onAllNodesWithText("Evening News").assertCountEquals(2)
        composeRule.onNodeWithTag("recording-list-entry-7").pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsDisplayed().assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        composeRule.onNodeWithText("Delete “Evening News”?").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-confirmation-confirm").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-confirmation-confirm").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-confirmation-confirm").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-confirmation-confirm").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        dispatchBack()
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
    }

    @Test
    fun folderReplacementKeepsListOwnershipInProductionDrawer() {
        val state = RecordingsScreenState()
        val entries = (1..20).map {
            recording(id = it, title = "Episode $it", path = "Series/Season/episode-$it.ts")
        }
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true,
                    onRootBack = {},
                    onNavigate = {},
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(
                        entries = entries,
                        contentPadding = padding,
                        initialFocusEnabled = !active,
                        backEnabled = !active,
                        state = state,
                    )
                }
            }
        }
        waitForFocus("recordings-folder-Series")
        composeRule.mainClock.autoAdvance = false
        try {
            for (folder in listOf("Series", "Series/Season")) {
                captureBrowseFrame(composeRule.onRoot(), "folder-entry-${folder.replace('/', '-')}-start")
                composeRule.onNodeWithTag("recordings-folder-$folder").pressCenter()
                repeat(12) { frame ->
                    composeRule.mainClock.advanceTimeByFrame()
                    composeRule.runOnIdle { assertFalse("Drawer opened during $folder replacement", drawerActive) }
                    assertArchiveListOwnsFocus()
                    if (frame in listOf(0, 2, 5, 11)) {
                        captureBrowseFrame(composeRule.onRoot(), "folder-entry-${folder.replace('/', '-')}-frame-$frame")
                    }
                }
            }
            composeRule.runOnIdle { assertEquals(listOf("Series", "Season"), state.archivePath.value) }
            composeRule.onAllNodes(isFocused()).assertCountEquals(1)
            dispatchBack()
            repeat(12) { frame ->
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.runOnIdle { assertFalse("Drawer opened during folder Back", drawerActive) }
                assertArchiveListOwnsFocus()
                if (frame in listOf(0, 2, 5, 11)) captureBrowseFrame(composeRule.onRoot(), "folder-back-frame-$frame")
            }
            composeRule.onNodeWithTag("recordings-folder-Series/Season").assertIsFocused()
            composeRule.runOnIdle { assertEquals(listOf("Series"), state.archivePath.value) }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun assertArchiveListOwnsFocus() {
        val focused = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().single()
        val list = composeRule.onNodeWithTag("recordings-archive-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Focus must stay in the archive list or its transition container",
            focused.boundsInRoot.center.x in list.left..list.right &&
                focused.boundsInRoot.center.y in list.top..list.bottom)
    }

    @Test
    fun archivePagingMotionKeepsDirectionThroughFocusHandoff() = assertArchivePagingMotion(inFolder = false)

    @Test
    fun scrolledFolderBackRestoresViewportThenRootBackReturnsToDrawer() {
        val state = RecordingsScreenState()
        val entries = (1..20).map {
            recording(id = it, title = "Episode $it", path = "Folder ${it.toString().padStart(2, '0')}/episode.ts")
        }
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                var shellBack by remember { mutableStateOf<() -> Unit>({}) }
                androidx.activity.compose.BackHandler { shellBack() }
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                    onBackHandlerChanged = { shellBack = it },
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(entries = entries, contentPadding = padding,
                        initialFocusEnabled = !active, backEnabled = !active, state = state)
                }
            }
        }
        waitForFocus("recordings-folder-Folder 01")
        repeat(14) { composeRule.onAllNodes(isFocused())[0].performKeyInput { pressKey(Key.DirectionDown) } }
        val folder = composeRule.onNodeWithTag("recordings-folder-Folder 15").assertIsFocused()
        val beforeTop = folder.fetchSemanticsNode().boundsInRoot.top
        val beforeIndex = state.archiveScrollPositions["archive:"]
        val beforeOffset = state.archiveScrollOffsets["archive:"]
        assertTrue("Fixture must have a scrolled parent", requireNotNull(beforeIndex) > 0)
        captureBrowseFrame(composeRule.onRoot(), "folder-scrolled-parent-before")
        folder.pressCenter()
        waitForFocus("recording-list-entry-15")
        captureBrowseFrame(composeRule.onRoot(), "folder-scrolled-child")
        dispatchBack()
        waitForFocus("recordings-folder-Folder 15")
        composeRule.runOnIdle {
            assertFalse(drawerActive)
            assertEquals(beforeIndex, state.archiveScrollPositions["archive:"])
            assertEquals(beforeOffset, state.archiveScrollOffsets["archive:"])
        }
        assertEquals(beforeTop, folder.fetchSemanticsNode().boundsInRoot.top, 1f)
        captureBrowseFrame(composeRule.onRoot(), "folder-scrolled-parent-restored")
        dispatchBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue("Root Back retains shell behavior", drawerActive) }
    }

    @Test
    fun lastRecordingRemovalKeepsDeterministicLocalFocusInsideDrawer() = assertLastRemovalFocus(drawerOwnsFocus = false)

    @Test
    fun retainedFolderBackInScheduleReturnsToDrawer() = assertFolderBackInOtherMode(1)

    @Test
    fun retainedFolderBackInProblemsReturnsToDrawer() = assertFolderBackInOtherMode(2)

    @Test
    fun queuedFolderBackIsCancelledOnScheduleEntry() = assertFolderBackInOtherMode(1, queueBack = true)

    @Test
    fun queuedFolderBackIsCancelledOnProblemsEntry() = assertFolderBackInOtherMode(2, queueBack = true)

    private fun assertFolderBackInOtherMode(modeSteps: Int, queueBack: Boolean = false) {
        val state = RecordingsScreenState()
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                var shellBack by remember { mutableStateOf<() -> Unit>({}) }
                BackHandler { shellBack() }
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                    onBackHandlerChanged = { shellBack = it },
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(
                        entries = listOf(recording(id = 1, title = "First", path = "Series/Season/first.ts")),
                        contentPadding = padding, initialFocusEnabled = !active,
                        backEnabled = !active, state = state,
                    )
                }
            }
        }
        waitForFocus("recordings-folder-Series")
        composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
        waitForFocus("recordings-folder-Series/Season")
        composeRule.onNodeWithTag("recordings-folder-Series/Season").pressCenter()
        waitForFocus("recording-list-entry-1")
        composeRule.mainClock.autoAdvance = !queueBack
        try {
            if (queueBack) dispatchBack()
            composeRule.onAllNodes(isFocused())[0].performKeyInput {
                keyDown(Key.DirectionUp); keyUp(Key.DirectionUp)
                repeat(modeSteps) { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
            }
            if (queueBack) repeat(12) { composeRule.mainClock.advanceTimeByFrame() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.onNodeWithText(if (modeSteps == 1) "Schedule" else "Problems").assertIsFocused()
        composeRule.runOnIdle {
            assertFalse(drawerActive)
            assertEquals(listOf("Series", "Season"), state.archivePath.value)
        }
        dispatchBack()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue("Non-Archive Back belongs to the shell", drawerActive) }
    }

    @Test
    fun removedChildWhileScheduleActiveReturnsToSurvivingParent() = assertInactiveFolderRemoval(1)

    @Test
    fun removedChildWhileProblemsActiveReturnsToSurvivingParent() = assertInactiveFolderRemoval(2)

    private fun assertInactiveFolderRemoval(modeSteps: Int) {
        val remaining = recording(id = 2, title = "Remaining", path = "Series/remaining.ts")
        val entries = mutableStateOf(listOf(
            recording(id = 1, title = "First", path = "Series/Season/first.ts"), remaining,
        ))
        val state = RecordingsScreenState()
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(entries = entries.value, contentPadding = padding,
                        initialFocusEnabled = !active, backEnabled = !active, state = state)
                }
            }
        }
        waitForFocus("recordings-folder-Series")
        composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
        waitForFocus("recordings-folder-Series/Season")
        composeRule.onNodeWithTag("recordings-folder-Series/Season").pressCenter()
        waitForFocus("recording-list-entry-1")
        composeRule.onAllNodes(isFocused())[0].performKeyInput {
            pressKey(Key.DirectionUp)
            repeat(modeSteps) { pressKey(Key.DirectionRight) }
        }
        composeRule.onNodeWithText(if (modeSteps == 1) "Schedule" else "Problems").assertIsFocused()
        composeRule.runOnIdle { entries.value = listOf(remaining) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(drawerActive)
            assertEquals(listOf("Series"), state.archivePath.value)
        }
        composeRule.onAllNodes(isFocused())[0].performKeyInput {
            repeat(modeSteps) { pressKey(Key.DirectionLeft) }
            pressKey(Key.DirectionDown)
        }
        waitForFocus("recording-list-entry-2")
        dispatchBack()
        waitForFocus("recordings-folder-Series")
        composeRule.runOnIdle {
            assertFalse(drawerActive)
            assertTrue(state.archivePath.value.isEmpty())
        }
    }

    @Test
    fun lastRecordingRemovalDoesNotStealExistingDrawerFocus() = assertLastRemovalFocus(drawerOwnsFocus = true)

    @Test
    fun queuedFolderEntrySurvivesLastRecordingRemoval() =
        assertLastRemovalFocus(drawerOwnsFocus = false, queueFolderEntry = true)

    @Test
    fun upDuringFolderEntryCancelsHandoffAcrossCatalogueUpdate() {
        val entries = mutableStateOf(listOf(recording(id = 1, title = "First", path = "Series/first.ts")))
        val state = RecordingsScreenState()
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(entries = entries.value, contentPadding = padding,
                        initialFocusEnabled = !active, backEnabled = !active, state = state)
                }
            }
        }
        waitForFocus("recordings-folder-Series")
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
            composeRule.mainClock.advanceTimeUntil(timeoutMillis = 500) {
                state.archivePath.value == listOf("Series") && state.selectedKeys["archive:Series"] != null
            }
            composeRule.onAllNodes(hasTestTag("recording-list-entry-1") and isFocused()).assertCountEquals(0)
            composeRule.onAllNodes(isFocused())[0].performKeyInput {
                keyDown(Key.DirectionUp); keyUp(Key.DirectionUp)
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithText("Archive").assertIsFocused()
            composeRule.runOnIdle {
                entries.value += recording(id = 2, title = "Second", path = "Series/second.ts")
            }
            repeat(12) { composeRule.mainClock.advanceTimeByFrame() }
            composeRule.onNodeWithText("Archive").assertIsFocused()
            composeRule.runOnIdle { assertFalse(drawerActive) }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun assertLastRemovalFocus(drawerOwnsFocus: Boolean, queueFolderEntry: Boolean = false) {
        val entries = mutableStateOf(listOf(recording(id = 1, title = "Last recording",
            path = if (queueFolderEntry) "Series/last.ts" else "last.ts")))
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(entries = entries.value, contentPadding = padding,
                        initialFocusEnabled = !active, backEnabled = !active)
                }
            }
        }
        waitForFocus(if (queueFolderEntry) "recordings-folder-Series" else "recording-list-entry-1")
        if (drawerOwnsFocus) {
            composeRule.onAllNodes(isFocused())[0].performKeyInput { pressKey(Key.DirectionLeft) }
            composeRule.runOnIdle { assertTrue(drawerActive) }
        }
        composeRule.mainClock.autoAdvance = !queueFolderEntry
        try {
            if (queueFolderEntry) composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
            composeRule.runOnIdle { entries.value = emptyList() }
            if (queueFolderEntry) repeat(12) { composeRule.mainClock.advanceTimeByFrame() }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals("Removal must retain the current focus region", drawerOwnsFocus, drawerActive) }
        if (!drawerOwnsFocus) composeRule.onNodeWithText("Archive").assertIsFocused()
    }

    @Test
    fun focusedRecordingRemovalWithinFolderRetainsLocalRemainingRow() = assertFolderRemoval("Series/second.ts")

    @Test
    fun removedFolderReturnsToSurvivingParentWithLocalFocus() = assertFolderRemoval("second.ts")

    private fun assertFolderRemoval(remainingPath: String) {
        val remaining = recording(id = 2, title = "Second", path = remainingPath)
        val entries = mutableStateOf(listOf(recording(id = 1, title = "First", path = "Series/first.ts"), remaining))
        val state = RecordingsScreenState()
        var drawerActive = false
        composeRule.setContent {
            TVHeadendPlayerTheme {
                at.bernhardberger.tvhplayer.ui.components.SideRail(
                    currentRoute = at.bernhardberger.tvhplayer.ui.AppDestination.RECORDINGS,
                    showEpgMenu = true, onRootBack = {}, onNavigate = {},
                ) { padding, active ->
                    androidx.compose.runtime.SideEffect { drawerActive = active }
                    TestRecordingsScreen(entries = entries.value, contentPadding = padding,
                        initialFocusEnabled = !active, backEnabled = !active, state = state)
                }
            }
        }
        waitForFocus("recordings-folder-Series")
        composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
        waitForFocus("recording-list-entry-1")
        composeRule.runOnIdle { entries.value = listOf(remaining) }
        waitForFocus("recording-list-entry-2")
        composeRule.runOnIdle {
            assertFalse(drawerActive)
            assertEquals(if (remainingPath.contains('/')) listOf("Series") else emptyList<String>(), state.archivePath.value)
        }
    }

    @Test
    fun childFolderPagingMotionKeepsDirectionThroughFocusHandoff() = assertArchivePagingMotion(inFolder = true)

    private fun assertArchivePagingMotion(inFolder: Boolean) {
        val entries = (1..23).map {
            recording(id = it, title = "Episode %02d".format(it), path = "${if (inFolder) "Series/" else ""}episode-$it.ts")
        }
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }
        if (inFolder) {
            waitForFocus("recordings-folder-Series")
            composeRule.onNodeWithTag("recordings-folder-Series").pressCenter()
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        try {
            fun page(direction: Int, name: String, frames: Int = 100): Int {
                var previous = recordingRowTops()
                captureBrowseFrame(composeRule.onRoot(), "recordings-folder-$inFolder-$name-start")
                composeRule.onRoot().performKeyInput {
                    val key = Key(if (direction > 0) KeyEvent.KEYCODE_CHANNEL_DOWN else KeyEvent.KEYCODE_CHANNEL_UP)
                    keyDown(key)
                    keyUp(key)
                }
                var movingFrames = 0
                repeat(frames) { frame ->
                    composeRule.mainClock.advanceTimeByFrame()
                    val current = recordingRowTops()
                    val deltas = current.mapNotNull { (id, top) -> previous[id]?.let { top - it } }
                    assertTrue("folder=$inFolder direction=$direction frame=$frame previous=$previous current=$current", deltas.all { it * direction <= 1f })
                    if (deltas.any { it * direction < -1f }) movingFrames++
                    if (frame in listOf(2, 5, 10, 20, frames - 1)) {
                        captureBrowseFrame(composeRule.onRoot(), "recordings-folder-$inFolder-$name-frame-$frame")
                    }
                    previous = current
                }
                return movingFrames
            }
            fun focusedId() = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().single()
                .config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("recording-list-entry-").toInt()
            val first = focusedId()
            val last = if (first == 1) 23 else 1
            for (direction in listOf(1, -1)) {
                val end = if (direction > 0) last else first
                var pages = 0
                var movingPages = 0
                while (focusedId() != end && pages < 10) {
                    val before = focusedId()
                    if (page(direction, "direction-$direction-page-${pages++}") >= 3) movingPages++
                    assertTrue("Page must advance focus", focusedId() != before)
                }
                assertEquals(end, focusedId())
                assertTrue("Multiple pages must visibly move", movingPages >= 2)
                if (direction > 0) {
                    assertTrue("Up must be moving before interruption", page(-1, "interrupted-up", 8) >= 3)
                    assertTrue("Down reversal must visibly move rows up", page(1, "reverse-down") >= 3)
                    assertEquals(last, focusedId())
                }
            }
            assertTrue("Down must be moving before interruption", page(1, "interrupted-down", 8) >= 3)
            assertTrue("Reversal must visibly move upward-page rows down", page(-1, "reverse-up") >= 3)
            assertEquals(first, focusedId())
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun recordingRowTops(): Map<String, Float> = composeRule
        .onAllNodes(androidx.compose.ui.test.SemanticsMatcher("recording row") {
            it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }
                .startsWith("recording-list-entry-")
        }).fetchSemanticsNodes().filter {
            composeRule.onNodeWithTag(it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]).isDisplayed()
        }.associate {
            it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] to it.boundsInRoot.top
        }

    @Test
    fun replacementSessionCannotConfirmDeleteAgainstACollidingRecordingId() {
        val observationA = testSessionObservation(
            entries = listOf(recording(id = 7, title = "Generation A", path = "a.ts"))
        )
        val capabilityA = requireNotNull(observationA.currentSession)
        val observationB = testSessionObservation(
            entries = listOf(recording(id = 7, title = "Generation B", path = "b.ts"))
        )
        val capabilityB = requireNotNull(observationB.currentSession)
        val observation = mutableStateOf(observationA)
        var dispatchedCapability: CurrentSessionObservation? = null
        var dispatchedRecordingId: DvrEntryId? = null

        assertNotSame(capabilityA, capabilityB)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    onDeleteRecording = { currentSession, id ->
                        dispatchedCapability = currentSession
                        dispatchedRecordingId = id
                        DvrMutationResult.Confirmed(Unit)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        composeRule.runOnIdle { observation.value = observationB }
        composeRule.onAllNodesWithTag("recording-confirmation-back").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
        composeRule.waitForIdle()

        assertEquals(null, dispatchedCapability)
        assertEquals(null, dispatchedRecordingId)
    }

    @Test
    fun movingUpFromFolderPreviewReturnsToSelectedModeWithoutChangingIt() {
        val entries = listOf(
            recording(id = 7, title = "Evening News", path = "News/evening-news.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recordings-folder-News")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }

        composeRule.onNodeWithText("Archive").assertIsFocused()
        composeRule.onNodeWithTag("recordings-archive-list").assertIsDisplayed()
    }

    @Test
    fun backUnwindsPreviewFolderAndDetailsBeforeLeavingRecordings() {
        val entries = listOf(
            recording(id = 7, title = "Evening News", path = "News/evening-news.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recordings-folder-News")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7")
            .assertIsFocused()
        dispatchBack()
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused().pressCenter()
        waitForFocus("recording-list-entry-7")
        composeRule.onNodeWithTag("recording-list-entry-7").pressCenter()
        composeRule.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-play")
            .assertIsFocused()
        dispatchBack()
        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        waitForFocus("recording-list-entry-7")
        dispatchBack()
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused()
    }

    @Test
    fun disabledBackLeavesFolderPreviewForTheShell() {
        var shellBackCount = 0
        val entries = listOf(
            recording(id = 7, title = "Evening News", path = "News/evening-news.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box {
                    BackHandler { shellBackCount++ }
                    TestRecordingsScreen(entries = entries, backEnabled = false)
                }
            }
        }

        composeRule.onNodeWithTag("recordings-folder-News")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7")
            .assertIsFocused()
        dispatchBack()

        composeRule.onNodeWithTag("folder-preview-recording-7").assertIsFocused()
        composeRule.runOnIdle { assertEquals(1, shellBackCount) }
    }

    @Test
    fun closingDetailsRestoresRecordingWhenAutomaticInitialFocusIsDisabled() {
        val entries = listOf(
            recording(id = 7, title = "Evening News", path = "evening-news.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    entries = entries,
                    initialFocusEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionCenter)
            }

        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused()
    }

    @Test
    fun detailsConsumeBackBeforeTheShellAndRestoreTheRecording() {
        var shellBackCount = 0
        val entries = listOf(
            recording(id = 7, title = "Evening News", path = "evening-news.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box {
                    BackHandler { shellBackCount++ }
                    TestRecordingsScreen(entries = entries)
                }
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        dispatchBack()

        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, shellBackCount) }
    }

    private fun dispatchBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    @Test
    fun shortDetailsKeepPlaybackActionsAdjacentToMetadata() {
        val entries = listOf(
            recording(id = 8, title = "Short programme", path = "short.ts")
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }
        composeRule.onNodeWithTag("recording-list-entry-8").assertIsFocused().pressCenter()

        val metadata = composeRule.onNodeWithTag(
            "recording-details-metadata-anchor",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val playbackActions = composeRule.onNodeWithTag(
            "recording-details-playback-actions",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("recording-details-panel")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(playbackActions.top - metadata.bottom <= panel.height * 0.12f)
    }

    @Test
    fun resumableDetailsKeepSemanticFocusAndSendExplicitStartIntent() {
        var playbackStart: RecordingPlaybackStart? = null
        val entries = listOf(
            recording(
                id = 7,
                title = "Evening News",
                path = "evening-news.ts",
                stop = 7_300L,
                playPositionSeconds = 3_723L,
            )
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    entries = entries,
                    recordingProgressCapability = RecordingProgressCapability.UNSUPPORTED,
                    onPlayRecording = { _, start -> playbackStart = start },
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithContentDescription(
            "Resume from 1 hour, 2 minutes, 3 seconds"
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-details-beginning").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-details-beginning").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-details-resume").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("recording-details-beginning").assertIsFocused()
        composeRule.onNodeWithTag("recording-details-beginning").assertIsFocused().pressCenter()
        composeRule.runOnIdle {
            assertEquals(RecordingPlaybackStart.START_OVER, playbackStart)
        }
        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
    }

    @Test
    fun completedPlaybackRemainsAvailableWhenProgressWritesAreUnsupported() {
        var playbackStart: RecordingPlaybackStart? = null
        val entries = listOf(
            recording(
                id = 8,
                title = "Documentary",
                path = "documentary.ts",
                stop = 3_700L,
            )
        )

        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    entries = entries,
                    recordingProgressCapability = RecordingProgressCapability.UNSUPPORTED,
                    onPlayRecording = { _, start -> playbackStart = start },
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-8").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused().pressCenter()
        composeRule.runOnIdle {
            assertEquals(RecordingPlaybackStart.START_OVER, playbackStart)
        }
    }

    @Test
    fun browserLocationAndFocusSurviveLeavingAndReturningToTheScreen() {
        val entries = listOf(
            recording(
                id = 7,
                title = "Evening News",
                path = "News/evening-news.ts",
                fileSizeBytes = 500L,
            )
        )
        val screenState = RecordingsScreenState()
        val visible = mutableStateOf(true)

        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (visible.value) {
                    TestRecordingsScreen(entries = entries, state = screenState)
                }
            }
        }

        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused().pressCenter()
        waitForFocus("recording-list-entry-7")
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle { visible.value = true }
        waitForFocus("recording-list-entry-7")
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsDisplayed()
    }

    @Test
    fun archiveDoesNotMixScheduledOrFailedEntries() {
        val start = System.currentTimeMillis() / 1000L + 3600L
        val entries = listOf(
            recording(
                id = 1,
                title = "Saved Film",
                path = "saved.ts",
                start = start,
                stop = start + 3_600L,
            ),
            recording(
                id = 2,
                title = "Future Show",
                state = DvrEntryState.SCHEDULED,
                start = start,
                stop = start + 3_600L,
            ),
            recording(
                id = 3,
                title = "Failed Show",
                state = DvrEntryState.RECORDING_ERROR,
                start = start,
                stop = start + 3_600L,
            ),
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recording-list-entry-1").assertIsDisplayed()
        composeRule.onAllNodesWithText("Future Show").assertCountEquals(0)
        composeRule.onAllNodesWithText("Failed Show").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-1").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionRight)
            }
        composeRule.onNodeWithText("Schedule").assertIsFocused()
        composeRule.onNodeWithTag("recordings-schedule-list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-metadata-pane").assertCountEquals(0)
        composeRule.onNodeWithText("Future Show").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-list-entry-2").requestFocus().assertIsFocused()
            .pressCenter()
        composeRule.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel recording").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-cancel").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionUp)
            }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused().pressCenter()
        waitForFocus("recording-list-entry-2")
        composeRule.onNodeWithTag("recording-list-entry-2").performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithText("Problems").assertIsFocused()
        composeRule.onNodeWithTag("recordings-problems-list").assertIsDisplayed()
        composeRule.onNodeWithText("Failed").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-metadata-pane").assertCountEquals(0)
        composeRule.onNodeWithText("Failed Show").assertIsDisplayed()
        composeRule.onNodeWithText("Problems").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-list-entry-3").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionUp)
            }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused().pressCenter()
        waitForFocus("recording-list-entry-3")
    }

    @Test
    fun pageDownThenUpDoesNotReclaimFocusFromArchiveTab() {
        val entries = (1..50).map { id ->
            recording(id, "Recording $id", path = "recording-$id.ts", start = id.toLong())
        }
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }
        composeRule.onNodeWithTag("recording-list-entry-50").assertIsFocused()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("recording-list-entry-50").performKeyInput {
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
            pressKey(Key.DirectionUp)
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Archive").assertIsFocused()
    }

    @Test
    fun repeatedArchivePagesAdvanceBeforeTheFirstAnimationCompletes() {
        assertArchivePaging(removePending = false)
    }

    @Test
    fun archivePagingRecoversWhenPendingRecordingIsRemoved() {
        assertArchivePaging(removePending = true)
    }

    private fun assertArchivePaging(removePending: Boolean) {
        val entries = (1..50).map { id ->
            recording(id, "Recording $id", path = "recording-$id.ts", start = id.toLong())
        }
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(sessionObservation = observation.value) }
        }
        composeRule.onNodeWithTag("recording-list-entry-50").assertIsFocused()
        val listBounds = composeRule.onNodeWithTag("recordings-archive-list").fetchSemanticsNode().boundsInRoot
        val visibleRows = composeRule.onAllNodes(SemanticsMatcher("recording list row") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("recording-list-entry-")
        }).fetchSemanticsNodes().count {
            val bounds = it.boundsInRoot
            bounds.height > 0 && bounds.bottom > listBounds.top && bounds.top < listBounds.bottom
        }
        assertTrue(visibleRows > 2)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("recording-list-entry-50").performKeyInput {
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
        }
        if (removePending) {
            composeRule.runOnIdle {
                session.publish(testSessionObservation(entries = entries.filterNot {
                    it.id == DvrEntryId((51 - visibleRows).toLong())
                }))
                observation.value = session.observation.value
            }
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.onNode(isFocused()).performKeyInput {
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
        }
        composeRule.mainClock.autoAdvance = true
        if (removePending) {
            composeRule.waitForIdle()
            val id = composeRule.onNode(isFocused()).fetchSemanticsNode()
                .config[SemanticsProperties.TestTag].removePrefix("recording-list-entry-").toInt()
            assertTrue("Paging must recover after its pending row is removed", id < 50)
        } else {
            waitForFocus("recording-list-entry-${50 - 2 * (visibleRows - 1)}")
        }
    }

    @Test
    fun pageReversalKeepsTheNewestArchiveTarget() {
        val entries = (1..50).map { id ->
            recording(id, "Recording $id", path = "recording-$id.ts", start = id.toLong())
        }
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }
        composeRule.onNodeWithTag("recording-list-entry-50").assertIsFocused()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("recording-list-entry-50").performKeyInput {
            pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNode(isFocused()).performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_UP)) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("recording-list-entry-50").assertIsFocused()
    }

    @Test
    fun upWithinArchiveThenSelectedRemovalRestoresFirstRemainingRecording() {
        val entries = (1..3).map { id ->
            recording(id, "Recording $id", path = "recording-$id.ts", start = id.toLong())
        }
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        val capability = requireNotNull(observation.value.currentSession)
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(sessionObservation = observation.value) }
        }
        composeRule.onNodeWithTag("recording-list-entry-3").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-list-entry-2").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("recording-list-entry-1").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-list-entry-2").assertIsFocused()
        composeRule.runOnIdle {
            session.publish(testSessionObservation(entries = entries.filterNot { it.id == DvrEntryId(2) }))
            observation.value = session.observation.value
            assertSame(capability, observation.value.currentSession)
        }
        waitForFocus("recording-list-entry-3")
    }

    @Test
    fun schedulePageDownCountsRecordingRowsRatherThanSectionHeaders() {
        assertGroupedPageDown(DvrLibraryMode.SCHEDULE)
    }

    @Test
    fun problemsPageDownCountsRecordingRowsRatherThanSectionHeaders() {
        assertGroupedPageDown(DvrLibraryMode.PROBLEMS)
    }

    @Test
    fun repeatedSchedulePagesAdvanceBeforeTheFirstAnimationCompletes() {
        assertGroupedPageDown(DvrLibraryMode.SCHEDULE, pages = 2)
    }

    @Test
    fun repeatedProblemPagesAdvanceBeforeTheFirstAnimationCompletes() {
        assertGroupedPageDown(DvrLibraryMode.PROBLEMS, pages = 2)
    }

    @Test
    fun schedulePagingRecoversWhenPendingRecordingIsRemoved() {
        assertGroupedPageDown(DvrLibraryMode.SCHEDULE, pages = 2, removePending = true)
    }

    @Test
    fun problemsPagingRecoversWhenPendingRecordingIsRemoved() {
        assertGroupedPageDown(DvrLibraryMode.PROBLEMS, pages = 2, removePending = true)
    }

    private fun assertGroupedPageDown(mode: DvrLibraryMode, pages: Int = 1, removePending: Boolean = false) {
        val start = System.currentTimeMillis() / 1000L + 3_600L
        val entries = (1..30).map { id ->
            recording(
                id, "Recording $id",
                state = if (mode == DvrLibraryMode.SCHEDULE) {
                    if (id == 1) DvrEntryState.RECORDING else DvrEntryState.SCHEDULED
                } else {
                    if (id == 1) DvrEntryState.RECORDING_ERROR else DvrEntryState.MISSED
                },
                start = start + if (mode == DvrLibraryMode.SCHEDULE) id else 31 - id,
                stop = start + 3_600L,
            )
        }
        val screenState = RecordingsScreenState().apply { this.mode.value = mode }
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(sessionObservation = observation.value, state = screenState) }
        }
        composeRule.onNodeWithTag("recording-list-entry-1").assertIsFocused()
        val listTag = if (mode == DvrLibraryMode.SCHEDULE) "recordings-schedule-list"
            else "recordings-problems-list"
        val listBounds = composeRule.onNodeWithTag(listTag).fetchSemanticsNode().boundsInRoot
        val visibleRows = composeRule.onAllNodes(SemanticsMatcher("recording list row") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("recording-list-entry-")
        }).fetchSemanticsNodes().count {
            val bounds = it.boundsInRoot
            bounds.height > 0 && bounds.bottom > listBounds.top && bounds.top < listBounds.bottom
        }
        assertTrue("Fixture must show multiple rows across section headers", visibleRows > 2)
        composeRule.mainClock.autoAdvance = false
        repeat(pages) { page ->
            if (removePending && page == 1) {
                composeRule.runOnIdle {
                    session.publish(testSessionObservation(entries = entries.filterNot {
                        it.id == DvrEntryId(visibleRows.toLong())
                    }))
                    observation.value = session.observation.value
                }
                composeRule.mainClock.advanceTimeByFrame()
            }
            composeRule.onNode(isFocused()).performKeyInput { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
        }
        composeRule.mainClock.autoAdvance = true
        // IDs follow row order: a page overlaps by one recording, excluding both headers.
        try {
            if (pages == 1) {
                waitForFocus("recording-list-entry-$visibleRows")
            } else {
                composeRule.waitForIdle()
                val focusedTag = composeRule.onNode(isFocused()).fetchSemanticsNode()
                    .config[SemanticsProperties.TestTag]
                // The next viewport may contain fewer headers and therefore more rows.
                val focusedId = focusedTag.removePrefix("recording-list-entry-").toInt()
                assertTrue("Paging must advance from the latest valid origin",
                    focusedId > if (removePending) 1 else visibleRows)
            }
        } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val focusedTags = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().map {
                it.config.getOrElse(SemanticsProperties.TestTag) { "untagged" }
            }
            throw AssertionError("PageDown with $visibleRows visible rows focused $focusedTags", failure)
        }
    }

    @Test
    fun confirmedCancelRefreshesSameSessionDetailsAndActionFocus() {
        val entry = recording(7, "Future Show", state = DvrEntryState.SCHEDULED)
        val session = FakeTvheadendSession(testSessionObservation(entries = listOf(entry)))
        val observation = mutableStateOf(session.observation.value)
        val openingCapability = requireNotNull(observation.value.currentSession)
        val screenState = RecordingsScreenState().apply { mode.value = DvrLibraryMode.SCHEDULE }
        var cancellations = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    state = screenState,
                    onCancelRecording = { capability, id ->
                        assertSame(openingCapability, capability)
                        assertEquals(entry.id, id)
                        cancellations++
                        DvrMutationResult.Confirmed(Unit)
                    },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-cancel").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        composeRule.runOnIdle {
            assertEquals(1, cancellations)
            session.publish(testSessionObservation(entries = listOf(
                recording(7, "Cancelled Show", state = DvrEntryState.MISSED),
            )))
            observation.value = session.observation.value
            assertSame(openingCapability, observation.value.currentSession)
        }
        composeRule.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelled Show").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-details-cancel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
    }

    @Test
    fun confirmedDeleteThenRemovalDismissesSameSessionDetailsAndReturnsToTabs() {
        val session = FakeTvheadendSession(testSessionObservation(entries = listOf(
            recording(7, "Saved Film", path = "saved.ts"),
        )))
        val observation = mutableStateOf(session.observation.value)
        val openingCapability = requireNotNull(observation.value.currentSession)
        var deletions = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    onDeleteRecording = { capability, id ->
                        assertSame(openingCapability, capability)
                        assertEquals(DvrEntryId(7), id)
                        deletions++
                        DvrMutationResult.Confirmed(Unit)
                    },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        composeRule.runOnIdle {
            assertEquals(1, deletions)
            session.publish(testSessionObservation())
            observation.value = session.observation.value
            assertSame(openingCapability, observation.value.currentSession)
        }
        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onAllNodesWithTag("recording-details-delete").assertCountEquals(0)
        composeRule.onNodeWithText("Archive").assertIsFocused()
    }

    @Test
    fun delayedMutationFeedbackDoesNotLeakIntoReopenedDetails() {
        val entries = listOf(recording(7, "Saved Film", path = "saved.ts"))
        val result = CompletableDeferred<DvrMutationResult<Unit>>()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    entries = entries,
                    onDeleteRecording = { _, _ -> result.await() },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("recording-confirmation-back").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
        dispatchBack()
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        composeRule.runOnIdle { result.complete(DvrMutationResult.Confirmed(Unit)) }
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.recording_action_accepted)
        ).assertCountEquals(0)
    }

    @Test
    fun delayedMutationFeedbackDoesNotLeakAfterSessionReplacement() {
        val entries = listOf(recording(7, "Saved Film", path = "saved.ts"))
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        val result = CompletableDeferred<DvrMutationResult<Unit>>()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    onDeleteRecording = { _, _ -> result.await() },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("recording-confirmation-back").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
        composeRule.runOnIdle {
            session.replaceGeneration(testSessionObservation(entries = entries))
            observation.value = session.observation.value
        }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
        composeRule.runOnIdle { result.complete(DvrMutationResult.Confirmed(Unit)) }
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.recording_action_accepted)
        ).assertCountEquals(0)
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
    }

    @Test
    fun sessionReplacementDismissesConfirmationWithExplicitFeedback() {
        assertSessionReplacementFeedback(failedRecording = false)
    }

    @Test
    fun sessionReplacementFeedbackOutranksAnExistingRecordingFailure() {
        assertSessionReplacementFeedback(failedRecording = true)
    }

    private fun assertSessionReplacementFeedback(failedRecording: Boolean) {
        val entries = listOf(if (failedRecording) DvrEntry.create(
            id = DvrEntryId(7), title = "Failed Film", state = DvrEntryState.RECORDING_ERROR,
            subscriptionError = at.bernhardberger.tvheadend.sdk.core.DvrSubscriptionError.NO_FREE_ADAPTER,
        ) else recording(7, "Saved Film", path = "saved.ts"))
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        val screenState = RecordingsScreenState().apply {
            mode.value = if (failedRecording) DvrLibraryMode.PROBLEMS else DvrLibraryMode.ARCHIVE
        }
        var mutations = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    state = screenState,
                    onDeleteRecording = { _, _ -> mutations++; DvrMutationResult.Confirmed(Unit) },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        if (failedRecording) {
            composeRule.onNode(hasText("NO_FREE_ADAPTER") and
                hasAnyAncestor(hasTestTag("recording-details-panel"))).assertIsDisplayed()
            composeRule.onNodeWithTag("recording-details-delete").assertIsFocused().pressCenter()
        } else {
            composeRule.onNodeWithTag("recording-details-play").performKeyInput {
                pressKey(Key.DirectionDown)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionCenter)
            }
        }
        composeRule.onNodeWithTag("recording-confirmation-back").assertIsFocused()
        composeRule.runOnIdle {
            session.replaceGeneration(testSessionObservation(entries = entries))
            observation.value = session.observation.value
        }
        composeRule.onAllNodesWithTag("recording-confirmation-back").assertCountEquals(0)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.recording_action_connection))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, mutations) }
    }

    @Test
    fun replacementSessionCannotRefreshOrAuthorizeOpenDetails() {
        val session = FakeTvheadendSession(testSessionObservation(entries = listOf(
            recording(7, "Opening Film", path = "saved.ts"),
        )))
        val observation = mutableStateOf(session.observation.value)
        val openingCapability = requireNotNull(observation.value.currentSession)
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(sessionObservation = observation.value) }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.runOnIdle {
            session.replaceGeneration(testSessionObservation(entries = listOf(
                recording(7, "Replacement Film", path = "replacement.ts"),
            )))
            observation.value = session.observation.value
            assertNotSame(openingCapability, observation.value.currentSession)
        }
        composeRule.onNode(
            hasTestTag("recording-details-panel") and hasAnyDescendant(hasText("Opening Film"))
        ).assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-details-play").assertCountEquals(0)
        composeRule.onAllNodesWithTag("recording-details-delete").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithTag("recording-details-close").assertIsFocused()
    }

    @Test
    fun syntheticThreeHundredRecordingArchiveListRemainsScrollable() {
        val entries = (1..300).map { id ->
            recording(
                id = id,
                title = "Recording $id",
                path = "recording-$id.ts",
                start = id.toLong(),
                stop = id + 60L,
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recordings-archive-list").performScrollToIndex(299)
        composeRule.onNodeWithText("Recording 1").assertIsDisplayed()
    }

    @Test
    fun removingFocusedOffscreenRecordingRestoresFocusAtStartOfList() {
        var entries by mutableStateOf(
            (1..50).map { id ->
                recording(
                    id = id,
                    title = "Recording $id",
                    path = "recording-$id.ts",
                    start = id.toLong(),
                    stop = id + 60L,
                )
            }
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recordings-archive-list").performScrollToIndex(49)
        composeRule.onNodeWithTag("recording-list-entry-1").requestFocus().assertIsFocused()
        composeRule.runOnIdle { entries = entries.filterNot { it.id == DvrEntryId(1) } }

        waitForFocus("recording-list-entry-50")
    }

    @Test
    fun removingFocusedFolderPreviewRecordingRestoresFirstRemainingPreview() {
        var entries by mutableStateOf(
            (1..2).map { id ->
                recording(
                    id = id,
                    title = "Recording $id",
                    path = "News/recording-$id.ts",
                    start = id.toLong(),
                    stop = id + 60L,
                )
            }
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithTag("recordings-folder-News").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("folder-preview-recording-2").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("folder-preview-recording-1").assertIsFocused()
        composeRule.runOnIdle { entries = entries.filterNot { it.id == DvrEntryId(1) } }

        composeRule.onNodeWithTag("folder-preview-recording-2").assertIsFocused()
    }

    @Test
    fun selectedRecordingShowsFullMetadataInPersistentPane() {
        val entries = listOf(
            recording(
                id = 9,
                title = "A complete recording title that must remain readable",
                path = "Sport/highlights.ts",
                stop = 3_700L,
                channelName = "ORF SPORT +",
                subtitle = "Race highlights",
                summary = "The complete programme summary.",
                description = "A longer recording description shown in the detail pane.",
                episode = EpgEpisode(
                    id = null,
                    seriesLinkId = null,
                    seasonNumber = 2,
                    seasonCount = null,
                    episodeNumber = 4,
                    episodeCount = null,
                    partNumber = null,
                    partCount = null,
                    onscreen = null,
                ),
            )
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        composeRule.onNodeWithText("Race highlights").assertIsDisplayed()
        composeRule.onNodeWithTag("recordings-folder-Sport").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-metadata-pane").assertIsDisplayed()
        composeRule.onAllNodesWithText("Race highlights").assertCountEquals(2)
        composeRule.onNode(
            hasTestTag("recording-metadata-pane") and hasAnyDescendant(
                hasText("A complete recording title that must remain readable")
            )
        )
            .assertIsDisplayed()
        composeRule.onNodeWithText("ORF SPORT +", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("The complete programme summary.").assertIsDisplayed()
        composeRule.onNodeWithText("A longer recording description shown in the detail pane.")
            .assertIsDisplayed()
    }

    @Test
    fun longRecordingTitleDoesNotMoveLeadingOrTrailingContent() {
        val entries = listOf(
            recording(id = 1, title = "News", path = "recording-1.ts"),
            recording(
                id = 2,
                title = "A deliberately long recording title that wraps onto a second line",
                path = "recording-2.ts",
            ),
        )

        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
        }

        val shortRow = composeRule.onNodeWithTag("recording-list-entry-1")
            .fetchSemanticsNode().boundsInRoot
        val longRow = composeRule.onNodeWithTag("recording-list-entry-2")
            .fetchSemanticsNode().boundsInRoot
        val shortLeading = composeRule.onNodeWithTag(
            "recording-list-leading-1",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val longLeading = composeRule.onNodeWithTag(
            "recording-list-leading-2",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val shortTrailing = composeRule.onNodeWithTag(
            "recording-list-trailing-1",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val longTrailing = composeRule.onNodeWithTag(
            "recording-list-trailing-2",
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val shortHeadline = composeRule.onNodeWithTag(
            "recording-list-headline-1",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot
        val longHeadline = composeRule.onNodeWithTag(
            "recording-list-headline-2",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot

        assertEquals(shortRow.height, longRow.height, 1f)
        assertEquals(shortLeading.top - shortRow.top, longLeading.top - longRow.top, 1f)
        assertEquals(shortTrailing.top - shortRow.top, longTrailing.top - longRow.top, 1f)
        assertEquals(shortHeadline.height, longHeadline.height, 1f)
        assertTrue(longHeadline.right <= longTrailing.left)
    }

    private fun recording(
        id: Int,
        title: String,
        state: DvrEntryState = DvrEntryState.COMPLETED,
        path: String? = null,
        start: Long = 100L,
        stop: Long = 200L,
        playPositionSeconds: Long? = null,
        channelName: String? = null,
        subtitle: String? = null,
        summary: String? = null,
        description: String? = null,
        episode: EpgEpisode? = null,
        fileSizeBytes: Long? = null,
    ): DvrEntry = DvrEntry.create(
        id = DvrEntryId(id.toLong()),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
        state = state,
        path = path,
        files = path?.let {
            listOf(
                DvrRecordingFile(
                    fileId = null,
                    path = it,
                    start = null,
                    stop = null,
                    sizeBytes = fileSizeBytes,
                )
            )
        },
        playPosition = playPositionSeconds?.seconds,
        channelName = channelName,
        subtitle = subtitle,
        summary = summary,
        description = description,
        episode = episode,
    )

    private fun waitForFocus(tag: String) {
        composeRule.waitUntilExactlyOneExists(
            hasTestTag(tag) and isFocused(),
            timeoutMillis = 5_000,
        )
        composeRule.onNodeWithTag(tag).assertIsFocused()
    }

    private fun SemanticsNodeInteraction.pressCenter() =
        performKeyInput { pressKey(Key.DirectionCenter) }
}
