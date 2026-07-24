package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
                            mapOf("filename" to "/recordings/evening-news.ts", "size" to 500L)
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

        composeRule.onNodeWithText("Evening News").performClick()
        composeRule.onNodeWithText("Play").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(7, playedRecordingId) }
        composeRule.onNodeWithText("Delete recording").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Delete this recording?").assertIsDisplayed()
    }
}
