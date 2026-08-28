package at.bernhardberger.tvhplayer.ui.common

import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
fun List<EpgEventEntry>.nowEvent(nowSec: Long): EpgEventEntry? =
    firstOrNull { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }

fun List<EpgEventEntry>.nextAfter(now: EpgEventEntry?): EpgEventEntry? {
    val nowId = now?.id ?: return firstOrNull()
    val idx = indexOfFirst { it.id == nowId }
    return if (idx >= 0 && idx + 1 < size) this[idx + 1] else null
}

fun EpgEventEntry.progress(nowSec: Long): Float {
    val dur = (stop.epochSeconds - start.epochSeconds).coerceAtLeast(1L)
    val pos = (nowSec - start.epochSeconds).coerceIn(0L, dur)
    return (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
}

fun programmeMetadata(event: EpgEventEntry): String? = buildList {
    event.genre?.takeIf(String::isNotBlank)?.let(::add)
    if (event.episode?.seasonNumber != null || event.episode?.episodeNumber != null) {
        add(
            buildString {
                event.episode?.seasonNumber?.let { append("S$it") }
                event.episode?.episodeNumber?.let {
                    if (isNotEmpty()) append(" ")
                    append("E$it")
                    event.episode?.episodeCount?.let { count -> append("/$count") }
                }
            }
        )
    }
    if (event.episode?.partNumber != null) {
        add(
            buildString {
                append("Part ${event.episode?.partNumber}")
                event.episode?.partCount?.let { append("/$it") }
            }
        )
    }
}.takeIf { it.isNotEmpty() }?.joinToString(" • ")
