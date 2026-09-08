package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSurfacePolicyTest {
    @Test
    fun `only visible foreground playing video keeps the screen awake`() {
        assertTrue(
            shouldKeepPlaybackScreenOn(
                isForeground = true,
                isVideoVisible = true,
                isPlaying = true,
                hasSelectedVideo = true,
                hasError = false,
            )
        )
        val ineligibleStates = listOf(
            "background" to booleanArrayOf(false, true, true, true, false),
            "concealed before first frame" to booleanArrayOf(true, false, true, true, false),
            "paused buffering idle or ended" to booleanArrayOf(true, true, false, true, false),
            "audio only or video deselected" to booleanArrayOf(true, true, true, false, false),
            "failed playback" to booleanArrayOf(true, true, true, true, true),
        )
        ineligibleStates.forEach { (reason, state) ->
            assertFalse(
                reason,
                shouldKeepPlaybackScreenOn(
                    isForeground = state[0],
                    isVideoVisible = state[1],
                    isPlaying = state[2],
                    hasSelectedVideo = state[3],
                    hasError = state[4],
                ),
            )
        }
    }

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
