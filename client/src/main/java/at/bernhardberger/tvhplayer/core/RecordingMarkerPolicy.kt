package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrCutpoint
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction

enum class RecordingMarkKind { AD_BREAK, CUT, CHAPTER }

/** Chapters are points (end == start); interval ends are exclusive. Names are file text only. */
data class RecordingMark(
    val kind: RecordingMarkKind,
    val startMs: Long,
    val endMs: Long = startMs,
    val name: String? = null,
) {
    fun contains(positionMs: Long): Boolean = positionMs >= startMs && positionMs < endMs
}

data class RecordingChapter(val startMs: Long, val name: String? = null)

/** Does not clip interval identity to a growing recording's changing duration. */
fun recordingMarks(cutpoints: List<DvrCutpoint>, chapters: List<RecordingChapter> = emptyList()): List<RecordingMark> {
    val intervals = cutpoints.mapNotNull { point ->
        val kind = when (point.action) {
            DvrCutpointAction.COMMERCIAL_BREAK -> RecordingMarkKind.AD_BREAK
            DvrCutpointAction.CUT -> RecordingMarkKind.CUT
            else -> return@mapNotNull null
        }
        val start = point.start.inWholeMilliseconds.coerceAtLeast(0)
        val end = point.end.inWholeMilliseconds
        if (end <= start) null else RecordingMark(kind, start, end)
    }
    val merged = RecordingMarkKind.entries.flatMap { kind ->
        val result = mutableListOf<RecordingMark>()
        for (mark in intervals.filter { it.kind == kind }.sortedBy { it.startMs }) {
            val previous = result.lastOrNull()
            if (previous != null && mark.startMs < previous.endMs) {
                result[result.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, mark.endMs))
            } else result.add(mark)
        }
        result
    }
    val hasIntervals = cutpoints.any {
        it.action == DvrCutpointAction.COMMERCIAL_BREAK || it.action == DvrCutpointAction.CUT
    }
    val fileChapters = chapters.mapNotNull { chapter ->
        val name = chapter.name?.trim()?.takeIf { it.isNotEmpty() }
        if (chapter.startMs < 0 || (hasIntervals && name == null)) null
        else RecordingMark(RecordingMarkKind.CHAPTER, chapter.startMs, name = name)
    }
    val scenes = cutpoints.filter { it.action == DvrCutpointAction.SCENE_MARKER }.mapNotNull {
        it.sceneBoundary?.inWholeMilliseconds?.takeIf { boundary -> boundary >= 0 }
            ?.let { boundary -> RecordingMark(RecordingMarkKind.CHAPTER, boundary) }
    }
    return (merged + fileChapters + scenes).distinct().sortedWith(compareBy({ it.startMs }, { it.kind.ordinal }))
}

/**
 * One stop of the marks row: the recording's start ([mark] null), a mark's start, or with [atEnd]
 * the end of the ad break or cut [mark].
 */
data class RecordingMarkStop(val positionMs: Long, val mark: RecordingMark? = null, val atEnd: Boolean = false)

/** Which kind leads where marks meet or their symbols collide: ad break, cut, chapter. */
val RecordingMarkKind.priority: Int
    get() = when (this) {
        RecordingMarkKind.AD_BREAK -> 0
        RecordingMarkKind.CUT -> 1
        RecordingMarkKind.CHAPTER -> 2
    }

/**
 * The marks row's stops: the start, then every mark's start within [0, verifiedDurationMs) in order,
 * and each ad break's and cut's end inside the duration, where one would skip to by hand; chapters
 * are single points. A mark's end beyond a growing recording's duration does not hide its start.
 * Marks that start together make one stop led by the higher-[priority] kind, a named one first; a mark
 * starting where an interval ends takes that stop, and intervals ending together leave one end stop
 * led by priority. Without a mark starting inside the duration there are no stops. These are the
 * positions the marks row seeks.
 */
fun recordingMarkStops(marks: List<RecordingMark>, verifiedDurationMs: Long): List<RecordingMarkStop> {
    val byPosition = marks.filter { it.startMs in 0 until verifiedDurationMs }
        .groupBy { it.startMs }
        .mapValues { (start, group) ->
            RecordingMarkStop(start, group.minWith(compareBy<RecordingMark>({ it.kind.priority }, { it.name == null })))
        }.toMutableMap()
    marks.filter { it.kind != RecordingMarkKind.CHAPTER && it.startMs >= 0 && it.endMs in 1 until verifiedDurationMs }
        .sortedBy { it.kind.priority }
        .forEach { byPosition.putIfAbsent(it.endMs, RecordingMarkStop(it.endMs, it, atEnd = true)) }
    val starts = byPosition.toSortedMap().values.toList()
    return when {
        starts.isEmpty() -> emptyList()
        starts.first().positionMs == 0L -> starts
        else -> listOf(RecordingMarkStop(0L)) + starts
    }
}
