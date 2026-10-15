package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes
import at.bernhardberger.tvhplayer.ui.common.formatWholeHours
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackDurationTest {
    @Test fun recordingDatesUseLocaleYearAndClockPreference() {
        val zone = java.time.ZoneId.systemDefault()
        fun epoch(year: Int) = java.time.LocalDateTime.of(year, 3, 12, 20, 15).atZone(zone).toEpochSecond()
        val now = epoch(2026)
        for (locale in listOf(Locale.US, Locale.GERMAN)) {
            val same = at.bernhardberger.tvhplayer.ui.common.formatRecordingDate(epoch(2026), true, locale, now)
            val old = at.bernhardberger.tvhplayer.ui.common.formatRecordingDate(epoch(2025), true, locale, now)
            org.junit.Assert.assertFalse(same.contains("2026"))
            org.junit.Assert.assertTrue(old.contains("2025"))
            org.junit.Assert.assertTrue(old, old.contains(if (locale == Locale.US) "Mar" else "März"))
            for (hour24 in listOf(true, false)) {
                val clock = at.bernhardberger.tvhplayer.ui.common.formatClock(java.time.LocalTime.of(20, 15), locale, hour24)
                assertEquals("$old $clock", at.bernhardberger.tvhplayer.ui.common.formatRecordingDate(
                    epoch(2025), false, locale, now, hour24))
            }
            assertEquals(old, at.bernhardberger.tvhplayer.ui.common.formatRecordingSchedule(
                epoch(2025), epoch(2025) + 3600, dateOnly = true, locale = locale, nowSec = now))
        }
    }

    @Test fun wholeHourJumpUsesWideUnitsInConfiguredLocale() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMAN)) {
                Locale.setDefault(if (locale == Locale.US) Locale.GERMAN else Locale.US)
                val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    setLocale(locale)
                }).resources
                assertEquals(if (locale == Locale.US) "1 hour" else "1 Stunde", formatWholeHours(1, resources))
                assertEquals(if (locale == Locale.US) "2 hours" else "2 Stunden", formatWholeHours(2, resources))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun durationUsesOnlyMinutesInConfiguredLocaleIncludingSpokenForm() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun resources(locale: Locale) = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMAN)) {
                // The configured app language must win over the process default.
                Locale.setDefault(if (locale == Locale.US) Locale.GERMAN else Locale.US)
                for (minutes in listOf(45L, 60L, 90L, 135L, 0L, -1L)) {
                    val clamped = minutes.coerceAtLeast(0)
                    assertEquals(if (locale == Locale.US) "${clamped}m" else "$clamped Min.",
                        formatDurationMinutes(minutes, resources(locale)))
                    assertEquals(if (locale == Locale.US) "$clamped minutes" else "$clamped Minuten",
                        formatDurationMinutes(minutes, resources(locale), spoken = true))
                }
                assertEquals(if (locale == Locale.US) "1 minute" else "1 Minute",
                    formatDurationMinutes(1, resources(locale), spoken = true))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
}
