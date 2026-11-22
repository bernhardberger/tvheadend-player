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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.RecordingBadge
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.recordingStatusSpoken
import at.bernhardberger.tvhplayer.ui.components.rule

private val InfoBarFactsBadgeGap = 12.dp

/**
 * Presentation strings follow the displayed programme/seek target. A recording's [metadata]
 * is its entire facts line, and it has no Next.
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
    val recording: RecordingIdentity? = null,
    val spokenTimeRange: String = "",
    val nowRecording: DvrEntry? = null,
    val nextRecording: DvrEntry? = null,
    val nowRecordingStateOverride: DvrEntryState? = null,
)

data class RecordingIdentity(val headline: String?, val recordedDate: String?)

/**
 * The [identity] slot (the programme's artwork) one grid gutter before the programme block, both
 * sharing the bar's top: an eyebrow with the programme's time and metadata, the title, the subtitle
 * (a recording: its episode) and, live, Next on the slot's bottom edge. Every line of the block keeps
 * its place, present or not, so nothing moves between programmes. The channel stands at the top of the
 * screen; the description still names it. The Next row runs the full width.
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
    badges: List<GlanceBadge> = emptyList(),
    identity: @Composable (Modifier) -> Unit,
) {
    val recorded = data.recording != null
    // A recording's metadata is its whole line; live's is the time range followed by the metadata.
    val facts = if (recorded) data.metadata.orEmpty()
        else listOfNotNull(data.timeRange, data.metadata).filter(String::isNotBlank).joinToString(" • ")
    val next = data.next?.let { stringResource(R.string.player_next, it) }
    val remaining = data.remainingMinutes?.let { playerMinutesLeft(it, spoken = true) }
    val nextRecording = data.nextRecording.takeIf { next != null && !recorded }
    val nowRecordingState = data.nowRecordingStateOverride ?: data.nowRecording?.state
    val subtitle = data.subtitle?.takeIf(String::isNotBlank)
    val description = listOfNotNull(
        if (data.channelNumber.isBlank()) data.channelName else stringResource(R.string.player_channel_spoken,
            channelTitleText(data.channelNumber.toLongOrNull(), data.channelName)),
        when {
            !recorded -> stringResource(R.string.player_now_spoken, data.title, data.spokenTimeRange)
            data.recording?.recordedDate != null -> stringResource(
                R.string.player_recorded_spoken, data.title, data.recording.recordedDate, data.spokenTimeRange,
            )
            else -> listOf(data.title, data.spokenTimeRange).filter(String::isNotBlank).joinToString(", ")
        },
        subtitle,
        facts.takeIf { recorded && data.nowRecording == null && it.isNotBlank() },
        data.nowRecording?.let { recordingStatusSpoken(nowRecordingState, it.rule) },
        remaining, next, nextRecording?.let { recordingStatusSpoken(it.state, it.rule, skipped = it.enabled == false) },
        badges.takeIf { it.isNotEmpty() }?.map { glanceBadgeSpoken(it) }?.joinToString(". "),
    ).joinToString(". ")
    val faded = Modifier.graphicsLayer { alpha = contentAlpha() }
    // Live ends on Next; a recording has none, its episode stands on the subtitle's line.
    val lastText = next.takeIf { !recorded }
    val reserved = Modifier.reserveEnd(lastLineEndReserve)
    // Without a last row, the lowest present line leaves the distance its room.
    val reserveOn = when {
        lastText != null -> "last"
        subtitle != null -> "subtitle"
        facts.isNotEmpty() -> "facts"
        else -> "title"
    }
    // The bar speaks for itself.
    Row(modifier.height(IntrinsicSize.Min).semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(TvGridGutter), verticalAlignment = Alignment.Top) {
        identity(faded)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            PlayerInfoFacts(facts, badges, data.nowRecording.takeUnless { recorded }, nowRecordingState,
                faded.then(if (reserveOn == "facts") reserved else Modifier))
            Spacer(Modifier.height(4.dp))
            Text(data.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface,
                modifier = faded.then(if (reserveOn == "title") reserved else Modifier)
                    .testTag("player-info-title").clearAndSetSemantics {})
            Spacer(Modifier.height(2.dp))
            Text(subtitle.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextSecondaryAlpha),
                modifier = faded.then(if (reserveOn == "subtitle") reserved else Modifier)
                    .then(if (subtitle != null) Modifier.testTag("player-info-subtitle") else Modifier).clearAndSetSemantics {})
            // Now above, Next on the artwork's bottom edge.
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            if (lastText != null) {
                PlayerInfoNext(lastText, nextRecording, lastLineEndReserve, faded)
            } else {
                Text("", style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

@Composable
private fun PlayerInfoNext(
    title: String,
    recording: DvrEntry?,
    endReserve: Dp,
    modifier: Modifier,
) {
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha)
    Row(
        modifier.fillMaxWidth().testTag("player-info-last-row").padding(end = endReserve),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = secondary,
            modifier = Modifier.weight(1f, fill = false)
                .testTag("player-info-last-text").clearAndSetSemantics {})
        // Centred on the text line without adding to its height: the row is as tall as its text.
        if (recording != null) RecordingStatusIndicator(
            recording.state, Modifier.noHeight()
                .then(if (recording.state == DvrEntryState.SCHEDULED) Modifier.testTag("player-scheduled-marker") else Modifier),
            rule = recording.rule, announceState = false, skipped = recording.enabled == false,
            textStyle = MaterialTheme.typography.labelLarge, scheduledTint = secondary)
    }
}

@Composable
private fun PlayerInfoFacts(
    facts: String, badges: List<GlanceBadge>, recording: DvrEntry?, state: DvrEntryState?, modifier: Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (recording != null) {
            Box(Modifier.testTag("player-info-timing-mark").clearAndSetSemantics {}) {
                RecordingBadge(state, compact = true, rule = recording.rule)
            }
            if (facts.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        Text(facts, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTextTertiaryAlpha),
            modifier = Modifier.weight(1f, fill = false).testTag("player-info-programme").clearAndSetSemantics {})
        if (badges.isNotEmpty()) {
            if (facts.isNotEmpty()) Spacer(Modifier.width(InfoBarFactsBadgeGap))
            PlayerGlanceBadges(badges)
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
internal fun Modifier.noHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    layout(placeable.width, 0) { placeable.placeRelative(0, -placeable.height / 2) }
}
