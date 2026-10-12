package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.ui.common.formatPlaybackDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackDurationTest {
    @Test fun durationUsesMinutesBelowAnHourAndLocalizedUnits() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun resources(locale: Locale) = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources
        assertEquals("28 min", formatPlaybackDuration(28 * 60, resources(Locale.US)))
        assertEquals("1 h 30 min", formatPlaybackDuration(90 * 60, resources(Locale.US)))
        assertEquals("0 min", formatPlaybackDuration(-1, resources(Locale.US)))
        assertEquals("28 Min.", formatPlaybackDuration(28 * 60, resources(Locale.GERMAN)))
        assertEquals("1 Std. 30 Min.", formatPlaybackDuration(90 * 60, resources(Locale.GERMAN)))
    }
}
