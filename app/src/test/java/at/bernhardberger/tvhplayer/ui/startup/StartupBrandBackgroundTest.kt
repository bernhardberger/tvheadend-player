package at.bernhardberger.tvhplayer.ui.startup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class StartupBrandBackgroundTest {
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
}
