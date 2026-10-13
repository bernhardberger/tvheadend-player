package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseContextAction

/** The opener's held activation is finished here, before any menu button sees it. */
@Composable
internal fun BrowseContextMenu(
    title: String,
    metadata: String,
    timeRange: String,
    actions: List<BrowseContextAction>,
    onAction: (BrowseContextAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    watchChannel: Boolean = false,
    initialAction: BrowseContextAction? = null,
    programDetailsLabel: Boolean = false,
    logo: @Composable () -> Unit,
) {
    val requesters = remember(actions) { actions.map { FocusRequester() } }
    var openingReleasePending by remember { mutableStateOf(true) }
    BackHandler(onBack = onClose)
    LaunchedEffect(actions) {
        withFrameNanos { }
        requesters.getOrNull(actions.indexOf(initialAction).coerceAtLeast(0))?.requestFocus()
    }
    PlaybackOptionsOverlayFrame(
        paneTitle = title,
        panelTag = "browse-context-menu",
        modifier = modifier.onPreviewKeyEvent { event ->
            val activation = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            when {
                event.key == Key.Menu -> true
                activation && openingReleasePending -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        openingReleasePending = false
                        false
                    } else {
                        if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                        true
                    }
                }
                event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                else -> false
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                logo()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(metadata, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(timeRange, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            actions.forEachIndexed { index, action ->
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .focusRequester(requesters[index]).focusProperties {
                            up = requesters.getOrNull(index - 1) ?: FocusRequester.Cancel
                            down = requesters.getOrNull(index + 1) ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Icon(painterResource(action.icon()), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(if (programDetailsLabel && action == BrowseContextAction.DETAILS)
                        R.string.details_program else action.label(watchChannel)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal fun BrowseContextAction.label(watchChannel: Boolean): Int = when (this) {
    BrowseContextAction.WATCH -> if (watchChannel) R.string.context_watch_channel else R.string.context_watch
    BrowseContextAction.DETAILS -> R.string.context_details
    BrowseContextAction.RECORD -> R.string.context_record
    BrowseContextAction.STOP -> R.string.stop_recording
    BrowseContextAction.CANCEL -> R.string.cancel_recording
    BrowseContextAction.SHOW_GUIDE -> R.string.context_show_guide
    BrowseContextAction.RESUME -> R.string.context_resume
    BrowseContextAction.START_OVER -> R.string.context_start_over
    BrowseContextAction.PLAY -> R.string.play
    BrowseContextAction.DELETE -> R.string.context_delete
}

internal fun BrowseContextAction.icon(): Int = when (this) {
    BrowseContextAction.DETAILS -> R.drawable.ic_info
    BrowseContextAction.RECORD -> R.drawable.ic_fiber_manual_record
    BrowseContextAction.STOP, BrowseContextAction.CANCEL -> R.drawable.ic_stop
    BrowseContextAction.DELETE -> R.drawable.ic_delete_outlined
    BrowseContextAction.SHOW_GUIDE -> R.drawable.ic_event
    BrowseContextAction.START_OVER -> R.drawable.ic_replay
    else -> R.drawable.ic_play_arrow
}
