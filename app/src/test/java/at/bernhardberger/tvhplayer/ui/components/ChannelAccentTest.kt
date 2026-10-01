package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
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
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelAccentTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val session = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).captureCurrentSession()
    private val store = ChannelAccentStore()
    private val accents = ChannelAccents(store) { _, id -> "profile-${id.value}" }
    private val loads = AtomicInteger()
    private val loader = ImageLoader.Builder(app).components {
        add(Interceptor { chain ->
            loads.incrementAndGet()
            val picon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(200, 30, 30)) }
            SuccessResult(picon.asImage(), chain.request, DataSource.NETWORK)
        })
    }.build()
    private val neutral = Color(0xFF000000.toInt() or NEUTRAL_ACCENT_RGB)

    /** Every colour [rememberChannelAccent] returned, one per composition. */
    private fun show(): List<Color> {
        val returned = mutableListOf<Color>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalChannelAccents provides accents) {
                val colour = rememberChannelAccent(loader, session, ArtworkId(1), ChannelId(1))
                SideEffect { returned += colour }
            }
        }
        return returned
    }

    @Test fun aStoredColourIsReturnedInTheFirstFrameAndNeverAnimatesFromNeutral() {
        runBlocking { store.put("profile-1", 0x336699) }
        val returned = show()
        assertEquals("the first frame", Color(0xFF336699), returned.first())
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("no other colour in any frame", setOf(Color(0xFF336699)), returned.toSet())
        assertEquals("the picon is not loaded", 0, loads.get())
    }

    @Test fun anUnknownColourStartsNeutralAndEndsOnTheSampledColourWhichIsStored() {
        val returned = show()
        assertEquals("the first frame", neutral, returned.first())
        compose.waitUntil(10_000) { store["profile-1"] != null }
        val sampled = Color(0xFF000000.toInt() or store["profile-1"]!!)
        assertNotEquals(neutral, sampled)
        // The sampler resumes off the test's clock; the crossfade then runs on it.
        compose.waitUntil(10_000) { compose.mainClock.advanceTimeByFrame(); returned.last() == sampled }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("it ends on the sampled colour", sampled, returned.last())
        assertEquals("the crossfade passes through colours between the two", true, returned.any { it != neutral && it != sampled })
        assertEquals(1, loads.get())
    }
}
