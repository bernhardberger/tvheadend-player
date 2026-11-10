package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration

enum class DvrLibraryMode {
    ARCHIVE,
    SCHEDULE,
    PROBLEMS,
}

enum class DvrScheduleSectionKind {
    RECORDING_NOW,
    TODAY,
    TOMORROW,
    DATE,
}

data class DvrScheduleSection(
    val kind: DvrScheduleSectionKind,
    val date: LocalDate?,
    val entries: List<DvrEntry>,
)

enum class DvrProblemBucket {
    FAILED,
    CANCELLED,
}

data class DvrLibraryPartition(
    val archive: List<DvrEntry>,
    val schedule: List<DvrEntry>,
    val problems: List<DvrEntry>,
)

data class DvrArchiveFolder(
    val name: String,
    val path: List<String>,
    val folders: List<DvrArchiveFolder>,
    val recordings: List<DvrEntry>,
    /** A channel group's channel, for its logo; null for every other folder. */
    val channelId: ChannelId? = null,
    /** Whether a root lists its running recordings in a Recording now row; a flat list keeps them in the list. */
    val offersRecordingNow: Boolean = true,
    /** A named genre group's category; never inferred from its display name. */
    val genreCategory: ProgrammeCategory? = null,
) {
    val newestRecordingStart: Long?
        get() = (recordings.mapNotNull { it.start?.epochSeconds } +
            folders.mapNotNull { it.newestRecordingStart })
            .maxOrNull()

    fun folderAt(targetPath: List<String>): DvrArchiveFolder? {
        if (targetPath.isEmpty()) return this
        val child = folders.firstOrNull { it.path.lastOrNull() == targetPath.first() } ?: return null
        return child.folderAt(targetPath.drop(1))
    }
}

data class DvrFolderSummary(
    val recordingCount: Int,
    val totalSizeBytes: Long,
    val oldestStart: Long?,
    val newestStart: Long?,
    val recentRecordings: List<DvrEntry>,
)

fun summarizeDvrFolder(folder: DvrArchiveFolder): DvrFolderSummary {
    val recordings = folder.descendantRecordings().sortedByDescending {
        it.start?.epochSeconds ?: Long.MIN_VALUE
    }
    return DvrFolderSummary(
        recordingCount = recordings.size,
        totalSizeBytes = recordings.sumOf { entry ->
            entry.files.orEmpty().sumOf { it.sizeBytes ?: 0L }
        },
        oldestStart = recordings.mapNotNull { it.start?.epochSeconds }.minOrNull(),
        newestStart = recordings.mapNotNull { it.start?.epochSeconds }.maxOrNull(),
        recentRecordings = recordings.take(5),
    )
}

private fun DvrArchiveFolder.descendantRecordings(): List<DvrEntry> =
    recordings + folders.flatMap { it.descendantRecordings() }

/** Every recording under the folder, at any depth. */
fun DvrArchiveFolder.allRecordings(): List<DvrEntry> = descendantRecordings()

/**
 * How many recordings under the folder are watched to the end; partly watched ones don't count,
 * since there is still something to see.
 */
fun DvrArchiveFolder.watchedCount(resumePosition: (DvrEntry) -> Duration?): Int =
    descendantRecordings().count { recordingWatchedToEnd(it, resumePosition(it)) }

/** Watched to the end by the rule behind the check mark; a saved position means partly watched. */
fun recordingWatchedToEnd(entry: DvrEntry, resumePosition: Duration?): Boolean =
    recordingWatchState(entry, resumePosition, nowSec = 0L) == RecordingWatchState.Watched

fun DvrArchiveFolder.runningRecordings(): List<DvrEntry> =
    descendantRecordings().filter(::recordingIsRunning)
        .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }

fun chooseDvrConfig(configs: List<DvrConfiguration>, preferredId: DvrConfigId? = null): DvrConfigId? =
    (configs.firstOrNull { it.id == preferredId } ?: configs.firstOrNull { it.name.isBlank() } ?: configs.firstOrNull())?.id

/**
 * Matches Tvheadend's web UI Removed classification for completed entries in
 * dvr_entry_is_finished (src/dvr/dvr_db.c), reported as "File missing" by
 * src/htsp_server.c. This covers deletions before and after a server restart,
 * files removed outside Tvheadend, and recordings that never wrote a file once
 * Tvheadend has reloaded them. HTSP exposes nothing finer (fileremoved is not exposed).
 * Interim policy until the SDK exposes a dedicated removed state.
 */
fun recordingWasRemoved(entry: DvrEntry): Boolean = entry.state == DvrEntryState.FILE_MISSING

