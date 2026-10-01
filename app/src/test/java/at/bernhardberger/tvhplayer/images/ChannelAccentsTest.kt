package at.bernhardberger.tvhplayer.images

import at.bernhardberger.tvheadend.sdk.core.ArtworkFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendArtworkLoadException
import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shared sampler and the fill-in pass, over a fake session and an image loader that serves fixed picons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelAccentsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val red = ArtworkId(1)
    private val stored = ArtworkId(2)
    private val white = ArtworkId(3)
    private val broken = ArtworkId(4)
    private val blue = ArtworkId(5)
    private val missing = ArtworkId(6)
    private val corrupt = ArtworkId(7)
    private val session = FakeTvheadendSession(observation(catalog(listOf(red, stored, null, white, broken))))
    private val store = ChannelAccentStore()
    private val accents = ChannelAccents(store) { current, id -> key(id).takeIf { session.isCurrent(current) } }
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    /** Every picon load in order, and how many ran at once at most. */
    private val loads: MutableList<ArtworkId> = Collections.synchronizedList(mutableListOf())
    private val running = AtomicInteger()
    private val mostAtOnce = AtomicInteger()
    /** Loads of these picons wait here. */
    private val gates = mutableMapOf<ArtworkId, CompletableDeferred<Unit>>()
    private val loader = ImageLoader.Builder(app).components {
        add(Interceptor { chain ->
            val id = (chain.request.data as AppArtworkSource).id
            assertFalse("the small bitmap is kept out of the memory cache", chain.request.memoryCachePolicy.writeEnabled)
            loads += id
            mostAtOnce.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            try {
                gates[id]?.await()
                delay(20)
                if (id == broken) ErrorResult(null, chain.request, TvheadendArtworkLoadException(ArtworkFailure.TIMEOUT))
                else if (id == missing) ErrorResult(null, chain.request, TvheadendArtworkLoadException(ArtworkFailure.FILE_UNAVAILABLE))
                else if (id == corrupt) ErrorResult(null, chain.request, IllegalStateException("synthetic decode failure"))
                else SuccessResult(picon(id).asImage(), chain.request, DataSource.NETWORK)
            } finally {
                running.decrementAndGet()
            }
        })
    }.build()

    @After fun stop() { scope.coroutineContext[Job]!!.cancel() }

    @Test fun thePassSamplesOnlyMissingPiconsOneAtATimeAndStoresNeutralForAColourlessOne() {
        runBlocking { store.put(key(stored), 0x123456) }
        startPass()
        await { loads.size == 3 && running.get() == 0 && store[key(white)] != null }
        assertEquals("never two at once", 1, mostAtOnce.get())
        assertEquals("the stored picon and the channel without one are skipped", listOf(red, white, broken).ids(), loaded())
        assertNotEquals("a coloured picon gives its colour", NEUTRAL_ACCENT_RGB, store[key(red)])
        assertEquals(0x123456, store[key(stored)])
        assertEquals("a colourless picon stores neutral, so it is not sampled again", NEUTRAL_ACCENT_RGB, store[key(white)])
        assertNull("a failed load is not stored", store[key(broken)])
    }

    @Test fun aFailedLoadIsNotTriedAgainByALaterPassOrACaller() {
        startPass()
        await { loads.size == 4 && running.get() == 0 }
        session.publish(observation(catalog(listOf(red, white, broken, blue))))
        await { store[key(blue)] != null }
        assertEquals("only the new picon is loaded", listOf(red, stored, white, broken, blue).ids(), loaded())
        assertNull(runBlocking { sampleNow(broken).awaitIdling() })
        assertEquals(1, loads.count { it == broken })
        // A clear starts over: the failed picon is tried once more with the rest.
        runBlocking { store.clear() }
        await { store[key(blue)] != null && running.get() == 0 }
        assertEquals("a clear forgets the failure", 2, loads.count { it == broken })
    }

    @Test fun aPiconThereIsNoPictureOfStoresNeutralAndOnlyAPassingFailureIsTriedAgainAfterARestart() {
        session.publish(observation(catalog(listOf(missing, corrupt, broken))))
        startPass()
        await { loads.size == 3 && running.get() == 0 }
        assertEquals("the server does not have it", NEUTRAL_ACCENT_RGB, store[key(missing)])
        assertEquals("it does not decode", NEUTRAL_ACCENT_RGB, store[key(corrupt)])
        assertNull("a timeout passes and is not stored", store[key(broken)])
        // The next process, over the same store.
        val restarted = ChannelAccents(store) { current, id -> key(id).takeIf { session.isCurrent(current) } }
        scope.launch { restarted.fillIn(session.observation, loader, app) }
        await { loads.size == 4 && running.get() == 0 }
        idle(100)
        assertEquals(listOf(missing, corrupt, broken, broken).ids(), loaded())
    }

    @Test fun thePassKeepsRunningThroughGuideAndRecordingChangesAndStartsAgainForAChangedPiconSet() {
        val gate = CompletableDeferred<Unit>().also { gates[red] = it }
        val catalog = catalog(listOf(red, white))
        session.publish(observation(catalog))
        startPass()
        await { loads.size == 1 }
        // Only the guide and the recordings change: the pass, waiting on its first picon, is left alone.
        session.publish(observation(catalog, epg = EpgRepositoryState.Current(EpgSnapshot.create())))
        session.publish(observation(catalog, dvr = DvrRepositoryState.Current(DvrSnapshot.create())))
        idle(100)
        assertEquals("no restart", listOf(red).ids(), loaded())
        // A new set of picons starts it again: the waiting load is cancelled and the picon loaded anew.
        session.publish(observation(catalog(listOf(red, white, blue))))
        await { loads.size == 2 }
        assertEquals(listOf(red, red).ids(), loaded())
        gate.complete(Unit)
        await { store[key(blue)] != null }
        assertEquals(listOf(red, red, white, blue).ids(), loaded())
        assertEquals(1, mostAtOnce.get())
    }

    @Test fun thePassRunsAgainAfterAClearAndStopsWhenTheSessionIsNoLongerCurrent() {
        session.publish(observation(catalog(listOf(red, white))))
        startPass()
        await { store[key(white)] != null }
        runBlocking { store.clear() }
        await { loads.size == 4 && store[key(white)] != null }
        assertEquals(listOf(red, white, red, white).ids(), loaded())
        // Not current: the pass waiting on a picon is cancelled and stores nothing.
        runBlocking { store.clear() }
        val gate = CompletableDeferred<Unit>().also { gates[red] = it }
        await { loads.size == 5 }
        session.publish(SessionObservation.create())
        await { running.get() == 0 }
        gate.complete(Unit)
        idle(100)
        assertEquals(listOf(red, white, red, white, red).ids(), loaded())
        assertNull(store[key(red)])
    }

    @Test fun aResultFromASupersededSessionIsNotWrittenAndTheNextSessionSamplesAgain() {
        val gate = CompletableDeferred<Unit>().also { gates[red] = it }
        val superseded = sampleNow(red)
        await { loads.size == 1 }
        session.replaceGeneration(observation(catalog(listOf(red))))
        gate.complete(Unit)
        assertNull("the superseded session's result is dropped", runBlocking { superseded.awaitIdling() })
        assertNull(store[key(red)])
        gates.clear()
        val colour = runBlocking { sampleNow(red).awaitIdling() }
        assertEquals("it did not count as a failed load", colour, store[key(red)])
        assertTrue(colour != null && colour != NEUTRAL_ACCENT_RGB)
    }

    @Test fun twoCallersForOnePiconSampleItOnceAndAStoredColourNeedsNoLoad() {
        val both = listOf(sampleNow(red), sampleNow(red))
        val colours = runBlocking { both.map { it.awaitIdling() } }
        assertEquals(listOf(red).ids(), loaded())
        assertEquals(colours[0], colours[1])
        assertEquals(colours[0], accents.stored(source(red)))
        assertEquals(colours[0], runBlocking { sampleNow(red).awaitIdling() })
        assertEquals(listOf(red).ids(), loaded())
    }

    private fun List<ArtworkId>.ids() = map { it.value }

    private fun loaded() = synchronized(loads) { loads.map { it.value } }

    private fun startPass() { scope.launch { accents.fillIn(session.observation, loader, app) } }

    private fun sampleNow(id: ArtworkId): Deferred<Int?> = scope.async { accents.sample(loader, app, source(id)) }

    private fun source(id: ArtworkId) = AppArtworkSource(requireNotNull(session.observation.value.currentSession), id)

    /** Coil dispatches on the main looper, which Robolectric runs only when idled. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out; loads: ${loaded()}" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(2)
        }
    }

    private fun idle(millis: Long) {
        val end = System.nanoTime() + millis * 1_000_000
        while (System.nanoTime() < end) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2) }
    }

    private fun <T> Deferred<T>.awaitIdling(): T {
        await { isCompleted }
        return runBlocking { await() }
    }

    private fun picon(id: ArtworkId): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(when (id) {
            white -> android.graphics.Color.WHITE
            blue -> android.graphics.Color.rgb(20, 60, 200)
            else -> android.graphics.Color.rgb(200, 30, 30)
        })
    }

    private companion object {
        // The test's own stand-in for the SDK's opaque key.
        fun key(id: ArtworkId) = "profile-${id.value}"

        fun catalog(picons: List<ArtworkId?>) = ChannelCatalog.create(picons.mapIndexed { index, icon ->
            Channel.create(ChannelId(index + 1L), name = "Channel", number = index + 1L, icon = icon)
        })

        fun observation(
            catalog: ChannelCatalog,
            epg: EpgRepositoryState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvr: DvrRepositoryState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(catalog),
            epgState = epg,
            dvrState = dvr,
        )
    }
}
