package at.bernhardberger.tvhplayer.ui.common

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import kotlin.math.abs

fun List<EpgEventEntry>.nowEvent(nowSec: Long): EpgEventEntry? =
    firstOrNull { it.start <= nowSec && nowSec < it.stop }
        ?: minByOrNull { abs(it.start - nowSec) }

fun List<EpgEventEntry>.nextAfter(now: EpgEventEntry?): EpgEventEntry? {
    val nowId = now?.eventId ?: return firstOrNull()
    val idx = indexOfFirst { it.eventId == nowId }
    return if (idx >= 0 && idx + 1 < size) this[idx + 1] else null
}

fun EpgEventEntry.progress(nowSec: Long): Float {
    val dur = (stop - start).coerceAtLeast(1L)
    val pos = (nowSec - start).coerceIn(0L, dur)
    return (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
}

fun programmeMetadata(event: EpgEventEntry): String? = buildList {
    event.genre?.takeIf(String::isNotBlank)?.let(::add)
    if (event.seasonNumber != null || event.episodeNumber != null) {
        add(
            buildString {
                event.seasonNumber?.let { append("S$it") }
                event.episodeNumber?.let {
                    if (isNotEmpty()) append(" ")
                    append("E$it")
                    event.episodeCount?.let { count -> append("/$count") }
                }
            }
        )
    }
    if (event.partNumber != null) {
        add(
            buildString {
                append("Part ${event.partNumber}")
                event.partCount?.let { append("/$it") }
            }
        )
    }
}.takeIf { it.isNotEmpty() }?.joinToString(" • ")