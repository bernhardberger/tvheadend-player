package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerBusyIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tuningWaitsFiveHundredMillisecondsAcrossLayerChangesAndExitsOnPresentation() =
        delayedOverlay(PlayerBusyStatus.TUNING)

    @Test fun bufferingWaitsOneSecondAcrossLayerChangesAndExitsOnResume() =
        delayedOverlay(PlayerBusyStatus.BUFFERING)

    private fun delayedOverlay(status: PlayerBusyStatus) {
        val delayMs = if (status == PlayerBusyStatus.TUNING) 500L else 1_000L
        var layer by mutableStateOf(0)
        var eligible by mutableStateOf(true)
        var target by mutableStateOf(1)
        lateinit var visible: State<Boolean>
        compose.mainClock.autoAdvance = false
        compose.setContent {
            visible = rememberBusyVisible(target, eligible, status)
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().testTag("video")) {
                    if (layer != 0) RecordingChromeFixture(mode = if (layer == 1) PlayerChromeMode.BANNER else PlayerChromeMode.CONTROLS)
                    PlayerBusyIndicator(status.takeIf { visible.value }, Modifier.align(Alignment.Center))
                }
            }
        }
        val start = compose.mainClock.currentTime
        ring().assertDoesNotExist()
        compose.mainClock.advanceTimeBy(200, ignoreFrameDuration = true)
        compose.runOnIdle { layer = 1 }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { layer = 2 }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(start + delayMs - 1 - compose.mainClock.currentTime, ignoreFrameDuration = true)
        compose.runOnIdle { assertFalse(visible.value) }
        ring().assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
        compose.runOnIdle { assertTrue(visible.value) }
        settle()
        val expected = ring().fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("video").fetchSemanticsNode().boundsInRoot
        assertEquals(video.center, expected.center)
        assertEquals(44f, expected.width, 0.01f)
        assertEquals(44f, expected.height, 0.01f)
        val nodeId = ring().fetchSemanticsNode().id
        for (nextLayer in listOf(0, 1, 2, 0)) {
            compose.runOnIdle { layer = nextLayer }
            settle()
            assertEquals("chrome layers cannot move the overlay", expected, ring().fetchSemanticsNode().boundsInRoot)
            assertEquals("chrome layers cannot replace its announcement node", nodeId, ring().fetchSemanticsNode().id)
        }
        compose.runOnIdle { eligible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse("no minimum hold after presentation/resume", visible.value) }
        compose.mainClock.advanceTimeBy(200)
        ring().assertDoesNotExist()
        // A quick zap/stall that ends before its own delay must never compose the ring.
        compose.runOnIdle { target++; eligible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(200)
        ring().assertDoesNotExist()
        compose.runOnIdle { eligible = false }
        compose.mainClock.advanceTimeBy(delayMs + 200)
        ring().assertDoesNotExist()
    }

    @Test fun oneStablePoliteDescriptionPerStartAndNoProgressOrFocusSemantics() {
        var status by mutableStateOf<PlayerBusyStatus?>(null)
        var tick by mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("video-$tick")) {
                PlayerBusyIndicator(status, Modifier.align(Alignment.Center))
                PlayerHiddenStatusChip(PlayerStateCell.PLAYING, liveBarEnd(0, null))
            }
        }
        ring().assertDoesNotExist()
        compose.onNodeWithTag("player-hidden-slot", useUnmergedTree = true).assertDoesNotExist()
        for (reason in listOf(PlayerBusyStatus.TUNING, PlayerBusyStatus.BUFFERING, PlayerBusyStatus.BUFFERING)) {
            compose.runOnIdle { status = reason }
            settle()
            val description = if (reason == PlayerBusyStatus.TUNING) "Tuning" else "Buffering"
            val id = ring().fetchSemanticsNode().id
            repeat(3) {
                compose.runOnIdle { tick++ }
                compose.mainClock.advanceTimeBy(300)
                val nodes = compose.onAllNodes(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals("one announcing node, no primitive progress announcement", 1, nodes.size)
                assertEquals(id, nodes.single().id)
                val config = nodes.single().config
                assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
                assertFalse(config.contains(SemanticsProperties.ProgressBarRangeInfo))
                assertFalse(config.contains(SemanticsProperties.Focused))
                compose.onNodeWithTag("player-hidden-slot", useUnmergedTree = true).assertDoesNotExist()
            }
            compose.runOnIdle { status = null }
            settle()
            ring().assertDoesNotExist()
            assertTrue(compose.onAllNodes(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test fun onlyAnOwnedUnpresentedTuneWithPlaybackIntentCanStartTheDelay() {
        val live = AppPlaybackTarget.Live(ChannelId(1))
        fun eligible(state: AppPlaybackState = AppPlaybackState.Starting, intent: Boolean = true,
                     target: AppPlaybackTarget? = live, active: Boolean = true, blocked: Boolean = false, presented: Boolean = false) =
            tuningStatusEligible(state, intent, target, live, active, blocked, presented)
        for (state in listOf(AppPlaybackState.Starting, AppPlaybackState.Buffering, AppPlaybackState.Playing)) assertTrue(eligible(state))
        assertFalse("first visible frame or audio presentation ends tuning", eligible(presented = true))
        assertFalse("intentional pause", eligible(intent = false))
        assertFalse("centre message or recovery", eligible(blocked = true))
        assertFalse(eligible(active = false))
        assertFalse(eligible(target = null))
        assertFalse(eligible(target = AppPlaybackTarget.Live(ChannelId(2))))
        assertFalse(eligible(target = AppPlaybackTarget.Recording(DvrEntryId(1))))
        for (state in listOf(AppPlaybackState.Idle, AppPlaybackState.Finished,
            AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER),
            AppPlaybackState.Recovering(PlaybackRecoveryReason.LIVE_ENDED, 1_000))) assertFalse(eligible(state))
    }

    @Test fun foregroundMessageImmediatelyReplacesEvenTheExitingRing() {
        var blocked by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (!blocked) PlayerBusyIndicator(PlayerBusyStatus.BUFFERING)
        }
        settle()
        ring().assertExists()
        compose.runOnIdle { blocked = true }
        compose.mainClock.advanceTimeByFrame()
        ring().assertDoesNotExist()
    }

    private fun ring() = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true)
    private fun settle() {
        repeat(6) { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    }
}
