package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.SuccessResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PiconPrefetchTest {
    @Test fun focusedRowUsesPrefetchedKeyWithoutFetchingOrDecodingAgain() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val loader = ImageLoader.Builder(context).diskCache(null).build()
        val original = File.createTempFile("picon", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val size = IntSize(96, 48)
            val unfocused = loader.execute(inkedPiconRequest(context, original, Color.BLACK, size)) as SuccessResult
            val warmed = prefetchPicon(loader, inkedPiconRequest(context, original, Color.LTGRAY, size)) as SuccessResult
            assertNotEquals(unfocused.memoryCacheKey, warmed.memoryCacheKey)
            assertEquals(DataSource.DISK, warmed.dataSource)

            // With no original and no disk cache, a second fetch/decode cannot succeed.
            assertTrue(original.delete())
            val focused = loader.execute(inkedPiconRequest(context, original, Color.LTGRAY, size,
                unfocused.memoryCacheKey)) as SuccessResult
            assertEquals(warmed.memoryCacheKey, focused.memoryCacheKey)
            assertEquals(DataSource.MEMORY_CACHE, focused.dataSource)
            assertTrue(warmed.image === focused.image)

            val restored = loader.execute(inkedPiconRequest(context, original, Color.BLACK, size,
                focused.memoryCacheKey)) as SuccessResult
            assertEquals(unfocused.memoryCacheKey, restored.memoryCacheKey)
            assertEquals(DataSource.MEMORY_CACHE, restored.dataSource)
        } finally {
            original.delete()
            loader.shutdown()
        }
    }
}
