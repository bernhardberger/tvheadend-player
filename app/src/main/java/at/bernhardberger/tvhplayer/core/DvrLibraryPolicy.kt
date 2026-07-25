package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.DvrConfig
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import java.time.Instant
import java.time.ZoneId

enum class DvrLibraryMode {
    ARCHIVE,
    SCHEDULE,
    PROBLEMS,
}

enum class DvrScheduleBucket {
    RECORDING_NOW,
    TODAY,
    TOMORROW,
    LATER,
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
        get() = (recordings.map { it.start } + folders.mapNotNull { it.newestRecordingStart })
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
    val recordings = folder.descendantRecordings().sortedByDescending { it.start }
    return DvrFolderSummary(
        recordingCount = recordings.size,
        totalSizeBytes = recordings.sumOf { entry -> entry.files.sumOf { it.size ?: 0L } },
        oldestStart = recordings.minOfOrNull { it.start },
        newestStart = recordings.maxOfOrNull { it.start },
        recentRecordings = recordings.take(5),
    )
}

private fun DvrArchiveFolder.descendantRecordings(): List<DvrEntry> =
    recordings + folders.flatMap { it.descendantRecordings() }

sealed interface DvrConfigChoice {
    data class Automatic(val configId: String?) : DvrConfigChoice
    data class RequiresSelection(val configs: List<DvrConfig>) : DvrConfigChoice
}

fun chooseDvrConfig(configs: List<DvrConfig>): DvrConfigChoice {
    val usable = configs.filter { it.enabled }
    return when {
        usable.size <= 1 -> DvrConfigChoice.Automatic(usable.singleOrNull()?.id)
        else -> DvrConfigChoice.RequiresSelection(usable)
    }
}

fun partitionDvrLibrary(entries: List<DvrEntry>): DvrLibraryPartition = DvrLibraryPartition(
    archive = entries
        .filter {
            it.state == DvrState.COMPLETED &&
                recordingPlaybackAvailability(it) is RecordingPlaybackAvailability.Ready
        }
        .sortedByDescending { it.start },
    schedule = entries
        .filter { it.state == DvrState.RECORDING || it.state == DvrState.SCHEDULED }
        .sortedWith(compareBy<DvrEntry> { it.state != DvrState.RECORDING }.thenBy { it.start }),
    problems = entries
        .filter { it.state == DvrState.FAILED || it.state == DvrState.CANCELLED }
        .sortedByDescending { it.start },
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
): Map<DvrScheduleBucket, List<DvrEntry>> {
    val today = Instant.ofEpochSecond(nowSec).atZone(zoneId).toLocalDate()
    return DvrScheduleBucket.entries.associateWith { bucket ->
        entries.filter { entry ->
            when (bucket) {
                DvrScheduleBucket.RECORDING_NOW -> entry.state == DvrState.RECORDING
                DvrScheduleBucket.TODAY -> entry.state == DvrState.SCHEDULED &&
                    Instant.ofEpochSecond(entry.start).atZone(zoneId).toLocalDate() <= today
                DvrScheduleBucket.TOMORROW -> entry.state == DvrState.SCHEDULED &&
                    Instant.ofEpochSecond(entry.start).atZone(zoneId).toLocalDate() ==
                    today.plusDays(1)
                DvrScheduleBucket.LATER -> entry.state == DvrState.SCHEDULED &&
                    Instant.ofEpochSecond(entry.start).atZone(zoneId).toLocalDate() >
                    today.plusDays(1)
            }
        }.sortedBy { it.start }
    }
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

private fun recordingFolderPath(entry: DvrEntry): List<String>? {
    val fileParents = entry.files.mapNotNull { file ->
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
            recordings = recordings.sortedByDescending { it.start },
        )
    }
}
