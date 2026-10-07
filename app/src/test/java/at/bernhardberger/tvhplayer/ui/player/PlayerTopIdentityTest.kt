package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
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
import org.robolectric.annotation.GraphicsMode

/** Live's top identity: its anchor, its place in the controls' focus graph, its keys and its Recent row. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerTopIdentityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theIdentityStandsAtTheTopStartOnTheStripsAnchor() {
        chrome(PlayerChromeMode.CONTROLS)
        val chip = bounds("player-top-identity-number")
        val identity = bounds(PlayerTopIdentityTag)
        val clock = bounds("player-top-cluster")
        // The strip's chip starts on the grid margin and is centred on the clock's line, as this is.
        assertEquals("the chip starts on the grid margin", TvGridHorizontalMargin.value,
            identity.left + 24f, 0.5f)
        assertTrue("the number sits in the chip", chip.left >= identity.left + 24f)
        assertEquals("centred on the clock's line", clock.center.y, identity.center.y, 1f)
        assertTrue("it stands in the top band", identity.bottom < 120f)
    }

    @Test fun theBannersIdentityTakesNoFocusAndSaysNothing() {
        chrome(PlayerChromeMode.BANNER)
        assertTrue(exists(PlayerTopIdentityTag))
        assertEquals(0, compose.onAllNodes(hasTestTag(PlayerTopIdentityTag) and isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        assertEquals(null, compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(emptyList<String>(), focused())
    }

    @Test fun upFromTheSeekbarReachesTheIdentityWhereLeftAndRightChangeChannelAndDownReturns() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf(PlayerTopIdentityTag), focused())
        val node = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertEquals(listOf("Channel 1, One"), node.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(string(R.string.player_channel_card_hint_no_recents), node.getOrNull(SemanticsProperties.StateDescription))
        assertTrue("the arrows show while it has focus", exists("player-top-identity-previous") && exists("player-top-identity-next"))
        key(Key.DirectionUp)
        assertEquals("Up has nowhere to go", listOf(PlayerTopIdentityTag), focused())
        key(Key.DirectionRight)
        key(Key.DirectionLeft)
        assertEquals("Left and Right change channel", listOf(1, -1), zaps)
        assertEquals("and keep the focus", listOf(PlayerTopIdentityTag), focused())
        key(Key.DirectionCenter)
        assertFalse("without recent channels OK opens nothing", exists("player-identity-recent-row"))
        assertEquals(listOf(PlayerTopIdentityTag), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns to the action row", listOf("player-pause"), focused())
        assertEquals(0, toggles)
        assertEquals(0, downs)
    }

    @Test fun heldLeftOrRightStepsOnEveryRepeatLikeAChannelKeyAndNeverMovesFocus() {
        chrome(PlayerChromeMode.CONTROLS, restore = PlayerIdentityCardTag)
        assertEquals(listOf(PlayerTopIdentityTag), focused())
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionRight) { advanceEventTime(1_200) } }
        settle()
        assertTrue("the press and its repeats each step: $zaps", zaps.size > 1 && zaps.all { it == 1 })
        assertEquals(listOf(PlayerTopIdentityTag), focused())
        assertEquals("nothing opens", 0, downs)
    }

    @Test fun withoutAFocusableTimelineUpFromAnyActionReachesTheIdentityAndDownReturnsToThatAction() {
        chrome(PlayerChromeMode.CONTROLS, timeshift = AppTimeshiftState(), livePause = LivePauseAvailability.UNAVAILABLE)
        assertEquals(listOf("player-pause"), focused())
        assertFalse("the passive timeline takes no focus", exists("player-seekbar"))
        key(Key.DirectionRight)
        assertEquals(listOf("player-stop"), focused())
        key(Key.DirectionUp)
        assertEquals("Up from an action reaches the identity", listOf(PlayerTopIdentityTag), focused())
        // Held Down returns once and goes no further, to the channel rows.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionDown) { advanceEventTime(1_200) } }
        settle()
        assertEquals("Down returns to the action it came from", listOf("player-stop"), focused())
        assertEquals(0, downs)
        assertEquals(0, toggles)
    }

    @Test fun theIdentityReportsItsFocusAndTheRestoreTokenReturnsToIt() {
        chrome(PlayerChromeMode.CONTROLS, restore = PlayerIdentityCardTag)
        assertEquals(listOf(PlayerTopIdentityTag), focused())
        assertEquals(PlayerIdentityCardTag, reported.last())
        assertEquals("the restore is acknowledged once", 1, restores)
    }

    @Test fun okOpensTheRecentRowBelowWithoutTuningAndItsKeysReturnOrMoveOn() {
        chrome(PlayerChromeMode.CONTROLS, restore = PlayerIdentityCardTag, recents = recentChannels)
        assertEquals(string(R.string.player_channel_card_hint), compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        key(Key.DirectionCenter)
        assertTrue(recentOpen)
        assertEquals("OK opens the row onto the newest", listOf("player-identity-recent-0"), focused())
        assertEquals("the opening OK tunes nothing", emptyList<Int>(), picks)
        assertTrue("the row drops down below the identity",
            bounds("player-identity-recent-row").top >= bounds(PlayerTopIdentityTag).bottom)
        key(Key.DirectionLeft)
        assertEquals("the row's start holds focus", listOf("player-identity-recent-0"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-identity-recent-1"), focused())
        key(Key.DirectionUp)
        assertEquals("Up returns to the identity", listOf(PlayerTopIdentityTag), focused())
        assertFalse("and the row closes", recentOpen)
        key(Key.DirectionCenter)
        assertEquals(listOf("player-identity-recent-0"), focused())
        // Held Down leaves the row for the action row and opens nothing below it.
        compose.onRoot().performKeyInput { withKeyDown(Key.DirectionDown) { advanceEventTime(1_200) } }
        settle()
        assertEquals(listOf("player-pause"), focused())
        assertFalse(recentOpen)
        assertEquals(0, downs)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals("OK on a chip tunes it", listOf(1), picks)
        assertEquals("and focus returns to the identity", listOf(PlayerTopIdentityTag), focused())
        assertEquals(emptyList<Int>(), zaps)
    }

    @Test fun theInfoBarLeadsWithANonFocusableArtworkTileAndLeavesTheChannelToTheTop() {
        chrome(PlayerChromeMode.CONTROLS)
        assertTrue("without artwork the tile shows its placeholder", exists("player-info-artwork-placeholder"))
        assertFalse(exists("player-info-artwork"))
        assertFalse("the card is gone", exists("player-identity-card"))
        assertFalse("the channel line moved to the top", exists("player-info-eyebrow"))
        assertEquals(0, compose.onAllNodes(hasTestTag("player-info-artwork-tile") and isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes().size)
        // Two grid columns at 16:9 on the start margin; the text from the third column.
        val tile = bounds("player-info-artwork-tile")
        assertEquals(124f, tile.width, 0.5f)
        assertEquals(70f, tile.height, 0.5f)
        assertEquals("the placeholder fills the slot", tile, bounds("player-info-artwork-placeholder"))
        assertEquals("on the grid's start margin", 58f, tile.left, 0.5f)
        assertEquals("it stands on the title's top", bounds("player-info-title").top, tile.top, 0.5f)
        assertEquals("the text starts at the third grid column", 202f, bounds("player-info-title").left, 0.5f)
        val row = bounds("player-timeline-labels")
        assertEquals("the timeline row runs from the start margin", 58f, row.left, 0.5f)
        assertEquals("to the end margin", 902f, row.right, 0.5f)
        assertTrue("below the artwork", row.top >= tile.bottom)
        for (tag in listOf("player-pause", "player-stop", "player-settings")) {
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
    fun theProgrammeTextAndArtworkSitTightOnTheTimelineWhichStandsApartFromTheActions() {
        val info = PlayerInfoBarData("1", "One", "Title", "20:15–21:45", metadata = "Drama", subtitle = "Episode", next = "21:45 News")
        chrome(PlayerChromeMode.CONTROLS, info = info, badges = sampleBadges)
        // The info bar's top with the 196 × 110dp channel card at this canvas and font scale.
        val cardTops = mapOf(PlayerChromeMode.CONTROLS to 302f, PlayerChromeMode.BANNER to 354f)
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
            assertEquals("$mode: the artwork stands on the title's top", title.top, tile.top, 0.5f)
            assertEquals("$mode: and ends with the Next line", next.bottom, tile.bottom, 0.5f)
            assertTrue("$mode: the chrome is shorter than with the card (${tile.top} vs ${cardTops.getValue(mode)})",
                tile.top > cardTops.getValue(mode))
            val inside = listOf(programme.top - title.bottom, next.top - programme.bottom)
            assertTrue("$mode: the lines follow each other: $inside", inside.all { it >= 0f })
            // Visible edges: the Next text and the artwork to the timeline row's labels.
            val toTimeline = endClock.top - next.bottom
            assertEquals("$mode: the text stands 8dp above the timeline's labels", 8f, toTimeline, 0.5f)
            if (mode == PlayerChromeMode.CONTROLS) {
                // The unfocused icon's 24dp glyph is centred in its 40dp button.
                val glyphTop = bounds("player-stop").center.y - 12f
                val toActions = glyphTop - endClock.bottom
                println("VISIBLE_GAPS text->timeline=$toTimeline timeline->glyphs=$toActions glyph->bottom=${540f - glyphTop - 24f}")
                assertEquals("the action glyphs stand 24dp below the timeline's labels", 24f, toActions, 0.5f)
                assertTrue("further than the text from the timeline", toTimeline < toActions)
            } else {
                assertEquals("the Banner's timeline row ends on the bottom margin", 540f - 28f, labels.bottom, 0.5f)
            }
            assertTrue("$mode: the title stays the largest text", title.height > bounds("player-top-identity-name").height)
        }
    }

    @Test fun theTopIdentityIsOneLineWithoutStreamBadges() {
        chrome(PlayerChromeMode.CONTROLS, badges = sampleBadges)
        assertEquals("the identity names the channel only", listOf("Channel 1, One"),
            compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription))
        for (mode in listOf(PlayerChromeMode.CONTROLS, PlayerChromeMode.BANNER)) {
            compose.runOnIdle { shownMode = mode }
            settle()
            val identity = bounds(PlayerTopIdentityTag)
            assertEquals("$mode: the name is centred on the chip's line", identity.center.y,
                bounds("player-top-identity-name").center.y, 1f)
            assertTrue("$mode: one line, no badge row (${identity.height}dp)", identity.height <= 36f + 12f + 0.5f)
            assertEquals("$mode: no stream badge on the live chrome", 0,
                compose.onAllNodes(hasContentDescription("1080", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    private val sampleBadges = at.bernhardberger.tvhplayer.core.glanceBadges(videoHeight = 1080, videoSampleMimeType = "video/avc",
        audioMimeType = "audio/eac3", audioChannelCount = 6, audioDescription = false, subtitles = true, teletext = true)

    @Test fun pauseKeepsItsFocusWhenTheTimeshiftBufferLeaves() {
        chrome(PlayerChromeMode.CONTROLS)
        assertEquals(listOf("player-pause"), focused())
        compose.runOnIdle { timeshift = AppTimeshiftState() }
        settle()
        assertFalse("the timeline takes no focus without timeshift", exists("player-seekbar"))
        assertEquals("Play/Pause stays in the row and keeps the focus", listOf("player-pause"), focused())
        assertFalse("the action row has no Info", exists("player-info"))
    }

    private var timeshift by mutableStateOf(
        AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -203_000, liveEdgeMs = 0, timingKnown = true))
    private var restoreFocus by mutableStateOf<String?>(null)
    private var recentOpen by mutableStateOf(false)
    private var shownMode by mutableStateOf(PlayerChromeMode.CONTROLS)
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
        info: PlayerInfoBarData = liveInfoBarData(1, "One", null, null, false, 1_800, "Programme"),
        badges: List<at.bernhardberger.tvhplayer.core.GlanceBadge> = emptyList(),
        /** Composed beside the chrome. */
        extra: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        shownMode = mode
        this.timeshift = timeshift
        restoreFocus = restore
        val loader = ImageLoader(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = shownMode,
                    content = PlayerChromeContent("20:15", info, badges = badges),
                    timeline = PlayerChromeTimeline.Live(this.timeshift, nowSec = 1_800),
                    actions = PlayerChromeActions(active = shownMode == PlayerChromeMode.CONTROLS, paused = false,
                        livePause = livePause, restoreFocus = restoreFocus),
                    imageLoader = loader, currentSession = null,
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
                )
                extra()
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

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
