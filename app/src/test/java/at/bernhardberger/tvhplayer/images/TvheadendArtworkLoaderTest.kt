package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkContent
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ArtworkLoadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
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
    @Volatile private var profileGeneration = 0L
    private val loader = buildImageLoader(app, session) { profileGeneration == 0L }
    private val first = ServerProfile("first.tvheadend.test")
    private val second = ServerProfile("second.tvheadend.test")

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

    @Test fun afterAProfileChangeAScreenFromTheFirstProfileShowsNeitherProfilesSavedLogoForTheSameId() {
        session.artwork.scriptCached(first, saved(Color.RED))
        val firstLogo = AppArtworkSource(null, ArtworkId(1))
        assertEquals(Color.RED, pixel(load(firstLogo)))

        switchProfile()
        session.artwork.scriptCached(second, saved(Color.BLUE))

        val result = load(firstLogo)
        assertTrue("$result", result is ErrorResult)
    }

    @Test fun afterAProfileChangeTheSecondProfilesLogoComesFromItsCurrentSession() {
        session.artwork.scriptCached(first, saved(Color.RED))
        assertEquals(Color.RED, pixel(load(AppArtworkSource(null, ArtworkId(1)))))

        switchProfile()
        session.artwork.scriptCached(second, saved(Color.BLUE))
        session.replaceGeneration(ready())
        session.artwork.scriptLoad(saved(Color.BLUE))

        assertEquals(Color.BLUE, pixel(load(AppArtworkSource(session.captureCurrentSession(), ArtworkId(1)))))
    }

    // Like the connection editor: the generation rises before the session connects elsewhere.
    private fun switchProfile() {
        profileGeneration++
    }

    private fun load(source: AppArtworkSource): ImageResult = runBlocking {
        loader.execute(ImageRequest.Builder(app).data(source).build())
    }

    private fun pixel(result: ImageResult): Int {
        assertTrue("$result", result is SuccessResult)
        return (result as SuccessResult).image.toBitmap().getPixel(0, 0)
    }

    private fun saved(color: Int) = ArtworkLoadResult.Available(ArtworkContent.create(png(color)))

    private fun png(color: Int = Color.RED): ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            .compress(Bitmap.CompressFormat.PNG, 100, out)
    }.toByteArray()

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )
}
