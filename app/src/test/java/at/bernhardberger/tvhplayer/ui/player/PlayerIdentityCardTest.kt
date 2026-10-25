package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A recording's identity card in the info bar: its size, its place in the controls' focus graph and
 * the lines beside it. Live names its channel at the top instead ([PlayerTopIdentityTest]).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerIdentityCardTest {
    @get:Rule val compose = createComposeRule()
    private var infos = 0

    @Test fun missingEpgStillOffersLabelledDetails() {
        recording(info = PlayerInfoBarData("101", "Ridge Earth", "", ""))
        key(Key.DirectionRight); key(Key.DirectionRight)
        assertEquals(listOf("Program details"), description())
        key(Key.DirectionCenter)
        assertEquals(1, infos)
    }

    @Test fun persistentLiveSeekFeedbackRemainsVisibleWithInfoFocused() = focusedInfoDuringFeedback(recording = false)

    @Test fun retainedRecordingSeekPreviewRemainsVisibleWithInfoFocused() = focusedInfoDuringFeedback(recording = true)

    @Test fun liveScheduleFeedbackRemainsVisibleWithInfoFocused() = focusedInfoDuringFeedback(recording = false, seekable = false)

    private fun focusedInfoDuringFeedback(recording: Boolean, seekable: Boolean = true) {
        compose.setContent {
            TVHeadendPlayerTheme {
                if (recording) RecordingChromeFixture(targetMs = 1_900_000L,
                    onInfo = { infos++ })
                else PlayerChrome(mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", PlayerInfoBarData("101", "Ridge Earth", "Programme", "20:00–21:00")),
                    timeline = PlayerChromeTimeline.Live(AppTimeshiftState(available = seekable, timingKnown = true,
                        bufferStartMs = -600_000, positionMs = -60_000, liveEdgeMs = 0), nowSec = 1_800,
                        feedback = "Persistent seek feedback"),
                    actions = PlayerChromeActions(active = true, paused = false),
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = { infos++ }, onOptions = {}, onInteraction = {},
                    onPauseUnavailable = {}, onActionFocused = {}, onFocusRestored = {}, onDownFromActions = {})
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-info"), focused())
        if (recording) compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertIsDisplayed()
        else compose.onNodeWithText("Persistent seek feedback", useUnmergedTree = true).assertIsDisplayed()
        key(Key.DirectionCenter)
        assertEquals("OK reaches programme details", 1, infos)
    }

    @Test fun theDetailsButtonIsBetweenStopAndSettingsAndOpensOnce() {
        recording()
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-info"), focused())
        assertEquals(listOf("Program details"), description())
        assertFalse("no channel to change: no arrows", exists("player-top-identity-previous"))
        key(Key.DirectionRight)
        assertEquals(listOf("player-settings"), focused())
        key(Key.DirectionLeft)
        assertEquals(listOf("player-info"), focused())
        assertEquals(0, infos)
        key(Key.DirectionCenter)
        assertEquals("OK opens Info once", 1, infos)
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns to details", listOf("player-info"), focused())
    }

    @Test fun theBannersIdentityIsAPictureWithoutFocus() {
        recording(PlayerChromeMode.BANNER)
        assertTrue(exists(PlayerTopIdentityTag))
        assertEquals(0, compose.onAllNodes(hasTestTag(PlayerTopIdentityTag) and isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), focused())
        assertFalse("the card is gone", exists(PlayerIdentityCardTag))
    }

    /** One chrome: a recording's layout is live's, at the same canvas and font scale. */
    @Test fun aRecordingHasLivesChromeGeometry() {
        for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER)) {
            val recording = geometry(mode, live = false)
            val live = geometry(mode, live = true)
            assertEquals("$mode: the same geometry\nrecording $recording\nlive $live", live, recording)
        }
    }

    private fun geometry(mode: PlayerChromeMode, live: Boolean): Map<String, Any> {
        shownLive = live
        shownMode = mode
        if (!composed) {
            composed = true
            compose.setContent {
                TVHeadendPlayerTheme {
                    val info = PlayerInfoBarData("1", "One", "Title", "20:15–21:45", subtitle = "Episode",
                        next = "21:45 News".takeIf { shownLive })
                    if (shownLive) LiveChromeFixture(shownMode, info)
                    else RecordingChromeFixture(mode = shownMode, info = info, active = shownMode == PlayerChromeMode.CONTROLS)
                }
            }
            compose.mainClock.autoAdvance = false
        }
        settle()
        val identity = bounds(PlayerTopIdentityTag)
        val tile = bounds("player-info-artwork-tile")
        val labels = bounds("player-timeline-labels")
        return buildMap {
            put("identity", identity)
            put("artwork", tile)
            put("placeholder", bounds("player-info-artwork-fallback"))
            put("title", bounds("player-info-title"))
            put("timeline", labels.left to labels.right)
            put("timeline top", labels.top)
            put("info top", bounds("player-info-bar").top)
            if (mode == PlayerChromeMode.CONTROLS) {
                put("actions", listOf("player-pause", "player-stop", "player-settings").map(::bounds))
                put("timeline to actions", bounds("player-pause").top - labels.bottom)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LiveChromeFixture(mode: PlayerChromeMode, info: PlayerInfoBarData) {
        PlayerChrome(
            mode = mode,
            content = PlayerChromeContent("20:15", info),
            timeline = PlayerChromeTimeline.Live(AppTimeshiftState(), nowSec = 1_800),
            actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS, paused = false),
            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            onPauseUnavailable = {}, onActionFocused = {}, onFocusRestored = {}, onDownFromActions = {},
            onChannelZap = { _, _, _ -> }, onRecentPick = {},
        )
    }

    private var composed = false
    private var shownLive by mutableStateOf(false)
    private var shownMode by mutableStateOf(PlayerChromeMode.CONTROLS)

    @Test fun everyLineKeepsItsPlaceBesideTheArtworkWhateverTheProgrammeHas() {
        var info by mutableStateOf(PlayerInfoBarData("1", "One", "Title", "20:15–21:45", subtitle = "Episode", next = "21:45 News"))
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(800.dp)) {
                    PlayerInfoBar(info, Modifier.testTag("player-info-bar")) {
                        Box(it.size(124.dp, 70.dp).testTag("slot"))
                    }
                }
            }
        }
        val full = listOf("player-info-bar", "player-info-title", "player-info-programme", "player-info-last-row")
            .associateWith(::bounds)
        val cases = mapOf(
            "no subtitle" to info.copy(subtitle = null),
            "no Next" to info.copy(next = null),
            "title only" to info.copy(subtitle = null, next = null, timeRange = ""),
            "recording with its episode" to info.copy(next = null, recordedDate = "14 Nov"),
            "recording without one" to info.copy(subtitle = null, next = null, recordedDate = "14 Nov"),
        )
        for ((name, case) in cases) {
            compose.runOnIdle { info = case }
            compose.waitForIdle()
            assertEquals("$name: the bar keeps its height", full.getValue("player-info-bar").height, bounds("player-info-bar").height, 0.5f)
            assertEquals("$name: the title keeps its place", full.getValue("player-info-title").top, bounds("player-info-title").top, 0.5f)
            assertEquals("$name: the slot stands on the bar's top", bounds("player-info-bar").top, bounds("slot").top, 0.5f)
            if (exists("player-info-programme")) assertEquals("$name: the programme line keeps its place",
                full.getValue("player-info-programme").top, bounds("player-info-programme").top, 0.5f)
            if (exists("player-info-last-row")) assertEquals("$name: the last row keeps its place",
                full.getValue("player-info-last-row").top, bounds("player-info-last-row").top, 0.5f)
        }
    }

    @Test fun theTimeLeadsAsAnEyebrowAboveTheTitleAndTheSubtitleStandsUnderIt() {
        recording(info = PlayerInfoBarData("1", "One", "Title", "20:15–21:45", subtitle = "Episode", next = "21:45 News"))
        assertFalse("the channel stands at the top, not in the info bar", exists("player-info-eyebrow"))
        val title = bounds("player-info-title")
        val programme = bounds("player-info-programme")
        val subtitle = bounds("player-info-subtitle")
        val last = bounds("player-info-last-row")
        assertTrue("the time and metadata stand above the title", programme.bottom <= title.top + 0.5f)
        assertTrue("the subtitle stands under the title", title.bottom <= subtitle.top + 0.5f)
        assertTrue("the last row stands under them", subtitle.bottom <= last.top + 0.5f)
        assertEquals("the artwork and the text share the bar's top", bounds("player-info-bar").top, bounds("player-info-artwork-tile").top, 1f)
    }

    @Test fun withoutASeekableTimelineUpStaysInTheActionRow() {
        recording(canSeek = false)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionUp)
        assertEquals("Up stays on the action", listOf("player-stop"), focused())
        assertEquals(0, infos)
    }

    @Test fun theKeyThatMovesFocusToOrFromTheTimelineDoesNotActOnItsNewTarget() {
        recording()
        key(Key.DirectionUp)
        // Held Up stays on the timeline; passive artwork is not a focus target.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionUp) { advanceEventTime(1_200) } }
        settle()
        assertEquals(listOf("player-seekbar"), focused())
        // Held Down returns to Pause and goes no further.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionDown) { advanceEventTime(1_200) } }
        settle()
        assertEquals(listOf("player-pause"), focused())
        assertEquals(0, infos)
    }

    @Test fun closingInfoReturnsFocusToDetailsHoweverInfoWasOpened() {
        // Info opened by the remote's INFO key: focus was on Pause, not on the (i) button.
        recording()
        assertEquals(listOf("player-pause"), focused())
        compose.runOnIdle { panelOpen = true }
        settle()
        assertEquals("the covered controls hold no focus", emptyList<String>(), focused())
        compose.runOnIdle { panelOpen = false; restoreFocus = "player-info" }
        settle()
        assertEquals(listOf("player-info"), focused())
        assertEquals("the restore is acknowledged once", 1, restores)
        // And opened from the (i) button itself.
        key(Key.DirectionCenter)
        assertEquals(1, infos)
        compose.runOnIdle { panelOpen = true }
        settle()
        compose.runOnIdle { panelOpen = false; restoreFocus = "player-info" }
        settle()
        assertEquals(listOf("player-info"), focused())
        assertEquals(2, restores)
    }

    @Test fun detailsReportsItsFocusAsAControlSoAPanelCanReturnToIt() {
        recording()
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        assertEquals("player-info", reported.last())
    }

    @Test fun aboveARecordingsMarkersUpReturnsToTheTimeline() {
        val navigation = RecordingMarkerNavigation()
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(markers = listOf(0L, 900_000L, 2_700_000L), markerNavigation = navigation, onInfo = { infos++ })
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertTrue("Up from the seekbar opens the markers", navigation.open)
        key(Key.DirectionUp)
        assertFalse("Up above the markers closes them", navigation.open)
        assertEquals(listOf("player-seekbar"), focused())
        assertFalse(navigation.open)
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        assertFalse(navigation.open)
        assertEquals("Down still closes the markers onto the seekbar", listOf("player-seekbar"), focused())
        assertEquals(0, infos)
    }

    private var panelOpen by mutableStateOf(false)
    private var restoreFocus by mutableStateOf<String?>(null)
    private var restores = 0
    private val reported = mutableListOf<String>()

    private fun recording(
        mode: PlayerChromeMode = PlayerChromeMode.CONTROLS,
        info: PlayerInfoBarData? = null,
        canSeek: Boolean = true,
    ) {
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(
                    mode = mode, info = info, canSeek = canSeek,
                    active = mode == PlayerChromeMode.CONTROLS && !panelOpen, panelOpen = panelOpen,
                    restoreFocus = restoreFocus,
                    onInfo = { infos++ },
                    onActionFocused = { reported += it },
                    onFocusRestored = { restores++; restoreFocus = null },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun description(): List<String>? = compose.onNodeWithTag("player-info")
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private fun stateDescription(): String? = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun focused(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun settle() {
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }
}
