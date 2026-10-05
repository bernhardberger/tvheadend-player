package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The info bar's identity card: its size, its place in the controls' focus graph and the lines beside it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerIdentityCardTest {
    @get:Rule val compose = createComposeRule()
    private var infos = 0

    @Test fun upFromTheSeekbarFocusesTheCardWhichOpensInfoAndDownReturns() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-identity-card"), focused())
        assertEquals("the focused card says what it opens", listOf("Info"), cardDescription())
        listOf(Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach {
            key(it)
            assertEquals("$it leads nowhere from the card", listOf("player-identity-card"), focused())
        }
        assertEquals(0, infos)
        key(Key.DirectionCenter)
        assertEquals("OK opens Info once", 1, infos)
        key(Key.DirectionDown)
        assertEquals(listOf("player-seekbar"), focused())
    }

    @Test fun theBannersCardSaysNothing() {
        chrome(PlayerChromeMode.BANNER)
        assertEquals("the Banner's card opens nothing", null, cardDescription())
    }

    @Test fun theCardHasOneSizeCentresItsMarkAndStandsOnTheFootersStartEdge() {
        chrome(PlayerChromeMode.CONTROLS)
        val card = bounds("player-identity-card")
        val bar = bounds("player-info-bar")
        assertEquals("one height, whatever the programme block holds", 110f, card.height, 0.5f)
        assertEquals("one width, three grid columns", 196f, card.width, 0.5f)
        assertEquals("it stands on the info bar's bottom edge", bar.bottom, card.bottom, 0.5f)
        val mark = bounds(if (exists("player-identity-logo")) "player-identity-logo" else "player-identity-name")
        assertEquals("the mark is centred across the card", card.center.x, mark.center.x, 0.5f)
        assertEquals("without a label the mark sits in the card's centre", card.center.y, mark.center.y, 0.5f)
        assertEquals("on the bar row's start edge", bounds("player-timeline-labels").left, card.left, 0.5f)
        assertTrue("the card carries no label of its own", !exists("player-identity-label"))
    }

    @Test fun everyLineKeepsItsPlaceBesideTheCardWhateverTheProgrammeHas() {
        var info by mutableStateOf(PlayerInfoBarData("1", "One", "Title", "20:15–21:45", subtitle = "Episode", next = "21:45 News"))
        val loader = ImageLoader(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.width(800.dp)) {
                    PlayerInfoBar(info, emptyList(), Modifier.testTag("player-info-bar")) {
                        PlayerIdentityCard(PlayerChromeContent("", info), loader, null, it)
                    }
                }
            }
        }
        val full = listOf("player-info-bar", "player-info-eyebrow", "player-info-title", "player-info-programme", "player-info-last-row")
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
            assertEquals("$name: the channel keeps its place", full.getValue("player-info-eyebrow").top, bounds("player-info-eyebrow").top, 0.5f)
            assertEquals("$name: the title keeps its place", full.getValue("player-info-title").top, bounds("player-info-title").top, 0.5f)
            if (exists("player-info-programme")) assertEquals("$name: the programme line keeps its place",
                full.getValue("player-info-programme").top, bounds("player-info-programme").top, 0.5f)
            if (exists("player-info-last-row")) assertEquals("$name: the last row keeps its place",
                full.getValue("player-info-last-row").top, bounds("player-info-last-row").top, 0.5f)
        }
    }

    @Test fun theChannelLeadsAndTheProgrammeLineStandsUnderTheTitle() {
        chrome(PlayerChromeMode.CONTROLS, info = PlayerInfoBarData("1", "One", "Title", "20:15–21:45", subtitle = "Episode", next = "21:45 News"))
        val eyebrow = bounds("player-info-eyebrow")
        val title = bounds("player-info-title")
        val programme = bounds("player-info-programme")
        val last = bounds("player-info-last-row")
        assertTrue("the channel stands above the title", eyebrow.bottom <= title.top + 0.5f)
        assertTrue("the time, metadata and subtitle stand under it", title.bottom <= programme.top + 0.5f)
        assertTrue("Next stands under them", programme.bottom <= last.top + 0.5f)
        assertEquals("card and text block share the bar's bottom", bounds("player-info-bar").bottom, last.bottom, 1f)
    }

    @Test fun withSteppingOffLeftAndRightOnTheChannelCardDoNeitherZapNorOpenTheRail() {
        chrome(PlayerChromeMode.CONTROLS, channelCard = true, stepping = false)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertEquals(listOf("player-identity-card"), focused())
        assertFalse("no arrows without stepping", exists("player-identity-previous") || exists("player-identity-next"))
        val hint = ApplicationProvider.getApplicationContext<Application>().getString(R.string.player_channel_card_hint_fixed)
        assertEquals("the card does not promise stepping", listOf(hint), cardDescription())
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionRight) { advanceEventTime(1_200) } }
        settle()
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionLeft) { advanceEventTime(1_200) } }
        settle()
        assertEquals("focus stays on the card", listOf("player-identity-card"), focused())
        assertEquals("no channel change", emptyList<Int>(), zaps)
        assertEquals("no rail", emptyList<Int>(), steps)
    }

    @Test fun withSteppingOnTheChannelCardShowsArrowsTapsChangeChannelAndHoldOpensTheRail() {
        chrome(PlayerChromeMode.CONTROLS, channelCard = true, stepping = true)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertEquals(listOf("player-identity-card"), focused())
        assertTrue("the arrows show", exists("player-identity-previous") && exists("player-identity-next"))
        key(Key.DirectionRight)
        key(Key.DirectionLeft)
        assertEquals("a tap changes channel", listOf(1, -1), zaps)
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionRight) { advanceEventTime(1_200) } }
        settle()
        assertEquals("a hold opens the rail stepping that way", listOf(1), steps)
        assertEquals("and does not also change channel", listOf(1, -1), zaps)
    }

    @Test fun inTheBannerTheCardIsAPictureWithoutFocus() {
        chrome(PlayerChromeMode.BANNER)
        assertTrue(exists("player-identity-card"))
        assertEquals(0, compose.onAllNodes(hasTestTag("player-identity-card") and isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(emptyList<String>(), focused())
    }

    @Test fun withoutAFocusableTimelineUpFromTheActionRowFocusesTheCardAndDownReturnsToPause() {
        // No timeshift: the timeline takes no focus.
        chrome(PlayerChromeMode.CONTROLS, timeshift = AppTimeshiftState(), livePause = LivePauseAvailability.UNAVAILABLE)
        assertEquals(listOf("player-pause"), focused())
        assertFalse("the passive timeline takes no focus", exists("player-seekbar"))
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionUp)
        assertEquals("Up from any action reaches the card", listOf("player-identity-card"), focused())
        key(Key.DirectionDown)
        assertEquals("Down leads to the action row's entry", listOf("player-pause"), focused())
        assertEquals(0, toggles)
        assertEquals(0, infos)
    }

    @Test fun pauseKeepsItsFocusWhenTheTimeshiftBufferLeaves() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        compose.runOnIdle { timeshift = AppTimeshiftState() }
        settle()
        assertFalse("the timeline takes no focus without timeshift", exists("player-seekbar"))
        assertEquals("Play/Pause stays in the row and keeps the focus", listOf("player-pause"), focused())
        assertFalse("the action row has no Info", exists("player-info"))
    }

    @Test fun theKeyThatMovesFocusToOrFromTheCardDoesNotActOnItsNewTarget() {
        chrome(PlayerChromeMode.CONTROLS)
        key(Key.DirectionUp)
        // Held Up reaches the card once; its repeats and release change nothing there.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionUp) { advanceEventTime(1_200) } }
        settle()
        assertEquals(listOf("player-identity-card"), focused())
        // Held Down returns to the seekbar and goes no further, to the action row.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionDown) { advanceEventTime(1_200) } }
        settle()
        assertEquals(listOf("player-seekbar"), focused())
        assertEquals(0, infos)
        assertEquals(0, toggles)
    }

    @Test fun closingInfoReturnsFocusToTheCardHoweverInfoWasOpened() {
        // Info opened by the remote's INFO key: focus was on Pause, not on the card.
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        compose.runOnIdle { panelOpen = true }
        settle()
        assertEquals("the covered controls hold no focus", emptyList<String>(), focused())
        compose.runOnIdle { panelOpen = false; restoreFocus = "player-identity-card" }
        settle()
        assertEquals(listOf("player-identity-card"), focused())
        assertEquals("the restore is acknowledged once", 1, restores)
        // And opened from the card itself.
        key(Key.DirectionCenter)
        assertEquals(1, infos)
        compose.runOnIdle { panelOpen = true }
        settle()
        compose.runOnIdle { panelOpen = false; restoreFocus = "player-identity-card" }
        settle()
        assertEquals(listOf("player-identity-card"), focused())
        assertEquals(2, restores)
    }

    @Test fun theCardReportsItsFocusAsAControlSoAPanelCanReturnToIt() {
        chrome(PlayerChromeMode.CONTROLS)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertEquals("player-identity-card", reported.last())
    }

    @Test fun aboveARecordingsMarkersUpReachesTheCardAndDownReturnsToTheSeekbar() {
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
        assertEquals(listOf("player-identity-card"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        key(Key.DirectionDown)
        assertFalse(navigation.open)
        assertEquals("Down still closes the markers onto the seekbar", listOf("player-seekbar"), focused())
        assertEquals(0, infos)
    }

    private var timeshift by mutableStateOf(
        AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -203_000, liveEdgeMs = 0, timingKnown = true))
    private var panelOpen by mutableStateOf(false)
    private var restoreFocus by mutableStateOf<String?>(null)
    private var restores = 0
    private var toggles = 0
    private val zaps = mutableListOf<Int>()
    private val steps = mutableListOf<Int>()
    private var options = 0
    private val reported = mutableListOf<String>()

    private fun chrome(
        mode: PlayerChromeMode,
        info: PlayerInfoBarData = liveInfoBarData(1, "One", null, null, false, 1_800, "Programme"),
        timeshift: AppTimeshiftState = this.timeshift,
        livePause: LivePauseAvailability? = null,
        channelCard: Boolean = false,
        stepping: Boolean = false,
    ) {
        this.timeshift = timeshift
        val loader = ImageLoader(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = mode,
                    content = PlayerChromeContent("20:15", info),
                    timeline = PlayerChromeTimeline.Live(this.timeshift, nowSec = 1_800),
                    actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS && !panelOpen, paused = false,
                        livePause = livePause, restoreFocus = restoreFocus),
                    imageLoader = loader, currentSession = null,
                    onTogglePause = { toggles++ }, onSeek = {}, onStop = {}, onInfo = { infos++ }, onOptions = { options++ },
                    onInteraction = {}, panelOpen = panelOpen,
                    onPauseUnavailable = { toggles++ },
                    onActionFocused = { reported += it },
                    onFocusRestored = { restores++; restoreFocus = null },
                    channelCardStepping = stepping,
                    onChannelStep = if (channelCard) { direction, _ -> steps += direction } else null,
                    onChannelZap = if (channelCard) { direction, _ -> zaps += direction } else null,
                )
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun cardDescription(): List<String>? = compose.onNodeWithTag(PlayerIdentityCardTag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

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
