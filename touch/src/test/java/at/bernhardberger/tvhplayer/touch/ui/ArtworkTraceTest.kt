package at.bernhardberger.tvhplayer.touch.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.images.LogoInkTransformation
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import java.io.IOException
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtworkTraceTest {
    @Test
    fun terminalLinesDescribeTheRequestWithoutItsIdentity() {
        val address = URI("https", null, "192.0.2.1", -1, "/not-for-the-log", null, null)
        val builder = ImageRequest.Builder(ApplicationProvider.getApplicationContext<Context>())
            .data(address.toString())
        val picture = builder.build()
        val logo = picture.newBuilder().transformations(LogoInkTransformation(0, 80, 40)).build()
        val cached = ArtworkRequestTimes(startMs = 900, endMs = 1)
        val decoded = ArtworkRequestTimes(900, fetchStartMs = 2, fetchEndMs = 3, decodeEndMs = 5, endMs = 10)
        val inked = decoded.copy(transformEndMs = 7)
        val success = Result.success(Unit)
        val failure = Result.failure<Unit>(IOException(address.toString()))
        val stages = "fetchStartMs=2 fetchEndMs=3 decodeEndMs=5"
        val notReached = "fetchStartMs=- fetchEndMs=- decodeEndMs=- transformEndMs=-"
        val prefix = "request=17"
        val lines = listOf(
            artworkTraceLine(17, logo, Size(400, 240), DataSource.MEMORY_CACHE, cached, success) to
                "$prefix kind=logo sizePx=400x240 source=memory startMs=900 $notReached successMs=1",
            artworkTraceLine(17, picture, Size(120, 80), DataSource.DISK, decoded, success) to
                "$prefix kind=picture sizePx=120x80 source=disk startMs=900 $stages transformEndMs=- successMs=10",
            artworkTraceLine(17, logo, Size(400, 240), DataSource.NETWORK, inked, success) to
                "$prefix kind=logo sizePx=400x240 source=network startMs=900 $stages transformEndMs=7 successMs=10",
            artworkTraceLine(17, picture, null, null, cached, null) to
                "$prefix kind=picture sizePx=- source=- startMs=900 $notReached cancelledMs=1",
            artworkTraceLine(17, logo, Size(400, 240), DataSource.DISK, decoded, failure) to
                "$prefix kind=logo sizePx=400x240 source=disk startMs=900 $stages transformEndMs=- " +
                "errorMs=10 error=java.io.IOException",
        )
        lines.forEach { (line, expected) ->
            assertFalse(line.contains(address.toString()))
            assertFalse(line.contains(address.host))
            assertEquals(expected, line)
        }
    }
}
