package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.core.playerPlaybackProgressing
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerAutoHideEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bufferingCancelsTheTimerAndStablePlaybackRestartsItFromZero() {
        var playerReady by mutableStateOf(true)
        var interactionToken by mutableIntStateOf(0)
        var hideCalls = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PlayerControlsAutoHideEffect(
                eligible = playerPlaybackProgressing(
                    isPlaying = true,
                    playerReady = playerReady,
                ),
                interactionToken = interactionToken,
                timeoutMillis = 5_000L,
                onHide = { hideCalls++ },
            )
        }

        composeRule.mainClock.advanceTimeBy(4_000L)
        composeRule.runOnIdle { playerReady = false }
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.runOnIdle { assertEquals(0, hideCalls) }

        composeRule.runOnIdle { playerReady = true }
        composeRule.mainClock.advanceTimeBy(4_999L)
        composeRule.runOnIdle { assertEquals(0, hideCalls) }
        composeRule.mainClock.advanceTimeBy(1L)
        composeRule.runOnIdle { assertEquals(1, hideCalls) }

        composeRule.runOnIdle { interactionToken++ }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.runOnIdle { assertEquals(2, hideCalls) }
    }

    @Test
    fun ineligibleHiddenControlsNeverTriggerAVisibilityChange() {
        var hideCalls = 0
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            PlayerControlsAutoHideEffect(
                eligible = false,
                interactionToken = 0,
                timeoutMillis = 5_000L,
                onHide = { hideCalls++ },
            )
        }

        composeRule.mainClock.advanceTimeBy(10_000L)
        composeRule.runOnIdle { assertEquals(0, hideCalls) }
    }
}
