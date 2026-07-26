package at.bernhardberger.tvhplayer.player.htsp

import at.bernhardberger.tvhplayer.player.htsp.utils.AspectRatioUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AspectRatioUtilsTest {
    @Test
    fun provisionalSarUsesPalNtScSixteenByNineTables() {
        val pal = AspectRatioUtils.provisionalSarWhenUnknown(720, 576)!!
        assertTrue(abs(pal - 64f / 45f) < 0.001f)
        // 720×576 with that SAR → DAR ≈ 16:9
        val palDar = (720f / 576f) * pal
        assertTrue(abs(palDar - 16f / 9f) < 0.02f)

        val ntsc = AspectRatioUtils.provisionalSarWhenUnknown(720, 480)!!
        assertTrue(abs(ntsc - 32f / 27f) < 0.001f)
    }

    @Test
    fun provisionalSarIsNullForHd() {
        assertNull(AspectRatioUtils.provisionalSarWhenUnknown(1920, 1080))
        assertNull(AspectRatioUtils.provisionalSarWhenUnknown(1280, 720))
    }

    @Test
    fun provisionalSarCoversCommonDvbSdWidths() {
        assertTrue(AspectRatioUtils.provisionalSarWhenUnknown(544, 576) != null)
        assertTrue(AspectRatioUtils.provisionalSarWhenUnknown(704, 576) != null)
        assertTrue(AspectRatioUtils.provisionalSarWhenUnknown(480, 576) != null)
    }

    @Test
    fun adjustSarKeepsNearTargetSixteenByNine() {
        val raw = 64f / 45f
        val adjusted = AspectRatioUtils.adjustSarForBroadcast(720, 576, raw)
        val dar = (720f / 576f) * adjusted
        assertTrue(abs(dar - 16f / 9f) < 0.05f)
        assertEquals(raw, adjusted, 0.05f)
    }
}
