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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
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
import coil3.ImageLoader
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
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
        val entries = (1..50).map { id ->
            recording(id, "Recording $id", path = "recording-$id.ts", start = id.toLong())
        }
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries) }
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
            repeat(2) { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
        }
        composeRule.mainClock.autoAdvance = true
        waitForFocus("recording-list-entry-${50 - 2 * (visibleRows - 1)}")
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

    private fun assertGroupedPageDown(mode: DvrLibraryMode, pages: Int = 1) {
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
        composeRule.setContent {
            TVHeadendPlayerTheme { TestRecordingsScreen(entries = entries, state = screenState) }
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
        composeRule.onNodeWithTag("recording-list-entry-1").performKeyInput {
            repeat(pages) { pressKey(Key(KeyEvent.KEYCODE_CHANNEL_DOWN)) }
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
                assertTrue("Repeated paging must advance beyond the first page", focusedId > visibleRows)
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
        val entries = listOf(recording(7, "Saved Film", path = "saved.ts"))
        val session = FakeTvheadendSession(testSessionObservation(entries = entries))
        val observation = mutableStateOf(session.observation.value)
        var mutations = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                TestRecordingsScreen(
                    sessionObservation = observation.value,
                    onDeleteRecording = { _, _ -> mutations++; DvrMutationResult.Confirmed(Unit) },
                )
            }
        }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused().pressCenter()
        composeRule.onNodeWithTag("recording-details-play").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
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
