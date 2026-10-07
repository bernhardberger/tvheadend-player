package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlin.time.Instant
import org.junit.Rule
import org.junit.Test

class ProgrammeDetailsPanelTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recording = mutableStateOf<DvrEntry?>(null)
    private val canModify = mutableStateOf(true)
    private val event = EpgEvent.create(
        id = EventId(21), title = "Future programme",
        start = Instant.fromEpochSeconds(2_000),
        stop = Instant.fromEpochSeconds(3_000),
    )

    private fun showPanel(withDescription: Boolean = false) {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ProgrammeDetailsPanel(
                    event = if (withDescription) EpgEvent.create(
                        id = event.id, title = event.title, start = event.start, stop = event.stop,
                        description = "A programme description",
                    ) else event, channel = null,
                    recording = recording.value, nowSecProvider = { 1_000 },
                    canModifyRecordings = canModify.value,
                    onAction = {}, onClose = {},
                    imageLoader = androidx.compose.runtime.remember { coil3.ImageLoader.Builder(context).build() },
                    currentSession = null,
                )
            }
        }
    }

    @Test
    fun moreInfoKeepsFocusWhenPublicationReplacesRecordWithCancel() {
        showPanel(withDescription = true)
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText(context.getString(R.string.details_read_more)).assertIsFocused()
        publishScheduledRecording()
        composeRule.onNodeWithText(context.getString(R.string.details_read_more)).assertIsFocused()
    }

    @Test
    fun removedFocusedRecordActionFallsBackToNewCancelAction() {
        showPanel()
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
        publishScheduledRecording()
        composeRule.onNodeWithText(context.getString(R.string.cancel_recording)).assertIsFocused()
    }

    @Test
    fun removedMutationCapabilityFallsBackToPanel() {
        showPanel()
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
        composeRule.runOnIdle { canModify.value = false }
        composeRule.onNodeWithTag("programme-details-panel").assertIsFocused()
    }

    private fun publishScheduledRecording() {
        composeRule.runOnIdle {
            recording.value = DvrEntry.create(
                id = DvrEntryId(31), eventId = event.id, state = DvrEntryState.SCHEDULED,
            )
        }
    }
}
