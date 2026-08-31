package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LivePlayerLayerStateLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leavingCompositionCancelsPendingAutoHide() {
        var mounted by mutableStateOf(true)
        lateinit var state: LivePlayerLayerState
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            if (mounted) {
                state = rememberLivePlayerLayerState(autoHideTimeoutMillis = 5_000L)
                SideEffect { state.updateAutoHideEligibility(eligible = true) }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_500L)
        composeRule.runOnIdle { mounted = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(5_000L)

        composeRule.runOnIdle { assertTrue(state.controlsVisible) }
    }
}
