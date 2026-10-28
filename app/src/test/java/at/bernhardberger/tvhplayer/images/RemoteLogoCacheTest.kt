package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The HTTP interceptor returns bytes locally; no socket, DNS, or real server is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteLogoCacheTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val session = FakeTvheadendSession()
    private var cache = RemoteLogoCache(app)
    private var loader: ImageLoader? = null
    private val requests = AtomicInteger()
    // RFC 5737 documentation address, intercepted before network I/O.
    private val url = "https://192.0.2.1/logo.png"

    @After fun close() {
        loader?.shutdown()
        cache.diskCache.clear()
        cache.diskCache.shutdown()
    }

    @Test fun remoteBytesSurviveAColdStartOfflineWithoutGoingThroughTheSdk() = runBlocking {
        val online = imageLoader()
        assertEquals(DataSource.NETWORK, (online.execute(request()) as SuccessResult).dataSource)
        assertEquals(1, requests.get())
        assertNotNull(cache.diskCache.openSnapshot(url)?.also { it.close() })
        assertEquals(16L * 1024 * 1024, cache.diskCache.maxSize)
        online.shutdown()
        cache.diskCache.shutdown()

        cache = RemoteLogoCache(app)
        val offline = imageLoader { throw IOException("Offline fixture") }
        assertEquals(DataSource.DISK, (offline.execute(request()) as SuccessResult).dataSource)
        assertEquals(1, requests.get())
        assertFalse(FakeSessionCall.LOAD_ARTWORK in session.calls)
    }

    @Test fun clearDropsBothDiskAndDecodedMemory() = runBlocking {
        val imageLoader = imageLoader()
        assertTrue(imageLoader.execute(request(memory = true)) is SuccessResult)
        assertTrue(requireNotNull(imageLoader.memoryCache).size > 0)
        cache.clear()
        assertNull(cache.diskCache.openSnapshot(url))
        assertEquals(0L, imageLoader.memoryCache!!.size)
        imageLoader.execute(request())
        assertEquals(2, requests.get())
    }

    @Test fun aResponseAlreadyInFlightCannotRepopulateAClearedCache() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val imageLoader = imageLoader {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS)) { "Fixture response was not released" }
        }
        val result = async(Dispatchers.Default) { imageLoader.execute(request(memory = true)) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            cache.clear()
        } finally {
            release.countDown()
        }
        assertTrue(result.await() is ErrorResult)
        assertNull(cache.diskCache.openSnapshot(url))
        assertEquals(0L, imageLoader.memoryCache!!.size)
    }

    @Test fun plainHttpUsesTheSameFetcherAndFailureDoesNotRetry() = runBlocking {
        val imageLoader = imageLoader { throw IOException("Offline fixture") }
        val result = imageLoader.execute(request().newBuilder().data(url.replace("https:", "http:")).build())
        assertTrue(result is ErrorResult)
        assertEquals(1, requests.get())
        assertEquals(0L, cache.diskCache.size)
        assertFalse(FakeSessionCall.LOAD_ARTWORK in session.calls)
    }

    private fun request(memory: Boolean = false) = ImageRequest.Builder(app).data(url)
        .memoryCachePolicy(if (memory) CachePolicy.ENABLED else CachePolicy.DISABLED).build()

    private fun imageLoader(beforeResponse: () -> Unit = {}): ImageLoader {
        val bytes = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            requests.incrementAndGet()
            assertNull(chain.request().header("Authorization"))
            assertNull(chain.request().header("Cookie"))
            beforeResponse()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Content-Type", "image/png").body(bytes.toResponseBody()).build()
        }.build()
        // Replace only the component registry to use a no-network HTTP client. The production
        // disk/memory configuration and clearing interceptor stay in use.
        return buildImageLoader(app, session, cache).newBuilder().components {
            add(cache)
            addTvheadendArtwork()
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
        }.build().also { loader = it }
    }
}
