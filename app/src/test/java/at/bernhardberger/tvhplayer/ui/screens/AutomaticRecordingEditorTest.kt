package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.key
import at.bernhardberger.tvhplayer.core.resolveOccurrenceOn
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.readRecordingPattern
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.core.newTimerDraft
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
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class AutomaticRecordingEditorTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun programmeDetailsOpensRestoresAndCreatesThroughTheEditorHost() {
        val session = automaticRecordingFakeSession(emptyList())
        val repository = session.dvrRepository
        val requests = mutableListOf<AutorecRuleCreate>()
        repository.scriptCreateAutorecRule(DvrMutationResult.Confirmed(AutorecRuleId("created")))
        val mutations = DvrMutationActions(
            repository::scheduleEntry, repository::stopEntry, repository::cancelEntry,
            repository::deleteEntry, repository::updateWatchState,
            createAutorecRule = { current, request ->
                requests += request
                repository.createAutorecRule(current, request)
            },
        )
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        val settings = UiSettingsStore(InMemoryPreferencesDataStore())
        compose.setContent {
            val observation by session.observation.collectAsState()
            val scope = rememberCoroutineScope()
            TVHeadendPlayerTheme {
                ProgrammeDetailsHost(
                    automaticRecordingEvents.first(), session.observation.value, observation,
                    { session.observation.value }, { automaticRecordingNow.epochSeconds },
                    showContextMenu = false, onShowDetails = {}, mutations, notices, scope,
                    remember { mutableStateOf(null) }, onPlay = { _, _ -> }, onPlayRecording = null,
                    onClose = {}, settingsStore = settings, session = session,
                )
            }
        }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("details-record_series").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        assertTrue(requests.isEmpty())
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("details-record_series").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("details-record_series").assertIsFocused()
        assertEquals(1, requests.size)
        assertEquals(automaticRecordingEvents.first().seriesLinkUri, requests.single().seriesLinkUri)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CREATE_AUTOREC_RULE })
        assertTrue(notices.state.value.pending.isEmpty())
    }

    @Test fun timerRepeatKeepsExistingTypeAndHidesDate() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: TimerRecordingEditorState
        var replacement by mutableStateOf<TimerRecordingEditorState?>(null)
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            val initial = remember { fixture.timer(newTimerDraft(automaticRecordingNow, automaticRecordingZone).copy(
                channelId = ChannelId(101), days = setOf(DayOfWeek.MONDAY), existingTimer = true,
            )) }
            state = replacement ?: initial
            TVHeadendPlayerTheme { TimerRecordingEditor(state, automaticRecordingChannels) }
        }
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-date").assertDoesNotExist()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-done").assertIsFocused()
        compose.onNodeWithTag("timer-recording-never").assertDoesNotExist()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOn()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOff()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-repeat").assertIsFocused()
        compose.onNodeWithTag("timer-recording-date").assertDoesNotExist()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-main").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(state.draft.repeating)
            assertTrue(state.draft.days.isEmpty())
            assertTrue(fixture.timerSubmissions.isEmpty())
            replacement = fixture.timer()
        }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-date").assertExists()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-never").assertIsOn()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOn()
        compose.onNodeWithTag("timer-recording-never").assertIsOff()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-never").assertIsOn()
        compose.onNodeWithTag("timer-recording-day-1").assertIsOff()
        compose.runOnIdle {
            assertTrue(state.draft.days.isEmpty())
            assertTrue(fixture.timerSubmissions.isEmpty())
            state.updateDraft { it.copy(channelId = ChannelId(101)) }
        }
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-hour-${state.draft.start.hour}").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-start").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("timer-recording-main").assertIsFocused()
        compose.runOnIdle {
            fixture.timerNow = state.draft.resolveOccurrenceOn(state.draft.date, state.zone).stop
        }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-main").assertIsNotEnabled()
        compose.onNodeWithText(org.robolectric.RuntimeEnvironment.getApplication().getString(
            at.bernhardberger.tvhplayer.R.string.timer_editor_time_passed,
        )).assertExists()
    }

    @Test fun timerHostRefusesMissingChannelWithoutLosingTheDraft() {
        val sessions = List(2) { automaticRecordingFakeSession(emptyList()) }
        val notices = List(2) { NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) }) }
        var repeating by mutableStateOf(false)
        var closes = 0
        compose.setContent {
            key(repeating) {
                val index = if (repeating) 1 else 0
                val session = sessions[index]
                val current = remember { session.captureCurrentSession() }
                TVHeadendPlayerTheme {
                    TimerRecordingEditorHost(
                        current, session.observation, remember { DvrMutationActions(session.dvrRepository) },
                        notices[index], now = { automaticRecordingNow }, onClose = { closes++ },
                        startingRepeating = repeating,
                    )
                }
            }
        }
        for (index in sessions.indices) {
            compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.Back)
            compose.pressKey(Key.DirectionUp)
            compose.onNodeWithTag("timer-recording-main").assertIsFocused()
            compose.runOnIdle {
                sessions[index].publish(automaticRecordingObservation(emptyList(), emptyList()))
            }
            compose.pressKey(Key.DirectionCenter)
            compose.onNodeWithTag("timer-recording-main").assertIsFocused()
            compose.runOnIdle {
                assertEquals(0, closes)
                assertEquals(1, notices[index].state.value.pending.size)
                assertTrue(sessions[index].calls.none {
                    it == FakeSessionCall.DVR_SCHEDULE_ENTRY || it == FakeSessionCall.DVR_CREATE_TIMEREC_RULE
                })
            }
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.onNodeWithTag("timer-recording-channel-101").assertIsFocused()
            if (index == 0) compose.runOnIdle { repeating = true }
        }
    }

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
