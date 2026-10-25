package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.crossfade
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.floor
import java.util.concurrent.atomic.AtomicInteger

/** A white wordmark on a transparent ground, drawn on the light fill of a focused row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PiconBoxInkTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View

    @Test fun aWhiteLogoOnALightGroundIsDrawnDark() {
        show(ground = LIGHT_FILL)
        compose.waitUntil(5_000) { pixels().dark > 0 }
        val shown = pixels()
        assertTrue("dark strokes expected, got $shown", shown.dark >= 200)
        assertEquals("no white strokes may stay: $shown", 0, shown.white)
    }

    @Test fun withoutAGroundTheLogoKeepsItsOwnPixels() {
        show(ground = null)
        compose.waitUntil(5_000) { pixels().white > 0 }
        val shown = pixels()
        assertTrue("white strokes expected, got $shown", shown.white >= 200)
        assertEquals("nothing may be darkened: $shown", 0, shown.dark)
    }

    @Test fun aThinLineDrawnFarSmallerStaysALineRatherThanDots() {
        show(ground = DARK_FILL, fill = DARK_FILL, width = 72, height = 40, logo = slopedLine())
        compose.waitUntil(5_000) { litColumns().isNotEmpty() }
        val lit = litColumns()
        val gaps = (lit.first()..lit.last()).count { it !in lit }
        assertTrue("the line spans ${lit.size} columns: $lit", lit.size >= 50)
        assertEquals("columns the shrunk line skips, of ${lit.first()}..${lit.last()}", 0, gaps)
    }

    @Test fun reinkingForANewGroundSwapsTheShownLogoWithoutTheLoadingIconBetween() {
        var ground by mutableStateOf(DARK_FILL)
        images.install(cachingLoader(mapOf(ArtworkId(1) to wordmark())))
        val session = readySession()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(LIGHT_FILL).padding(40.dp)) {
                        ProvideArtworkSession(session) {
                            PiconBox(ArtworkId(1), Modifier.size(200.dp, 120.dp).testTag("picon"), ground = ground)
                        }
                    }
                }
            }
        }
        // The first load fades in over the loading icon; then the white logo stands whole.
        compose.waitUntil(5_000) { pixels().white >= WHOLE_LOGO }

        compose.mainClock.autoAdvance = false
        ground = LIGHT_FILL
        val frames = mutableListOf<Shown>()
        val frameColours = mutableListOf<String>()
        val deadline = System.currentTimeMillis() + 5_000
        do {
            compose.mainClock.advanceTimeByFrame()
            frames += pixels()
            frameColours += colours()
            if (frames.last().let { it.dark >= WHOLE_LOGO && it.white == 0 }) break
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
        compose.mainClock.autoAdvance = true

        assertTrue("the logo should end inked dark: ${frames.last()}", frames.last().dark >= WHOLE_LOGO)
        assertTrue("every frame shows one whole logo, never the icon or a blend; most common colours per frame:\n" +
            frameColours.joinToString("\n"), frames.all { it.white >= WHOLE_LOGO || it.dark >= WHOLE_LOGO })
    }

    @Test fun aPrefetchedFocusGroundShowsWithoutAnotherLoad() {
        var focused by mutableStateOf(false)
        val fetches = AtomicInteger()
        val loader = cachingLoader(mapOf(ArtworkId(1) to wordmark())) { fetches.incrementAndGet() }
        images.install(loader)
        val session = readySession()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(LIGHT_FILL).padding(40.dp)) {
                        ProvideArtworkSession(session) {
                            PiconBox(ArtworkId(1), Modifier.size(200.dp, 120.dp).testTag("picon"),
                                ground = if (focused) LIGHT_FILL else DARK_FILL,
                                prefetchGround = if (focused) DARK_FILL else LIGHT_FILL)
                        }
                    }
                }
            }
        }
        // Completion conditions, not latency budgets: both variants must actually be cached.
        compose.waitUntil(5_000) { pixels().white >= WHOLE_LOGO }
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            loader.memoryCache!!.keys.size == 2
        }
        assertEquals(2, fetches.get())
        compose.runOnIdle { focused = true }
        compose.waitUntil(5_000) { pixels().dark >= WHOLE_LOGO && pixels().white == 0 }
        assertEquals("focus must reuse the warmed bitmap", 2, fetches.get())
    }

    /** Loads [images] through Coil's own decode path, so transformations, the memory cache and the fade are real. */
    private fun cachingLoader(images: Map<ArtworkId, Bitmap>, onFetch: () -> Unit = {}): ImageLoader =
        ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>()).components {
            add(Fetcher.Factory<AppArtworkSource> { data, _, _ ->
                Fetcher {
                    onFetch()
                    val image = images[data.id] ?: error("no fixture art for ${data.id}")
                    ImageFetchResult(image.copy(Bitmap.Config.ARGB_8888, false).asImage(), isSampled = false, DataSource.MEMORY)
                }
            }, AppArtworkSource::class)
            // As the SDK's artwork keyer does in the app: without a key nothing is memory-cached.
            add(Keyer<AppArtworkSource> { data, _ -> "fixture-art:${data.id}" }, AppArtworkSource::class)
        }.memoryCache { MemoryCache.Builder().maxSizeBytes(16L * 1024 * 1024).build() }
            .crossfade(PlayerMotion.MediumMs).build()

    @Test fun aPaneLogoOnItsOwnColourIsInkedForThatColour() {
        showPanePicture(ChannelId(1))
        compose.waitUntil(5_000) { pixels("pane-logo").light > 0 }
        val shown = pixels("pane-logo")
        assertTrue("the dim logo should be lightened on its dark colour: $shown", shown.light >= 200)
    }

    @Test fun aPaneLogoWithoutItsColourIsInkedForThePlainGround() {
        showPanePicture(accent = null)
        compose.waitUntil(5_000) { pixels("pane-logo").light > 0 }
        val shown = pixels("pane-logo")
        assertTrue("the dim logo should be lightened: $shown", shown.light >= 200)
    }

    private fun showPanePicture(accent: ChannelId?) {
        val session = readySession()
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(ArtworkId(1) to wordmark(DIM_BLUE))))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(DARK_FILL).padding(40.dp)) {
                        ProvideArtworkSession(session) {
                            ProgrammePanePicture(art = null, logo = ArtworkId(1), accent = accent, logoTag = "pane-logo",
                                modifier = Modifier.size(268.dp, 151.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun readySession() = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()

    private fun show(ground: Color?, fill: Color = LIGHT_FILL, width: Int = 200, height: Int = 120, logo: Bitmap = wordmark()) {
        val session = readySession()
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(ArtworkId(1) to logo)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    Box(Modifier.fillMaxSize().background(fill).padding(40.dp)) {
                        ProvideArtworkSession(session) {
                            PiconBox(ArtworkId(1), Modifier.size(width.dp, height.dp).testTag("picon"), ground = ground)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Three thick white bars, like the letters of a light wordmark. */
    private fun wordmark(colour: Int = 0xFFFFFFFF.toInt()): Bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888).apply {
        for (bar in 0 until 3) for (y in 60 until 180) for (x in 60 + bar * 110 until 120 + bar * 110) setPixel(x, y, colour)
    }

    /** A white line two pixels thick, rising across the picon like ServusTV's swoosh. */
    private fun slopedLine(): Bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888).apply {
        for (x in 20 until 380) {
            val top = 60 + x * 3 / 10
            for (y in top until top + 2) setPixel(x, y, 0xFFFFFFFF.toInt())
        }
    }

    /** The picon's columns where the line shows clearly brighter than the dark fill. */
    private fun litColumns(): List<Int> {
        val bounds = compose.onNodeWithTag("picon").fetchSemanticsNode().boundsInRoot
        val lit = mutableListOf<Int>()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) {
                val brightest = (floor(bounds.top).toInt() until ceil(bounds.bottom).toInt())
                    .maxOf { y -> (bitmap.getPixel(x, y) shr 8) and 255 }
                if (brightest > 40) lit += x
            }
            bitmap.recycle()
        }
        return lit
    }

    /** Near-black and white pixels, the dim blue as drawn, and light ones it would be inked to. */
    /** The picon's four most common colours with their counts, to tell a failed frame's icon from a blend. */
    private fun colours(): String {
        val bounds = compose.onNodeWithTag("picon", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        var out = ""
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val counts = HashMap<Int, Int>()
            for (y in floor(bounds.top).toInt() until ceil(bounds.bottom).toInt())
                for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) counts.merge(bitmap.getPixel(x, y), 1, Int::plus)
            out = counts.entries.sortedByDescending { it.value }.take(4).joinToString { "%06X×%d".format(it.key and 0xFFFFFF, it.value) }
            bitmap.recycle()
        }
        return out
    }

    private data class Shown(val dark: Int, val white: Int, val dimBlue: Int = 0, val light: Int = 0)

    private fun pixels(tag: String = "picon"): Shown {
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        var shown = Shown(0, 0)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var dark = 0
            var white = 0
            var dimBlue = 0
            var light = 0
            for (y in floor(bounds.top).toInt() until ceil(bounds.bottom).toInt()) {
                for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    val channels = listOf(16, 8, 0).map { (pixel shr it) and 255 }
                    if (channels.all { it < 80 }) dark++
                    if (channels.all { it > 245 }) white++
                    val (r, g, b) = channels
                    if (b in 60..100 && r < 30 && g in 10..45) dimBlue++
                    if (b > 200 && r > 100 && g > 120) light++
                }
            }
            bitmap.recycle()
            shown = Shown(dark, white, dimBlue, light)
        }
        return shown
    }

    private companion object {
        val LIGHT_FILL = Color(0xFFE1E2E5)
        val DARK_FILL = Color(0xFF111416)
        /** Well under 1.6:1 on the dark grounds, so it is lightened there. */
        /** The three bars at 200×120 hold 5,400 pixels; edges may blend. */
        const val WHOLE_LOGO = 4_500
        const val DIM_BLUE = 0xFF0A1A50.toInt()
    }
}
