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

fun recordingMarkPositions(marks: List<RecordingMark>, verifiedDurationMs: Long): List<Long> {
    if (verifiedDurationMs <= 0) return emptyList()
    val positions = marks.flatMap { listOf(it.startMs, it.endMs) }
        .filter { it > 0 && it < verifiedDurationMs }.distinct().sorted()
    return if (positions.isEmpty()) emptyList() else listOf(0L) + positions
}

/** Position-only callers derive navigation from the same typed policy. */
fun recordingMarkerPositions(
    cutpoints: List<DvrCutpoint>,
    verifiedDurationMs: Long,
    chapterStartsMs: List<Long> = emptyList(),
): List<Long> = recordingMarkPositions(recordingMarks(cutpoints, chapterStartsMs.map { RecordingChapter(it) }), verifiedDurationMs)
