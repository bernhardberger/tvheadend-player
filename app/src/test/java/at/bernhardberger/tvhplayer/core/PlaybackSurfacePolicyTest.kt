package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSurfacePolicyTest {
    @Test
    fun `warm root surface is used only behind browse UI`() {
        assertFalse(shouldUseWarmVideoSurface(hasActivePlayback = false, isPlayerRoute = false))
        assertFalse(shouldUseWarmVideoSurface(hasActivePlayback = true, isPlayerRoute = true))
        assertTrue(shouldUseWarmVideoSurface(hasActivePlayback = true, isPlayerRoute = false))
    }
}
