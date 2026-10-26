package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSurfacePolicyTest {
    @Test fun browseModesKeepTheSurfaceButOnlyOnShowsThePictureAndOnlyOffStops() {
        for (mode in WarmPlaybackMode.entries) {
            assertTrue(shouldMountPersistentPlayerSurface(hasActivePlayback = true, isPlayerRoute = false))
            assertEquals(mode == WarmPlaybackMode.ON, shouldShowWarmPlaybackScrim(true, false, mode))
            assertEquals(mode == WarmPlaybackMode.OFF, shouldStopPlaybackWhileBrowsing(false, mode))
            assertEquals(mode != WarmPlaybackMode.ON, shouldCoverWarmPlayback(false, mode))
            assertFalse(shouldCoverWarmPlayback(true, mode))
            assertFalse(shouldStopPlaybackWhileBrowsing(true, mode))
            assertFalse(shouldShowWarmPlaybackScrim(true, true, mode))
            assertFalse(shouldShowWarmPlaybackScrim(false, false, mode))
        }
    }
    @Test fun loadingPreferencesCoverBrowseWithoutScrimOrStop() {
        assertTrue(shouldCoverWarmPlayback(false, null))
        assertFalse(shouldCoverWarmPlayback(true, null))
        assertFalse(shouldShowWarmPlaybackScrim(true, false, null))
        assertFalse(shouldStopPlaybackWhileBrowsing(false, null))
    }
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

    @Test
    fun `only warm playback behind an ordinary route gets the global scrim`() {
        assertTrue(shouldShowWarmPlaybackScrim(hasActivePlayback = true, isPlayerRoute = false))
        assertFalse(shouldShowWarmPlaybackScrim(hasActivePlayback = false, isPlayerRoute = false))
        assertFalse(shouldShowWarmPlaybackScrim(hasActivePlayback = true, isPlayerRoute = true))
    }
}
