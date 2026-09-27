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

/** Icon size for status marks set next to small text: chips, guide cells, list rows. */
val StatusIconSize: Dp = 16.dp

/** Recording now is the red dot; scheduled is a clock in the surrounding content colour. */
@Composable
fun RecordingStatusIndicator(
    state: DvrEntryState,
    modifier: Modifier = Modifier,
    announceState: Boolean = true,
) {
    val recording = when (state) {
        DvrEntryState.RECORDING -> true
        DvrEntryState.SCHEDULED -> false
        else -> return
    }
    val description = stringResource(
        if (recording) R.string.recording_state_recording else R.string.recording_state_scheduled
    )
    Icon(
        painter = painterResource(if (recording) R.drawable.ic_fiber_manual_record else R.drawable.ic_schedule),
        contentDescription = description.takeIf { announceState },
        modifier = modifier.size(StatusIconSize),
        tint = if (recording) TvRecordingColor else LocalContentColor.current,
    )
}

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
