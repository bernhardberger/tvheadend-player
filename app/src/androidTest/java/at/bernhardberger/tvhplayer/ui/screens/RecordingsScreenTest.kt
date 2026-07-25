package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class RecordingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedRecordingOpensDetailsBeforeDeleteConfirmation() {
        var playedRecordingId: Int? = null
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
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
                    onPlayRecording = { playedRecordingId = it },
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
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(7, playedRecordingId) }
        composeRule.onNodeWithContentDescription("Delete recording")
            .assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Delete “Evening News”?").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsFocused()
    }

    @Test
    fun archiveDoesNotMixScheduledOrFailedEntries() {
        val start = System.currentTimeMillis() / 1000L + 3600L
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
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
        composeRule.onNodeWithText("Future Show").assertIsDisplayed()
        composeRule.onNodeWithText("Problems").performClick()
        composeRule.onNodeWithText("Failed Show").assertIsDisplayed()
    }

    @Test
    fun syntheticThreeHundredRecordingArchiveListRemainsScrollable() {
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
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
    fun selectedRecordingShowsFullMetadataInPersistentPane() {
        val repository = DvrRepository(
            htsp = HtspService(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
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

        composeRule.onNodeWithTag("recordings-folder-Sport").performClick()
        composeRule.onNodeWithTag("recording-metadata-pane").assertIsDisplayed()
        composeRule.onNodeWithText("A complete recording title that must remain readable")
            .assertIsDisplayed()
        composeRule.onNodeWithText("ORF SPORT +").assertIsDisplayed()
        composeRule.onNodeWithText("The complete programme summary.").assertIsDisplayed()
        composeRule.onNodeWithText("A longer recording description shown in the detail pane.")
            .assertIsDisplayed()
    }
}
