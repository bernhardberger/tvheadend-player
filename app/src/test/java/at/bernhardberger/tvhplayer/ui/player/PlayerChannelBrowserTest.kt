package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerChannelBrowserTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PlayerChannelBrowserState())
    private var shown by mutableStateOf(true)
    private var active by mutableStateOf(true)
    private var channels by mutableStateOf((1L..16L).map { Channel.create(ChannelId(it), name = "Channel $it", number = it) })
    private var recent by mutableStateOf(channels.drop(1).reversed())
    private val picks = mutableListOf<ChannelId>()
    private val details = mutableListOf<ChannelId>()
    private val closes = mutableListOf<Int?>()

    private fun screen(events: Boolean = true) {
        compose.setContent {
            TVHeadendPlayerTheme {
                Column(Modifier.fillMaxSize()) {
                    if (shown) {
                        PlayerChannelBrowser(state, channels, recent, ChannelId(1), emptySet(),
                            { id -> if (events) EpgEvent.create(EventId(id.value), id, Instant.fromEpochSeconds(1),
                                Instant.fromEpochSeconds(100), title = "Programme ${id.value}") else null },
                            50, active, {}, { picks += it.id }, { channel, _, _ -> details += channel.id },
                            { closes += it }, channelsTitle = "Sport")
                        PlayerChannelBrowserHint(state, recent.isNotEmpty(), "Sport", Modifier.testTag("hint"))
                    }
                }
            }
        }
        compose.settleFrames(1, frameMillis = 600)
    }

    private fun focused(id: Long) = compose.onNodeWithTag("player-channel-card-$id").assertIsFocused()

    @Test fun rowsReopenDirectlyOnTheirIndependentCardAndViewport() {
        screen()
        focused(1)
        repeat(9) { compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600) }
        focused(10)
        val onNowViewport = state.onNow.listState.firstVisibleItemIndex to state.onNow.listState.firstVisibleItemScrollOffset
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        focused(16)
        repeat(5) { compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600) }
        focused(11)
        val recentViewport = state.recent.listState.firstVisibleItemIndex to state.recent.listState.firstVisibleItemScrollOffset
        compose.runOnIdle { shown = false }; compose.settleFrames(1, frameMillis = 600)
        compose.runOnIdle { shown = true }; compose.settleFrames(1, frameMillis = 600)
        focused(11)
        assertEquals(recentViewport, state.recent.listState.firstVisibleItemIndex to state.recent.listState.firstVisibleItemScrollOffset)
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        focused(10)
        assertEquals(onNowViewport, state.onNow.listState.firstVisibleItemIndex to state.onNow.listState.firstVisibleItemScrollOffset)
        assertTrue(picks.isEmpty())
        assertTrue(closes.isEmpty())
    }

    @Test fun downStopsAtTheLastRowAndUpFromTheFirstClosesTheRail() {
        screen()
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        focused(16)
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertTrue(closes.isEmpty())
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_UP), closes)
    }

    @Test fun firstRowIsNamedAfterItsGroupAndTheHintNamesWhereUpGoes() {
        fun hint(text: String) = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("hint")))
        screen()
        compose.onNode(hasText("Sport") and hasAnyAncestor(hasTestTag("player-channel-row-ON_NOW"))).assertExists()
        hint("Back to TV").assertCountEquals(1)
        hint("Sport").assertCountEquals(0)
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        hint("Sport").assertCountEquals(1)
        hint("Back to TV").assertCountEquals(0)
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        hint("Back to TV").assertCountEquals(1)
    }

    @Test fun leftFromAFirstCardStaysOnItInEitherRow() {
        screen()
        compose.pressKey(Key.DirectionLeft, settleFrames = 1, frameMillis = 600)
        focused(1)
        compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600)
        focused(2)
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        focused(16)
        compose.pressKey(Key.DirectionLeft, settleFrames = 1, frameMillis = 600)
        focused(16)
        compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600)
        focused(15)
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        compose.pressKey(Key.DirectionLeft, settleFrames = 1, frameMillis = 600)
        focused(1)
    }

    @Test fun theFocusedRowKeepsOnePlace() {
        screen()
        fun title(row: PlayerChannelRow) = compose.onNodeWithTag("player-channel-row-${row.name}")
            .fetchSemanticsNode().boundsInRoot.top
        val onNow = title(PlayerChannelRow.ON_NOW)
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        assertEquals(onNow, title(PlayerChannelRow.RECENT), .5f)
    }

    @Test fun withoutRecentChannelsThereIsOneRowAndDownStaysOnIt() {
        recent = emptyList()
        screen()
        compose.onNodeWithTag("player-channel-row-RECENT").assertDoesNotExist()
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        focused(1)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertTrue(closes.isEmpty())
    }

    @Test fun removedChannelFallsBackAndNewSessionDropsBothCollections() {
        screen()
        compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600)
        focused(2)
        compose.runOnIdle { channels = channels.filterNot { it.id == ChannelId(2) } }; compose.settleFrames(1, frameMillis = 600)
        assertEquals("removed ID uses playing fallback", ChannelId(1), state.onNow.focusedId)
        focused(1)
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        focused(16)
        compose.runOnIdle { state = PlayerChannelBrowserState() }; compose.settleFrames(1, frameMillis = 600)
        focused(1)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertNull(state.recent.focusedId)
    }

    @Test fun aRowChangeReleasedWhileTheRailWasAwayDoesNotSwallowTheNextPress() {
        screen()
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionUp) }; compose.settleFrames(1, frameMillis = 600)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        compose.runOnIdle { shown = false }; compose.settleFrames(1, frameMillis = 600)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionUp) }
        compose.runOnIdle { shown = true }; compose.settleFrames(1, frameMillis = 600)
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_UP), closes)
    }

    @Test fun aRowThatEmptiesDoesNotComeBackFocused() {
        screen()
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        val history = recent
        compose.runOnIdle { recent = emptyList() }; compose.settleFrames(1, frameMillis = 600)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        compose.runOnIdle { recent = history }; compose.settleFrames(1, frameMillis = 600)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        focused(1)
    }

    @Test fun newSessionOnTheSameRowStartsOverOnThePlayingChannel() {
        screen()
        repeat(3) { compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600) }
        focused(4)
        compose.runOnIdle { state = PlayerChannelBrowserState() }; compose.settleFrames(1, frameMillis = 600)
        focused(1)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertEquals(ChannelId(1), state.onNow.focusedId)
    }

    @Test fun retainedAccessibilityActionsCannotActOnceTheRailStopsOwningInput() {
        screen()
        fun actions() = compose.onNodeWithTag("player-channel-card-1").fetchSemanticsNode().config.let {
            it[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!! to
                it[androidx.compose.ui.semantics.SemanticsActions.OnLongClick].action!!
        }
        val (click, longClick) = actions()
        compose.runOnIdle { active = false }; compose.settleFrames(1, frameMillis = 600)
        compose.runOnIdle { click(); longClick() }
        compose.runOnIdle { active = true }; compose.settleFrames(1, frameMillis = 600)
        val (keptClick, keptLongClick) = actions()
        compose.runOnIdle { shown = false }; compose.settleFrames(1, frameMillis = 600)
        compose.runOnIdle { keptClick(); keptLongClick() }
        assertTrue(picks.isEmpty())
        assertTrue(details.isEmpty())
    }

    @Test fun heldUpMovesOnlyOneRowAndASecondPressReturnsToControls() {
        screen()
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionUp) { advanceEventTime(1_200) } }; compose.settleFrames(1, frameMillis = 600)
        focused(1)
        assertTrue(closes.isEmpty())
        compose.pressKey(Key.DirectionUp, settleFrames = 1, frameMillis = 600)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_UP), closes)
    }

    @Test fun onlyAHeldLeftOrRightScrollsAndReleasingEndsIt() {
        screen()
        compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600)
        compose.pressKey(Key.DirectionRight, settleFrames = 1, frameMillis = 600)
        assertFalse("separate presses are steps", state.scrolling)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionRight); advanceEventTime(1_200) }
        compose.waitForIdle()
        assertTrue("a held key repeats", state.scrolling)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionRight) }
        compose.settleFrames(1, frameMillis = 600)
        assertFalse("releasing ends the scroll", state.scrolling)
        assertTrue(picks.isEmpty())
    }

    @Test fun nativeLongClickNeverAlsoTunesAndHasAccessibleSecondaryAction() {
        screen()
        val card = compose.onNodeWithTag("player-channel-card-1")
        card.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionCenter) { advanceEventTime(1_200) } }; compose.settleFrames(1, frameMillis = 600)
        assertEquals(listOf(ChannelId(1)), details)
        assertTrue(picks.isEmpty())
        compose.pressKey(Key.DirectionCenter, settleFrames = 1, frameMillis = 600)
        assertEquals(listOf(ChannelId(1)), picks)
    }

    @Test fun withoutProgrammeHoldIsHandledButShortClickStillTunes() {
        screen(events = false)
        compose.onRoot().performKeyInput { withKeyDown(Key.Enter) { advanceEventTime(1_200) } }; compose.settleFrames(1, frameMillis = 600)
        assertTrue(details.isEmpty())
        assertTrue(picks.isEmpty())
        compose.pressKey(Key.Enter, settleFrames = 1, frameMillis = 600)
        assertEquals(listOf(ChannelId(1)), picks)
    }

    @Test fun outgoingRowCannotTuneThroughARetainedAccessibilityAction() {
        screen()
        val click = compose.onNodeWithTag("player-channel-card-1").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.pressKey(Key.DirectionDown, settleFrames = 1, frameMillis = 600)
        compose.runOnIdle { click() }
        assertEquals(PlayerChannelRow.RECENT, state.row)
        assertTrue(picks.isEmpty())
    }
}
