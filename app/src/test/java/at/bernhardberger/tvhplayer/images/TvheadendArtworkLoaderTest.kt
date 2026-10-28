package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkContent
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
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
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.components.ArtworkContext
import coil3.decode.DataSource
import coil3.network.NetworkFetcher
import coil3.request.Options
import coil3.toUri
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Logos before a current session come from the saved copy of the profile their observation
 * belongs to, or not at all: never from another profile, neither fetched nor from memory.
 */
@OptIn(ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvheadendArtworkLoaderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val session = FakeTvheadendSession()
    private val loader = buildImageLoader(app, session)
    private val first = ServerProfile("first.tvheadend.test")
    private val second = ServerProfile("second.tvheadend.test")

    @After fun close() {
        loader.shutdown()
        loader.diskCache?.shutdown()
    }

    @Test fun beforeASessionALogoLoadsFromTheSavedCopyOfItsProfile() {
        val observation = connecting(first)
        session.artwork.scriptCached(first, saved(Color.RED))

        val result = load(logo(observation))

        assertEquals(Color.RED, pixel(result))
        assertEquals(DataSource.DISK, (result as SuccessResult).dataSource)
    }

    @Test fun beforeASessionWithoutASavedCopyTheLogoFailsInsteadOfReachingTheServer() {
        val observation = connecting(first)
        session.artwork.scriptCached(profile = null)

        assertTrue(load(logo(observation)) is ErrorResult)
        assertTrue(FakeSessionCall.LOAD_ARTWORK !in session.calls)
    }

    @Test fun observationsOfOneProfileGiveOneModelAndAnotherProfilesObservationAnother() {
        session.artwork.scriptCached(first, saved(Color.RED))
        val logo = logo(connecting(first))

        assertEquals(logo, logo(connecting(first)))

        session.artwork.scriptCached(second, saved(Color.BLUE))
        assertNotEquals(logo, logo(connecting(second)))
    }

    @Test fun afterAProfileChangeALogoOfTheFirstProfileNeverShowsTheSecondProfilesBytes() {
        session.artwork.scriptCached(first, saved(Color.RED))
        val firstObservation = connecting(first)
        val firstLogo = logo(firstObservation)
        val firstKey = memoryKey(load(firstLogo))

        session.artwork.scriptCached(second, saved(Color.BLUE))
        val secondKey = memoryKey(load(logo(connecting(second))))

        assertNotEquals(firstKey, secondKey)
        // The model of the first profile is refused once it is no longer active, and a model the
        // screen still built from the first profile's observation has no saved copy at all.
        assertTrue(load(firstLogo, memory = false) is ErrorResult)
        assertTrue(load(logo(firstObservation), memory = false) is ErrorResult)
        assertEquals(Color.RED, pixel(load(firstLogo)))
    }

    @Test fun backOnTheFirstProfileItsLogoIsTheSameModelAgain() {
        session.artwork.scriptCached(first, saved(Color.RED))
        val firstLogo = logo(connecting(first))
        val firstKey = memoryKey(load(firstLogo))
        session.artwork.scriptCached(second, saved(Color.BLUE))
        assertEquals(Color.BLUE, pixel(load(logo(connecting(second)), memory = false)))

        session.artwork.scriptCached(first, saved(Color.RED))
        val again = logo(connecting(first))

        assertEquals(firstLogo, again)
        assertEquals(Color.RED, pixel(load(again, memory = false)))
        assertEquals(firstKey, memoryKey(load(again)))
    }

    // A connection save before Ready: the new profile's saved logos show without waiting.
    @Test fun afterAConnectionSaveTheNewProfilesSavedLogosShowBeforeReady() {
        session.artwork.scriptCached(first, saved(Color.RED))
        assertEquals(Color.RED, pixel(load(logo(connecting(first)))))

        session.artwork.scriptCached(second, saved(Color.BLUE))

        assertEquals(Color.BLUE, pixel(load(logo(connecting(second)))))
    }

    @Test fun atReadyTheLogoComesFromTheCurrentSession() {
        session.artwork.scriptCached(second, saved(Color.BLUE))
        assertEquals(Color.BLUE, pixel(load(logo(connecting(second)))))

        session.replaceGeneration(ready(), second)
        session.artwork.scriptLoad(saved(Color.BLUE))
        val live = logo(session.observation.value)
        val result = load(live, memory = false)

        assertEquals(AppArtworkSource(session.captureCurrentSession(), ArtworkId(1)), live)
        assertEquals(DataSource.NETWORK, (result as SuccessResult).dataSource)
        assertEquals("SDK artwork is not duplicated in Coil's disk cache", 0L, loader.diskCache!!.size)
    }

    @Test fun remoteLogoSelectsTheNetworkFetcherNotTheSdkFetcher() {
        val icon = ChannelIcon.parse("https://192.0.2.1/logo.png") as ChannelIcon.RemoteUrl
        val context = ArtworkContext(session, mutableStateOf(null), mutableStateOf(null))
        val data = context.logo(icon)
        assertEquals(icon.url, data)
        val mapped = loader.components.map(data, Options(app))
        assertEquals(icon.url.toUri(), mapped)
        assertTrue(loader.components.newFetcher(mapped, Options(app), loader)?.first is NetworkFetcher)
        assertTrue(FakeSessionCall.LOAD_ARTWORK !in session.calls)
    }

    private fun connecting(profile: ServerProfile): SessionObservation {
        session.publish(SessionObservation.create(sessionState = SessionState.Synchronizing), profile)
        return session.observation.value
    }

    // The model a logo of a surface drawing from [observation] gets.
    private fun logo(observation: SessionObservation): Any =
        ArtworkContext(session, mutableStateOf(observation.currentSession), mutableStateOf(observation)).logo(ChannelIcon.ImageCache(ArtworkId(1)))

    // Without [memory] the image is fetched, not read from memory, and still stored there.
    private fun load(source: Any, memory: Boolean = true): ImageResult = runBlocking {
        loader.execute(ImageRequest.Builder(app).data(source)
            .memoryCachePolicy(if (memory) CachePolicy.ENABLED else CachePolicy.WRITE_ONLY).build())
    }

    private fun memoryKey(result: ImageResult): String {
        assertTrue("$result", result is SuccessResult)
        return requireNotNull((result as SuccessResult).memoryCacheKey).key
    }

    private fun pixel(result: ImageResult): Int {
        assertTrue("$result", result is SuccessResult)
        return (result as SuccessResult).image.toBitmap().getPixel(0, 0)
    }

    private fun saved(color: Int) = ArtworkLoadResult.Available(ArtworkContent.create(png(color)))

    private fun png(color: Int): ByteArray = ByteArrayOutputStream().also { out ->
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
