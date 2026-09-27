package at.bernhardberger.tvhplayer.ui.player

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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ScheduledIndicator
import coil3.ImageLoader

/** Presentation strings follow the displayed programme/seek target. Callers pass no next for recordings. */
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

@Composable
fun PlayerInfoBar(
    data: PlayerInfoBarData,
    badges: List<GlanceBadge>,
    picon: ArtworkId?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
) {
    val identity = listOfNotNull(data.channelNumber.takeIf(String::isNotBlank), data.channelName.takeIf(String::isNotBlank)).joinToString(" · ")
    val eyebrow = if (data.recordedDate != null) stringResource(R.string.trial_recorded, data.recordedDate, data.timeRange)
        else listOfNotNull(data.timeRange.takeIf(String::isNotBlank), data.metadata?.takeIf(String::isNotBlank)).joinToString(" · ")
    val next = data.next?.let { stringResource(R.string.trial_next, it) }
    val remaining = data.remainingMinutes?.let { trialMinutesLeft(it, spoken = true) }
    val scheduled = if (data.nextScheduled && next != null) stringResource(R.string.trial_scheduled) else null
    val description = listOfNotNull(
        if (data.channelNumber.isBlank()) data.channelName else stringResource(R.string.trial_channel_spoken, data.channelNumber, data.channelName),
        if (data.recordedDate == null) stringResource(R.string.trial_now_spoken, data.title, trialSpokenTimeRange(data.timeRange))
        else stringResource(R.string.trial_recorded_spoken, data.title, data.recordedDate, trialSpokenTimeRange(data.timeRange)),
        remaining, next, scheduled,
    ).joinToString(". ")
    Row(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(PlayerTrialTokens.identityWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                PiconBox(imageLoader, picon, Modifier.size(PlayerTrialTokens.identityWidth, PlayerTrialTokens.identityHeight), currentSession)
                Text(identity, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                Text(data.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface)
                data.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f))
                }
                if (next != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (data.nextScheduled) ScheduledIndicator(Modifier.testTag("trial-scheduled-marker"),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                    Text(next, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                }
            }
        }
        PlayerGlanceBadges(badges, Modifier.width(PlayerTrialTokens.badgeColumnWidth))
    }
}
