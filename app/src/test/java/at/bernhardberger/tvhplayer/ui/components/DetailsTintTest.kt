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
import androidx.compose.ui.test.onNodeWithTag
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import at.bernhardberger.tvhplayer.testutil.setContentWithArtworkSampling
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
    private val accents = ChannelAccents(store, EmptyCoroutineContext) { _, id -> "tint-test-${id.value}" }
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

    @Test fun absentArtGlowsWithTheStoredPiconAccent() = absentArtUsesPiconAccent(stored = true)

    @OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
    @Test fun beforeASessionASavedLogosStoredColourShowsInTheFirstFrame() {
        val profile = ServerProfile("first.tvheadend.test")
        val tvheadend = FakeTvheadendSession()
        tvheadend.artwork.scriptCached(profile)
        tvheadend.publish(SessionObservation.create(
            sessionState = SessionState.Synchronizing,
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Test", icon = ChannelIcon.ImageCache(picon))))),
        ), profile)
        runBlocking { store.put(TvheadendTestResultFactory.cachedArtwork(profile, picon).cacheKey, 0x336699) }
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContentWithArtworkSampling {
            CompositionLocalProvider(LocalChannelAccents provides accents) {
                TVHeadendPlayerTheme {
                    ProvideAppArtwork(tvheadend) {
                        val tint = rememberDetailsTint(null, ChannelIcon.ImageCache(picon)).value
                        SideEffect { returned += tint }
                    }
                }
            }
        }
        assertNull(tvheadend.observation.value.currentSession)
        assertEquals(Color(0xFF000000.toInt() or detailsTintRgb(0x336699)), returned.first())
    }

    @Test fun absentArtAndPiconKeepsTheTheme() {
        showPage(hasArt = { false }, hasPicon = false)
        assertPixel(20, 20, 0xFF111416.toInt())
    }

    @Test fun absentArtSamplesThePiconThroughTheExistingAccentStore() = absentArtUsesPiconAccent(stored = false)

    private fun absentArtUsesPiconAccent(stored: Boolean) {
        if (stored) runBlocking { store.put("tint-test-2", 0x336699) }
        else releaseSample.complete(Unit)
        showPage(hasArt = { false })
        assertNotNull(store["tint-test-2"])
        awaitGlow()
        val accent = if (stored) 0x336699 else requireNotNull(store["tint-test-2"])
        assertNear(1919, 0, Color(0xFF000000.toInt() or accent).copy(alpha = .55f)
            .compositeOver(neutral))
        if (!stored) assertEquals(1, samples.get())
    }

    @Test fun aCachedNeutralGlowIsNeitherShownNorReportedWhileTheLogosColourIsSampled() = staleGlowIsHidden(changedInPlace = false)

    @Test fun aLogoChangedInPlaceHidesTheOldGlowWhileItsColourIsSampled() = staleGlowIsHidden(changedInPlace = true)

    private fun staleGlowIsHidden(changedInPlace: Boolean) {
        if (changedInPlace) runBlocking { store.put("tint-test-2", 0x336699) }
        val noLogo = ChannelId(9)
        val sampled = ChannelId(1)
        val replaced = ArtworkId(5)
        val settled = mutableListOf<Pair<ChannelId, ArtworkId?>>()
        var shown by mutableStateOf(noLogo)
        var logo by mutableStateOf(picon)
        compose.setContentWithArtworkSampling {
            CompositionLocalProvider(LocalChannelAccents provides accents, LocalDensity provides Density(2f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    ProvideArtworkSession(session) {
                        key(if (changedInPlace) sampled else shown) {
                            val id = if (changedInPlace) sampled else shown
                            val artwork = if (changedInPlace) logo else picon.takeIf { id == sampled }
                            ChannelLogoGlow(artwork?.let { ChannelIcon.ImageCache(it) }, id, Modifier.fillMaxSize(),
                                onSettled = { settled += id to artwork })
                        }
                    }
                }
            }
        }
        val initial = if (changedInPlace) sampled to picon else noLogo to null
        val next = sampled to if (changedInPlace) replaced else picon
        compose.mainClock.advanceTimeUntil { initial in settled }
        if (changedInPlace) awaitGlow()
        compose.runOnIdle { if (changedInPlace) logo = replaced else shown = sampled }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // Neither a cached neutral glow nor the previous logo's glow belongs to the pending logo.
        assertEquals(listOf(initial), settled)
        assertPixel(1919, 0, neutral.toArgb())
        releaseSample.complete(Unit)
        compose.mainClock.advanceTimeUntil { next in settled }
        if (!changedInPlace) assertEquals(listOf(initial, next), settled)
        val sampledKey = if (changedInPlace) "tint-test-5" else "tint-test-2"
        assertNear(1919, 0, Color(0xFF000000.toInt() or requireNotNull(store[sampledKey])).copy(alpha = .55f)
            .compositeOver(neutral))
    }

    @Test fun failedArtUsesTheSameLogoFallbackAsAbsentArt() {
        fail = true
        releaseSample.complete(Unit)
        runBlocking { store.put("tint-test-2", 0x336699) }
        val hasArt = mutableStateOf(true)
        showPage(hasArt = { hasArt.value })
        compose.onNodeWithTag("details-logo-glow").assertExists()
        compose.onNodeWithTag("details-hero-logo").assertExists()
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        val failedGround = pixel(20, 20)
        compose.runOnIdle { hasArt.value = false }
        compose.onNodeWithTag("details-logo-glow").assertExists()
        compose.onNodeWithTag("details-hero-logo").assertExists()
        compose.onNodeWithTag("details-hero").assertDoesNotExist()
        assertPixel(20, 20, failedGround)
    }

    @Test fun unknownArtStartsNeutralAndCrossfadesToTheSampledTint() {
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContentWithArtworkSampling {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    val tint = rememberDetailsTint(ChannelIcon.ImageCache(art), ChannelIcon.ImageCache(picon)).value
                    SideEffect { returned += tint }
                }
            }
        }
        assertEquals(neutral, returned.first())
        releaseSample.complete(Unit)
        compose.mainClock.advanceTimeUntil { DetailsArtColors.stored(AppArtworkSource(session, art)) != null }
        compose.mainClock.advanceTimeUntil { returned.last() == redTint }
        assertTrue(returned.any { it != neutral && it != redTint })
    }

    @Test fun aSmallVividAreaWinsOverLargerSkinTones() = sampledArtTint(vivid = true)

    @Test fun artWithoutAVividColourStaysNeutral() = sampledArtTint(vivid = false)

    private fun sampledArtTint(vivid: Boolean) {
        artBitmap = {
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (vivid) android.graphics.Color.rgb(0xD9, 0xA0, 0x7A) else android.graphics.Color.GRAY)
                val accent = if (vivid) android.graphics.Color.rgb(0xC8, 0x18, 0x10) else android.graphics.Color.WHITE
                for (x in 0 until 64) for (y in 0 until 16) setPixel(x, y, accent)
            }
        }
        val sampled = requireNotNull(sampleArt(ArtworkId(if (vivid) 3 else 4)))
        if (vivid) assertEquals(0f, rgbToHsv(sampled).first, 15f)
        else assertEquals(0x131313, detailsTintRgb(sampled))
    }

    private fun sampleArt(id: ArtworkId): Int? {
        releaseSample.complete(Unit)
        var sampled: Int? = null
        runTest { sampled = DetailsArtColors.sample(ApplicationProvider.getApplicationContext(), AppArtworkSource(session, id), EmptyCoroutineContext) }
        return sampled
    }

    @Test fun cachedArtAppearsInTheFirstFrameWithoutAnotherSample() {
        releaseSample.complete(Unit)
        assertNotNull(sampleArt(art))
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContentWithArtworkSampling {
            TVHeadendPlayerTheme {
                ProvideArtworkSession(session) {
                    val tint = rememberDetailsTint(ChannelIcon.ImageCache(art), ChannelIcon.ImageCache(picon)).value
                    SideEffect { returned += tint }
                }
            }
        }
        assertEquals(redTint, returned.first())
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(setOf(redTint), returned.toSet())
        assertEquals(1, samples.get())
    }

    private fun showPage(hasArt: () -> Boolean = { true }, hasPicon: Boolean = true, warm: Boolean = false) {
        val channel = Channel.create(ChannelId(1), name = "Test", icon = picon.takeIf { hasPicon }?.let { ChannelIcon.ImageCache(it) })
        compose.setContentWithArtworkSampling {
            val event = EpgEvent.create(EventId(1), channel.id, Instant.fromEpochSeconds(0), Instant.fromEpochSeconds(3600),
                title = "Test programme", image = art.takeIf { hasArt() })
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

    /** The effect clock covers both the sample publication and the glow bake. */
    private fun awaitGlow() = compose.mainClock.advanceTimeUntil { pixel(1919, 0) != neutral.toArgb() }

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
        assertEquals("pixel $x,$y", expected, pixel(x, y))
    }
}
