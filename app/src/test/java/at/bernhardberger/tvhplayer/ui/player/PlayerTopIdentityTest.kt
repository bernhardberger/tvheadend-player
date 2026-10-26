package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Live's top identity: its anchor, its place in the controls' focus graph, its keys and its Recent row. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerTopIdentityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theIdentityStandsAtTheTopStartOnTheStripsAnchor() {
        chrome(PlayerChromeMode.CONTROLS)
        val name = bounds("player-top-identity-name")
        val identity = bounds(PlayerTopIdentityTag)
        val clock = bounds("player-top-cluster")
        // The strip's chip starts on the grid margin and is centred on the clock's line, as this is.
        assertEquals("the chip starts on the grid margin", TvGridHorizontalMargin.value,
            identity.left + 24f, 0.5f)
        assertEquals("without a logo the name starts on the grid margin", TvGridHorizontalMargin.value, name.left, 0.5f)
        assertEquals("centred on the clock's line", clock.center.y, identity.center.y, 1f)
        assertTrue("it stands in the top band", identity.bottom < 120f)
        assertEquals("centred on the room's header band", TvGridVerticalMargin.value + 20f, identity.center.y, 1f)
    }

    @Test fun theNumberEntryStandsWhereTheIdentityStandsWithTheChromeShownOrHidden() {
        chrome(PlayerChromeMode.CONTROLS, extra = numberEntrySlot)
        val identity = bounds(PlayerTopIdentityTag)
        assertEquals(listOf("Channel 1 One"), identityDescription())
        for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER, PlayerChromeMode.HIDDEN)) {
            compose.runOnIdle { shownMode = mode; numberEntry = "12"; numberTarget = ChannelNumberTarget.Channel("12", "Twelve", null) }
            settle()
            if (mode == PlayerChromeMode.HIDDEN) assertFalse("hidden chrome has no identity", exists(PlayerTopIdentityTag))
            else assertEquals("$mode: the identity under the entry is not read out", null, identityDescription())
            val entry = bounds("number-entry")
            assertEquals("$mode: the entry starts where the identity does", identity.left, entry.left, 0.5f)
            assertEquals("$mode: the entry stands on the identity's line", identity.top, entry.top, 0.5f)
            assertEquals("$mode: one line, as the identity", identity.height, entry.height, 0.5f)
            val digits = compose.onNodeWithTag("channel-number-digits").fetchSemanticsNode().boundsInRoot
            assertTrue("$mode: the digits start inside the identity's inset", digits.left >= TvGridHorizontalMargin.value - 0.5f)
            assertEquals("$mode: the destination shares the line", identity.center.y,
                compose.onNodeWithText("Twelve").fetchSemanticsNode().boundsInRoot.center.y, 1f)
            compose.runOnIdle { numberEntry = "" }
            settle()
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun atLargerTextTheEntryKeepsTheIdentitysTopEdgeAndHeight() {
        chrome(PlayerChromeMode.CONTROLS, extra = numberEntrySlot)
        for (scale in listOf(1.3f, 2f)) {
            compose.runOnIdle { fontScale = scale; shownMode = PlayerChromeMode.CONTROLS; numberEntry = "" }
            settle()
            val identity = bounds(PlayerTopIdentityTag)
            for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER, PlayerChromeMode.HIDDEN)) {
                compose.runOnIdle { shownMode = mode; numberEntry = "12"; numberTarget = ChannelNumberTarget.Channel("12", "Twelve", null) }
                settle()
                val entry = bounds("number-entry")
                assertEquals("$scale $mode: the entry starts where the identity does", identity.left, entry.left, 0.5f)
                assertEquals("$scale $mode: the entry's top edge is the identity's", identity.top, entry.top, 0.5f)
                // The digits share the name's style, so the entry is as tall as the identity at every scale.
                assertEquals("$scale $mode: the entry's height is the identity's", identity.height, entry.height, 0.5f)
                compose.runOnIdle { numberEntry = "" }
                settle()
            }
        }
    }

    @Test fun anUnknownOrPendingEntryStandsInPlaceAndTheIdentityReturnsWhenItLeaves() {
        chrome(PlayerChromeMode.CONTROLS, extra = numberEntrySlot)
        val identity = bounds(PlayerTopIdentityTag)
        for ((typed, target) in listOf("1" to ChannelNumberTarget.Pending, "999" to ChannelNumberTarget.None)) {
            compose.runOnIdle { numberEntry = typed; numberTarget = target }
            settle()
            assertEquals(identity.left, bounds("number-entry").left, 0.5f)
            assertEquals(identity.top, bounds("number-entry").top, 0.5f)
            assertEquals(target == ChannelNumberTarget.None, compose.onAllNodes(hasText(string(R.string.player_channel_number_none)))
                .fetchSemanticsNodes().isNotEmpty())
            assertEquals(null, identityDescription())
            // Timed out or cancelled: the screen clears the digits and the identity comes back.
            compose.runOnIdle { numberEntry = "" }
            settle()
            assertFalse("the entry left", compose.onAllNodes(hasText(typed) and hasAnyAncestor(hasTestTag("number-entry")))
                .fetchSemanticsNodes().isNotEmpty())
            assertEquals(listOf("Channel 1 One"), identityDescription())
            assertEquals("the identity did not move", identity, bounds(PlayerTopIdentityTag))
        }
    }

    @Test fun theBannersIdentityIsPassiveButAccessible() {
        chrome(PlayerChromeMode.BANNER)
        assertTrue(exists(PlayerTopIdentityTag))
        assertEquals(0, compose.onAllNodes(hasTestTag(PlayerTopIdentityTag) and isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        assertEquals(listOf("Channel 1 One"), compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(emptyList<String>(), focused())
    }

    @Test fun upFromTheSeekbarStaysThereAndTheHeaderStaysPassive() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        val node = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(listOf("Channel 1 One"), node.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(null, node.getOrNull(SemanticsProperties.Focused))
        assertFalse("no channel-zap arrows", exists("player-top-identity-previous") || exists("player-top-identity-next"))
        key(Key.DirectionUp)
        assertEquals("Up has nowhere to go", listOf("player-seekbar"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns to the actions", listOf("player-pause"), focused())
        assertEquals(0, toggles)
        assertEquals(0, downs)
    }

    @Test fun rightFromDetailsReachesSettingsWithoutZapping() {
        chrome(PlayerChromeMode.CONTROLS, restore = "player-info")
        assertEquals(listOf("player-info"), focused())
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionRight) { advanceEventTime(1_200) } }
        settle()
        assertTrue("the press and its repeats never zap: $zaps", zaps.isEmpty())
        assertEquals(listOf("player-settings"), focused())
        assertEquals("nothing opens", 0, downs)
    }

    @Test fun withoutAFocusableTimelineUpStaysInTheActionRow() {
        chrome(PlayerChromeMode.CONTROLS, timeshift = AppTimeshiftState(), livePause = LivePauseAvailability.UNAVAILABLE)
        assertEquals(listOf("player-pause"), focused())
        assertFalse("the passive timeline takes no focus", exists("player-seekbar"))
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionUp)
        assertEquals("Up stays on the action", listOf("player-stop"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-info"), focused())
        assertEquals(0, downs)
        assertEquals(0, toggles)
    }

    @Test fun detailsReportsItsFocusAndTheRestoreTokenReturnsToIt() {
        chrome(PlayerChromeMode.CONTROLS, restore = "player-info")
        assertEquals(listOf("player-info"), focused())
        assertEquals("player-info", reported.last())
        assertEquals("the restore is acknowledged once", 1, restores)
    }

    @Test fun oldRecentInputsCannotMakeTheHeaderInteractive() {
        chrome(PlayerChromeMode.CONTROLS, restore = "player-info", recents = recentChannels)
        val node = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(null, node.getOrNull(SemanticsProperties.Focused))
        key(Key.DirectionCenter)
        assertFalse(recentOpen)
        assertFalse(exists("player-identity-recent-row"))
        assertEquals(emptyList<Int>(), picks)
        assertEquals(listOf("player-info"), focused())
        assertEquals(emptyList<Int>(), zaps)
    }

    @Test fun theInfoBarLeadsWithANonFocusableArtworkTileAndLeavesTheChannelToTheTop() {
        chrome(PlayerChromeMode.CONTROLS)
        assertTrue("without artwork the tile shows the channel", exists("player-info-artwork-fallback"))
        assertFalse(exists("player-info-artwork"))
        assertFalse("the artwork no longer opens details", exists("player-identity-card"))
        assertFalse("the channel line moved to the top", exists("player-info-eyebrow"))
        assertEquals(0, compose.onAllNodes(hasTestTag("player-info-artwork-tile") and isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        // 16:9 over three grid columns on the start margin.
        val tile = bounds("player-info-artwork-tile")
        assertEquals(196f, tile.width, 0.5f)
        assertEquals(110f, tile.height, 0.5f)
        assertEquals("the channel fills the slot", tile, bounds("player-info-artwork-fallback"))
        assertEquals("on the grid's start margin", 58f, tile.left, 0.5f)
        assertEquals("it stands on the bar's top", bounds("player-info-bar").top, tile.top, 0.5f)
        assertEquals("the text starts at the fourth grid column", 274f, bounds("player-info-title").left, 0.5f)
        val row = bounds("player-timeline-labels")
        assertEquals("the timeline row runs from the start margin", 58f, row.left, 0.5f)
        assertEquals("to the end margin", 902f, row.right, 0.5f)
        assertTrue("below the artwork", row.top >= tile.bottom)
        for (tag in listOf("player-pause", "player-stop", "player-info", "player-settings")) {
            assertEquals("$tag: a medium icon button", 40f, bounds(tag).height, 0.5f)
            assertEquals("$tag: a medium icon button", 40f, bounds(tag).width, 0.5f)
        }
        assertEquals("the action row ends on the 28dp bottom margin", 540f - 28f, bounds("player-pause").bottom, 0.5f)
        compose.runOnIdle { shownMode = PlayerChromeMode.BANNER }
        settle()
        assertEquals("the Banner's timeline row ends on the bottom margin", 540f - 28f,
            bounds("player-timeline-labels").bottom, 0.5f)
    }

    /** Real text metrics at font scale 1.0: the info bar's geometry is the fonts' own. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theTimelineStandsAsItsOwnBandBetweenTheProgrammeAndTheActions() {
        val info = PlayerInfoBarData("1", "One", "Title", "20:15–21:45", metadata = "Drama", subtitle = "Episode", next = "21:45 News")
        chrome(PlayerChromeMode.CONTROLS, info = info)
        for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER)) {
            compose.runOnIdle { shownMode = mode }
            settle()
            val tile = bounds("player-info-artwork-tile")
            val title = compose.onNode(hasTestTag("player-info-title") and hasAnyAncestor(hasTestTag("player-info-bar")), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val programme = bounds("player-info-programme")
            val next = bounds("player-info-last-row")
            val labels = bounds("player-timeline-labels")
            // The row's drawn text: its time labels.
            val endClock = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-timeline-labels")) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.boundsInRoot }
                .reduce { a, b -> androidx.compose.ui.geometry.Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
            val track = bounds("player-timeline-track")
            println("INFO_BAR mode=$mode top=${tile.top} artwork=$tile title=$title programme=$programme next=$next " +
                "labels=$labels endClock=$endClock track=$track")
            assertEquals("$mode: the artwork and the eyebrow share the bar's top", programme.top, tile.top, 0.5f)
            assertEquals("$mode: Next stands on the artwork's bottom edge", tile.bottom, next.bottom, 1f)
            val inside = listOf(title.top - programme.bottom, next.top - title.bottom)
            assertTrue("$mode: the lines follow each other: $inside", inside.all { it >= 0f })
            // Visible edges: the programme block (artwork and Next) to the timeline row's labels.
            val toTimeline = endClock.top - maxOf(tile.bottom, next.bottom)
            assertEquals("$mode: the programme block stands 16dp above the timeline's labels", 16f, toTimeline, 0.5f)
            if (mode == PlayerChromeMode.CONTROLS) {
                val toActions = bounds("player-stop").top - endClock.bottom
                println("VISIBLE_GAPS block->timeline=$toTimeline timeline->buttons=$toActions")
                assertEquals("the buttons stand as far below the timeline's labels", toTimeline, toActions, 0.5f)
            } else {
                assertEquals("the Banner's timeline row ends on the bottom margin", 540f - 28f, labels.bottom, 0.5f)
            }
            assertTrue("$mode: the channel's headline is one step below the title", bounds("player-top-identity-name").height < title.height)
        }
    }

    @Test fun theTopIdentityIsOneLine() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals("the identity names the channel only", listOf("Channel 1 One"),
            compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription))
        for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER)) {
            compose.runOnIdle { shownMode = mode }
            settle()
            val identity = bounds(PlayerTopIdentityTag)
            assertEquals("$mode: the name is centred on the chip's line", identity.center.y,
                bounds("player-top-identity-name").center.y, 1f)
            assertTrue("$mode: one line (${identity.height}dp)", identity.height <= 36f + 12f + 0.5f)
        }
    }

    @Test fun pauseKeepsItsFocusWhenTheTimeshiftBufferLeaves() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        compose.runOnIdle { timeshift = AppTimeshiftState() }
        settle()
        assertFalse("the timeline takes no focus without timeshift", exists("player-seekbar"))
        assertEquals("Play/Pause stays in the row and keeps the focus", listOf("player-pause"), focused())
        assertTrue("details remains reachable", exists("player-info"))
    }

    @Test fun recordingInfoKeepsTheRecordedDateAndStartTimeWhileGrowing() {
        var growing by mutableStateOf(false)
        val start = kotlin.time.Instant.parse("2026-09-15T19:00:00Z")
        val entry = at.bernhardberger.tvheadend.sdk.core.DvrEntry.create(
            at.bernhardberger.tvheadend.sdk.core.DvrEntryId(1), title = "Ridge Light", start = start,
            stop = kotlin.time.Instant.parse("2026-09-15T21:00:00Z"))
        lateinit var info: PlayerInfoBarData
        compose.setContent {
            TVHeadendPlayerTheme {
                info = recordingInfoBarData(entry, 101, start.epochSeconds + 8000, 300_000, 7_200_000, growing)
                PlayerInfoBar(info, Modifier.testTag("recorded-info"), identity = {})
            }
        }
        for (stillRecording in listOf(false, true)) {
            compose.runOnIdle { growing = stillRecording }
            compose.waitForIdle()
            val date = requireNotNull(info.recordedDate)
            assertTrue("recorded time follows the local clock", date.endsWith(at.bernhardberger.tvhplayer.ui.common.formatClock(start.epochSeconds)))
            assertTrue("the growing status never replaces the recorded date", requireNotNull(info.metadata).contains(date))
            compose.onNodeWithTag("player-info-programme", useUnmergedTree = true).assertExists()
            compose.onNodeWithTag("recorded-info")
                .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString().let {
                    assertTrue("the recorded date and time are spoken", it.contains(date))
                }
        }
    }

    private var timeshift by mutableStateOf(
        AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -203_000, liveEdgeMs = 0, timingKnown = true))
    private var restoreFocus by mutableStateOf<String?>(null)
    private var recentOpen by mutableStateOf(false)
    private var shownMode by mutableStateOf(PlayerChromeMode.CONTROLS)
    private var numberEntry by mutableStateOf("")
    private var fontScale by mutableStateOf(1f)
    private var numberTarget by mutableStateOf<ChannelNumberTarget>(ChannelNumberTarget.Pending)
    /** The screen's number entry, drawn over the chrome in the identity's place. */
    private val numberEntrySlot: @androidx.compose.runtime.Composable () -> Unit = {
        PlayerTopIdentitySlot { ChannelNumberOverlay(numberEntry, numberTarget, Modifier.testTag("number-entry")) }
    }
    private var restores = 0
    private var toggles = 0
    private var downs = 0
    private val zaps = mutableListOf<Int>()
    private val picks = mutableListOf<Int>()
    private val reported = mutableListOf<String>()

    private val recentChannels = listOf(
        RecentChannelPeek(null, "2", "Two", now = "Second"),
        RecentChannelPeek(null, "3", "Three", now = "Third"),
    )

    private fun chrome(
        mode: PlayerChromeMode,
        timeshift: AppTimeshiftState = this.timeshift,
        livePause: LivePauseAvailability? = null,
        restore: String? = null,
        recents: List<RecentChannelPeek> = emptyList(),
        info: PlayerInfoBarData = liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, false, 1_800, "Programme"),
        /** Composed beside the chrome. */
        extra: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        shownMode = mode
        this.timeshift = timeshift
        restoreFocus = restore
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale),
            ) {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = shownMode,
                    content = PlayerChromeContent("20:15", info),
                    timeline = PlayerChromeTimeline.Live(this.timeshift, nowSec = 1_800),
                    actions = PlayerChromeActions(active = shownMode == PlayerChromeMode.CONTROLS, paused = false,
                        livePause = livePause, restoreFocus = restoreFocus),
                    onTogglePause = { toggles++ }, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {},
                    onInteraction = {},
                    onPauseUnavailable = { toggles++ },
                    onActionFocused = { reported += it },
                    onFocusRestored = { restores++; restoreFocus = null },
                    onDownFromActions = { downs++ },
                    onChannelZap = { direction, _, _ -> zaps += direction },
                    recents = recents,
                    onRecentPick = { picks += it },
                    recentRowOpen = recentOpen,
                    onRecentRowOpenChange = { recentOpen = it },
                    numberEntryShown = numberEntry.isNotEmpty(),
                )
                extra()
            }
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun identityDescription(): List<String>? = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
