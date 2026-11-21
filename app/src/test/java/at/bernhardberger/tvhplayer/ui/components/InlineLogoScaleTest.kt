package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineLogoScaleTest {
    private val box = Size(36f, 20f)

    private fun drawn(logo: Size) = InlineLogoScale.computeScaleFactor(logo, box).let {
        Size(logo.width * it.scaleX, logo.height * it.scaleY)
    }

    @Test fun squareAndPortraitLogosStayInsideTheTextLine() {
        assertEquals(14f, drawn(Size(100f, 100f)).height, 0.01f)
        assertEquals(14f, drawn(Size(75f, 100f)).height, 0.01f)
        assertEquals(36f, drawn(Size(300f, 100f)).width, 0.01f)
        assertEquals(36f, drawn(Size(500f, 100f)).width, 0.01f)
    }

    @Test fun theLineKeepsOnlyTheWidthTheLogoIsDrawnIn() {
        // A square logo is 14 wide, so the text follows it instead of the 36 the widest logo takes.
        assertEquals(14f, inlineLogoDrawnWidth(Size(100f, 100f), box), 0.01f)
        assertEquals(36f, inlineLogoDrawnWidth(Size(500f, 100f), box), 0.01f)
        assertEquals(36f, inlineLogoDrawnWidth(Size.Unspecified, box), 0.01f)
    }
}
