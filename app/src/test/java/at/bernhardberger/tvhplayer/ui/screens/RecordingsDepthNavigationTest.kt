package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import org.junit.Assert.*
import org.junit.Test

class RecordingsDepthNavigationTest {
    @Test fun removedDeepBranchPopsToNearestAncestorAndRetainsItsViewport() {
        val parent = DvrArchiveFolder("Series", listOf("Series"), emptyList(), listOf(entry(1), entry(2)))
        val root = DvrArchiveFolder("", emptyList(), listOf(parent), emptyList())
        val saved = DepthFrame("archive:Series", "recording:2", 1, "recording:2", 1, 23)
        val stack = DepthStack(listOf(DepthFrame("archive:"), saved,
            DepthFrame("archive:Series/Removed"), DepthFrame("archive:Series/Removed/Deep")), visit = 8)

        val result = reconcileArchiveStack(stack, root)

        assertEquals(listOf("archive:", "archive:Series"), result.path)
        assertEquals(saved, result.active)
        assertEquals(9, result.visit)
    }

    @Test fun validEmptyFolderIsNotMistakenForMissingPath() {
        val empty = DvrArchiveFolder("Empty", listOf("Empty"), emptyList(), emptyList())
        val root = DvrArchiveFolder("", emptyList(), listOf(empty), emptyList())
        val stack = DepthStack(listOf(DepthFrame("archive:"), DepthFrame("archive:Empty", "recording:1")), visit = 3)

        val result = reconcileArchiveStack(stack, root)

        assertEquals(stack.path, result.path)
        assertEquals(stack.visit, result.visit)
        assertNull(result.active.focusedItemId)
        assertTrue(result.canPop)
    }

    @Test fun removedRecordingUsesNeighborAtOldIndexAndClampsAtEnd() {
        val root = DvrArchiveFolder("", emptyList(), emptyList(), listOf(entry(1), entry(3)))
        val middle = DepthStack(listOf(DepthFrame("archive:", "recording:2", 1)))
        val last = DepthStack(listOf(DepthFrame("archive:", "recording:4", 3)))

        assertEquals("recording:3", reconcileArchiveStack(middle, root).active.focusedItemId)
        assertEquals("recording:3", reconcileArchiveStack(last, root).active.focusedItemId)
        assertEquals(middle.visit, reconcileArchiveStack(middle, root).visit)
    }

    private fun entry(id: Long) = DvrEntry.create(id = DvrEntryId(id), title = "Recording $id")
}
