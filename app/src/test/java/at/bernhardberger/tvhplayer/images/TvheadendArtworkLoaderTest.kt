package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkContent
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ArtworkLoadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Logos before a current session come from the active profile's saved copy, or not at all. */
@OptIn(ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvheadendArtworkLoaderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val session = FakeTvheadendSession()
    private val loader = buildImageLoader(app, session)

    @Test fun beforeASessionALogoLoadsFromTheSavedCopy() {
        session.artwork.scriptCached(ServerProfile("tvheadend.test"), ArtworkLoadResult.Available(ArtworkContent.create(png())))

        val result = load(AppArtworkSource(null, ArtworkId(1)))

        assertTrue("$result", result is SuccessResult)
        assertEquals(DataSource.DISK, (result as SuccessResult).dataSource)
    }

    @Test fun beforeASessionWithoutASavedCopyTheLogoFailsInsteadOfReachingTheServer() {
        session.artwork.scriptCached(profile = null)

        assertTrue(load(AppArtworkSource(null, ArtworkId(1))) is ErrorResult)
    }

    private fun load(source: AppArtworkSource): ImageResult = runBlocking {
        loader.execute(ImageRequest.Builder(app).data(source).build())
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            .compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()
}
