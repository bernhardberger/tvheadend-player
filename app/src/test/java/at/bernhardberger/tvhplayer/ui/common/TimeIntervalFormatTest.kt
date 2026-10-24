package at.bernhardberger.tvhplayer.ui.common

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.recordingMetadataFacts
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingDateTime
import at.bernhardberger.tvhplayer.ui.screens.recordings.recordingTimeRange
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimeIntervalFormatTest {
    private fun epoch(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.systemDefault()).toEpochSecond()

    // Tuesday 15 September 2026; "now" is in the same year.
    private val now = epoch(2026, 10, 1, 12, 0)
    private val sameDay = epoch(2026, 9, 15, 18, 58) to epoch(2026, 9, 15, 21, 5)
    private val acrossNoon = epoch(2026, 9, 15, 11, 30) to epoch(2026, 9, 15, 13, 15)
    private val acrossMidnight = epoch(2026, 9, 15, 23, 30) to epoch(2026, 9, 16, 1, 5)
    private val differentDays = epoch(2026, 9, 15, 18, 58) to epoch(2026, 9, 17, 9, 5)
    private val acrossYears = epoch(2026, 12, 31, 23, 30) to epoch(2027, 1, 1, 0, 30)
    private val lastYear = epoch(2025, 3, 12, 20, 15) to epoch(2025, 3, 12, 21, 45)

    private fun interval(range: Pair<Long, Long>, locale: Locale, uses24Hour: Boolean, withDate: Boolean) =
        formatTimeInterval(range.first, range.second, withDate, locale, now, uses24Hour)

    // ICU separates the endpoints in English with thin spaces around the en dash ( – )
    // and keeps each endpoint's own spaces non-breaking ( ).

    @Test fun `English 24-hour clock range shares nothing and keeps the app's hour cycle`() {
        assertEquals("18:58 – 21:05", interval(sameDay, Locale.US, true, withDate = false))
        assertEquals("11:30 – 13:15", interval(acrossNoon, Locale.US, true, withDate = false))
    }

    @Test fun `English 12-hour clock range names the day period once when shared`() {
        assertEquals("6:58 – 9:05 PM", interval(sameDay, Locale.US, false, withDate = false))
        assertEquals("11:30 AM – 1:15 PM", interval(acrossNoon, Locale.US, false, withDate = false))
    }

    @Test fun `German ranges follow the app's hour cycle, not the locale default`() {
        assertEquals("18:58–21:05 Uhr", interval(sameDay, Locale.GERMAN, true, withDate = false))
        assertEquals("6:58–9:05 PM", interval(sameDay, Locale.GERMAN, false, withDate = false))
        assertEquals("11:30 AM – 1:15 PM", interval(acrossNoon, Locale.GERMAN, false, withDate = false))
    }

    @Test fun `same-day recording shows the date once`() {
        assertEquals("Tue, Sep 15, 18:58 – 21:05", interval(sameDay, Locale.US, true, withDate = true))
        assertEquals("Tue, Sep 15, 6:58 – 9:05 PM", interval(sameDay, Locale.US, false, withDate = true))
        assertEquals("Di., 15. Sept., 18:58–21:05 Uhr", interval(sameDay, Locale.GERMAN, true, withDate = true))
    }

    @Test fun `range across midnight or days names both days`() {
        assertEquals("Tue, Sep 15, 23:30 – Wed, Sep 16, 01:05",
            interval(acrossMidnight, Locale.US, true, withDate = true))
        assertEquals("Tue, Sep 15, 6:58 PM – Thu, Sep 17, 9:05 AM",
            interval(differentDays, Locale.US, false, withDate = true))
        assertEquals("Di., 15. Sept., 23:30 – Mi., 16. Sept., 01:05",
            interval(acrossMidnight, Locale.GERMAN, true, withDate = true))
    }

    @Test fun `a clock-only range across midnight stays a clock range`() {
        assertEquals("23:30 – 01:05", interval(acrossMidnight, Locale.US, true, withDate = false))
        assertEquals("11:30 PM – 1:05 AM", interval(acrossMidnight, Locale.US, false, withDate = false))
        assertEquals("23:30–01:05 Uhr", interval(acrossMidnight, Locale.GERMAN, true, withDate = false))
    }

    @Test fun `the year appears only when an endpoint is outside the current year`() {
        assertEquals("Wed, Mar 12, 2025, 20:15 – 21:45", interval(lastYear, Locale.US, true, withDate = true))
        assertEquals("Mi., 12. März 2025, 20:15–21:45 Uhr", interval(lastYear, Locale.GERMAN, true, withDate = true))
        assertEquals("Thu, Dec 31, 2026, 23:30 – Fri, Jan 1, 2027, 00:30",
            interval(acrossYears, Locale.US, true, withDate = true))
        assertEquals("Do., 31. Dez. 2026, 23:30 – Fr., 1. Jan. 2027, 00:30",
            interval(acrossYears, Locale.GERMAN, true, withDate = true))
    }

    @Test fun `only the separator can break the line`() {
        val dashes = "\u2013\u2014\u2012\u2015~\uff5e-"
        val joined = setOf('\u00a0', '\u202f', '\u2060')
        for (locale in listOf(Locale.US, Locale.GERMAN)) for (uses24Hour in listOf(true, false)) for (withDate in listOf(false, true)) {
            for (range in listOf(sameDay, acrossNoon, acrossMidnight, differentDays, acrossYears, lastYear)) {
                val text = interval(range, locale, uses24Hour, withDate)
                val separators = text.indices.filter { text[it] in dashes }
                assertEquals("$locale $text", 1, separators.size)
                val breakable = text.indices.filter { (text[it].isWhitespace() || Character.isSpaceChar(text[it])) && text[it] !in joined }
                for (i in breakable) {
                    val between = if (i < separators.single()) text.substring(i, separators.single()) else text.substring(separators.single() + 1, i + 1)
                    assertTrue("$locale '$text' breaks at $i outside the separator",
                        between.all { it.isWhitespace() || Character.isSpaceChar(it) })
                }
            }
        }
    }

    @Test fun `a clock range ending in a DST gap's shadow keeps both local times`() {
        val saved = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Berlin"))
        try {
            // 02:30 on 29 March 2026 does not exist in Berlin; the end is 02:30 on the 30th.
            val range = epoch(2026, 3, 29, 23, 30) to epoch(2026, 3, 30, 2, 30)
            assertEquals("23:30\u2009\u2013\u200902:30", interval(range, Locale.US, true, withDate = false))
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }

    @Test fun `a whole-day clock range shows both dates instead of one time`() {
        val range = epoch(2026, 9, 15, 6, 0) to epoch(2026, 9, 16, 6, 0)
        val text = interval(range, Locale.US, true, withDate = false)
        assertEquals("Tue,\u00a0Sep\u00a015,\u00a006:00\u2009\u2013\u2009Wed,\u00a0Sep\u00a016,\u00a006:00", text)
        assertEquals(interval(range, Locale.US, true, withDate = true), text)
    }

    @Test fun `a stop before the start never throws and shows the start`() {
        assertEquals("18:58", formatTimeInterval(sameDay.first, sameDay.first - 60, false, Locale.US, now, true))
    }

    @Test fun `spoken range is built from the instants in each locale`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        fun resources(locale: Locale) = application.createConfigurationContext(
            Configuration(application.resources.configuration).apply { setLocale(locale) }).resources
        assertEquals("18:58 to 21:05",
            formatTimeIntervalSpoken(sameDay.first, sameDay.second, resources(Locale.US), Locale.US, uses24Hour = true))
        assertEquals("18:58 bis 21:05",
            formatTimeIntervalSpoken(sameDay.first, sameDay.second, resources(Locale.GERMAN), Locale.GERMAN, uses24Hour = true))
        fun clock12(sec: Long) = formatClock(java.time.Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()), Locale.US, false)
        assertEquals("${clock12(sameDay.first)} to ${clock12(sameDay.second)}",
            formatTimeIntervalSpoken(sameDay.first, sameDay.second, resources(Locale.US), Locale.US, uses24Hour = false))
    }

    @Test fun `spoken range matches the display when the stop does not follow the start`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val earlier = sameDay.first - 60
        assertEquals(formatTimeInterval(sameDay.first, earlier, false, Locale.US, now, true),
            formatTimeIntervalSpoken(sameDay.first, earlier, application.resources, Locale.US, uses24Hour = true))
        assertEquals("18:58", formatTimeIntervalSpoken(sameDay.first, sameDay.first, application.resources, Locale.US, uses24Hour = true))
    }

    @Test fun `recording time range covers every start and stop combination`() {
        assertEquals(formatTimeInterval(sameDay.first, sameDay.second, true, Locale.US),
            recordingTimeRange(sameDay.first, sameDay.second, Locale.US))
        assertEquals(sameDay.first.recordingDateTime(Locale.US), recordingTimeRange(sameDay.first, null, Locale.US))
        assertEquals(formatClock(sameDay.second), recordingTimeRange(null, sameDay.second, Locale.US))
        assertEquals("", recordingTimeRange(null, null, Locale.US))
    }

    @Test fun `recording details give the recorded span as one interval with the date once`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val recording = DvrEntry.create(DvrEntryId(1),
            start = Instant.fromEpochSeconds(sameDay.first), stop = Instant.fromEpochSeconds(sameDay.second))
        AppClock.format = at.bernhardberger.tvhplayer.settings.ClockFormat.TWENTY_FOUR_HOUR
        try {
            val recorded = recordingMetadataFacts(recording, emptyList(), context, Locale.US, now)
                .single { it.first == context.getString(R.string.details_recorded) }.second
            assertEquals("Tue, Sep 15, 18:58 – 21:05", recorded)
            val stoppedEarly = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED,
                start = Instant.fromEpochSeconds(sameDay.first), stop = Instant.fromEpochSeconds(sameDay.second),
                files = listOf(DvrRecordingFile.create(start = Instant.fromEpochSeconds(sameDay.first),
                    stop = Instant.fromEpochSeconds(sameDay.first + 30 * 60))))
            assertEquals("Tue, Sep 15, 18:58 – 19:28", recordingMetadataFacts(stoppedEarly, emptyList(), context, Locale.US, now)
                .single { it.first == context.getString(R.string.details_recorded) }.second)
            // Nothing was recorded, so the slot it was scheduled for is labelled as such.
            for (state in listOf(DvrEntryState.MISSED, DvrEntryState.INVALID)) {
                val facts = recordingMetadataFacts(DvrEntry.create(DvrEntryId(3), state = state,
                    start = Instant.fromEpochSeconds(sameDay.first), stop = Instant.fromEpochSeconds(sameDay.second)),
                    emptyList(), context, Locale.US, now)
                assertEquals(recorded, facts.single { it.first == "Scheduled" }.second)
                assertTrue(facts.none { it.first == context.getString(R.string.details_recorded) })
            }
        } finally {
            AppClock.format = at.bernhardberger.tvhplayer.settings.ClockFormat.SYSTEM
        }
    }
}
