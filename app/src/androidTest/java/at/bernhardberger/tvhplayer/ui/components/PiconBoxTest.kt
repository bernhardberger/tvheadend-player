package at.bernhardberger.tvhplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.EventListener
import coil3.ImageLoader
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PiconBoxTest {
    @get:Rule val compose = createComposeRule()

    @Test fun loadingAndFailureKeepTheirTintSizingAndDistinctOpacity() {
        val current = requireNotNull(testSessionObservation().currentSession)
        val started = CompletableDeferred<Unit>()
        val fail = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .components {
                add(Interceptor { chain ->
                    assertSame(current, (chain.request.data as AppArtworkSource).currentSession)
                    started.complete(Unit)
                    fail.await()
                    ErrorResult(null, chain.request, IllegalStateException("Offline test failure"))
                })
            }.build()
        try {
            compose.setContent {
                TVHeadendPlayerTheme {
                    Column {
                        PiconBox(loader, "imagecache/1", bounds("subject"), current,
                            contentScale = ContentScale.Crop)
                        // The former subcomposition propagated its fixed minimum constraints
                        // to the icon. Preserve that full, centred square for load/error states.
                        Box(bounds("reference")) { PiconPlaceholder(Modifier.fillMaxSize()) }
                    }
                }
            }
            compose.waitUntil(5_000) { started.isCompleted }
            val reference = pixels("reference")
            val loading = pixels("subject")
            assertEquals(maxBrightness(reference) * 0.35f, maxBrightness(loading), 0.025f)
            assertScaledPixels(reference, loading, 0.35f)
            fail.complete(Unit)
            compose.waitUntil(5_000) { maxBrightness(pixels("subject")) > maxBrightness(reference) * 0.9f }
            assertScaledPixels(reference, pixels("subject"), 1f)
        } finally {
            fail.complete(Unit)
            loader.shutdown()
        }
    }

    @Test fun retargetingSessionCannotPaintALateResultFromThePreviousSession() {
        val first = requireNotNull(testSessionObservation().currentSession)
        val second = requireNotNull(testSessionObservation().currentSession)
        val current = mutableStateOf<CurrentSessionObservation?>(first)
        val started = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val oldReturned = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val red = bitmap(android.graphics.Color.RED)
        val blue = bitmap(android.graphics.Color.BLUE)
        val loader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .eventListenerFactory { request ->
                if ((request.data as AppArtworkSource).currentSession === first) {
                    object : EventListener() {
                        override fun onCancel(request: ImageRequest) {
                            oldCancelled.complete(Unit)
                        }
                    }
                } else EventListener.NONE
            }
            .components {
                add(Interceptor { chain ->
                    val source = chain.request.data as AppArtworkSource
                    assertEquals("imagecache/1", source.selector)
                    if (source.currentSession === first) {
                        started.complete(Unit)
                        // Exercise a late completion even after Coil cancels the old request.
                        withContext(NonCancellable) { releaseOld.await() }
                        oldReturned.complete(Unit)
                        SuccessResult(red.asImage(), chain.request)
                    } else {
                        assertSame(second, source.currentSession)
                        SuccessResult(blue.asImage(), chain.request)
                    }
                })
            }.build()
        try {
            compose.setContent {
                TVHeadendPlayerTheme {
                    PiconBox(loader, "imagecache/1", bounds("subject"), current.value)
                }
            }
            compose.waitUntil(5_000) { started.isCompleted }
            compose.runOnIdle { current.value = second }
            compose.waitUntil(5_000) { isBlue(pixels("subject")) }
            releaseOld.complete(Unit)
            // Observe Coil's terminal cancellation too, not just the interceptor returning.
            compose.waitUntil(5_000) { oldReturned.isCompleted && oldCancelled.isCompleted }
            compose.waitForIdle()
            val result = pixels("subject")
            assertTrue("Late artwork must not replace the current session's image", isBlue(result))
            // Fit keeps the 2:1 image centred, rather than stretching to the 128:80 box.
            assertEquals(0f, result[result.width / 2, 0].blue, 0.01f)
            compose.runOnIdle { current.value = null }
            val disconnected = pixels("subject")
            assertTrue("Losing current authority must remove the artwork", !isBlue(disconnected))
            assertTrue("The missing-model placeholder must remain visible", maxBrightness(disconnected) > 0.1f)
            val outsidePlaceholder = disconnected[disconnected.width / 8, disconnected.height / 2]
            assertEquals(0f, outsidePlaceholder.red + outsidePlaceholder.green + outsidePlaceholder.blue, 0.01f)
        } finally {
            releaseOld.complete(Unit)
            loader.shutdown()
        }
    }

    private fun bounds(tag: String) = Modifier.size(128.dp, 80.dp).background(Color.Black).testTag(tag)
    private fun pixels(tag: String) = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
    private fun bitmap(color: Int) = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun isBlue(pixels: PixelMap): Boolean {
        val centre = pixels[pixels.width / 2, pixels.height / 2]
        return centre.blue > 0.95f && centre.red < 0.05f
    }
    private fun maxBrightness(pixels: PixelMap): Float {
        var maximum = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            maximum = maxOf(maximum, pixels[x, y].red)
        }
        return maximum
    }
    private fun assertScaledPixels(reference: PixelMap, actual: PixelMap, scale: Float) {
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        var difference = 0f
        for (y in 0 until reference.height) for (x in 0 until reference.width) {
            difference += kotlin.math.abs(reference[x, y].red * scale - actual[x, y].red)
        }
        assertTrue("Placeholder size, placement or tint changed", difference / (reference.width * reference.height) < 0.005f)
    }
}
