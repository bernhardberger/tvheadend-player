package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsArtColorsTest {
    @get:Rule val images = ImageLoaderRule()
    @After fun clear() = DetailsArtColors.clear()

    @Test fun clearDuringSamplingRejectsTheColorAndAllowsAFreshSample() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val source = "palette-fixture"
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        images.install(ImageLoader.Builder(app).components {
            add(Interceptor { chain ->
                entered.complete(Unit)
                release.await()
                SuccessResult(bitmap.asImage(), chain.request, DataSource.NETWORK)
            })
        }.build())
        val old = async { DetailsArtColors.sample(app, source) }
        entered.await()
        DetailsArtColors.clear()
        release.complete(Unit)
        assertNull(old.await())
        assertNull(DetailsArtColors.stored(source))
        val fresh = DetailsArtColors.sample(app, source)
        assertNotNull(fresh)
        assertEquals(fresh, DetailsArtColors.stored(source))
    }
}
