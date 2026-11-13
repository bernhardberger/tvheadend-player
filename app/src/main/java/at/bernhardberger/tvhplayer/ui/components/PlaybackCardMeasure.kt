package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.core.PlaybackCardFit
import at.bernhardberger.tvhplayer.core.fitPlaybackCard

internal class PlaybackCardMeasure(val fit: PlaybackCardFit, val airTimeOwnRow: Boolean)

/** Line counts measured at the card's text widths, fitted under the height cap. */
@Composable
internal fun rememberPlaybackCardFit(
    picture: PlaybackPicture, hasPicon: Boolean, channel: String, airTime: String?, title: String,
    episode: String, status: String?, waiting: String?, buttonCount: Int,
): PlaybackCardMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val type = MaterialTheme.typography
    return remember(picture, hasPicon, channel, airTime, title, episode, status, waiting, buttonCount, density, type) {
        with(density) {
            val poster = picture == PlaybackPicture.Poster
            val column = (if (poster) PosterColumnWidth else PlaybackCardWidth - CardPadding * 2).roundToPx()
            val text = PlaybackTextMeasurement(measurer, density, column)
            val ownRow =
                airTimeNeedsOwnRow(picture, hasPicon, channel, airTime, column, measurer, type.labelLarge, density)
            val channelRow = maxOf(
                PLAYBACK_CHANNEL_ROW, text.measure(channel, type.labelLarge, 1).size.height.toDp().value,
            )
            val channelRows = if (ownRow) channelRow * 2 + 4f else channelRow
            val waitingHeight = waiting?.let { 16f + text.height(it, type.bodyLarge.balanced(), Int.MAX_VALUE) } ?: 0f
            val statusHeight = status?.let {
                (if (poster) 16f else 12f) + maxOf(8f, text.height(it, type.bodyMedium, 1)) + 12f + 4f
            } ?: 0f
            val stack = 24f + buttonCount * 40f + (buttonCount - 1).coerceAtLeast(0) * 12f + 24f
            val fit = fitPlaybackCard(
                PLAYBACK_CARD_MAX_HEIGHT, if (picture == PlaybackPicture.Band) 112f..160f else 0f..0f,
                text.lines(title, type.headlineMedium, 3), text.lines(episode, type.bodyLarge, 2),
            ) { t, e ->
                playbackHeadingHeight(picture, channelRow, channelRows, title, e, airTime != null,
                    text.height(title, type.headlineMedium, t), text.height(episode, type.bodyLarge, e)) +
                    statusHeight + waitingHeight + stack
            }
            PlaybackCardMeasure(fit, ownRow)
        }
    }
}

private class PlaybackTextMeasurement(
    private val measurer: TextMeasurer, private val density: Density, private val column: Int,
) {
    fun measure(text: String, style: TextStyle, max: Int) = measurer.measure(
        text, style, overflow = TextOverflow.Ellipsis, maxLines = max, constraints = Constraints(maxWidth = column),
    )

    fun lines(text: String, style: TextStyle, max: Int) = if (text.isBlank()) 0 else measure(text, style, max).lineCount

    fun height(text: String?, style: TextStyle, max: Int) = if (text.isNullOrBlank() || max == 0) 0f else
        with(density) { measure(text, style, max).size.height.toDp().value }
}

private fun airTimeNeedsOwnRow(
    picture: PlaybackPicture, hasPicon: Boolean, channel: String, airTime: String?, column: Int,
    measurer: TextMeasurer, style: TextStyle, density: Density,
): Boolean = with(density) {
    val logo = if (hasPicon && picture != PlaybackPicture.Logo) InlineLogoSpace.roundToPx() else 0
    picture != PlaybackPicture.Poster && airTime != null &&
        measurer.measure(channelLine(channel, airTime), style, maxLines = 1, softWrap = false)
            .size.width > column - logo
}

private fun playbackHeadingHeight(
    picture: PlaybackPicture, channelRow: Float, channelRows: Float, title: String, episodeLines: Int,
    hasAirTime: Boolean, titleHeight: Float, episodeHeight: Float,
): Float {
    val text = (if (title.isNotBlank()) 8f + titleHeight else 0f) +
        (if (episodeLines > 0) (if (title.isBlank()) 8f else 4f) + episodeHeight else 0f)
    return when (picture) {
        PlaybackPicture.Band -> -16f + channelRows + text
        PlaybackPicture.Logo -> LogoHeadingTop.value + channelRows + text
        PlaybackPicture.None -> CardPadding.value + channelRows + text
        PlaybackPicture.Poster -> CardPadding.value +
            maxOf(PosterHeight.value, channelRow + text + (if (hasAirTime) 4f + channelRow else 0f))
    }
}

internal fun channelLine(channel: String, airTime: String?) = listOfNotNull(channel.takeIf(String::isNotBlank), airTime)
    .joinToString(" · ")

internal fun TextStyle.balanced() = copy(lineBreak = lineBreak.copy(strategy = LineBreak.Strategy.Balanced))
