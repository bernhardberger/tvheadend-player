package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpace
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RecordingSpacePolicyTest {
    private val space = DvrDiskSpace(freeBytes = 412_000_000_000L, usedBytes = 1_588_000_000_000L, totalBytes = 2_000_000_000_000L)

    @Test fun currentShowsItsValue() = assertSame(space, DvrDiskSpaceState.Current(space).displayedDiskSpace())
    @Test fun staleShowsItsValue() = assertSame(space, DvrDiskSpaceState.Stale(space).displayedDiskSpace())
    @Test fun synchronizingKeepsItsStaleValue() = assertSame(space, DvrDiskSpaceState.Synchronizing(space).displayedDiskSpace())
    @Test fun synchronizingWithoutValueHides() = assertNull(DvrDiskSpaceState.Synchronizing(null).displayedDiskSpace())
    @Test fun unknownHides() = assertNull(DvrDiskSpaceState.Unknown.displayedDiskSpace())

    @Test fun nonPositiveTotalHidesEveryValueBearingState() {
        for (total in listOf(0L, -1L)) {
            val empty = DvrDiskSpace(freeBytes = 0L, usedBytes = null, totalBytes = total)
            assertNull(DvrDiskSpaceState.Current(empty).displayedDiskSpace())
            assertNull(DvrDiskSpaceState.Stale(empty).displayedDiskSpace())
            assertNull(DvrDiskSpaceState.Synchronizing(empty).displayedDiskSpace())
        }
    }

    private fun levelAt(freeBytes: Long, totalBytes: Long) = DvrDiskSpaceState.Current(
        DvrDiskSpace(freeBytes = freeBytes, usedBytes = totalBytes - freeBytes, totalBytes = totalBytes),
    ).recordingSpaceIndicator()?.level

    @Test fun lowStartsUnderFifteenPercentOrFiveHundredGigabytesWhicheverIsSmaller() {
        // 4 TB: 15% is 600 GB, so the 500 GB ceiling rules; 1 TB: 15% is 150 GB.
        assertEquals(RecordingSpaceLevel.NORMAL, levelAt(500_000_000_000L, 4_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.LOW, levelAt(499_999_999_999L, 4_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.NORMAL, levelAt(150_000_000_000L, 1_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.LOW, levelAt(149_999_999_999L, 1_000_000_000_000L))
    }

    @Test fun criticalStartsUnderFivePercentOrOneHundredGigabytesWhicheverIsSmaller() {
        // 4 TB: 5% is 200 GB, so the 100 GB ceiling rules; 1 TB: 5% is 50 GB.
        assertEquals(RecordingSpaceLevel.LOW, levelAt(100_000_000_000L, 4_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.CRITICAL, levelAt(99_999_999_999L, 4_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.LOW, levelAt(50_000_000_000L, 1_000_000_000_000L))
        assertEquals(RecordingSpaceLevel.CRITICAL, levelAt(49_999_999_999L, 1_000_000_000_000L))
    }

    @Test fun theIndicatorReportsTheUsedShareAndOnlyTrustsACurrentReading() {
        val indicator = DvrDiskSpaceState.Current(space).recordingSpaceIndicator()
        assertEquals(0.794f, indicator!!.usedFraction, 0.001f)
        assertEquals(412_000_000_000L, indicator.freeBytes)
        assertNull(DvrDiskSpaceState.Stale(space).recordingSpaceIndicator())
        assertNull(DvrDiskSpaceState.Synchronizing(space).recordingSpaceIndicator())
        assertNull(DvrDiskSpaceState.Unknown.recordingSpaceIndicator())
        assertNull(DvrDiskSpaceState.Current(space.copy(totalBytes = 0L)).recordingSpaceIndicator())
    }
}
