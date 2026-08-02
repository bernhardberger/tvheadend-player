package at.bernhardberger.tvhplayer.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRateMatchingPolicyTest {
    @Test
    fun enabled_usesSeamlessFrameRateMatching() {
        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
            videoChangeFrameRateStrategy(enabled = true),
        )
    }

    @Test
    fun disabled_turnsFrameRateMatchingOff() {
        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            videoChangeFrameRateStrategy(enabled = false),
        )
    }
}
