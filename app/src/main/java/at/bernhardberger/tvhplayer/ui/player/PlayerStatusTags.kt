package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

internal fun statusDuration(milliseconds: Long, hour: String, minute: String, second: String): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return when {
        seconds >= 3600 -> "${seconds / 3600}$hour ${seconds % 3600 / 60}$minute"
        seconds >= 60 -> "${seconds / 60}$minute ${seconds % 60}$second"
        else -> "$seconds$second"
    }
}

internal fun currentRecordingIsGrowing(
    observation: at.bernhardberger.tvheadend.sdk.core.SessionObservation,
    selection: at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection?,
): Boolean = selection != null && observation.currentSession === selection.currentSession &&
    observation.dvrEntry(selection.recordingId)?.state == at.bernhardberger.tvheadend.sdk.core.DvrEntryState.RECORDING

@Composable
internal fun PlayerStatusTags(
    paused: Boolean,
    modifier: Modifier = Modifier,
    timeshift: AppTimeshiftState = AppTimeshiftState(),
    recordingPlayback: Boolean = false,
    growing: Boolean = false,
    recordingNow: Boolean = false,
    playbackPresented: Boolean = true,
) {
    val position = timeshift.takeIf { it.available && it.timingKnown }?.let(::timeshiftPositionPresentation)
    val duration = position?.let { statusDuration(it.behindLiveMs,
        stringResource(R.string.player_status_hour), stringResource(R.string.player_status_minute), stringResource(R.string.player_status_second)) }
    val label = when {
        !playbackPresented -> null
        recordingPlayback -> if (growing) stringResource(R.string.player_status_recording_playback) else null
        position?.atLiveEdge == true -> stringResource(R.string.timeshift_live)
        position != null -> stringResource(R.string.timeshift_behind_live, duration.orEmpty())
        else -> null
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        label?.let {
            StatusTag(it, if (paused) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                when {
                    recordingPlayback -> Color(0xFFFFD180)
                    position?.atLiveEdge == false -> Color(0xFFFFD180)
                    else -> MaterialTheme.colorScheme.primary
                },
                Modifier.testTag("player-clock-status"),
                listOf(
                    it,
                    stringResource(if (paused) R.string.player_paused else R.string.player_status_playing),
                ).joinToString(". "))
        }
        if (playbackPresented && !recordingPlayback && recordingNow) {
            RecordingNowTag()
        }
    }
}

@Composable
private fun StatusTag(label: String, icon: Int, color: Color, modifier: Modifier, description: String) {
    Surface(modifier = modifier.clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp), colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = 0.82f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun RecordingNowTag() {
    val description = stringResource(R.string.player_status_recording_now)
    Surface(modifier = Modifier.testTag("player-recording-now")
        .clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(4.dp), colors = SurfaceDefaults.colors(
            containerColor = TvSurfaceColors.container.copy(alpha = 0.82f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(R.drawable.ic_fiber_manual_record), contentDescription = null,
                tint = TvRecordingColor, modifier = Modifier.size(10.dp))
            Text(stringResource(R.string.player_status_rec), color = TvRecordingColor,
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}
