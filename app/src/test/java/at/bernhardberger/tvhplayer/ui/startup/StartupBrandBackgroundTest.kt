package at.bernhardberger.tvhplayer.ui.startup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StartupBrandBackgroundTest {
    @Test fun quietOpeningPeakAndGentleDriftShareTheBrandTimeline() {
        assertEquals(StartupBrandBackgroundFrame(0f, 0f), startupBrandBackgroundFrame(0f))
        assertEquals(0f, startupBrandBackgroundFrame(120f)!!.alpha, 0f)
        assertEquals(0.25f, startupBrandBackgroundFrame(535f)!!.alpha, 0f)
        assertEquals(0.5f, startupBrandBackgroundFrame(950f)!!.alpha, 0f)
        assertEquals(0.33f, startupBrandBackgroundFrame(1400f)!!.alpha, 0.0001f)
        assertEquals(0.5f, startupBrandBackgroundFrame(975f)!!.drift, 0f)
        assertEquals(0.16f, startupBrandBackgroundFrame(1849f)!!.alpha, 0.0001f)
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertNull(startupBrandBackgroundFrame(it))
        }
    }

    @Test fun disabledBackgroundHasNoPlateWhileFinishedIntroKeepsTheBaseGlow() {
        assertNull(startupBrandBackgroundFrame(100f, enabled = false))
        assertNull(startupBrandBackgroundFrame(0f, enabled = false))
        val intro = StartupBrandIntro(eligible = true)
        try {
            intro.passive(true)
            intro.resumed(true)
            intro.focused(true)
            intro.entranceReady()
            intro.frame(100f)
            assertEquals(0f, startupBrandBackgroundFrame(intro.millis)!!.alpha, 0f)
            intro.finish()
            val settled = startupBrandBackgroundFrame(intro.millis)!!
            assertEquals(1f, settled.drift, 0f)
            assertEquals(0.16f, settled.alpha, 0.0001f)
            intro.passive(true)
            intro.entranceReady()
            assertEquals(settled, startupBrandBackgroundFrame(intro.millis))
        } finally {
            intro.finish()
        }
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun lightOnlyPlateHasOneDensityIndependentFrameWithinTwoMiB() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val atlas = BitmapFactory.decodeResource(resources, R.drawable.startup_background_plate,
            BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )!!
        try {
            assertEquals(960, atlas.width)
            assertEquals(540, atlas.height)
            assertEquals(2_073_600, atlas.allocationByteCount)
            assertTrue(atlas.allocationByteCount <= 2 * 1024 * 1024)
            for (y in 0 until atlas.height) for (x in 0 until atlas.width) {
                val pixel = atlas.getPixel(x, y)
                assertTrue(((pixel shr 16) and 255) >= 15)
                assertTrue(((pixel shr 8) and 255) >= 16)
                assertTrue((pixel and 255) >= 20)
            }
        } finally {
            atlas.recycle()
        }
    }

    @Test fun settledEnvelopeNeverReplaysTheSweepOrMovesThePlate() {
        val settled = startupBrandBackgroundFrame(StartupBrandDurationMillis)
        listOf(2000f, 10_000f, 60_000f).forEach {
            assertEquals(settled, startupBrandBackgroundFrame(it))
        }
    }
}
