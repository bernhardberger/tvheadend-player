package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.RemoteArtworkUrl
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.SessionArtworkRequest
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionArtworkLoaderTest {
    @Test fun selectsPiconsAndDvrPicturesWithBoundedEncodingAndFallbacks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(CapabilityAccess.ALLOWED, CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(emptyList()))))
        val observation = session.observation.value
        val logo = ArtworkId(1)
        val picture = ArtworkId(2)
        val picon = FixtureArt.picon("harbor-sport")
        val photo = Bitmap.createScaledBitmap(FixtureArt.art("harbor-kickoff"), 2048, 1152, true)
        val random = kotlin.random.Random(7)
        val noisy = Bitmap.createBitmap(IntArray(512 * 512) { random.nextInt() or Color.BLACK }, 512, 512, Bitmap.Config.ARGB_8888)
        val unbounded = java.io.ByteArrayOutputStream().also { noisy.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        assertTrue(unbounded.size() > 256 * 1024) // The first quality must fail the byte bound.
        val live = SessionArtworkRequest(AppPlaybackTarget.Live(ChannelId(1)), ChannelIcon.ImageCache(logo), null, requireNotNull(observation.currentSession))
        val recording = live.copy(target = AppPlaybackTarget.Recording(DvrEntryId(1)), recordingImage = picture)
        val cases = listOf(
            Triple(live, mapOf(logo to picon, picture to photo), "image/png"),
            Triple(recording, mapOf(logo to picon, picture to photo), "image/jpeg"),
            Triple(recording, mapOf(picture to noisy), "image/jpeg"),
            Triple(recording, mapOf(logo to picon), "image/png"),
            Triple(recording, emptyMap(), null),
        )
        for ((request, images, mime) in cases) {
            val requested = mutableListOf<ArtworkId>()
            val imageLoader = FixtureArt.imageLoader(context, images) { requested += it.id }
            try {
                val result = runCatching { AppSessionArtworkLoader(context, session, imageLoader).load(request, observation) }
                if (mime == null) assertTrue(result.exceptionOrNull() is java.io.IOException)
                else {
                    val bytes = result.getOrThrow()
                    requireNotNull(bytes)
                    assertTrue(bytes.size <= 256 * 1024)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    assertEquals(mime, options.outMimeType)
                    val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                    assertTrue(maxOf(decoded.width, decoded.height) <= 512)
                    if (mime == "image/png") {
                        assertEquals(decoded.width, decoded.height)
                        assertTrue(decoded.hasAlpha())
                        assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
                    }
                }
                assertEquals(if (request == live) listOf(logo)
                    else if (picture in images) listOf(picture) else listOf(picture, logo), requested)
            } finally {
                imageLoader.shutdown()
            }
        }
        val remote = requireNotNull(RemoteArtworkUrl.parse("https://192.0.2.1/recording.jpg"))
        val requested = mutableListOf<Any>()
        val remoteLoader = ImageLoader.Builder(context).components {
            add(Interceptor { chain ->
                requested += chain.request.data
                SuccessResult(photo.asImage(), chain.request, DataSource.MEMORY)
            })
        }.build()
        try {
            val loader = AppSessionArtworkLoader(context, session, remoteLoader)
            assertNotNull(loader.load(recording.copy(recordingImage = null, recordingRemoteImage = remote), observation))
            assertEquals(listOf(remote.url), requested)
            requested.clear()
            assertNotNull(loader.load(recording.copy(recordingRemoteImage = remote), observation))
            assertEquals(listOf(AppArtworkSource(recording.session, picture)), requested)
        } finally {
            remoteLoader.shutdown()
        }
    }
}
