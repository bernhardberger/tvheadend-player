package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.components.ScheduledIndicator

/**
 * Presentation strings follow the displayed programme/seek target. Callers pass no next for
 * recordings; a recording's [metadata] is its whole eyebrow (`Recorded 14 Nov · Ends 23:45`).
 */
data class PlayerInfoBarData(
    val channelNumber: String,
    val channelName: String,
    val title: String,
    val timeRange: String,
    val metadata: String? = null,
    val subtitle: String? = null,
    val remainingMinutes: Int? = null,
    val next: String? = null,
    val nextScheduled: Boolean = false,
    val recordedDate: String? = null,
)

/**
 * The [identity] slot (the programme's artwork) on the title's top, one grid gutter before the
 * programme block: the title, the programme's time, metadata and subtitle on one line, and a last
 * row. Every line of the block keeps its place, present or not, so the block is as tall with one line
 * as with three and nothing moves between programmes. The channel stands at the top of the screen;
 * the description still names it. The last row (live: Next, recording: the episode) runs the full width.
 *
 * @param contentAlpha alpha of the bar's content.
 * @param lastLineEndReserve width the programme block's last line (Next; without it the subtitle;
 * without either the title) leaves free at its end, where live's distance behind live is drawn: a
 * long text ellipsizes before it. The line's own bounds with a text that fits do not change.
 * @param identity the identity, given the content's alpha.
 */
@Composable
fun PlayerInfoBar(
    data: PlayerInfoBarData,
    modifier: Modifier = Modifier,
    contentAlpha: () -> Float = { 1f },
    lastLineEndReserve: Dp = 0.dp,
    identity: @Composable (Modifier) -> Unit,
) {
    val recorded = data.recordedDate != null
    // A recording's metadata is its whole line; live's is the time range followed by the metadata.
    val facts = if (data.recordedDate != null) data.metadata ?: stringResource(R.string.player_recorded_on, data.recordedDate)
        else listOfNotNull(data.timeRange.takeIf(String::isNotBlank), data.metadata?.takeIf(String::isNotBlank)).joinToString(" · ")
    val next = data.next?.let { stringResource(R.string.player_next, it) }
    val remaining = data.remainingMinutes?.let { playerMinutesLeft(it, spoken = true) }
    val scheduled = if (data.nextScheduled && next != null) stringResource(R.string.player_scheduled) else null
    val description = listOfNotNull(
        if (data.channelNumber.isBlank()) data.channelName else stringResource(R.string.player_channel_spoken, data.channelNumber, data.channelName),
        if (data.recordedDate == null) stringResource(R.string.player_now_spoken, data.title, playerSpokenTimeRange(data.timeRange))
        else stringResource(R.string.player_recorded_spoken, data.title, data.recordedDate, playerSpokenTimeRange(data.timeRange)),
        remaining, next, scheduled,
    ).joinToString(". ")
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha)
    val faded = Modifier.graphicsLayer { alpha = contentAlpha() }
    // A recording's episode takes the last row; live keeps its subtitle above Next.
    val lastText = if (recorded) data.subtitle?.takeIf(String::isNotBlank) else next
    val subtitle = data.subtitle?.takeIf(String::isNotBlank).takeIf { !recorded }
    val programmeLine = buildAnnotatedString {
        withStyle(SpanStyle(color = secondary)) { append(facts) }
        if (subtitle != null) {
            if (facts.isNotEmpty()) withStyle(SpanStyle(color = secondary)) { append(" · ") }
            append(subtitle)
        }
    }
    val reserved = Modifier.reserveEnd(lastLineEndReserve)
    // The bar speaks for itself.
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(TvGridGutter), verticalAlignment = Alignment.Top) {
        identity(faded)
        // An absent line's place, kept empty.
        val emptyLine: @Composable (TextStyle) -> Unit = { style ->
            Text("", style = style, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
        }
        Column(Modifier.weight(1f)) {
            Text(data.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface, modifier = faded.then(if (programmeLine.isEmpty() && lastText == null) reserved else Modifier)
                    .testTag("player-info-title").clearAndSetSemantics {})
            Spacer(Modifier.height(2.dp))
            if (programmeLine.isNotEmpty()) {
                Text(programmeLine, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
                    modifier = faded.then(if (lastText == null) reserved else Modifier)
                        .testTag("player-info-programme").clearAndSetSemantics {})
            } else emptyLine(MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (lastText != null) Row(faded.fillMaxWidth().testTag("player-info-last-row"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Centred on the text line without adding to its height: the row is as tall as its text.
                if (!recorded && data.nextScheduled && next != null) ScheduledIndicator(
                    Modifier.noHeight().testTag("player-scheduled-marker"), tint = secondary)
                Text(lastText, style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (recorded) MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextSecondaryAlpha) else secondary,
                    modifier = reserved.testTag("player-info-last-text").clearAndSetSemantics {})
            } else emptyLine(MaterialTheme.typography.labelLarge)
        }
    }
}

/** Measures the content [reserve] narrower than the room it is given, at the start of that room. */
private fun Modifier.reserveEnd(reserve: Dp): Modifier = if (reserve <= 0.dp) this else layout { measurable, constraints ->
    val max = if (constraints.hasBoundedWidth) (constraints.maxWidth - reserve.roundToPx()).coerceAtLeast(0) else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, max), maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** Measures the content at its own height but takes none, centred on where it is placed. */
private fun Modifier.noHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.placeRelative(0, -placeable.height / 2) }
}
