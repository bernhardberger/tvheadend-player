package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bar row of the player chrome: what its state cell, end, live state and step readout claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeTimelineRowTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_800L
    private val atEdge = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0)

    // 1. Unavailable playback claims neither Live nor Playing.

    @Test fun anUnavailableChannelHasNoBarStatusAndAnAvailableOneHas() {
        var unavailable: PlayerBarStatus? = null
        var available: PlayerBarStatus? = null
        compose.setContent {
            unavailable = live(liveAvailable = false).barStatus(PlayerStateCell.PLAYING)
            available = live().barStatus(PlayerStateCell.PLAYING)
        }
        compose.waitForIdle()
        assertNull(unavailable)
        assertNotNull(available)
        assertNull(live(liveAvailable = false).barEnd())
    }

    @Test fun anAvailableChannelsBannerShowsTheStateCellAndAnnouncesTheLiveState() {
        chrome(PlayerChromeMode.BANNER, live())
        assertTrue(exists("player-state"))
        assertTrue(exists("player-live-state"))
    }

    @Test fun anAvailableChannelsControlsAnnounceTheLiveStateAndShowNoStateCell() {
        chrome(PlayerChromeMode.CONTROLS, live())
        assertFalse(exists("player-state"))
        assertTrue(exists("player-live-state"))
    }

    @Test fun anUnavailableChannelsControlsShowNoStateCellOrLiveState() {
        chrome(PlayerChromeMode.CONTROLS, live(liveAvailable = false))
        assertTrue("the controls stay", exists("player-actions"))
        assertFalse(exists("player-state"))
        assertFalse(exists("player-live-state"))
    }

    @Test fun theHiddenChipShowsTheRealStateOnlyWhileHiddenAndAvailable() {
        for (state in PlayerStateCell.entries) {
            assertEquals(state, hiddenChipState(state, hidden = true, available = true))
            assertEquals(PlayerStateCell.PLAYING, hiddenChipState(state, hidden = true, available = false))
            assertEquals(PlayerStateCell.PLAYING, hiddenChipState(state, hidden = false, available = true))
        }
    }

    // 2. The step readout agrees with the pinned progress: it measures against the verified live edge.

    private val extrapolated = atEdge.copy(displayLiveEdgeMs = 4_000)

    @Test fun theStepReadoutWithinToleranceOfTheVerifiedEdgeIsLiveEvenWithAnExtrapolatedAxis() {
        compose.setContent { TVHeadendPlayerTheme { TimeshiftSeekPreviewTimeline(extrapolated, TimeshiftSeekDecision(-4_000, -4_000, false)) } }
        compose.waitForIdle()
        assertTrue(exists("timeshift-preview-live"))
        assertTrue(texts("Live"))
    }

    @Test fun theStepReadoutBeyondToleranceOfTheVerifiedEdgeIsTheDistanceFromIt() {
        compose.setContent { TVHeadendPlayerTheme { TimeshiftSeekPreviewTimeline(extrapolated, TimeshiftSeekDecision(-6_000, -6_000, false)) } }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-live"))
        assertTrue(exists("timeshift-preview-behind"))
        assertTrue(texts("−0:06"))
    }

    @Test fun theSeekbarReadoutAgreesWithTheVerifiedEdgeToo() {
        fun seekbar(positionMs: Long) = compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = timeshiftSeekbarRange(extrapolated.copy(positionMs = positionMs)),
                    onSeekTo = {}, previewing = true, stepDeltaMs = positionMs,
                )
            }
        }
        seekbar(-4_000)
        compose.waitForIdle()
        assertTrue(exists("timeshift-preview-live"))
        assertTrue(texts("Live"))
    }

    @Test fun theSeekbarReadoutBeyondToleranceIsTheDistanceFromTheVerifiedEdge() {
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = timeshiftSeekbarRange(extrapolated.copy(positionMs = -6_000)),
                    onSeekTo = {}, previewing = true, stepDeltaMs = -6_000,
                )
            }
        }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-live"))
        assertTrue(texts("−0:06"))
    }

    // 3. A preview into a metadata gap does not borrow the committed programme.

    private val programme = EpgEvent.create(EventId(42), ChannelId(1), Instant.fromEpochSeconds(now - 3_600),
        Instant.fromEpochSeconds(now + 1_800), title = "Known")
    private val committedWindow = ProgrammeWindow(programme, Instant.fromEpochSeconds(now - 203), 0.6f, 0.4f, 0.65f, 0.65f, true)
    private val committedState = atEdge.copy(positionMs = -203_000)

    @Test fun atRestTheRightGroupCarriesTheCommittedProgrammesEnd() {
        chrome(PlayerChromeMode.CONTROLS, live(committedState, committedWindow = committedWindow))
        assertEquals("at rest the programme's end", formatClock(programme.stop.epochSeconds), endClock())
    }

    @Test fun aPreviewIntoAGapShowsTheBufferAxisNotTheCommittedProgrammesEnd() {
        chrome(PlayerChromeMode.CONTROLS, live(committedState.copy(positionMs = -500_000), committedState = committedState,
            committedWindow = committedWindow, previewing = true))
        assertFalse("no borrowed end clock", exists("player-end-clock") && endClock() == formatClock(programme.stop.epochSeconds))
        assertFalse("no programme clock on the left", exists("player-window-start"))
    }

    // 4. The ticking distance is confined to the leaf.

    @Test fun aTickingDistanceRecomposesTheLeafAndNotTheBlock() {
        val end = mutableStateOf(liveBarEnd(203_000, "21:45"))
        var blockCompositions = 0
        val counted = Modifier.composed { SideEffect { blockCompositions++ }; Modifier }
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerTimelineBlock(
                    progress = 0.5f, tone = PlayerTimelineTone.INTERACTIVE, reserveLabelSpace = true, modifier = counted,
                    status = rememberPlayerBarStatus(PlayerStateCell.PLAYING, end.value),
                )
            }
        }
        compose.waitForIdle()
        assertEquals("3:23 behind live", endText())
        assertEquals("21:45", endClock())
        val first = blockCompositions

        compose.runOnIdle { end.value = liveBarEnd(204_000, "21:45") }
        compose.waitForIdle()
        assertEquals("the leaf shows the tick", "3:24 behind live", endText())
        assertEquals("the block did not recompose", first, blockCompositions)

        compose.runOnIdle { end.value = liveBarEnd(205_000, "22:00") }
        compose.waitForIdle()
        assertEquals("3:25 behind live", endText())
        assertEquals("22:00", endClock())
        assertTrue("static row data does recompose the block", blockCompositions > first)
    }

    // 5. The buffer-start clock needs the timing, not a programme window.

    @Test fun theFirstStepShowsTheBufferStartClockOnceTheTimingIsKnown() {
        fun preview(state: AppTimeshiftState) = compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(state, TimeshiftSeekDecision(-30_000, -30_000, false),
                    barStatus = rememberPlayerBarStatus(PlayerStateCell.PLAYING, liveBarEnd(0, null)),
                    nowSec = now)
            }
        }
        preview(atEdge.copy(timingKnown = true))
        compose.waitForIdle()
        assertTrue(texts(formatClock(now - 600)))
        assertTrue(exists("timeshift-preview-buffer-start"))
    }

    @Test fun withoutKnownTimingTheStepShowsNoBufferStartClock() {
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(atEdge.copy(timingKnown = false), TimeshiftSeekDecision(-30_000, -30_000, false),
                    barStatus = rememberPlayerBarStatus(PlayerStateCell.PLAYING, liveBarEnd(0, null)),
                    nowSec = now)
            }
        }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-buffer-start"))
    }

    // 6. A step to the live edge leads with the play glyph, never fast-forward.

    @Test fun aLiveBannerStepAtTheEdgeLeadsWithTheLiveGlyph() {
        chrome(PlayerChromeMode.BANNER_STEP, live(step = TimeshiftSeekDecision(0, 30_000, true)))
        assertTrue(exists("timeshift-preview-live"))
        assertFalse(exists("timeshift-preview-ahead"))
    }

    @Test fun aGrowingRecordingsBannerStepAtItsHeadLeadsWithTheLiveGlyph() {
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(mode = PlayerChromeMode.BANNER_STEP, growing = true, positionMs = 3_400_000,
                    durationMs = 3_600_000, targetMs = 3_598_000, originMs = 3_400_000)
            }
        }
        settle()
        assertTrue(exists("timeshift-preview-live"))
        assertFalse(exists("timeshift-preview-ahead"))
    }

    private fun live(
        timeshift: AppTimeshiftState = atEdge,
        committedState: AppTimeshiftState = timeshift,
        committedWindow: ProgrammeWindow? = null,
        previewing: Boolean = false,
        step: TimeshiftSeekDecision? = null,
        liveAvailable: Boolean = true,
    ) = PlayerChromeTimeline.Live(
        timeshift = timeshift, nowSec = now, committedTimeshift = committedState, committedWindow = committedWindow,
        previewing = previewing, step = step, liveAvailable = liveAvailable,
    )

    private fun chrome(mode: PlayerChromeMode, timeline: PlayerChromeTimeline.Live) {
        compose.setContent {
            val context = LocalContext.current
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = mode,
                    content = PlayerChromeContent("20:15", liveInfoBarData(1, "One", null, null, false, now, "Programme")),
                    timeline = timeline,
                    actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS),
                    imageLoader = remember { ImageLoader(context) },
                    currentSession = null,
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.autoAdvance = false
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun exists(tag: String) =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun texts(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun endClock(): String =
        compose.onNodeWithTagUnmerged("player-end-clock").config[SemanticsProperties.Text].joinToString { it.text }

    private fun endText(): String =
        compose.onNodeWithTagUnmerged("player-distance").config[SemanticsProperties.Text].joinToString { it.text }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTagUnmerged(tag: String) =
        onNode(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
}
