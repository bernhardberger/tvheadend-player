package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RecordingMetadataPolicyTest {
    @Test fun removalUsesStopAndOnlyFutureFiniteDays() {
        fun entry(days: Long?, stop: Instant? = Instant.fromEpochSeconds(100)) =
            DvrEntry.create(DvrEntryId(1), stop = stop, removalDays = days)
        assertEquals(86500L, recordingRemovalTime(entry(1), 100))
        for (days in listOf(null, 0L, 2147483646L, 2147483647L))
            assertNull(recordingRemovalTime(entry(days), 100))
        assertNull(recordingRemovalTime(entry(1), 86500))
        assertNull(recordingRemovalTime(entry(1), 86501))
        assertNull(recordingRemovalTime(entry(1, null), 100))
        assertEquals(100L + 2147483645L * 86400L, recordingRemovalTime(entry(2147483645L), 100))
    }

    @Test fun sizePrefersTotalAndFallsBackToKnownFileSizes() {
        val files = listOf(DvrRecordingFile.create(sizeBytes = 100), DvrRecordingFile.create(sizeBytes = 200),
            DvrRecordingFile.create())
        assertEquals(500L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 500, files = files)))
        assertEquals(300L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = files)))
        assertEquals(0L, recordingSizeBytes(DvrEntry.create(DvrEntryId(1), dataSizeBytes = 0, files = files)))
        assertNull(recordingSizeBytes(DvrEntry.create(DvrEntryId(1), files = listOf(DvrRecordingFile.create()))))
    }

    @Test fun errorsKeepUnknownDistinctFromZero() {
        fun errors(stream: Long?, data: Long?) = recordingErrorCount(
            DvrEntry.create(DvrEntryId(1), streamErrors = stream, dataErrors = data))
        assertNull(errors(null, null))
        assertEquals(0L, errors(0, null))
        assertEquals(3L, errors(1, 2))
        assertEquals(2L, errors(null, 2))
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test fun pictureUsesHighestKnownVideoHeight() {
        fun picture(vararg heights: Int?) = recordingPicture(DvrEntry.create(DvrEntryId(1), files = listOf(
            DvrRecordingFile.create(streams = heights.map { DvrRecordingStream(SubscriptionStreamType.H264, height = it) }))))
        assertNull(picture(null, 0))
        assertEquals("SD 576", picture(576))
        assertEquals("HD 720", picture(720))
        assertEquals("HD 1080", picture(720, 1080))
        assertEquals("UHD 2160", picture(1080, 2160))
    }
}
