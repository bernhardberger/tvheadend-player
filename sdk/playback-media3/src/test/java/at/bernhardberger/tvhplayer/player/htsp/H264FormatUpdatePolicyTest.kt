package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.player.htsp.reader.shouldProbeH264Sar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H264FormatUpdatePolicyTest {
    @Test
    fun `unconfirmed SAR is probed only on keyframes`() {
        assertFalse(shouldProbeH264Sar(configured = false, isKey = false, elapsedUs = 0L))
        assertTrue(shouldProbeH264Sar(configured = false, isKey = true, elapsedUs = 0L))
    }

    @Test
    fun `confirmed SAR is rechecked only after cooldown on a keyframe`() {
        assertFalse(shouldProbeH264Sar(configured = true, isKey = true, elapsedUs = 999_999L))
        assertFalse(shouldProbeH264Sar(configured = true, isKey = false, elapsedUs = 1_000_000L))
        assertTrue(shouldProbeH264Sar(configured = true, isKey = true, elapsedUs = 1_000_000L))
    }
}
