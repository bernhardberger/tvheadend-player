package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRecordingFile
import java.time.ZoneId
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DvrLibraryPolicyTest {
    @Test fun explicitlyEmptyMissingFilesMeansRemoved() {
        assertTrue(recordingWasRemoved(DvrEntry.create(DvrEntryId(1),
            state = DvrEntryState.FILE_MISSING, files = emptyList())))
    }

    @Test fun listedMissingFileMeansRemoved() {
        assertTrue(recordingWasRemoved(DvrEntry.create(DvrEntryId(1),
            state = DvrEntryState.FILE_MISSING, files = listOf(DvrRecordingFile.create()))))
    }

    @Test fun absentMissingFilesAfterServerRestartMeansRemoved() {
        assertTrue(recordingWasRemoved(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.FILE_MISSING)))
    }

    @Test fun otherStatesWithAnyFilesShapeAreNotRemoved() {
        listOf(DvrEntryState.COMPLETED, DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED,
            DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR).forEach { state ->
            assertFalse(recordingWasRemoved(DvrEntry.create(DvrEntryId(1), state = state, files = emptyList())))
            assertFalse(recordingWasRemoved(DvrEntry.create(DvrEntryId(1), state = state)))
            assertFalse(recordingWasRemoved(DvrEntry.create(DvrEntryId(1), state = state,
                files = listOf(DvrRecordingFile.create()))))
        }
    }

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

    @Test fun foldersGroupingIsTheServerTree() {
        val entries = listOf(entry(1, DvrEntryState.COMPLETED, 100, listOf("Series")), entry(2, DvrEntryState.COMPLETED, 200, null))
        assertEquals(buildDvrArchive(entries), buildDvrArchive(entries, DvrArchiveGrouping.FOLDERS, naming()))
    }

    @Test fun channelsGroupingMakesOneFolderPerChannelInNumberOrderNoChannelLast() {
        val oldNews = onChannel(1, 100, "News", folder = listOf("Series"))
        val newNews = onChannel(2, 300, "News")
        val film = onChannel(3, 200, "Film")
        val orphan = onChannel(4, 400, null)
        val root = buildDvrArchive(
            listOf(oldNews, orphan, film, newNews), DvrArchiveGrouping.CHANNELS,
            naming(numbers = mapOf("News" to 2, "Film" to 9)),
        )

        assertEquals(listOf("News", "Film", "No channel"), root.folders.map { it.name })
        assertEquals(listOf(newNews, oldNews), root.folderAt(listOf("News"))?.recordings)
        assertEquals(listOf(orphan), root.folderAt(listOf("No channel"))?.recordings)
        assertTrue(root.recordings.isEmpty())
    }

    @Test fun genresGroupingMakesOneFolderPerGenreNewestFirstNoGenreLast() {
        val sport = genred(1, 100, "Sport")
        val news = genred(2, 300, "News")
        val plain = genred(3, 500, null)
        val root = buildDvrArchive(listOf(sport, plain, news), DvrArchiveGrouping.GENRES, naming())

        assertEquals(listOf("News", "Sport", "No genre"), root.folders.map { it.name })
        assertEquals(listOf(plain), root.folderAt(listOf("No genre"))?.recordings)
    }

    @Test fun noGroupingIsOneNewestFirstListWithUnknownStartsLast() {
        val old = entry(1, DvrEntryState.COMPLETED, 100, listOf("A"))
        val undated = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED)
        val new = entry(3, DvrEntryState.COMPLETED, 300, null)
        val root = buildDvrArchive(listOf(old, undated, new), DvrArchiveGrouping.NONE, naming())

        assertEquals(listOf(new, old, undated), root.recordings)
        assertTrue(root.folders.isEmpty())
        // The running ones stay in the list, so the root has no Recording now row of its own.
        assertFalse(root.offersRecordingNow)
        assertTrue(buildDvrArchive(listOf(old), DvrArchiveGrouping.CHANNELS, naming()).offersRecordingNow)
    }

    @Test fun dateSectionsOpenOncePerDayThenPerMonthOlder() {
        val zone = ZoneId.of("UTC")
        val now = java.time.Instant.parse("2026-11-08T12:00:00Z")
        var nextId = 0L
        fun at(iso: String) = entry(++nextId, DvrEntryState.COMPLETED,
            java.time.Instant.parse(iso).epochSecond, null)
        val entries = listOf(
            at("2026-11-08T20:00:00Z"), at("2026-11-08T08:00:00Z"), at("2026-11-07T23:00:00Z"),
            at("2026-11-04T10:00:00Z"), at("2026-11-04T09:00:00Z"), at("2026-10-01T10:00:00Z"),
            at("2026-10-20T10:00:00Z"), at("2025-03-02T10:00:00Z"),
        ).sortedByDescending { it.start?.epochSeconds }

        val starts = dvrDateSectionStarts(entries, now, zone)

        assertEquals(
            listOf(
                DvrDateSection.Today, null, DvrDateSection.Yesterday,
                DvrDateSection.Day(java.time.LocalDate.of(2026, 11, 4)), null,
                DvrDateSection.Month(java.time.YearMonth.of(2026, 10)), null,
                DvrDateSection.Month(java.time.YearMonth.of(2025, 3)),
            ),
            starts,
        )
        assertEquals(DvrDateSection.Undated, dvrDateSection(DvrEntry.create(DvrEntryId(9), state = DvrEntryState.COMPLETED), now, zone))
    }

    private fun naming(numbers: Map<String, Int> = emptyMap()) = DvrArchiveNaming(
        channelName = { it.channelName },
        channelNumber = { numbers[it.channelName] },
        genreName = { it.title },
        noChannel = "No channel",
        noGenre = "No genre",
    )

    private fun onChannel(id: Long, start: Long, channel: String?, folder: List<String>? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start),
            channelName = channel, folder = folder)

    /** The test naming reads the genre from the title. */
    private fun genred(id: Long, start: Long, genre: String?) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start), title = genre)

    private fun entry(id: Long, state: DvrEntryState, start: Long, folder: List<String>?) =
        DvrEntry.create(DvrEntryId(id), state = state, start = Instant.fromEpochSeconds(start), folder = folder)
}
