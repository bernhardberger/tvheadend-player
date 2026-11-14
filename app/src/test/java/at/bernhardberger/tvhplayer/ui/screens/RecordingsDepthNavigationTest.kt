package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.buildDvrArchive
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.reconcileArchiveStack
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelId
import at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelItems
import at.bernhardberger.tvhplayer.ui.screens.recordings.listItems
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RecordingsDepthNavigationTest {
    @Test fun virtualGroupPathsRoundTripWithoutDisplayLabelsBecomingLevels() {
        val entries = listOf("News", " News ", "No channel", "News/Local", null, "News%2FLocal").mapIndexed { index, name ->
            DvrEntry.create(DvrEntryId(index.toLong() + 1), state = DvrEntryState.COMPLETED,
                channelId = name?.let { at.bernhardberger.tvheadend.sdk.core.ChannelId(index.toLong() + 1) }, channelName = name)
        }
        val naming = at.bernhardberger.tvhplayer.core.DvrArchiveNaming(
            { it.channelName }, { null }, "No channel", "No genre",
        )
        val root = buildDvrArchive(entries, at.bernhardberger.tvhplayer.core.DvrArchiveGrouping.CHANNELS, naming)
        assertEquals(entries.size - 1, root.folders.size)
        assertEquals(entries.take(2), root.folders.single { it.name == "News" }.recordings)
        root.folders.forEach { folder ->
            val id = archiveLevelId(folder.path)
            assertEquals(folder.path, at.bernhardberger.tvhplayer.ui.screens.recordings.archiveLevelPath(id))
            assertEquals(folder.recordings.map { "recording:${it.id.value}" }, archiveLevelItems(root, id)?.map { it.key })
        }
    }
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

    @Test fun emptyNonRootLevelReturnsToRoot() {
        val empty = DvrArchiveFolder("Empty", listOf("Empty"), emptyList(), emptyList())
        val root = DvrArchiveFolder("", emptyList(), listOf(empty), emptyList())
        val stack = DepthStack(listOf(DepthFrame("archive:"), DepthFrame("archive:Empty", "recording:1")), visit = 3)

        val result = reconcileArchiveStack(stack, root)

        assertEquals(listOf("archive:"), result.path)
        assertEquals(stack.visit + 1, result.visit)
        assertFalse(result.canPop)
    }

    @Test fun removedRecordingUsesNeighborAtOldIndexAndClampsAtEnd() {
        val root = DvrArchiveFolder("", emptyList(), emptyList(), listOf(entry(1), entry(3)))
        val middle = DepthStack(listOf(DepthFrame("archive:", "recording:2", 1)))
        val last = DepthStack(listOf(DepthFrame("archive:", "recording:4", 3)))

        assertEquals("recording:3", reconcileArchiveStack(middle, root).active.focusedItemId)
        assertEquals("recording:3", reconcileArchiveStack(last, root).active.focusedItemId)
        assertEquals(middle.visit, reconcileArchiveStack(middle, root).visit)
    }

    @Test fun aRestoredFolderTheShowFilterEmptiesReturnsToTheEmptyRoot() {
        val filtered = buildDvrArchive(emptyList())
        val stack = DepthStack(listOf(DepthFrame("archive:", "folder:Series", 0), DepthFrame("archive:Series")))

        assertEquals(listOf("archive:"), reconcileArchiveStack(stack, filtered).path)
        assertTrue(archiveLevelItems(filtered, "archive:")!!.isEmpty())
    }

    @Test fun emptiedNestedLevelsPopRepeatedlyAndSelectTheNearestSurvivingRow() {
        val remaining = DvrEntry.create(DvrEntryId(2), folder = listOf("Series", "Other"))
        val filtered = buildDvrArchive(listOf(remaining))
        val stack = DepthStack(listOf(
            DepthFrame("archive:", "folder:Series"),
            DepthFrame("archive:Series", "folder:Series/Season", 1),
            DepthFrame("archive:Series/Season", "folder:Series/Season/Episode"),
            DepthFrame("archive:Series/Season/Episode", "recording:1"),
        ))
        val result = reconcileArchiveStack(stack, filtered)
        assertEquals(listOf("archive:", "archive:Series"), result.path)
        assertEquals("folder:Series/Other", result.active.focusedItemId)
    }

    private fun entry(id: Long) = DvrEntry.create(id = DvrEntryId(id), title = "Recording $id")
}
