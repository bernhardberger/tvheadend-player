package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvRecordingFocusedColor

/** Icon size for status marks set next to small text: chips, guide cells, list rows. */
val StatusIconSize: Dp = 16.dp

/**
 * Recording now, with or without errors, is the red dot; scheduled is a clock in the surrounding content colour.
 * Pass [focused] when the mark sits on a focused, light row or cell: the dot turns deeper red.
 */
@Composable
fun RecordingStatusIndicator(
    state: DvrEntryState,
    modifier: Modifier = Modifier,
    announceState: Boolean = true,
    focused: Boolean = false,
) {
    val recording = when (state) {
        DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR -> true
        DvrEntryState.SCHEDULED -> false
        else -> return
    }
    val description = stringResource(
        when (state) {
            DvrEntryState.RECORDING_ERROR -> R.string.recording_state_recording_errors
            DvrEntryState.RECORDING -> R.string.recording_state_recording
            else -> R.string.recording_state_scheduled
        }
    )
    Icon(
        painter = painterResource(if (recording) R.drawable.ic_fiber_manual_record else R.drawable.ic_schedule),
        contentDescription = description.takeIf { announceState },
        modifier = modifier.size(StatusIconSize),
        tint = if (recording) recordingMarkColor(focused) else LocalContentColor.current,
    )
}

/** The red of a recording mark drawn outside this indicator: deeper on a focused, light row. */
fun recordingMarkColor(focused: Boolean): Color = if (focused) TvRecordingFocusedColor else TvRecordingColor

/** Scheduled mark for places that only know "scheduled", not a DVR entry state. */
@Composable
fun ScheduledIndicator(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(R.drawable.ic_schedule),
        contentDescription = contentDescription,
        modifier = modifier.size(StatusIconSize),
        tint = tint,
    )
}
