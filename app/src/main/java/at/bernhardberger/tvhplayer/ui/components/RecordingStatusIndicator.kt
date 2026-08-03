package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.core.DvrState
import at.bernhardberger.tvhplayer.ui.TvRecordingColor

@Composable
fun RecordingStatusIndicator(
    state: DvrState,
    modifier: Modifier = Modifier,
    announceState: Boolean = true,
) {
    if (state != DvrState.RECORDING && state != DvrState.SCHEDULED) return
    val accessibilityModifier = if (announceState) {
        val description = stringResource(
            if (state == DvrState.RECORDING) {
                R.string.recording_state_recording
            } else {
                R.string.recording_state_scheduled
            }
        )
        Modifier.semantics { contentDescription = description }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(12.dp)
            .then(accessibilityModifier)
            .then(
                if (state == DvrState.RECORDING) {
                    Modifier.background(TvRecordingColor, CircleShape)
                } else {
                    Modifier.border(2.dp, TvRecordingColor, CircleShape)
                }
            )
    )
}
