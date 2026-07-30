package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVisualStylePolicyTest {
    @Test
    fun unknownRecordingTimelineKeepsStatusAboveMetadataEmphasis() {
        assertEquals(TvOverlayTextTertiaryAlpha, recordingDurationStatusEmphasis.elapsedAlpha)
        assertEquals(TvOverlayTextSecondaryAlpha, recordingDurationStatusEmphasis.statusAlpha)
        assertEquals(TvOverlayTextTertiaryAlpha, recordingDurationStatusEmphasis.deltaAlpha)
    }

    @Test
    fun topScrimProtectsBothIdentityColumnsThroughSupportingText() {
        assertTrue(playerTopScrimTone.topAlpha >= 0.84f)
        assertTrue(playerTopScrimTone.middleAlpha >= 0.60f)
        assertTrue(playerTopScrimTone.middleStop >= 0.55f)
    }

    @Test
    fun compactTuningUsesBrowsePanelOpacityWithoutBecomingModal() {
        assertEquals(0.84f, compactTuningSurfaceAlpha)
    }
}
