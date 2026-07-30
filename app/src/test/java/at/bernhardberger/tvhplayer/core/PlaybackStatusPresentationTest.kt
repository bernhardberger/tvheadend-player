package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusPresentationTest {

    @Test
    fun ordinaryChannelStart_isCompactAndNonBlocking() {
        assertEquals(
            PlaybackStatusPresentation.COMPACT_TUNING,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = true,
                playbackRecovering = false,
                playbackPlaying = false,
            )
        )
    }

    @Test
    fun genuineRecovery_orConnectionLoss_isFullScreen() {
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(true, false, true, false),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(false, true, false, false),
        )
        assertEquals(
            PlaybackStatusPresentation.FULL_RECOVERY,
            playbackStatusPresentation(
                connectionAvailable = true,
                playbackStarting = false,
                playbackRecovering = false,
                playbackPlaying = false,
                playbackFailed = true,
            ),
        )
    }

    @Test
    fun playingSession_hasNoWaitingPresentation() {
        assertEquals(
            PlaybackStatusPresentation.NONE,
            playbackStatusPresentation(true, false, false, true),
        )
    }

    @Test
    fun compactTuning_waitsBeforeAppearingAndThenKeepsAnOpaqueInterval() {
        assertEquals(
            CompactTuningVisibilityAction.SHOW_AFTER_DELAY,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = false,
            ),
        )
        assertEquals(500L, COMPACT_TUNING_DELAY_MS)

        assertEquals(
            CompactTuningVisibilityAction.KEEP_VISIBLE,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.HIDE_AFTER_MINIMUM,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.NONE,
                currentlyVisible = true,
            ),
        )
        assertEquals(150L, COMPACT_TUNING_FADE_IN_MS)
        assertEquals(600L, COMPACT_TUNING_MINIMUM_OPAQUE_MS)
    }

    @Test
    fun recoveryAndInactiveScreen_hideCompactTuningImmediately() {
        assertEquals(
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.FULL_RECOVERY,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.HIDE_IMMEDIATELY,
            compactTuningVisibilityAction(
                screenActive = false,
                presentation = PlaybackStatusPresentation.COMPACT_TUNING,
                currentlyVisible = true,
            ),
        )
        assertEquals(
            CompactTuningVisibilityAction.KEEP_HIDDEN,
            compactTuningVisibilityAction(
                screenActive = true,
                presentation = PlaybackStatusPresentation.NONE,
                currentlyVisible = false,
            ),
        )
    }
}
