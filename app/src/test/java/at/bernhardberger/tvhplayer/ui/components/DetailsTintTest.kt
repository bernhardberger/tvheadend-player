package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.images.DetailsArtColors
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailsTintTest {
    @get:Rule val images = ImageLoaderRule { loader }
    @get:Rule val compose = createComposeRule()
    private val session = requireNotNull(FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession())
    private val art = ArtworkId(1)
    private val picon = ArtworkId(2)
    private val store = ChannelAccentStore()
    private val accents = ChannelAccents(store) { _, id -> "tint-test-${id.value}" }
    private val releaseSample = CompletableDeferred<Unit>()
    private var fail = false
    private var artBitmap = {
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
    }
    private val samples = AtomicInteger()
    private val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext()).components {
        add(Interceptor { chain ->
            if (!chain.request.memoryCachePolicy.writeEnabled) {
                releaseSample.await()
                samples.incrementAndGet()
            }
            if (fail) ErrorResult(null, chain.request, IllegalStateException("synthetic missing image"))
            else SuccessResult(artBitmap().asImage(), chain.request, DataSource.MEMORY)
        })
    }.build()
    private lateinit var view: View
    private val neutral = Color(0xFF111416)
    private val redTint = Color(0xFF000000.toInt() or detailsTintRgb(0xFF0000))

    @After fun close() { releaseSample.complete(Unit) }

    @Test fun theArtsColourScrimsTheFullScreenArt() {
        releaseSample.complete(Unit)
        showPage()
        compose.waitUntil(10_000) { DetailsArtColors.stored(AppArtworkSource(session, art)) != null }
        compose.mainClock.advanceTimeBy(1000)
        // The bottom-left corner, beyond the scrim's reach from the top right, away from the text.
        assertNear(20, 1060, Color(0xFF190C0C).copy(alpha = .92f).compositeOver(Color.Red))
    }

    @Test fun warmPlaybackKeepsTheThemeBehindTheTransparentPage() {
        showPage(warm = true)
        assertPixel(20, 20, 0xFF111416.toInt())
    }

    @Test fun absentArtGlowsWithTheStoredPiconAccent() {
        runBlocking { store.put("tint-test-2", 0x336699) }
        showPage(hasArt = false)
        // Without art the logo's colour glows from the top-right corner, once baked.
        awaitGlow()
        assertNear(1919, 0, Color(0xFF336699).copy(alpha = .55f).compositeOver(neutral))
    }

    @OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
    @Test fun beforeASessionASavedLogosStoredColourShowsInTheFirstFrame() {
        val profile = ServerProfile("first.tvheadend.test")
        val tvheadend = FakeTvheadendSession()
        tvheadend.artwork.scriptCached(profile)
        tvheadend.publish(SessionObservation.create(
            sessionState = SessionState.Synchronizing,
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Test", icon = picon)))),
        ), profile)
        runBlocking { store.put(TvheadendTestResultFactory.cachedArtwork(profile, picon).cacheKey, 0x336699) }
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalChannelAccents provides accents) {
                TVHeadendPlayerTheme {
                    ProvideAppArtwork(tvheadend) {
                        val tint = rememberDetailsTint(null, picon).value
                        SideEffect { returned += tint }
                    }
                }
            }
        }
        assertNull(tvheadend.observation.value.currentSession)
        assertEquals(Color(0xFF000000.toInt() or detailsTintRgb(0x336699)), returned.first())
    }

    @Test fun absentArtAndPiconKeepsTheTheme() {
        showPage(hasArt = false, hasPicon = false)
        assertPixel(20, 20, 0xFF111416.toInt())
    }

    @Test fun absentArtSamplesThePiconThroughTheExistingAccentStore() {
        releaseSample.complete(Unit)
        showPage(hasArt = false)
        compose.waitUntil(10_000) { store["tint-test-2"] != null }
        awaitGlow()
        assertNear(1919, 0, Color(0xFF000000.toInt() or requireNotNull(store["tint-test-2"])).copy(alpha = .55f)
            .compositeOver(neutral))
        assertEquals(1, samples.get())
    }

    @Test fun failedArtFallsBackToTheStoredPiconAccent() {
        fail = true
        releaseSample.complete(Unit)
        runBlocking { store.put("tint-test-2", 0x336699) }
        showPage()
        compose.waitUntil(10_000) { samples.get() > 0 }
        compose.mainClock.advanceTimeBy(1000)
        // The scrim over the missing art is the page's own colour.
        assertNear(20, 20, Color(0xFF000000.toInt() or detailsTintRgb(0x336699)), tolerance = 1)
    }

    @Test fun unknownArtStartsNeutralAndCrossfadesToTheSampledTint() {
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    val tint = rememberDetailsTint(art, picon).value
                    SideEffect { returned += tint }
                }
            }
        }
        assertEquals(neutral, returned.first())
        releaseSample.complete(Unit)
        compose.waitUntil(10_000) { DetailsArtColors.stored(AppArtworkSource(session, art)) != null }
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeByFrame(); returned.last() == redTint }
        assertTrue(returned.any { it != neutral && it != redTint })
    }

    @Test fun aSmallVividAreaWinsOverLargerSkinTones() {
        artBitmap = {
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(0xD9, 0xA0, 0x7A))
                for (x in 0 until 64) for (y in 0 until 16) setPixel(x, y, android.graphics.Color.rgb(0xC8, 0x18, 0x10))
            }
        }
        assertEquals(0f, rgbToHsv(requireNotNull(sampleArt(ArtworkId(3)))).first, 15f)
    }

    @Test fun artWithoutAVividColourStaysNeutral() {
        artBitmap = {
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.GRAY)
                for (x in 0 until 64) for (y in 0 until 16) setPixel(x, y, android.graphics.Color.WHITE)
            }
        }
        assertEquals(0x131313, detailsTintRgb(requireNotNull(sampleArt(ArtworkId(4)))))
    }

    private fun sampleArt(id: ArtworkId): Int? {
        releaseSample.complete(Unit)
        return runBlocking { DetailsArtColors.sample(ApplicationProvider.getApplicationContext(), AppArtworkSource(session, id)) }
    }

    @Test fun cachedArtAppearsInTheFirstFrameWithoutAnotherSample() {
        releaseSample.complete(Unit)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val sampled = scope.async { DetailsArtColors.sample(ApplicationProvider.getApplicationContext(), AppArtworkSource(session, art)) }
            compose.waitUntil(10_000) { sampled.isCompleted }
            assertNotNull(runBlocking { sampled.await() })
        } finally { scope.cancel() }
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    val tint = rememberDetailsTint(art, picon).value
                    SideEffect { returned += tint }
                }
            }
        }
        assertEquals(redTint, returned.first())
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(setOf(redTint), returned.toSet())
        assertEquals(1, samples.get())
    }

    private fun showPage(hasArt: Boolean = true, hasPicon: Boolean = true, warm: Boolean = false) {
        val channel = Channel.create(ChannelId(1), name = "Test", icon = picon.takeIf { hasPicon })
        val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3600),
            title = "Test programme", image = art.takeIf { hasArt })
        compose.setContent {
            CompositionLocalProvider(LocalChannelAccents provides accents, LocalWarmPlayback provides warm,
                LocalDensity provides Density(2f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ProvideArtworkSession(session) { ProgrammeDetailsPage(event, null, channel, 100, emptyList(), {}, {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The glow is baked off the main thread; until then the page shows its plain background. */
    private fun awaitGlow() = compose.waitUntil(10_000) { pixel(1919, 0) != neutral.toArgb() }

    private fun pixel(x: Int, y: Int): Int {
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        return bitmap.getPixel(x, y).also { bitmap.recycle() }
    }

    private fun assertNear(x: Int, y: Int, expected: Color, tolerance: Int = 3) {
        val pixel = pixel(x, y)
        val argb = expected.toArgb()
        for (shift in listOf(16, 8, 0)) {
            assertEquals("pixel $x,$y ${Integer.toHexString(pixel)}", (argb shr shift and 0xff).toFloat(),
                (pixel shr shift and 0xff).toFloat(), tolerance.toFloat())
        }
    }

    private fun assertPixel(x: Int, y: Int, expected: Int) {
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertEquals("pixel $x,$y", expected, bitmap.getPixel(x, y))
        bitmap.recycle()
    }
}
