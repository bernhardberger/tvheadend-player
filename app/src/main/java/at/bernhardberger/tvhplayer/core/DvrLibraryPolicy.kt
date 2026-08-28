package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
) {
    val newestRecordingStart: Long?
        get() = (recordings.mapNotNull { it.start?.epochSeconds } +
            folders.mapNotNull { it.newestRecordingStart })
            .maxOrNull()

    fun folderAt(targetPath: List<String>): DvrArchiveFolder? {
        if (targetPath.isEmpty()) return this
        val child = folders.firstOrNull { it.name == targetPath.first() } ?: return null
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

sealed interface DvrConfigChoice {
    data class Automatic(val configId: DvrConfigId?) : DvrConfigChoice
    data class RequiresSelection(val configs: List<DvrConfiguration>) : DvrConfigChoice
}

fun chooseDvrConfig(configs: List<DvrConfiguration>): DvrConfigChoice = when {
        configs.size <= 1 -> DvrConfigChoice.Automatic(configs.singleOrNull()?.id)
        else -> DvrConfigChoice.RequiresSelection(configs)
    }

fun partitionDvrLibrary(entries: List<DvrEntry>): DvrLibraryPartition = DvrLibraryPartition(
    archive = entries
        .filter {
            it.state == DvrEntryState.COMPLETED
        }
        .sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE },
    schedule = entries
        .filter { it.state == DvrEntryState.RECORDING || it.state == DvrEntryState.SCHEDULED }
        .sortedWith(
            compareBy<DvrEntry> { it.state != DvrEntryState.RECORDING }
                .thenBy { it.start?.epochSeconds ?: Long.MAX_VALUE },
        ),
    problems = entries
        .filter {
            it.state in setOf(
                DvrEntryState.MISSED,
                DvrEntryState.INVALID,
                DvrEntryState.RECORDING_ERROR,
                DvrEntryState.COMPLETED_ERROR,
                DvrEntryState.FILE_MISSING,
            )
        }
        .sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE },
)

fun buildDvrArchive(entries: List<DvrEntry>): DvrArchiveFolder {
    val root = MutableArchiveFolder(name = "", path = emptyList())
    entries.forEach { entry ->
        val folderPath = recordingFolderPath(entry) ?: listOf("Other")
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
    val recordingNow = entries.filter { it.state == DvrEntryState.RECORDING }
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
        entries.filter {
            when (bucket) {
                DvrProblemBucket.FAILED -> it.state in setOf(
                    DvrEntryState.RECORDING_ERROR,
                    DvrEntryState.COMPLETED_ERROR,
                    DvrEntryState.FILE_MISSING,
                )
                DvrProblemBucket.CANCELLED -> it.state in setOf(
                    DvrEntryState.MISSED,
                    DvrEntryState.INVALID,
                )
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
    problem: Boolean = false,
): String = buildList {
    if (problem) entry.subscriptionError?.name?.let(::add)
    entry.subtitle
        ?.takeIf { it.isNotBlank() && !it.equals(entry.title.orEmpty(), ignoreCase = true) }
        ?.let(::add)
    if (isEmpty()) {
        entry.summary
            ?.takeIf { it.isNotBlank() && !it.equals(entry.title.orEmpty(), ignoreCase = true) }
            ?.let(::add)
    }
}.joinToString(" • ")

private fun recordingFolderPath(entry: DvrEntry): List<String>? {
    val fileParents = entry.files.orEmpty().mapNotNull { file ->
        val components = safePathComponents(file.path) ?: return null
        components.dropLast(1)
    }
    if (fileParents.isNotEmpty()) {
        return fileParents.reduce(::commonPrefix)
    }
    val entryPath = safePathComponents(entry.path) ?: return null
    return entryPath.dropLast(1)
}

private fun safePathComponents(path: String?): List<String>? {
    if (path.isNullOrBlank()) return null
    val components = path.replace('\\', '/').split('/').filter(String::isNotBlank)
    if (components.isEmpty() || components.any { it == "." || it == ".." }) return null
    return components
}

private fun commonPrefix(left: List<String>, right: List<String>): List<String> =
    left.zip(right).takeWhile { (a, b) -> a == b }.map { it.first }

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
