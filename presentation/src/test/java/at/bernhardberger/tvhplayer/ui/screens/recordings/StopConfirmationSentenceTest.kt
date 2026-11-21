package at.bernhardberger.tvhplayer.ui.screens.recordings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import at.bernhardberger.tvhplayer.presentation.R
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class StopConfirmationSentenceTest {
    /** Stop names the programme time it leaves out only while the listed end is ahead. */
    @Test fun stopSentenceNamesTheRemainderOnlyBeforeTheListedEnd() {
        val start = Instant.parse("2026-09-15T20:00:00Z")
        fun running(stop: Instant?, files: Boolean = false) = DvrEntry.create(DvrEntryId(1), title = "Recording",
            state = DvrEntryState.RECORDING, folder = emptyList(), start = start, stop = stop,
            startExtraMinutes = 2.minutes, stopExtraMinutes = 5.minutes,
            files = if (files) listOf(DvrRecordingFile.create(start = start, stop = null, sizeBytes = 1L)) else null)
        val now = start.epochSeconds + 3_600L
        val listed = running(start + 90.minutes)
        // Without file times the recording has run since its padded start.
        assertEquals(StopConfirmationTimes(recordedSec = 3_720L, remainingSec = 30 * 60L), stopConfirmationTimes(listed, now))
        assertEquals(3_600L, stopConfirmationTimes(running(start + 90.minutes, files = true), now).recordedSec)
        // In the post-padding, past the listed end, nothing of the programme is left out.
        assertEquals(null, stopConfirmationTimes(running(start + 58.minutes), now).remainingSec)
        assertEquals(null, stopConfirmationTimes(running(null), now).remainingSec)
        // From one hour the drawn sentence uses hours and minutes; TalkBack hears it spelled out.
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val longer = running(start + 110.minutes, files = true)
        val drawn = stopConfirmationSentence(longer, now + 300L, resources, spoken = false)
        val spoken = stopConfirmationSentence(longer, now + 300L, resources, spoken = true)
        assertTrue(drawn, "1h\u00a05m" in drawn && "45m" in drawn && "hour" !in drawn)
        assertTrue(spoken, "1 hour" in spoken && "45 minutes" in spoken && "1:05" !in spoken)
        val longRemainder = stopConfirmationSentence(StopConfirmationTimes(3_959L, 3_901L), resources, spoken = false)
        assertTrue(longRemainder, "1h\u00a05m" in longRemainder && "1h\u00a06m" in longRemainder)
        val boundary = stopConfirmationSentence(StopConfirmationTimes(119L, 30L), resources, spoken = false)
        assertEquals(resources.getString(R.string.stop_recording_confirm_body, "1m", "1m"), boundary)
        assertTrue(stopConfirmationSentence(StopConfirmationTimes(30L, null), resources, spoken = false).contains("30s"))
    }
}
