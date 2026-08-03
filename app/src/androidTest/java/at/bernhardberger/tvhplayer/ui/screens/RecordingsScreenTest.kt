package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import at.bernhardberger.tvheadend.client.RecordingProgressCapability
import at.bernhardberger.tvheadend.core.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.testing.DvrTestMessage as HtspMessage
import at.bernhardberger.tvhplayer.testing.TestDvrRuntime as DvrRepository
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class RecordingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modeTabsUseTheTitleLeadingAnchorOnTheirOwnRow() {
        val repository = DvrRepository()

        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreen(
                    contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
                    repository = repository,
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
        val repository = DvrRepository()
        repository.applyAuthenticatedDvrAccess(true)
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(
                            mapOf("filename" to "News/evening-news.ts", "size" to 500L)
                        ),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreen(
                    repository = repository,
                )
            }
        }

        composeRule.onNodeWithTag("recordings-folder-News").assertIsDisplayed()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused().performClick()
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithText("Evening News").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-list-entry-7").performClick()
        composeRule.onNodeWithTag("recording-details-play").assertIsDisplayed().assertIsFocused()
        composeRule.onNodeWithTag("recording-details-delete").performClick()
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
            .performKeyInput { pressKey(Key.Back) }
        composeRule.onNodeWithTag("recording-details-delete").assertIsFocused()
    }

    @Test
    fun movingUpFromFolderPreviewReturnsToSelectedModeWithoutChangingIt() {
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "News/evening-news.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
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
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "News/evening-news.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithTag("recordings-folder-News")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("folder-preview-recording-7")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused().performClick()
        composeRule.onNodeWithTag("recording-list-entry-7")
            .assertIsFocused()
            .performClick()
        composeRule.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-play")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-7")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        composeRule.onNodeWithTag("recordings-folder-News").assertIsFocused()
    }

    @Test
    fun closingDetailsRestoresRecordingWhenAutomaticInitialFocusIsDisabled() {
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "evening-news.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreen(
                    repository = repository,
                    initialFocusEnabled = false,
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7")
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
        composeRule.onNodeWithTag("recording-details-close").performClick()

        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused()
    }

    @Test
    fun detailsConsumeBackBeforeTheShellAndRestoreTheRecording() {
        var shellBackCount = 0
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "evening-news.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box {
                    RecordingsScreen(repository = repository)
                    BackHandler { shellBackCount++ }
                }
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7").performClick()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }

        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, shellBackCount) }
    }

    @Test
    fun shortDetailsKeepPlaybackActionsAdjacentToMetadata() {
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 8,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Short programme",
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "short.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }
        composeRule.onNodeWithTag("recording-list-entry-8").performClick()

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
        var playbackIntent: RecordingPlaybackIntent? = null
        val repository = DvrRepository()
        repository.applyAuthenticatedDvrAccess(true)
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 7_300L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "playposition" to 3_723L,
                        "files" to listOf(mapOf("filename" to "evening-news.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreen(
                    repository = repository,
                    progressCapabilityOverride = RecordingProgressCapability.ReadOnly,
                    onPlayRecording = { _, intent -> playbackIntent = intent },
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-7").performClick()
        composeRule.onNodeWithText(
            "Existing resume progress is available. New progress won’t be saved."
        ).assertIsDisplayed()
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
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryUpdate",
                    seq = null,
                    fields = mapOf("id" to 7, "playposition" to 3_800L),
                )
            )
        }
        composeRule.onNodeWithTag("recording-details-beginning").assertIsFocused().performClick()
        composeRule.runOnIdle {
            assertEquals(RecordingPlaybackIntent.FromBeginning, playbackIntent)
        }
        composeRule.onAllNodesWithTag("recording-details-panel").assertCountEquals(0)
    }

    @Test
    fun disappearingResumeFallsBackToPlayAndLegacyPlaybackStartsOver() {
        var playbackIntent: RecordingPlaybackIntent? = null
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 8,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 3_700L,
                        "title" to "Documentary",
                        "state" to "completed",
                        "playposition" to 600L,
                        "files" to listOf(mapOf("filename" to "documentary.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        val capability = mutableStateOf(RecordingProgressCapability.Full)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                RecordingsScreen(
                    repository = repository,
                    progressCapabilityOverride = capability.value,
                    onPlayRecording = { _, intent -> playbackIntent = intent },
                )
            }
        }

        composeRule.onNodeWithTag("recording-list-entry-8").performClick()
        composeRule.onNodeWithTag("recording-details-resume").assertIsFocused()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryUpdate",
                    seq = null,
                    fields = mapOf("id" to 8, "playposition" to 100L),
                )
            )
        }
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused()

        composeRule.runOnIdle { capability.value = RecordingProgressCapability.Unsupported }
        composeRule.onNodeWithText(
            "This server supports playback from the beginning, but not synchronized progress."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("recording-details-play").assertIsFocused().performClick()
        composeRule.runOnIdle {
            assertEquals(RecordingPlaybackIntent.FromBeginning, playbackIntent)
        }
    }

    @Test
    fun browserLocationAndFocusSurviveLeavingAndReturningToTheScreen() {
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 7,
                        "channelId" to 1,
                        "start" to 100L,
                        "stop" to 200L,
                        "title" to "Evening News",
                        "state" to "completed",
                        "files" to listOf(
                            mapOf("filename" to "News/evening-news.ts", "size" to 500L)
                        ),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }
        val screenState = RecordingsScreenState()
        val visible = mutableStateOf(true)

        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (visible.value) {
                    RecordingsScreen(repository = repository, state = screenState)
                }
            }
        }

        composeRule.onNodeWithTag("recordings-folder-News").performClick()
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsFocused()
        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithTag("recording-list-entry-7").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun archiveDoesNotMixScheduledOrFailedEntries() {
        val start = System.currentTimeMillis() / 1000L + 3600L
        val repository = DvrRepository()
        runBlocking {
            listOf(
                mapOf(
                    "id" to 1, "title" to "Saved Film", "state" to "completed",
                    "files" to listOf(mapOf("filename" to "saved.ts")),
                ),
                mapOf("id" to 2, "title" to "Future Show", "state" to "scheduled"),
                mapOf("id" to 3, "title" to "Failed Show", "state" to "failed"),
            ).forEach { fields ->
                repository.acceptDvrMessage(
                    HtspMessage(
                        method = "dvrEntryAdd",
                        seq = null,
                        fields = fields + mapOf(
                            "channelId" to 1,
                            "start" to start,
                            "stop" to start + 3600L,
                        ),
                    )
                )
            }
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithText("Saved Film").assertIsDisplayed()
        composeRule.onAllNodesWithText("Future Show").assertCountEquals(0)
        composeRule.onAllNodesWithText("Failed Show").assertCountEquals(0)
        composeRule.onNodeWithText("Schedule").performClick()
        composeRule.onNodeWithTag("recordings-schedule-list").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-metadata-pane").assertCountEquals(0)
        composeRule.onNodeWithText("Future Show").assertIsDisplayed()
        composeRule.onNodeWithTag("recording-list-entry-2").performClick()
        composeRule.onNodeWithTag("recording-details-panel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel recording").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onNodeWithText("Problems").performClick()
        composeRule.onNodeWithTag("recordings-problems-list").assertIsDisplayed()
        composeRule.onNodeWithText("Failed").assertIsDisplayed()
        composeRule.onAllNodesWithTag("recording-metadata-pane").assertCountEquals(0)
        composeRule.onNodeWithText("Failed Show").assertIsDisplayed()
    }

    @Test
    fun syntheticThreeHundredRecordingArchiveListRemainsScrollable() {
        val repository = DvrRepository()
        runBlocking {
            (1..300).forEach { id ->
                repository.acceptDvrMessage(
                    HtspMessage(
                        method = "dvrEntryAdd",
                        seq = null,
                        fields = mapOf(
                            "id" to id,
                            "channelId" to 1,
                            "start" to id.toLong(),
                            "stop" to id + 60L,
                            "title" to "Recording $id",
                            "state" to "completed",
                            "files" to listOf(mapOf("filename" to "recording-$id.ts")),
                        ),
                    )
                )
            }
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithTag("recordings-archive-list").performScrollToIndex(299)
        composeRule.onNodeWithText("Recording 1").assertIsDisplayed()
    }

    @Test
    fun removingFocusedOffscreenRecordingRestoresFocusAtStartOfList() {
        val repository = DvrRepository()
        runBlocking {
            (1..50).forEach { id ->
                repository.acceptDvrMessage(
                    HtspMessage(
                        method = "dvrEntryAdd",
                        seq = null,
                        fields = mapOf(
                            "id" to id,
                            "channelId" to 1,
                            "start" to id.toLong(),
                            "stop" to id + 60L,
                            "title" to "Recording $id",
                            "state" to "completed",
                            "files" to listOf(mapOf("filename" to "recording-$id.ts")),
                        ),
                    )
                )
            }
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithTag("recordings-archive-list").performScrollToIndex(49)
        composeRule.onNodeWithTag("recording-list-entry-1").requestFocus().assertIsFocused()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryDelete",
                    seq = null,
                    fields = mapOf("id" to 1),
                )
            )
        }

        composeRule.onNodeWithTag("recording-list-entry-50").assertIsFocused()
    }

    @Test
    fun removingFocusedFolderPreviewRecordingRestoresFirstRemainingPreview() {
        val repository = DvrRepository()
        runBlocking {
            (1..2).forEach { id ->
                repository.acceptDvrMessage(
                    HtspMessage(
                        method = "dvrEntryAdd",
                        seq = null,
                        fields = mapOf(
                            "id" to id,
                            "channelId" to 1,
                            "start" to id.toLong(),
                            "stop" to id + 60L,
                            "title" to "Recording $id",
                            "state" to "completed",
                            "files" to listOf(mapOf("filename" to "News/recording-$id.ts")),
                        ),
                    )
                )
            }
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithTag("recordings-folder-News").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("folder-preview-recording-2").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("folder-preview-recording-1").assertIsFocused()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryDelete",
                    seq = null,
                    fields = mapOf("id" to 1),
                )
            )
        }

        composeRule.onNodeWithTag("folder-preview-recording-2").assertIsFocused()
    }

    @Test
    fun selectedRecordingShowsFullMetadataInPersistentPane() {
        val repository = DvrRepository()
        runBlocking {
            repository.acceptDvrMessage(
                HtspMessage(
                    method = "dvrEntryAdd",
                    seq = null,
                    fields = mapOf(
                        "id" to 9,
                        "channelId" to 1,
                        "channelName" to "ORF SPORT +",
                        "start" to 100L,
                        "stop" to 3700L,
                        "title" to "A complete recording title that must remain readable",
                        "subtitle" to "Race highlights",
                        "summary" to "The complete programme summary.",
                        "description" to "A longer recording description shown in the detail pane.",
                        "seasonNumber" to 2,
                        "episodeNumber" to 4,
                        "state" to "completed",
                        "files" to listOf(mapOf("filename" to "Sport/highlights.ts")),
                    ),
                )
            )
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
        }

        composeRule.onNodeWithText("Race highlights").assertIsDisplayed()
        composeRule.onNodeWithTag("recordings-folder-Sport").performClick()
        composeRule.onNodeWithTag("recording-metadata-pane").assertIsDisplayed()
        composeRule.onAllNodesWithText("Race highlights").assertCountEquals(2)
        composeRule.onNodeWithText("A complete recording title that must remain readable")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ORF SPORT +").assertIsDisplayed()
        composeRule.onNodeWithText("The complete programme summary.").assertIsDisplayed()
        composeRule.onNodeWithText("A longer recording description shown in the detail pane.")
            .assertIsDisplayed()
    }

    @Test
    fun longRecordingTitleDoesNotMoveLeadingOrTrailingContent() {
        val repository = DvrRepository()
        runBlocking {
            listOf(
                1 to "News",
                2 to "A deliberately long recording title that wraps onto a second line",
            ).forEach { (id, title) ->
                repository.acceptDvrMessage(
                    HtspMessage(
                        method = "dvrEntryAdd",
                        seq = null,
                        fields = mapOf(
                            "id" to id,
                            "channelId" to 1,
                            "start" to 100L,
                            "stop" to 200L,
                            "title" to title,
                            "state" to "completed",
                            "files" to listOf(mapOf("filename" to "recording-$id.ts")),
                        ),
                    )
                )
            }
            repository.acceptDvrMessage(
                HtspMessage(method = "initialSyncCompleted", seq = null, fields = emptyMap())
            )
        }

        composeRule.setContent {
            TVHeadendPlayerTheme { RecordingsScreen(repository = repository) }
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
}
