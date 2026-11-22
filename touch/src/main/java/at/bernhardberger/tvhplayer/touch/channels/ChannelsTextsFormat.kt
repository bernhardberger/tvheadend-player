package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvhplayer.ui.common.AppClock
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatNarrowDuration
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class ChannelsTextsFormat(val locale: Locale, val zone: ZoneId, val uses24Hour: Boolean)

@Composable
internal fun channelsTextFormat() = ChannelsTextsFormat(
    LocalConfiguration.current.locales[0], ZoneId.systemDefault(), AppClock.uses24Hour,
)

/** The rows' texts from the shared formatters, in the device's locale and zone. */
@Composable
internal fun rememberChannelsTexts(format: ChannelsTextsFormat = channelsTextFormat()): ChannelsTexts {
    val resources = LocalContext.current.resources
    val (locale, zone, uses24Hour) = format
    return remember(resources, format) {
        ChannelsTexts(
            clock = { formatClock(Instant.ofEpochSecond(it).atZone(zone), locale, uses24Hour) },
            timeRange = { start, stop -> formatTimeInterval(start, stop, locale = locale, uses24Hour = uses24Hour) },
            duration = { formatNarrowDuration(it, resources) },
            episode = { programmeEpisodeLine(it.episode, it.subtitle, resources).takeIf(String::isNotBlank) },
            genre = { event ->
                programmeCategory(event)?.let { resources.getString(programmeCategoryLabelRes(it)) }
                    ?: event.genre?.takeIf(String::isNotBlank)
            },
        )
    }
}
