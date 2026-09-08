package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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

    private fun showPanel() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ProgrammeDetailsPanel(
                    contentPadding = PaddingValues(), event = event, channel = null,
                    recording = recording.value, nowSecProvider = { 1_000 },
                    canModifyRecordings = canModify.value, actionResult = null,
                    onAction = {}, onClose = {},
                )
            }
        }
    }

    @Test
    fun closeKeepsFocusWhenPublicationReplacesRecordWithCancel() {
        showPanel()
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText(context.getString(R.string.close)).assertIsFocused()
        publishScheduledRecording()
        composeRule.onNodeWithText(context.getString(R.string.close)).assertIsFocused()
    }

    @Test
    fun removedFocusedRecordActionFallsBackToNewCancelAction() {
        showPanel()
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
        publishScheduledRecording()
        composeRule.onNodeWithText(context.getString(R.string.cancel_recording)).assertIsFocused()
    }

    @Test
    fun removedMutationCapabilityFallsBackToClose() {
        showPanel()
        composeRule.onNodeWithText(context.getString(R.string.record)).assertIsFocused()
        composeRule.runOnIdle { canModify.value = false }
        composeRule.onNodeWithText(context.getString(R.string.close)).assertIsFocused()
    }

    private fun publishScheduledRecording() {
        composeRule.runOnIdle {
            recording.value = DvrEntry.create(
                id = DvrEntryId(31), eventId = event.id, state = DvrEntryState.SCHEDULED,
            )
        }
    }
}
