package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.geometry.Size
import at.bernhardberger.tvhplayer.images.LogoBounds
import at.bernhardberger.tvhplayer.images.visibleBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InlineLogoScaleTest {
    private val box = Size(36f, 20f)

    private fun drawn(logo: Size) = InlineLogoScale.computeScaleFactor(logo, box).let {
        Size(logo.width * it.scaleX, logo.height * it.scaleY)
    }

    @Test fun squareAndPortraitLogosStayInsideTheTextLine() {
        assertEquals(14f, drawn(Size(100f, 100f)).height, 0.01f)
        assertEquals(14f, drawn(Size(75f, 100f)).height, 0.01f)
    }

    @Test fun wideLogosKeepTheBoxWidth() {
        assertEquals(36f, drawn(Size(300f, 100f)).width, 0.01f)
        assertEquals(36f, drawn(Size(500f, 100f)).width, 0.01f)
    }

    @Test fun theLineKeepsOnlyTheWidthTheLogoIsDrawnIn() {
        // A square logo is 14 wide, so the text follows it instead of the 36 the widest logo takes.
        assertEquals(14f, inlineLogoDrawnWidth(Size(100f, 100f), box), 0.01f)
        assertEquals(36f, inlineLogoDrawnWidth(Size(500f, 100f), box), 0.01f)
        assertEquals(36f, inlineLogoDrawnWidth(Size.Unspecified, box), 0.01f)
    }

    @Test fun trimmingFindsTheLogoInsideItsTransparentCanvas() {
        val width = 10
        val pixels = IntArray(width * 6)
        // An opaque 3×2 logo at (4, 2) and one nearly transparent stray pixel at the corner.
        for (y in 2..3) for (x in 4..6) pixels[y * width + x] = 0xFFFF0000.toInt()
        pixels[0] = 0x08FFFFFF
        assertEquals(LogoBounds(4, 2, 3, 2), visibleBounds(pixels, width, 6))
        assertNull(visibleBounds(IntArray(width * 6), width, 6))
    }
}
