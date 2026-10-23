package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DvrLibraryPolicyTest {
    @Test fun runningEntriesKeepTheirFullFolderDepthAndExistingSort() {
        val path = listOf("Series", "Season", "Episodes")
        val completed = entry(1, DvrEntryState.COMPLETED, 100, path)
        val running = entry(2, DvrEntryState.RECORDING, 300, path)
        val error = entry(3, DvrEntryState.RECORDING_ERROR, 200, path)
        val other = entry(4, DvrEntryState.RECORDING, 50, null)
        val entries = listOf(completed, running, error, other)
        val partition = partitionDvrLibrary(entries)
        val archive = buildDvrArchive(partition.archive + entries.filter(::recordingIsRunning))

        assertEquals(listOf(running, error, completed), archive.folderAt(path)?.recordings)
        assertEquals(listOf(other), archive.folderAt(listOf("Other"))?.recordings)
        assertEquals(listOf("Series", "Other"), archive.folders.map { it.name })
        assertEquals(listOf(other, error, running), archive.runningRecordings())
        assertEquals(listOf(completed), partition.archive)
    }

    @Test fun finishingRecordingStaysInItsRealFolderAndLeavesRunningList() {
        val path = listOf("Series", "Season")
        val running = buildDvrArchive(listOf(entry(1, DvrEntryState.RECORDING, 100, path)))
        val completed = buildDvrArchive(listOf(entry(1, DvrEntryState.COMPLETED, 100, path)))

        assertEquals(running.folderAt(path)?.recordings?.map { it.id }, completed.folderAt(path)?.recordings?.map { it.id })
        assertEquals(1, running.runningRecordings().size)
        assertTrue(completed.runningRecordings().isEmpty())
    }

    @Test fun runningListIsFlatAscendingWithUnknownStartsLast() {
        val late = entry(1, DvrEntryState.RECORDING, 300, listOf("A", "B"))
        val early = entry(2, DvrEntryState.RECORDING_ERROR, 100, emptyList())
        val unknown = DvrEntry.create(DvrEntryId(3), state = DvrEntryState.RECORDING)
        val completed = entry(4, DvrEntryState.COMPLETED, 50, listOf("C"))

        assertEquals(listOf(early, late, unknown), buildDvrArchive(listOf(late, unknown, completed, early)).runningRecordings())
    }

    private fun entry(id: Long, state: DvrEntryState, start: Long, folder: List<String>?) =
        DvrEntry.create(DvrEntryId(id), state = state, start = Instant.fromEpochSeconds(start), folder = folder)
}
