package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpace
import at.bernhardberger.tvheadend.sdk.core.DvrDiskSpaceState
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
}
