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
            state = DvrEntryState.REMOVED, files = emptyList())))
    }

    @Test fun listedMissingFileMeansRemoved() {
        assertTrue(recordingWasRemoved(DvrEntry.create(DvrEntryId(1),
            state = DvrEntryState.REMOVED, files = listOf(DvrRecordingFile.create()))))
    }

    @Test fun absentMissingFilesAfterServerRestartMeansRemoved() {
        assertTrue(recordingWasRemoved(DvrEntry.create(DvrEntryId(1), state = DvrEntryState.REMOVED)))
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

    @Test fun recordingNowFolderListsRunningRecordingsOnlyAtTheFoldersRootWhileSwitchedOn() {
        val path = listOf("Sport")
        val running = entry(1, DvrEntryState.RECORDING, 100, path)
        val root = buildDvrArchive(listOf(running, entry(2, DvrEntryState.COMPLETED, 50, path)))
        val folders = DvrArchiveView(grouping = DvrArchiveGrouping.FOLDERS)

        assertTrue(DvrArchiveView().recordingNowFolder)
        assertEquals(listOf(running), root.recordingNowEntries(folders))
        assertTrue(root.recordingNowEntries(folders.copy(recordingNowFolder = false)).isEmpty())
        assertTrue(root.folderAt(path)!!.recordingNowEntries(folders).isEmpty())
        DvrArchiveGrouping.entries.filter { it != DvrArchiveGrouping.FOLDERS }.forEach {
            assertTrue("$it", root.recordingNowEntries(folders.copy(grouping = it)).isEmpty())
        }
        assertTrue(buildDvrArchive(listOf(entry(3, DvrEntryState.COMPLETED, 10, path))).recordingNowEntries(folders).isEmpty())
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
        assertTrue(buildDvrArchive(entries, DvrArchiveGrouping.FOLDERS, naming()).folders.all { it.genreCategory == null })
    }

    @Test fun channelsGroupingMakesOneFolderPerChannelNewestFirstNoChannelLast() {
        val oldNews = onChannel(1, 100, "News", folder = listOf("Series"), channelId = 2)
        val newNews = onChannel(2, 300, "News", channelId = 2)
        val film = onChannel(3, 200, "Film", channelId = 9)
        val orphan = onChannel(4, 400, null)
        val root = buildDvrArchive(
            listOf(oldNews, orphan, film, newNews), DvrArchiveGrouping.CHANNELS,
            naming(),
        )

        assertEquals(listOf("News", "Film", "No channel"), root.folders.map { it.name })
        assertEquals(listOf(newNews, oldNews), root.folders.single { it.name == "News" }.recordings)
        assertEquals(listOf(orphan), root.folders.single { it.name == "No channel" }.recordings)
        assertTrue(root.recordings.isEmpty())
        // A channel folder wears its channel's logo; the catch-all keeps the folder icon.
        assertEquals(ChannelId(2), root.folders.single { it.name == "News" }.channelId)
        assertNull(root.folders.single { it.name == "No channel" }.channelId)
        assertTrue(root.folders.all { it.genreCategory == null })
        val duplicate = onChannel(5, 500, "News", channelId = 20)
        val collision = onChannel(6, 600, "No channel", channelId = 21)
        val slash = onChannel(7, 700, "News/Local", channelId = 22)
        val distinct = buildDvrArchive(
            listOf(oldNews, newNews, orphan, duplicate, collision, slash), DvrArchiveGrouping.CHANNELS, naming(),
        )
        assertEquals(4, distinct.folders.size)
        assertEquals(listOf("News/Local", "No channel", "News", "No channel"), distinct.folders.map { it.name })
        assertEquals(listOf(orphan), distinct.folders.last().recordings)
        assertEquals(4, distinct.folders.map { it.path }.distinct().size)
        distinct.folders.forEach { folder ->
            assertEquals(folder, distinct.folderAt(folder.path))
            assertTrue(folder.path.none { '/' in it })
        }
        assertEquals(listOf(duplicate, newNews, oldNews), distinct.folders.single { it.name == "News" }.recordings)
        val sorted = buildDvrArchive(
            listOf(oldNews, orphan, duplicate, collision, slash), DvrArchiveGrouping.CHANNELS, naming(), DvrArchiveSort.NAME,
        )
        assertEquals(listOf(orphan), sorted.folders.last().recordings)
    }

    @Test fun rescannedChannelsMergeByTrimmedDisplayedNameAndUseTheNewestAvailableLogo() {
        val old = onChannel(1, 100, " News ", channelId = 2)
        val rescanned = onChannel(2, 300, "News", channelId = 20)
        val deleted = onChannel(3, 400, "News", channelId = null)
        val root = buildDvrArchive(listOf(old, deleted, rescanned), DvrArchiveGrouping.CHANNELS, naming())

        val folder = root.folders.single { old in it.recordings }
        assertEquals(ChannelId(20), folder.channelId)
        assertEquals(1, root.folders.size)
        assertEquals("News", folder.name)
        assertEquals(listOf(deleted, rescanned, old), folder.recordings)
    }

    @Test fun deletedChannelsKeepTheirNamedFolderWithoutALogoAndNamesMatchExactly() {
        val deleted = onChannel(1, 100, " News ", channelId = null)
        val lowerCase = onChannel(2, 200, "news", channelId = null)
        val root = buildDvrArchive(listOf(deleted, lowerCase), DvrArchiveGrouping.CHANNELS, naming())

        assertEquals(setOf("News", "news"), root.folders.map { it.name }.toSet())
        assertTrue(root.folders.all { it.channelId == null })
        assertEquals(listOf(deleted), root.folders.single { it.name == "News" }.recordings)
    }

    @Test fun onlyNamelessChannelsUseTheCatchAllEvenWhenARealNameMatchesItsLabelOrKey() {
        val blank = onChannel(1, 100, " \t ", channelId = 2)
        val missing = onChannel(2, 200, null, channelId = 3)
        val realLabel = onChannel(3, 300, "No channel", channelId = null)
        val realKey = onChannel(4, 400, "unassigned", channelId = null)
        val root = buildDvrArchive(listOf(blank, missing, realLabel, realKey),
            DvrArchiveGrouping.CHANNELS, naming(), DvrArchiveSort.NAME)

        assertEquals(3, root.folders.size)
        assertEquals(3, root.folders.map { it.path }.distinct().size)
        assertEquals(listOf(missing, blank), root.folders.last().recordings)
        assertNull(root.folders.last().channelId)
        assertEquals(listOf(realLabel), root.folders.first { it.name == "No channel" }.recordings)
        assertEquals(listOf(realKey), root.folders.single { it.name == "unassigned" }.recordings)
    }

    @Test fun genresGroupingMakesOneFolderPerGenreNewestFirstNoGenreLast() {
        val sport = genred(1, 100, "Sport", channelId = 7)
        val news = genred(2, 300, "News", channelId = 8)
        val plain = genred(3, 500, null, channelId = 9)
        val root = buildDvrArchive(listOf(sport, plain, news), DvrArchiveGrouping.GENRES, naming())

        assertEquals(listOf("News", "Sport", "No genre"), root.folders.map { it.name })
        assertEquals(listOf(plain), root.folders.single { it.name == "No genre" }.recordings)
        // Only a channel group wears a channel's logo; a genre group keeps the folder icon.
        assertTrue(root.folders.all { it.channelId == null })
        assertEquals(ProgrammeCategory.SPORT, root.folders.single { it.name == "Sport" }.genreCategory)
        assertEquals(ProgrammeCategory.NEWS, root.folders.single { it.name == "News" }.genreCategory)
        assertNull(root.folders.single { it.name == "No genre" }.genreCategory)
        val sameLabels = buildDvrArchive(listOf(sport, plain, news), DvrArchiveGrouping.GENRES,
            DvrArchiveNaming({ it.channelName }, { "No genre/News" }, "No channel", "No genre/News"))
        assertEquals(root.folders.map { it.path }, sameLabels.folders.map { it.path })
        sameLabels.folders.forEach { assertEquals(it, sameLabels.folderAt(it.path)) }
    }

    @Test fun flatIsTheDefaultAndOneNewestFirstListWithUnknownStartsLast() {
        val old = entry(1, DvrEntryState.COMPLETED, 100, listOf("A"))
        val undated = DvrEntry.create(DvrEntryId(2), state = DvrEntryState.COMPLETED)
        val new = entry(3, DvrEntryState.COMPLETED, 300, null)
        val root = buildDvrArchive(listOf(old, undated, new), DvrArchiveGrouping.FLAT, naming())

        assertEquals(listOf(new, old, undated), root.recordings)
        assertTrue(root.folders.isEmpty())
        assertEquals(DvrArchiveGrouping.FLAT, DvrArchiveView().grouping)
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
            naming(), DvrArchiveSort.NAME,
        )

        assertEquals(listOf("alpha channel", "Zeta", "No channel"), root.folders.map { it.name })
        assertEquals(listOf(alphaNewer, alpha, bravo, untitled), root.folders.single { it.name == "Zeta" }.recordings)

        val descending = buildDvrArchive(
            listOf(bravo, alpha, alphaNewer, untitled, other, orphan), DvrArchiveGrouping.CHANNELS,
            naming(), DvrArchiveSort.NAME_DESCENDING,
        )
        assertEquals(listOf("Zeta", "alpha channel", "No channel"), descending.folders.map { it.name })
        assertEquals(listOf(bravo, alphaNewer, alpha, untitled), descending.folders.single { it.name == "Zeta" }.recordings)
    }

    @Test fun sortByOldestOrdersRecordingsAndGroupsByTheirOldestStartWithUnknownStartLastAtEveryLevel() {
        val a = sized(1, 300, "a", 1, folder = listOf("Series", "Season"))
        val b = sized(2, 100, "b", 1, folder = listOf("Series", "Season"))
        val undated = DvrEntry.create(DvrEntryId(3), state = DvrEntryState.COMPLETED, folder = listOf("Series", "Season"))
        val film = sized(4, 200, "c", 1, folder = listOf("Film"))
        val loose = sized(5, 50, "loose", 1, folder = emptyList())
        val looseNewer = sized(6, 500, "loose newer", 1, folder = emptyList())
        val root = buildDvrArchive(listOf(a, b, undated, film, loose, looseNewer), DvrArchiveGrouping.FOLDERS,
            naming(), DvrArchiveSort.OLDEST)

        assertEquals(listOf("Series", "Film"), root.folders.map { it.name })
        assertEquals(listOf(b, a, undated), root.folderAt(listOf("Series", "Season"))?.recordings)
        assertEquals(listOf(loose, looseNewer), root.recordings)
    }

    @Test fun sortByLargestOrdersBySizeThenNewestWithUnknownSizeLastAtEveryLevel() {
        val small = sized(1, 100, "a", 10, folder = listOf("Series", "Season"))
        val big = sized(2, 200, "b", 90, folder = listOf("Series", "Season"))
        val tie = sized(3, 300, "c", 10, folder = listOf("Series", "Season"))
        val unknown = sized(4, 400, "d", null, folder = listOf("Series", "Season"))
        val huge = sized(5, 50, "e", 500, folder = listOf("Film"))
        val looseSmall = sized(6, 600, "loose small", 1, folder = emptyList())
        val looseBig = sized(7, 500, "loose big", 900, folder = emptyList())
        val root = buildDvrArchive(listOf(small, big, tie, unknown, huge, looseSmall, looseBig), DvrArchiveGrouping.FOLDERS,
            naming(), DvrArchiveSort.LARGEST)

        assertEquals(listOf("Film", "Series"), root.folders.map { it.name })
        assertEquals(listOf(big, tie, small, unknown), root.folderAt(listOf("Series", "Season"))?.recordings)
        assertEquals(listOf(looseBig, looseSmall), root.recordings)

        val smallest = buildDvrArchive(listOf(small, big, tie, unknown, huge, looseSmall, looseBig), DvrArchiveGrouping.FOLDERS,
            naming(), DvrArchiveSort.SMALLEST)
        assertEquals(listOf("Series", "Film"), smallest.folders.map { it.name })
        assertEquals(listOf(tie, small, big, unknown), smallest.folderAt(listOf("Series", "Season"))?.recordings)
        assertEquals(listOf(looseSmall, looseBig), smallest.recordings)
    }

    @Test fun eachShowValueKeepsItsRecordingsDropsTheGroupsItEmptiesAndNeverHidesARunningOne() {
        val seen = onChannel(1, 100, "News").withPlays(1)
        val unseen = onChannel(2, 200, "News")
        val allSeen = onChannel(3, 300, "Film").withPlays(2)
        val started = onChannel(5, 500, "Doc")
        val running = DvrEntry.create(DvrEntryId(4), state = DvrEntryState.RECORDING, start = Instant.fromEpochSeconds(400),
            channelName = "Film", channelId = ChannelId(9), playCount = 1)
        val entries = listOf(seen, unseen, allSeen, started, running)
        val resume = { entry: DvrEntry -> if (entry == started) kotlin.time.Duration.parse("5m") else null }
        val watchState = { entry: DvrEntry -> recordingWatchState(entry, resume(entry), 0L) }
        fun shown(show: DvrArchiveShow) = buildDvrArchive(entries, DvrArchiveGrouping.CHANNELS, naming(), show = show,
            watchState = watchState).folders.associate { it.name to it.recordings }

        assertEquals(mapOf("Doc" to listOf(started), "Film" to listOf(running, allSeen), "News" to listOf(unseen, seen)),
            shown(DvrArchiveShow.ALL))
        // Not played to the end includes the partly watched; only the running recording keeps Film.
        assertEquals(mapOf("Doc" to listOf(started), "Film" to listOf(running), "News" to listOf(unseen)),
            shown(DvrArchiveShow.UNWATCHED))
        assertEquals(mapOf("Doc" to listOf(started), "Film" to listOf(running)), shown(DvrArchiveShow.PARTIALLY_WATCHED))
        assertEquals(mapOf("Film" to listOf(running, allSeen), "News" to listOf(seen)), shown(DvrArchiveShow.WATCHED))

        val news = buildDvrArchive(entries, DvrArchiveGrouping.CHANNELS, naming(), show = DvrArchiveShow.UNWATCHED,
            watchState = watchState).folders.single { it.name == "News" }
        assertEquals(1, summarizeDvrFolder(news).recordingCount)

        val onlyWatched = buildDvrArchive(listOf(seen, allSeen), DvrArchiveGrouping.FOLDERS, naming(),
            show = DvrArchiveShow.UNWATCHED)
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
        channelName = channelName, channelId = channelId, playCount = count)

    /** A recording with a title and, when known, one file of [size] bytes. */
    private fun sized(id: Long, start: Long, title: String?, size: Long?, channel: String? = null, folder: List<String>? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start),
            title = title, channelName = channel, channelId = channel?.let { ChannelId(if (it == "Zeta") 1 else 2) }, folder = folder,
            files = size?.let { listOf(DvrRecordingFile.create(sizeBytes = it)) })

    private fun naming() = DvrArchiveNaming(
        channelName = { it.channelName },
        genreName = { it.title },
        noChannel = "No channel",
        noGenre = "No genre",
    )

    private fun onChannel(id: Long, start: Long, channel: String?, folder: List<String>? = null,
        channelId: Long? = channel?.let { if (it == "News") 2 else 9 }) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start),
            channelName = channel, folder = folder, channelId = channelId?.let(::ChannelId))

    /** The test naming reads the genre from the title. */
    private fun genred(id: Long, start: Long, genre: String?, channelId: Long? = null) =
        DvrEntry.create(DvrEntryId(id), state = DvrEntryState.COMPLETED, start = Instant.fromEpochSeconds(start), title = genre,
            channelId = channelId?.let(::ChannelId), contentGenre = when (genre) {
                "Sport" -> at.bernhardberger.tvheadend.sdk.core.ContentGenre.fromContentType(0x40)
                "News" -> at.bernhardberger.tvheadend.sdk.core.ContentGenre.fromContentType(0x20)
                else -> null
            })

    private fun entry(id: Long, state: DvrEntryState, start: Long, folder: List<String>?) =
        DvrEntry.create(DvrEntryId(id), state = state, start = Instant.fromEpochSeconds(start), folder = folder)
}
