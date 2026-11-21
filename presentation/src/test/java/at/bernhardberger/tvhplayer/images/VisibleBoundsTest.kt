package at.bernhardberger.tvhplayer.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisibleBoundsTest {
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
