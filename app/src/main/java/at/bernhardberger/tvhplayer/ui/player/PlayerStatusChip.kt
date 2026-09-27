package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.PlayerStatus
import at.bernhardberger.tvhplayer.core.PlayerStatusIndicator
import at.bernhardberger.tvhplayer.core.PlayerStatusKind
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.StatusIconSize

@Composable
fun PlayerStatusChip(status: PlayerStatus, modifier: Modifier = Modifier) {
    val kindLabel = stringResource(when (status.kind) {
        PlayerStatusKind.LIVE -> R.string.trial_live
        PlayerStatusKind.BEHIND_LIVE -> R.string.trial_behind
        PlayerStatusKind.PAUSED -> R.string.trial_paused
        PlayerStatusKind.TUNING -> R.string.trial_tuning
        PlayerStatusKind.BUFFERING -> R.string.trial_buffering
        PlayerStatusKind.RECORDING -> R.string.trial_recording
        PlayerStatusKind.GROWING_RECORDING -> R.string.trial_growing
        PlayerStatusKind.PROBLEM -> R.string.trial_problem
    })
    val behind = status.behindLiveSeconds?.let {
        stringResource(R.string.timeshift_behind_live, statusDuration(it * 1000,
            stringResource(R.string.player_status_hour), stringResource(R.string.player_status_minute), stringResource(R.string.player_status_second)))
    }
    val text = when (status.kind) {
        PlayerStatusKind.BEHIND_LIVE -> behind ?: kindLabel
        PlayerStatusKind.PAUSED -> listOfNotNull(kindLabel, behind).joinToString(" · ")
        PlayerStatusKind.PROBLEM -> status.problem ?: kindLabel
        PlayerStatusKind.RECORDING -> stringResource(R.string.player_status_rec)
        else -> kindLabel
    }
    // Hide the ticking visual descendants from accessibility. Only kind transitions publish a
    // new announcement, except that a changed problem must also be announced.
    val announcement = remember(status.kind, kindLabel, status.problem) {
        if (status.kind == PlayerStatusKind.PROBLEM) status.problem ?: kindLabel else kindLabel
    }
    val recording = status.indicator == PlayerStatusIndicator.RECORDING_DOT
    Row(
        modifier.background(TvSurfaceColors.containerHigh.copy(alpha = TvPanelBrowseAlpha), PlayerTrialTokens.chipShape)
            .heightIn(min = PlayerTrialTokens.chipHeight).padding(horizontal = 8.dp)
            .clearAndSetSemantics { contentDescription = announcement; liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // A chip without an indicator starts its label at the start padding.
        if (status.indicator != PlayerStatusIndicator.NONE) Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { when (status.indicator) {
            PlayerStatusIndicator.SPINNER -> CircularProgressIndicator(Modifier.size(12.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
            else -> Icon(
                painterResource(when (status.indicator) {
                    PlayerStatusIndicator.PLAY -> R.drawable.ic_play_arrow
                    PlayerStatusIndicator.PAUSE -> R.drawable.ic_pause
                    PlayerStatusIndicator.RECORDING_DOT -> R.drawable.ic_fiber_manual_record
                    else -> R.drawable.ic_error_outlined
                }), null, Modifier.size(StatusIconSize),
                tint = if (recording) TvRecordingColor else MaterialTheme.colorScheme.onSurface,
            )
        } }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (status.kind == PlayerStatusKind.RECORDING) TvRecordingColor else MaterialTheme.colorScheme.onSurface)
    }
}
