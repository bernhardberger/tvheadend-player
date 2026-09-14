package at.bernhardberger.tvhplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRecordingColorTest {
    @Test
    fun recordingTextMeetsSmallTextContrastOnDarkBadgeSurface() {
        listOf(TvDarkColors.surface, TvSurfaceColors.container, TvSurfaceColors.containerHigh)
            .forEach { surface ->
                assertTrue(contrastRatio(TvRecordingColor, surface) >= 4.5f)
            }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
