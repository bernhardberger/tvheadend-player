package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardGlow
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.images.DetailsArtColors
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtworkCardGlowTest {
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
    private val accents = ChannelAccents(store) { _, id -> "glow-${id.value}" }
    private val interaction = MutableInteractionSource()
    private val loads = AtomicInteger()
    private val release = CompletableDeferred<Unit>()
    private var fail = false
    private val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext()).components {
        add(Interceptor { chain ->
            loads.incrementAndGet()
            release.await()
            if (fail) ErrorResult(null, chain.request, IllegalStateException("synthetic unavailable art"))
            else SuccessResult(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.RED)
            }.asImage(), chain.request, DataSource.MEMORY)
        })
    }.build()
    private var shownArt by mutableStateOf<ArtworkId?>(null)
    private val returned = mutableListOf<CardGlow>()
    private lateinit var neutral: CardGlow

    @After fun close() { release.complete(Unit) }

    private fun show(picture: ArtworkId? = null, logo: ArtworkId? = picon) {
        shownArt = picture
        compose.setContent {
            CompositionLocalProvider(LocalChannelAccents provides accents) {
                MaterialTheme {
                    ProvideArtworkSession(session) {
                        val fallback = glow(MaterialTheme.colorScheme.onSurface)
                        val result = artworkCardGlow(shownArt, logo, interaction)
                        SideEffect { neutral = fallback; returned += result }
                    }
                }
            }
        }
    }

    private fun focus() {
        runBlocking { interaction.emit(FocusInteraction.Focus()) }
        compose.waitForIdle()
    }

    private fun glow(color: Color) = CardDefaults.glow(focusedGlow = Glow(color.copy(alpha = .45f), 12.dp))

    @Test fun noPictureOrLogoStaysNeutralWithoutDecoding() {
        show(logo = null)
        focus()
        assertEquals(neutral, returned.last())
        assertEquals(0, loads.get())
    }

    @Test fun storedPiconColourIsImmediateAndDoesNotDecode() {
        runBlocking { store.put("glow-2", 0x336699) }
        show()
        focus()
        assertEquals(setOf(glow(Color(0xFF336699))), returned.toSet())
        assertEquals(0, loads.get())
    }

    @Test fun pictureIsSampledOnlyOnFocusAndTakesPrecedenceOverThePicon() {
        runBlocking { store.put("glow-2", 0x336699) }
        release.complete(Unit)
        show(picture = art)
        assertEquals(neutral, returned.last())
        assertEquals(0, loads.get())
        focus()
        compose.waitUntil(10_000) { DetailsArtColors.stored(AppArtworkSource(session, art)) != null }
        val sampled = Color(0xFF000000.toInt() or requireNotNull(DetailsArtColors.stored(AppArtworkSource(session, art))))
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeByFrame(); returned.last() == glow(sampled) }
        assertNotEquals(glow(Color(0xFF336699)), returned.last())
        assertEquals(1, loads.get())
    }

    @Test fun missingPiconColourUsesTheExistingAccentStore() {
        release.complete(Unit)
        show()
        focus()
        compose.waitUntil(10_000) { store["glow-2"] != null }
        val sampled = Color(0xFF000000.toInt() or requireNotNull(store["glow-2"]))
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeByFrame(); returned.last() == glow(sampled) }
        assertEquals(1, loads.get())
    }

    @Test fun failedPictureStaysNeutralRatherThanBorrowingTheLogosColour() {
        fail = true
        release.complete(Unit)
        runBlocking { store.put("glow-2", 0x336699) }
        show(picture = art)
        focus()
        compose.waitUntil(10_000) { loads.get() == 1 }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(setOf(neutral), returned.toSet())
    }

    @Test fun switchingFromLogoToPictureDoesNotFlashThePreviousColour() {
        runBlocking { store.put("glow-2", 0x336699) }
        show()
        focus()
        assertEquals(glow(Color(0xFF336699)), returned.last())
        compose.runOnIdle { shownArt = art }
        compose.waitForIdle()
        assertEquals(neutral, returned.last())
        compose.runOnIdle { shownArt = null }
        release.complete(Unit)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(glow(Color(0xFF336699)), returned.last())
    }
}