fun partitionDvrLibrary(entries: List<DvrEntry>): DvrLibraryPartition = DvrLibraryPartition(
    archive = entries
        .filter {
            it.state == DvrEntryState.COMPLETED
        }
        .sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE },
    schedule = entries
        .filter { recordingIsRunning(it) || it.state == DvrEntryState.SCHEDULED }
        .sortedWith(
            compareBy<DvrEntry> { !recordingIsRunning(it) }
                .thenBy { it.start?.epochSeconds ?: Long.MAX_VALUE },
        ),
    problems = entries
        .filter {
            !recordingWasRemoved(it) && it.state in setOf(
                DvrEntryState.MISSED,
                DvrEntryState.INVALID,
                DvrEntryState.COMPLETED_ERROR,
                DvrEntryState.FILE_MISSING,
            )
        }
        .sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE },
)

fun buildDvrArchive(entries: List<DvrEntry>): DvrArchiveFolder {
    val root = MutableArchiveFolder(name = "", path = emptyList())
    entries.forEach { entry ->
        val folderPath = entry.folder ?: listOf("Other")
        var folder = root
        folderPath.forEach { component ->
            folder = folder.children.getOrPut(component) {
                MutableArchiveFolder(component, folder.path + component)
            }
        }
        folder.recordings += entry
    }
    return root.freeze()
}

fun groupDvrSchedule(
    entries: List<DvrEntry>,
    nowSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<DvrScheduleSection> {
    val today = Instant.ofEpochSecond(nowSec).atZone(zoneId).toLocalDate()
    val recordingNow = entries.filter(::recordingIsRunning)
        .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }
    val scheduledByDate = entries.filter {
        it.state == DvrEntryState.SCHEDULED && it.start != null
    }
        .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }
        .groupBy { Instant.ofEpochSecond(checkNotNull(it.start).epochSeconds).atZone(zoneId).toLocalDate() }
    return buildList {
        if (recordingNow.isNotEmpty()) {
            add(DvrScheduleSection(DvrScheduleSectionKind.RECORDING_NOW, null, recordingNow))
        }
        scheduledByDate.filterKeys { it <= today }.values.flatten()
            .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }
            .takeIf { it.isNotEmpty() }?.let {
                add(DvrScheduleSection(DvrScheduleSectionKind.TODAY, null, it))
            }
        scheduledByDate[today.plusDays(1)]?.let {
            add(DvrScheduleSection(DvrScheduleSectionKind.TOMORROW, null, it))
        }
        scheduledByDate.entries
            .filter { (date) -> date > today.plusDays(1) }
            .sortedBy { (date) -> date }
            .forEach { (date, datedEntries) ->
                add(DvrScheduleSection(DvrScheduleSectionKind.DATE, date, datedEntries))
            }
    }
}

fun groupDvrProblems(entries: List<DvrEntry>): Map<DvrProblemBucket, List<DvrEntry>> =
    DvrProblemBucket.entries.associateWith { bucket ->
        entries.filterNot(::recordingWasRemoved).filter {
            when (bucket) {
                // A missed recording failed to happen; only one never meant to happen is canceled.
                DvrProblemBucket.FAILED -> it.state in setOf(
                    DvrEntryState.COMPLETED_ERROR,
                    DvrEntryState.FILE_MISSING,
                    DvrEntryState.MISSED,
                )
                DvrProblemBucket.CANCELLED -> it.state == DvrEntryState.INVALID
            }
        }.sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE }
    }

fun recordingListPageTargetIndex(
    itemCount: Int,
    currentIndex: Int,
    visibleItemCount: Int,
    direction: Int,
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount) return null
    val pageSize = (visibleItemCount - 1).coerceAtLeast(1)
    return (currentIndex + direction * pageSize).coerceIn(0, itemCount - 1)
}

fun recordingFocusTargetKey(
    orderedKeys: List<String>,
    selectedKey: String?,
): String? = selectedKey?.takeIf(orderedKeys::contains) ?: orderedKeys.firstOrNull()

fun recordingListMetadata(
    entry: DvrEntry,
    problemLabel: String? = null,
): String = buildList {
    problemLabel?.let(::add)
    entry.subtitle
        ?.takeIf { it.isNotBlank() && !it.equals(entry.title.orEmpty(), ignoreCase = true) }
        ?.let(::add)
    if (isEmpty()) {
        entry.summary
            ?.takeIf { it.isNotBlank() && !it.equals(entry.title.orEmpty(), ignoreCase = true) }
            ?.let(::add)
    }
}.joinToString(" • ")

private class MutableArchiveFolder(
    val name: String,
    val path: List<String>,
) {
    val children = linkedMapOf<String, MutableArchiveFolder>()
    val recordings = mutableListOf<DvrEntry>()

    fun freeze(): DvrArchiveFolder {
        val frozenChildren = children.values.map(MutableArchiveFolder::freeze)
        return DvrArchiveFolder(
            name = name,
            path = path,
            folders = frozenChildren.sortedWith(
                compareByDescending<DvrArchiveFolder> {
                    it.newestRecordingStart ?: Long.MIN_VALUE
                }.thenBy { it.name.lowercase() }
            ),
            recordings = recordings.sortedByDescending {
                it.start?.epochSeconds ?: Long.MIN_VALUE
            },
        )
    }
}
