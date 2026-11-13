package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.readRecordingPattern
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class AutomaticRecordingEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wordsEntryPreservesRawExpressionAndReturnsWithoutSubmitting() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: AutomaticRecordingEditorState
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            state = remember {
                fixture.state(newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(
                    pattern = readRecordingPattern("A|B"),
                ), editing = true)
            }
            TVHeadendPlayerTheme { AutomaticRecordingEditor(state) }
        }
        compose.onNodeWithTag("automatic-recording-main").assertIsNotEnabled()
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-episode-1")
            .assertTextContains(requireNotNull(automaticRecordingMatches.rows.first().title))
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").assertTextContains("A|B")
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").performTextReplacement("A|B$")
        compose.onNodeWithTag("automatic-recording-words-input").performImeAction()
        compose.onNodeWithTag("automatic-recording-words").assertIsFocused()
        compose.runOnIdle {
            assertTrue(state.draft.pattern.rawExpression)
            assertEquals("A|B$", state.draft.toUpdateRequest(state.baseline)?.title)
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-search-in").assertIsFocused()
        compose.runOnIdle { assertTrue(state.draft.fullText) }
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").performTextReplacement("discard this")
        compose.pressKey(Key.Back)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-words").assertIsFocused()
        compose.runOnIdle {
            assertEquals("A|B$", state.draft.pattern.words)
            assertTrue(fixture.submissions.isEmpty())
        }
    }

    @Test fun levelOpeningReleaseTickAndBackRestoreFocusAndLeaveSafely() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: AutomaticRecordingEditorState
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            state = remember { fixture.state(automaticRecordingSeries.copy(days = setOf(DayOfWeek.SATURDAY))) }
            TVHeadendPlayerTheme { AutomaticRecordingEditor(state, channels = automaticRecordingChannels) }
        }
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-done").assertIsFocused()
        compose.runOnIdle {
            assertEquals(AutomaticRecordingEditorLevel.UPCOMING, state.navigation.page.level)
            assertTrue(fixture.submissions.isEmpty())
        }
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsOn()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsOff()
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-keep-editing").assertIsFocused()
        compose.pressKey(Key.Back, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        compose.runOnIdle {
            state.updateDraft { it.copy(channel = RecordingRuleChannel.SpecificChannel(ChannelId(999))) }
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-channel-999").assertIsFocused()
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(DayOfWeek.entries.toSet(), state.draft.days)
            assertEquals(DayOfWeek.entries.toSet(), state.draft.toUpdateRequest(state.baseline)?.daysOfWeekMask)
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(DayOfWeek.entries.toSet(), state.draft.days) }
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOn()
        compose.pressKey(Key.DirectionLeft)
        compose.runOnIdle { state.updateDraft { it.copy(days = emptySet()) } }
        compose.onNodeWithTag("automatic-recording-days").assertTextContains("No day")
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.onNodeWithTag("automatic-recording-day-1").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
        verifyResetRestoresFirstMoreRow(state)
        verifyMinuteChoiceReturnsToWindow(state)
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("automatic-recording-discard").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(1, fixture.closes)
            assertTrue(fixture.submissions.isEmpty())
        }
    }

    private fun verifyResetRestoresFirstMoreRow(state: AutomaticRecordingEditorState) {
        compose.runOnIdle { state.updateDraft { it.copy(priority = DvrPriority.HIGH) } }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        repeat(6) { compose.pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("automatic-recording-reset-options").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-duplicate").assertIsFocused()
        compose.runOnIdle { assertEquals(state.baseline.priority, state.draft.priority) }
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
    }

    private fun verifyMinuteChoiceReturnsToWindow(state: AutomaticRecordingEditorState) {
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        for (after in listOf(true, false)) {
            val original = compose.runOnIdle { state.draft.startWindow }
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionDown)
            val hour = if (after) 1 else 2
            compose.onNodeWithTag("automatic-recording-hour-$hour").assertIsFocused()
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.Back)
            compose.onNodeWithTag("automatic-recording-hour-$hour").assertIsFocused()
            compose.runOnIdle { assertEquals(original, state.draft.startWindow) }
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            val time = LocalTime.of(hour, if (after) 5 else 10)
            compose.onNodeWithTag("automatic-recording-${if (after) "after" else "before"}")
                .assertIsFocused().assertTextContains(formatClock(time))
            compose.runOnIdle {
                assertEquals(if (after) RecordingStartWindow.After(time) else RecordingStartWindow.Before(time),
                    state.draft.startWindow)
            }
        }
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
    }
}
