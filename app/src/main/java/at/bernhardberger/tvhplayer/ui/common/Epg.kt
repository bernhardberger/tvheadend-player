package at.bernhardberger.tvhplayer.ui.common

import android.content.res.Resources
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry

internal fun EpgEventEntry.isRunning(nowSec: Long): Boolean =
    start.epochSeconds <= nowSec && nowSec < stop.epochSeconds

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

fun episodeLabel(episode: EpgEpisode?, resources: Resources, spoken: Boolean = false,
    onscreenFallback: Boolean = true): String? {
    episode ?: return null
    val numbers = buildList {
        episode.seasonNumber?.let { add(resources.getString(if (spoken) R.string.episode_season_spoken else R.string.episode_season_compact, it)) }
        episode.episodeNumber?.let { add(resources.getString(if (spoken) R.string.episode_number_spoken else R.string.episode_number_compact, it)) }
    }.joinToString(if (spoken) ", " else " ")
    val part = episode.partNumber?.let { resources.getString(R.string.episode_part, it) }
    return listOfNotNull(numbers.takeIf(String::isNotEmpty), part).takeIf { it.isNotEmpty() }
        ?.joinToString(if (spoken) ", " else " · ")
        ?: episode.onscreen?.takeIf { onscreenFallback && it.isNotBlank() }
}

fun programmeMetadata(event: EpgEventEntry, resources: Resources): String? = buildList {
    event.genre?.takeIf(String::isNotBlank)?.let(::add)
    episodeLabel(event.episode, resources, onscreenFallback = false)?.let(::add)
}.takeIf { it.isNotEmpty() }?.joinToString(" • ")

internal fun programmeEpisodeLine(episode: EpgEpisode?, subtitle: String?, resources: Resources): String =
    listOfNotNull(episodeLabel(episode, resources), subtitle?.takeIf(String::isNotBlank))
        .distinct().joinToString(" · ")

/** Episodes identify their premiere; films and incomplete episode metadata keep the year. */
fun programmeDateLabel(episode: EpgEpisode?, firstAired: kotlin.time.Instant?, copyrightYear: String?,
    locale: java.util.Locale): String? =
    if ((episode?.seasonNumber != null || episode?.episodeNumber != null) && firstAired != null)
        formatShortDate(java.time.Instant.ofEpochSecond(firstAired.epochSeconds).atZone(java.time.ZoneId.systemDefault()), locale)
    else copyrightYear
