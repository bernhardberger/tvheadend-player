package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
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
    private var channels by mutableStateOf((1L..16L).map { Channel.create(ChannelId(it), name = "Channel $it", number = it) })
    private var recent by mutableStateOf(channels.drop(1).reversed())
    private val picks = mutableListOf<ChannelId>()
    private val details = mutableListOf<ChannelId>()
    private val closes = mutableListOf<Int?>()

    private fun screen(events: Boolean = true) {
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        compose.setContent {
            TVHeadendPlayerTheme {
                Column(Modifier.fillMaxSize()) {
                    if (shown) {
                        PlayerChannelBrowser(state, channels, recent, ChannelId(1), emptySet(),
                            { id -> if (events) EpgEvent.create(EventId(id.value), id, Instant.fromEpochSeconds(1),
                                Instant.fromEpochSeconds(100), title = "Programme ${id.value}") else null },
                            50, loader, null, true, {}, { picks += it.id }, { channel, _, _ -> details += channel.id },
                            { closes += it }, channelsTitle = "Sport")
                        PlayerChannelBrowserHint(state, recent.isNotEmpty(), "Sport", Modifier.testTag("hint"))
                    }
                }
            }
        }
        settle()
    }

    private fun settle() { compose.mainClock.advanceTimeBy(600); compose.waitForIdle() }
    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; settle() }
    private fun focused(id: Long) = compose.onNodeWithTag("player-channel-card-$id").assertIsFocused()

    @Test fun rowsReopenDirectlyOnTheirIndependentCardAndViewport() {
        screen()
        focused(1)
        repeat(9) { key(Key.DirectionRight) }
        focused(10)
        val onNowViewport = state.onNow.listState.firstVisibleItemIndex to state.onNow.listState.firstVisibleItemScrollOffset
        key(Key.DirectionDown)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        focused(16)
        repeat(5) { key(Key.DirectionRight) }
        focused(11)
        val recentViewport = state.recent.listState.firstVisibleItemIndex to state.recent.listState.firstVisibleItemScrollOffset
        compose.runOnIdle { shown = false }; settle()
        compose.runOnIdle { shown = true }; settle()
        focused(11)
        assertEquals(recentViewport, state.recent.listState.firstVisibleItemIndex to state.recent.listState.firstVisibleItemScrollOffset)
        key(Key.DirectionUp)
        focused(10)
        assertEquals(onNowViewport, state.onNow.listState.firstVisibleItemIndex to state.onNow.listState.firstVisibleItemScrollOffset)
        assertTrue(picks.isEmpty())
        assertTrue(closes.isEmpty())
    }

    @Test fun downStopsAtTheLastRowAndUpFromTheFirstClosesTheRail() {
        screen()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        focused(16)
        key(Key.DirectionUp)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertTrue(closes.isEmpty())
        key(Key.DirectionUp)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_UP), closes)
    }

    @Test fun firstRowIsNamedAfterItsGroupAndTheHintNamesWhereUpGoes() {
        fun hint(text: String) = compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("hint")))
        screen()
        compose.onNode(hasText("Sport") and hasAnyAncestor(hasTestTag("player-channel-row-ON_NOW"))).assertExists()
        hint("Back to TV").assertCountEquals(1)
        hint("Sport").assertCountEquals(0)
        key(Key.DirectionDown)
        hint("Sport").assertCountEquals(1)
        hint("Back to TV").assertCountEquals(0)
        key(Key.DirectionUp)
        hint("Back to TV").assertCountEquals(1)
    }

    @Test fun leftFromAFirstCardStaysOnItInEitherRow() {
        screen()
        key(Key.DirectionLeft)
        focused(1)
        key(Key.DirectionRight)
        focused(2)
        key(Key.DirectionDown)
        focused(16)
        key(Key.DirectionLeft)
        focused(16)
        key(Key.DirectionRight)
        focused(15)
        key(Key.DirectionUp)
        key(Key.DirectionLeft)
        focused(1)
    }

    @Test fun theFocusedRowKeepsOnePlace() {
        screen()
        fun title(row: PlayerChannelRow) = compose.onNodeWithTag("player-channel-row-${row.name}")
            .fetchSemanticsNode().boundsInRoot.top
        val onNow = title(PlayerChannelRow.ON_NOW)
        key(Key.DirectionDown)
        assertEquals(onNow, title(PlayerChannelRow.RECENT), .5f)
    }

    @Test fun withoutRecentChannelsThereIsOneRowAndDownStaysOnIt() {
        recent = emptyList()
        screen()
        compose.onNodeWithTag("player-channel-row-RECENT").assertDoesNotExist()
        key(Key.DirectionDown)
        focused(1)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertTrue(closes.isEmpty())
    }

    @Test fun removedChannelFallsBackAndNewSessionDropsBothCollections() {
        screen()
        key(Key.DirectionRight)
        focused(2)
        compose.runOnIdle { channels = channels.filterNot { it.id == ChannelId(2) } }; settle()
        assertEquals("removed ID uses playing fallback", ChannelId(1), state.onNow.focusedId)
        focused(1)
        key(Key.DirectionDown)
        focused(16)
        compose.runOnIdle { state = PlayerChannelBrowserState() }; settle()
        focused(1)
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertNull(state.recent.focusedId)
    }

    @Test fun heldUpMovesOnlyOneRowAndASecondPressReturnsToControls() {
        screen()
        key(Key.DirectionDown)
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionUp) { advanceEventTime(1_200) } }; settle()
        focused(1)
        assertTrue(closes.isEmpty())
        key(Key.DirectionUp)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_DPAD_UP), closes)
    }

    @Test fun onlyAHeldLeftOrRightScrollsAndReleasingEndsIt() {
        screen()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        assertFalse("separate presses are steps", state.scrolling)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionRight); advanceEventTime(1_200) }
        compose.waitForIdle()
        assertTrue("a held key repeats", state.scrolling)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionRight) }
        settle()
        assertFalse("releasing ends the scroll", state.scrolling)
        assertTrue(picks.isEmpty())
    }

    @Test fun nativeLongClickNeverAlsoTunesAndHasAccessibleSecondaryAction() {
        screen()
        val card = compose.onNodeWithTag("player-channel-card-1")
        card.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.OnLongClick))
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionCenter) { advanceEventTime(1_200) } }; settle()
        assertEquals(listOf(ChannelId(1)), details)
        assertTrue(picks.isEmpty())
        key(Key.DirectionCenter)
        assertEquals(listOf(ChannelId(1)), picks)
    }

    @Test fun withoutProgrammeHoldIsHandledButShortClickStillTunes() {
        screen(events = false)
        compose.onRoot().performKeyInput { withKeyDown(Key.Enter) { advanceEventTime(1_200) } }; settle()
        assertTrue(details.isEmpty())
        assertTrue(picks.isEmpty())
        key(Key.Enter)
        assertEquals(listOf(ChannelId(1)), picks)
    }

    @Test fun outgoingRowCannotTuneThroughARetainedAccessibilityAction() {
        screen()
        val click = compose.onNodeWithTag("player-channel-card-1").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        key(Key.DirectionDown)
        compose.runOnIdle { click() }
        assertEquals(PlayerChannelRow.RECENT, state.row)
        assertTrue(picks.isEmpty())
    }
}
