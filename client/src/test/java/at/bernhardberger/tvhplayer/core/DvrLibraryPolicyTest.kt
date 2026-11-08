package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
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
        val oldNews = onChannel(1, 100, "News", folder = listOf("Series"), channelId = 2)
        val newNews = onChannel(2, 300, "News", channelId = 2)
        val film = onChannel(3, 200, "Film", channelId = 9)
        val orphan = onChannel(4, 400, null, channelId = 5)
        val root = buildDvrArchive(
            listOf(oldNews, orphan, film, newNews), DvrArchiveGrouping.CHANNELS,
            naming(numbers = mapOf("News" to 2, "Film" to 9)),
        )

        assertEquals(listOf("News", "Film", "No channel"), root.folders.map { it.name })
        assertEquals(listOf(newNews, oldNews), root.folderAt(listOf("News"))?.recordings)
        assertEquals(listOf(orphan), root.folderAt(listOf("No channel"))?.recordings)
        assertTrue(root.recordings.isEmpty())
        // A channel folder wears its channel's logo; the catch-all keeps the folder icon.
        assertEquals(ChannelId(2), root.folderAt(listOf("News"))?.channelId)
        assertNull(root.folderAt(listOf("No channel"))?.channelId)
    }

    @Test fun genresGroupingMakesOneFolderPerGenreNewestFirstNoGenreLast() {
        val sport = genred(1, 100, "Sport", channelId = 7)
        val news = genred(2, 300, "News", channelId = 8)
        val plain = genred(3, 500, null, channelId = 9)
        val root = buildDvrArchive(listOf(sport, plain, news), DvrArchiveGrouping.GENRES, naming())

        assertEquals(listOf("News", "Sport", "No genre"), root.folders.map { it.name })
        assertEquals(listOf(plain), root.folderAt(listOf("No genre"))?.recordings)
        // Only a channel group wears a channel's logo; a genre group keeps the folder icon.
        assertTrue(root.folders.all { it.channelId == null })
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

    @Test fun sortByNameOrdersTitlesAndGroupsIgnoringCaseTiesNewestAndTheCatchAllStaysLast() {
        val bravo = sized(1, 100, "bravo", 5, channel = "Zeta")
        val alpha = sized(2, 200, "Alpha", 5, channel = "Zeta")
        val alphaNewer = sized(3, 300, "alpha", 5, channel = "Zeta")
        val untitled = sized(4, 400, null, 5, channel = "Zeta")
        val other = sized(5, 150, "Charlie", 5, channel = "alpha channel")
        val orphan = sized(6, 900, "Aardvark", 5, channel = null)
        val root = buildDvrArchive(
            listOf(bravo, alpha, alphaNewer, untitled, other, orphan), DvrArchiveGrouping.CHANNELS,
            naming(numbers = mapOf("Zeta" to 1)), DvrArchiveSort.NAME,
        )

        assertEquals(listOf("alpha channel", "Zeta", "No channel"), root.folders.map { it.name })
        assertEquals(listOf(alphaNewer, alpha, bravo, untitled), root.folderAt(listOf("Zeta"))?.recordings)
    }

    @Test fun sortByLargestOrdersBySizeThenNewestWithUnknownSizeLastAtEveryLevel() {
        val small = sized(1, 100, "a", 10, folder = listOf("Series", "Season"))
        val big = sized(2, 200, "b", 90, folder = listOf("Series", "Season"))
        val tie = sized(3, 300, "c", 10, folder = listOf("Series", "Season"))
        val unknown = sized(4, 400, "d", null, folder = listOf("Series", "Season"))
        val huge = sized(5, 50, "e", 500, folder = listOf("Film"))
        val root = buildDvrArchive(listOf(small, big, tie, unknown, huge), DvrArchiveGrouping.FOLDERS,
            naming(), DvrArchiveSort.LARGEST)

        assertEquals(listOf("Film", "Series"), root.folders.map { it.name })
        assertEquals(listOf(big, tie, small, unknown), root.folderAt(listOf("Series", "Season"))?.recordings)
    }

    @Test fun sortKeepsFolderRowsAndRecordingRowsAsTwoBlocks() {
        val inFolder = sized(1, 100, "z", 1, folder = listOf("A"))
        val loose = sized(2, 200, "a", 99, folder = emptyList())
        val root = buildDvrArchive(listOf(inFolder, loose), DvrArchiveGrouping.FOLDERS, naming(), DvrArchiveSort.LARGEST)

        assertEquals(listOf("A"), root.folders.map { it.name })
        assertEquals(listOf(loose), root.recordings)
    }

    @Test fun hideWatchedDropsWatchedRecordingsAndTheGroupsTheyEmptyButNeverARunningOne() {
        val seen = onChannel(1, 100, "News").withPlays(1)
        val unseen = onChannel(2, 200, "News")
        val allSeen = onChannel(3, 300, "Film").withPlays(2)
        val running = DvrEntry.create(DvrEntryId(4), state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(400),
            channelName = "Film", playCount = 1)
        val entries = listOf(seen, unseen, allSeen, running)
        val watched = { entry: DvrEntry -> recordingWatchedToEnd(entry, null) }

        val root = buildDvrArchive(entries, DvrArchiveGrouping.CHANNELS, naming(), isWatched = watched)
        assertEquals(listOf("Film", "News"), root.folders.map { it.name })
        assertEquals(listOf(running), root.folderAt(listOf("Film"))?.recordings)
        assertEquals(listOf(unseen), root.folderAt(listOf("News"))?.recordings)
        assertEquals(1, summarizeDvrFolder(checkNotNull(root.folderAt(listOf("News")))).recordingCount)

        val onlyWatched = buildDvrArchive(listOf(seen, allSeen), DvrArchiveGrouping.FOLDERS, naming(), isWatched = watched)
        assertTrue(onlyWatched.folders.isEmpty() && onlyWatched.recordings.isEmpty())
    }

    @Test fun watchedMeansPlayedToTheEndNotPartlyWatchedNorMissed() {
        val played = onChannel(1, 100, "News").withPlays(1)
        assertTrue(recordingWatchedToEnd(played, null))
        assertFalse(recordingWatchedToEnd(played, kotlin.time.Duration.parse("5m")))
        assertFalse(recordingWatchedToEnd(onChannel(2, 100, "News"), null))
        assertFalse(recordingWatchedToEnd(
            DvrEntry.create(DvrEntryId(3), state = DvrEntryState.MISSED, playCount = 1), null))
    }

    private fun DvrEntry.withPlays(count: Long) = DvrEntry.create(id, state = state, start = start,
        channelName = channelName, playCount = count)

    /** A recording with a title and, when known, one file of [size] bytes. */
    private fun sized(id: Long, start: Long, title: String?, size: Long?, channel: String? = null, folder: List<String>? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start),
            title = title, channelName = channel, folder = folder,
            files = size?.let { listOf(DvrRecordingFile.create(sizeBytes = it)) })

    private fun naming(numbers: Map<String, Int> = emptyMap()) = DvrArchiveNaming(
        channelName = { it.channelName },
        channelNumber = { numbers[it.channelName] },
        genreName = { it.title },
        noChannel = "No channel",
        noGenre = "No genre",
    )

    private fun onChannel(id: Long, start: Long, channel: String?, folder: List<String>? = null, channelId: Long? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start),
            channelName = channel, folder = folder, channelId = channelId?.let(::ChannelId))

    /** The test naming reads the genre from the title. */
    private fun genred(id: Long, start: Long, genre: String?, channelId: Long? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start), title = genre,
            channelId = channelId?.let(::ChannelId))

    private fun entry(id: Long, state: DvrEntryState, start: Long, folder: List<String>?) =
        DvrEntry.create(DvrEntryId(id), state = state, start = Instant.fromEpochSeconds(start), folder = folder)
}
