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

    private fun show(ground: Color?) {
        val session = FakeSessionObservation(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).captureCurrentSession()
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext<Application>(), mapOf(ArtworkId(1) to wordmark())))
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
        compose.waitForIdle()
    }

    /** Three thick white bars, like the letters of a light wordmark. */
    private fun wordmark(): Bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888).apply {
        for (bar in 0 until 3) for (y in 60 until 180) for (x in 60 + bar * 110 until 120 + bar * 110) setPixel(x, y, 0xFFFFFFFF.toInt())
    }

    private data class Shown(val dark: Int, val white: Int)

    private fun pixels(): Shown {
        val bounds = compose.onNodeWithTag("picon").fetchSemanticsNode().boundsInRoot
        var shown = Shown(0, 0)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            var dark = 0
            var white = 0
            for (y in floor(bounds.top).toInt() until ceil(bounds.bottom).toInt()) {
                for (x in floor(bounds.left).toInt() until ceil(bounds.right).toInt()) {
                    val pixel = bitmap.getPixel(x, y)
                    val channels = listOf(16, 8, 0).map { (pixel shr it) and 255 }
                    if (channels.all { it < 80 }) dark++
                    if (channels.all { it > 245 }) white++
                }
            }
            bitmap.recycle()
            shown = Shown(dark, white)
        }
        return shown
    }

    private companion object {
        val LIGHT_FILL = Color(0xFFE1E2E5)
    }
}
