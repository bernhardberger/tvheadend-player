package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrFile
import at.bernhardberger.tvhplayer.htsp.DvrState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DvrLibraryPolicyTest {
    @Test
    fun libraryModesKeepArchiveScheduleAndProblemsSeparate() {
        val entries = listOf(
            entry(1, DvrState.COMPLETED, 400),
            entry(2, DvrState.SCHEDULED, 300),
            entry(3, DvrState.RECORDING, 200),
            entry(4, DvrState.FAILED, 100),
            entry(5, DvrState.CANCELLED, 500),
        )

        val library = partitionDvrLibrary(entries)

        assertEquals(listOf(1), library.archive.map { it.id })
        assertEquals(listOf(3, 2), library.schedule.map { it.id })
        assertEquals(listOf(5, 4), library.problems.map { it.id })
    }

    @Test
    fun archiveBuildsNestedFoldersFromRelativeRecordingPaths() {
        val entries = listOf(
            entry(1, DvrState.COMPLETED, 400, "Sport/Formula 1/Race.ts"),
            entry(2, DvrState.COMPLETED, 300, "Sport/Formula 1/Qualifying.ts"),
            entry(3, DvrState.COMPLETED, 200, "Films/Drama.ts"),
        )

        val archive = buildDvrArchive(entries)

        assertEquals(listOf("Sport", "Films"), archive.folders.map { it.name })
        val formulaOne = archive.folders.single { it.name == "Sport" }
            .folders.single { it.name == "Formula 1" }
        assertEquals(listOf(1, 2), formulaOne.recordings.map { it.id })
    }

    @Test
    fun segmentedFilesUseTheirCommonParentFolder() {
        val recording = entry(1, DvrState.COMPLETED, 100).copy(
            files = listOf(
                DvrFile(path = "Series/Show/part-1.ts"),
                DvrFile(path = "Series/Show/part-2.ts"),
            )
        )

        val archive = buildDvrArchive(listOf(recording))

        assertEquals(1, archive.folders.single { it.name == "Series" }
            .folders.single { it.name == "Show" }.recordings.single().id)
    }

    @Test
    fun unsafeOrMissingPathsAreIsolatedUnderOther() {
        val unsafe = entry(1, DvrState.COMPLETED, 100, "../private/recording.ts")
        val missing = entry(2, DvrState.COMPLETED, 200).copy(files = emptyList())

        val archive = buildDvrArchive(listOf(unsafe, missing))

        assertEquals(listOf("Other"), archive.folders.map { it.name })
        assertEquals(listOf(2, 1), archive.folders.single().recordings.map { it.id })
    }

    @Test
    fun scheduleUsesRecordingNowTodayTomorrowAndLaterBuckets() {
        val zone = ZoneId.of("UTC")
        val now = 1_700_000_000L
        val todayStart = java.time.Instant.ofEpochSecond(now).atZone(zone)
            .toLocalDate().atStartOfDay(zone).toEpochSecond()
        val entries = listOf(
            entry(1, DvrState.RECORDING, now - 60),
            entry(2, DvrState.SCHEDULED, todayStart + 60),
            entry(3, DvrState.SCHEDULED, todayStart + 86_400 + 60),
            entry(4, DvrState.SCHEDULED, todayStart + 2 * 86_400 + 60),
        )

        val groups = groupDvrSchedule(entries, now, zone)

        assertEquals(listOf(1), groups.getValue(DvrScheduleBucket.RECORDING_NOW).map { it.id })
        assertEquals(listOf(2), groups.getValue(DvrScheduleBucket.TODAY).map { it.id })
        assertEquals(listOf(3), groups.getValue(DvrScheduleBucket.TOMORROW).map { it.id })
        assertEquals(listOf(4), groups.getValue(DvrScheduleBucket.LATER).map { it.id })
    }

    @Test
    fun channelKeysPageArchiveByVisibleListRowsWithOverlap() {
        assertEquals(4, recordingListPageTargetIndex(30, 0, 5, 1))
        assertEquals(0, recordingListPageTargetIndex(30, 4, 5, -1))
        assertEquals(29, recordingListPageTargetIndex(30, 28, 5, 1))
    }

    @Test
    fun archiveFoldersAndRecordingsAreNewestFirst() {
        val archive = buildDvrArchive(
            listOf(
                entry(1, DvrState.COMPLETED, 100, "Older/show.ts"),
                entry(2, DvrState.COMPLETED, 500, "Newest/show.ts"),
                entry(3, DvrState.COMPLETED, 300, "Middle/show.ts"),
                entry(4, DvrState.COMPLETED, 200, "Newest/older-show.ts"),
            )
        )

        assertEquals(listOf("Newest", "Middle", "Older"), archive.folders.map { it.name })
        assertEquals(listOf(2, 4), archive.folders.first().recordings.map { it.id })
    }

    @Test
    fun folderSummaryAggregatesDescendantSizeRangeAndRecentRecordings() {
        val unwatched = entry(1, DvrState.COMPLETED, 100, "Shows/one.ts").copy(
            files = listOf(DvrFile(path = "Shows/one.ts", size = 1_000)),
        )
        val inProgress = entry(2, DvrState.COMPLETED, 200, "Shows/Series/two.ts").copy(
            files = listOf(DvrFile(path = "Shows/Series/two.ts", size = 2_000)),
            playPosition = 30,
        )
        val watched = entry(3, DvrState.COMPLETED, 300, "Shows/Series/three.ts").copy(
            files = listOf(DvrFile(path = "Shows/Series/three.ts", size = 3_000)),
            playCount = 1,
        )

        val folder = buildDvrArchive(listOf(unwatched, inProgress, watched))
            .folders.single { it.name == "Shows" }
        val summary = summarizeDvrFolder(folder)

        assertEquals(3, summary.recordingCount)
        assertEquals(6_000L, summary.totalSizeBytes)
        assertEquals(100L, summary.oldestStart)
        assertEquals(300L, summary.newestStart)
        assertEquals(listOf(3, 2, 1), summary.recentRecordings.map { it.id })
    }

    @Test
    fun oneUsableConfigIsAutomaticAndSeveralRequireSelection() {
        val first = DvrConfig("one", "Default", enabled = true)
        val disabled = DvrConfig("off", "Disabled", enabled = false)
        assertEquals(
            DvrConfigChoice.Automatic("one"),
            chooseDvrConfig(listOf(first, disabled)),
        )

        val choice = chooseDvrConfig(
            listOf(first, DvrConfig("two", "Films", enabled = true))
        )
        assertTrue(choice is DvrConfigChoice.RequiresSelection)
    }

    @Test
    fun absentConfigMetadataDefersToServerDefault() {
        assertEquals(DvrConfigChoice.Automatic(null), chooseDvrConfig(emptyList()))
    }

    private fun entry(
        id: Int,
        state: DvrState,
        start: Long,
        path: String? = "Archive/recording-$id.ts",
    ) = DvrEntry(
        id = id,
        eventId = null,
        channelId = 1,
        start = start,
        stop = start + 60,
        title = "Recording $id",
        state = state,
        files = path?.let { listOf(DvrFile(path = it)) }.orEmpty(),
    )
}
