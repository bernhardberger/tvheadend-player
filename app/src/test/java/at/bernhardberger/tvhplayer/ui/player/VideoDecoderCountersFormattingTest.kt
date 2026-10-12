package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppVideoDecoderCounters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoDecoderCountersFormattingTest {
    @Test
    @Config(qualifiers = "en")
    fun englishCountersKeepRenderedDroppedSkippedOrder() {
        assertEquals("Rendered 12345 · Dropped 6 · Skipped 2", formatted())
    }

    @Test
    @Config(qualifiers = "de")
    fun germanCountersAreFullyTranslated() {
        assertEquals("Angezeigt 12345 · Verworfen 6 · Übersprungen 2", formatted())
    }

    private fun formatted(): String = formatVideoDecoderCounters(
        AppVideoDecoderCounters(12345, 6, 2),
        ApplicationProvider.getApplicationContext<Application>().getString(R.string.stats_video_buffers_value),
    )
}
