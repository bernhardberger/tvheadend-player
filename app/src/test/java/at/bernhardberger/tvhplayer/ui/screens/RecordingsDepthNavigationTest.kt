package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.ArchiveListItem
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingNowLevelId
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelId
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.listItems
import kotlin.time.Instant
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

    @Test fun recordingNowIsFirstOnlyAtRootAndHasIdentitySeparateFromServerFolder() {
        val late = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.RECORDING,
            start = Instant.fromEpochSeconds(200), folder = listOf("Recording now", "Season"))
        val early = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.RECORDING_ERROR,
            start = Instant.fromEpochSeconds(100), folder = listOf("Other"))
        val root = buildDvrArchive(listOf(late, early))
        val meta = root.listItems().first() as ArchiveListItem.RecordingNow
        val serverFolder = root.listItems().filterIsInstance<ArchiveListItem.Folder>().first { it.folder.name == "Recording now" }

        assertNotEquals(serverFolder.key, meta.key)
        assertNotEquals(archiveLevelId(serverFolder.folder.path), RecordingNowLevelId)
        assertEquals(listOf(early, late), meta.entries)
        assertEquals(listOf("recording:2", "recording:1"), archiveLevelItems(root, RecordingNowLevelId)?.map { it.key })
        assertTrue(serverFolder.folder.listItems().none { it is ArchiveListItem.RecordingNow })
        assertTrue(archiveLevelItems(root, archiveLevelId(serverFolder.folder.path))!!.single() is ArchiveListItem.Folder)
        val completed = buildDvrArchive(listOf(entry(3)))
        assertTrue(completed.listItems().none { it is ArchiveListItem.RecordingNow })
        assertNull(archiveLevelItems(completed, RecordingNowLevelId))
    }

    @Test fun disappearingRecordingNowPopsToRootAndSelectsSurvivingRow() {
        val root = buildDvrArchive(listOf(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.COMPLETED,
            folder = listOf("Recording now"))))
        val stack = DepthStack(listOf(
            DepthFrame("archive:", "meta:recording-now", 0),
            DepthFrame(RecordingNowLevelId, "recording:1", 0),
        ), visit = 2)

        val reconciled = reconcileArchiveStack(stack, root)

        assertEquals(listOf("archive:"), reconciled.path)
        assertEquals("folder:Recording now", reconciled.active.focusedItemId)
        assertEquals(3, reconciled.visit)
        assertEquals("folder:Recording now", reconcileArchiveStack(DepthStack(stack.frames.take(1)), root).active.focusedItemId)
        assertNull(reconcileArchiveStack(stack, buildDvrArchive(emptyList())).active.focusedItemId)
    }

    private fun entry(id: Long) = DvrEntry.create(id = DvrEntryId(id), title = "Recording $id")
}
