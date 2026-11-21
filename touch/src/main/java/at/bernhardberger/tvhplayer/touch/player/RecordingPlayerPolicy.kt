package at.bernhardberger.tvhplayer.touch.player

import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.core.priority
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import kotlin.math.abs

internal fun recordingDrag(
    target: Long,
    current: Long,
    snapRadius: Long,
    stops: List<RecordingMarkStop>,
    marks: List<RecordingMark>,
): RecordingMarkStop {
    val named = stops.filter { it.named && abs(it.positionMs - target) <= snapRadius }
        .minByOrNull { abs(it.positionMs - target) }
    if (named != null) return named
    val position = if (abs(current - target) <= snapRadius) current else target
    val section = marks.filter { it.contains(position) }.minByOrNull { it.kind.priority }
    return RecordingMarkStop(position, section)
}

internal val RecordingMarkStop.named: Boolean
    get() = mark?.let { it.kind != RecordingMarkKind.CHAPTER || !it.name.isNullOrBlank() } == true

internal fun recordingAdOffer(marks: List<RecordingMark>, position: Long, mode: AdBreakMode): RecordingMark? =
    if (mode == AdBreakMode.OFF) null else marks.firstOrNull {
        it.kind == RecordingMarkKind.AD_BREAK && it.contains(position)
    }

internal fun recordingTrackMarks(marks: List<RecordingMark>, duration: Long): List<RecordingMark> =
    marks.filter { it.startMs in 0 until duration }.map {
        if (it.endMs > duration) it.copy(endMs = duration) else it
    }
