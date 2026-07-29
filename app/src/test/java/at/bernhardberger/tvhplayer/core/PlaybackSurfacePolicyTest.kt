package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSurfacePolicyTest {
    @Test
    fun `shell mounts the persistent surface only for an active session`() {
        assertFalse(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = false,
                isPlayerRoute = false,
            )
        )
        assertTrue(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = true,
                isPlayerRoute = false,
            )
        )
    }

    @Test
    fun `live player route mounts the persistent surface before and during playback`() {
        assertTrue(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = false,
                isPlayerRoute = true,
            )
        )
        assertTrue(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = true,
                isPlayerRoute = true,
            )
        )
    }

    @Test
    fun `recording player route mounts the persistent surface before and during playback`() {
        assertTrue(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = false,
                isPlayerRoute = true,
            )
        )
        assertTrue(
            shouldMountPersistentPlayerSurface(
                hasActivePlayback = true,
                isPlayerRoute = true,
            )
        )
    }
}
