package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowingRecordingDisplayEndTest {
    @Test fun displayAdvancesLocallyAcrossVerifiedUpdatesThenStopsWithoutFreshEvidence() {
        val display = GrowingTimelineDisplayEnd()
        val values = (0L..12L).map { second ->
            display.update(if (second < 5) 60_000 else 65_000, second * 1_000, true)
        }
        assertEquals((60L..70L).map { it * 1_000 } + listOf(70_000L, 70_000L), values)
        assertTrue(values.zipWithNext().all { (a, b) -> b - a in 0..1_000 })
    }

    @Test fun displayedGrowthCannotGrantSeekAccess() {
        val range = recordingSeekbarRange(59_000, 60_000, 65_000)!!
        assertEquals(60_000L, seekbarScrub(range, 1, 100))
        assertEquals(65_000L, range.displayEndMs)
        assertEquals(60_000L, range.endMs)
        assertEquals(59_000f / 65_000, range.displayProgress, 0.0001f)
        val completed = recordingTimelinePresentation(59_000, 60_000, false, 65_000)
            as RecordingTimelinePresentation.Seekable
        assertEquals(60_000L, completed.range.displayEndMs)
    }

    @Test fun completionUnknownDurationAndSourceResetDiscardExtrapolation() {
        val display = GrowingTimelineDisplayEnd()
        display.update(60_000, 0, true)
        assertEquals(65_000L, display.update(60_000, 8_000, true))
        assertEquals(62_000L, display.update(62_000, 9_000, false))
        assertEquals(0L, display.update(0, 10_000, true))
        assertEquals(20_000L, display.update(20_000, 11_000, true))
        assertEquals(10_000L, display.update(10_000, 12_000, true))
    }
}
