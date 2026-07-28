package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRecordingColorTest {
    @Test
    fun recordingTextMeetsSmallTextContrastOnDarkBadgeSurface() {
        val badgeSurface = Color(0xFF17181D)

        assertTrue(contrastRatio(TvRecordingColor, badgeSurface) >= 4.5f)
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
