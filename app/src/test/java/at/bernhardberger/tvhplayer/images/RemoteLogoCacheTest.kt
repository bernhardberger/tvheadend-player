package at.bernhardberger.tvhplayer.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionCommandResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvheadend.sdk.android.addTvheadendArtwork
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        cache.clear() // Persist a nonzero generation, not just the initial default.
        val online = imageLoader()
        val result = online.execute(request()) as SuccessResult
        assertEquals(DataSource.NETWORK, result.dataSource)
        assertEquals(1, requests.get())
        assertNotNull(cache.diskCache.openSnapshot(requireNotNull(result.diskCacheKey))?.also { it.close() })
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
        val result = imageLoader.execute(request(memory = true)) as SuccessResult
        assertTrue(requireNotNull(imageLoader.memoryCache).size > 0)
        cache.clear()
        assertNull(cache.diskCache.openSnapshot(requireNotNull(result.diskCacheKey)))
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
        assertEquals(0L, cache.diskCache.size)
        assertEquals(0L, imageLoader.memoryCache!!.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun oldResponseDuringProfileReplacementCannotEvictNewGenerationOrSdkMemory() = runTest {
        val replacementEntered = CompletableDeferred<Unit>()
        val releaseReplacement = CompletableDeferred<Unit>()
        val fakeSession = FakeTvheadendSession()
        val session = object : TvheadendSession by fakeSession {
            override suspend fun connect(profile: ServerProfile): SessionCommandResult {
                replacementEntered.complete(Unit)
                releaseReplacement.await()
                return fakeSession.connect(profile)
            }
        }
        val owner = AppProfileOwner(
            session = session, profileStore = FakeServerProfileStore(),
            playerSettings = PlayerSettingsStore(app),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            readProfileForEditing = { error("Unexpected editing read") },
            clearAppArtwork = cache::clear,
        )
        backgroundScope.launch { owner.run() }
        runCurrent()
        val save = async { owner.saveServer("offline.invalid", 9982) }
        replacementEntered.await()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val imageLoader = imageLoader {
            if (requests.get() == 1) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Fixture response was not released" }
            }
        }
        val old = async(Dispatchers.Default) { imageLoader.execute(request(memory = true)) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            releaseReplacement.complete(Unit)
            save.await() // The clear follows replacement; the request above still has the old generation.
            val fresh = imageLoader.execute(request(memory = true)) as SuccessResult
            val memory = requireNotNull(imageLoader.memoryCache)
            val sdkKey = MemoryCache.Key("sdk-artwork-fixture")
            memory[sdkKey] = MemoryCache.Value(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage())
            release.countDown()
            val stale = old.await() as ErrorResult
            assertNotEquals(fresh.diskCacheKey, stale.request.diskCacheKey)
            assertNull(cache.diskCache.openSnapshot(requireNotNull(stale.request.diskCacheKey)))
            assertTrue(memory.keys.none { it.key == stale.request.memoryCacheKey })
            assertNotNull(cache.diskCache.openSnapshot(requireNotNull(fresh.diskCacheKey))?.also { it.close() })
            assertNotNull(memory[requireNotNull(fresh.memoryCacheKey)])
            assertNotNull(memory[sdkKey])
            assertEquals(DataSource.MEMORY_CACHE, (imageLoader.execute(request(memory = true)) as SuccessResult).dataSource)
            assertEquals(2, requests.get())
        } finally {
            release.countDown()
        }
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
