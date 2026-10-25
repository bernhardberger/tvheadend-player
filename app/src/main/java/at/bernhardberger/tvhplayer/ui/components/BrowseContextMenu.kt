package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.BrowseContextAction

internal class BrowseContextMenuTrigger(val modifier: Modifier, val onLongClick: (() -> Unit)?)

/** TV Material owns hold detection; the source and destination both consume its trailing release. */
@Composable
internal fun rememberBrowseContextMenuTrigger(
    onOpen: (() -> Unit)?,
    enabled: () -> Boolean = { true },
    menuKey: Boolean = true,
): BrowseContextMenuTrigger {
    var heldRelease by remember { mutableStateOf(false) }
    return BrowseContextMenuTrigger(
        Modifier.onPreviewKeyEvent { event ->
            val activation = event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
            if (activation && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                heldRelease = false
            }
            when {
                menuKey && event.key == Key.Menu && onOpen != null && enabled() -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onOpen()
                    true
                }
                else -> false
            }
        }.onKeyEvent { event ->
            // Let TV Material clear its native long-click state before consuming any unhandled tail.
            if (heldRelease && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                if (event.type == KeyEventType.KeyUp) heldRelease = false
                true
            } else false
        },
        onOpen?.let { open -> { if (enabled()) { heldRelease = true; open() } } },
    )
}

/** The shared dialog consumes the opener's held activation before any menu button sees it. */
@Composable
internal fun BrowseContextMenu(
    title: String,
    channel: String,
    episode: String,
    time: String,
    actions: List<BrowseContextAction>,
    onAction: (BrowseContextAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String = "",
    watchChannel: Boolean = false,
    programDetailsLabel: Boolean = false,
    backEnabled: Boolean = true,
    recordingState: DvrEntryState? = null,
    playbackIndicator: ChannelPlaybackIndicator = ChannelPlaybackIndicator.NONE,
    artwork: @Composable () -> Unit,
) {
    val requesters = remember { BrowseContextAction.entries.associateWith { FocusRequester() } }
    var focusedAction by remember { mutableStateOf<BrowseContextAction?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(actions, backEnabled) {
        if (!backEnabled) return@LaunchedEffect
        withFrameNanos { }
        val target = if (focusedAction in actions) focusedAction
            else actions.getOrNull(focusedIndex) ?: actions.firstOrNull()
        target?.let { requesters.getValue(it).requestFocus() }
    }
    TwoColumnDialog(title, "", onClose, modifier.testTag("browse-context-menu"), backEnabled,
        onPreviewKeyEvent = { it.key == Key.Menu },
        itemCardLayout = true,
        leading = {
            val subtitle = listOf(channel, time).filter(String::isNotBlank).joinToString(" • ")
            val description = listOf(episode, summary).filter(String::isNotBlank).joinToString("\n")
            Column(Modifier.fillMaxWidth().testTag("browse-context-item")) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { artwork() }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.heightIn(min = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChannelPlaybackMarker(playbackIndicator)
                        RecordingBadge(recordingState, Modifier.testTag("browse-context-recording"))
                    }
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }) {
        actions.forEachIndexed { index, action ->
            key(action) {
                TwoColumnDialogButton(
                    stringResource(if (programDetailsLabel && action == BrowseContextAction.DETAILS)
                        R.string.details_program else action.label(watchChannel)),
                    { onAction(action) },
                    Modifier.focusRequester(requesters.getValue(action))
                        .onFocusChanged { if (it.isFocused) { focusedAction = action; focusedIndex = index } }
                        .focusProperties {
                            up = actions.getOrNull(index - 1)?.let(requesters::get) ?: FocusRequester.Cancel
                            down = actions.getOrNull(index + 1)?.let(requesters::get) ?: FocusRequester.Cancel
                            left = FocusRequester.Cancel; right = FocusRequester.Cancel
                        }, action.icon())
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
    BrowseContextAction.CONTINUE_WATCHING -> R.string.player_continue_watching
    BrowseContextAction.WATCH_LIVE -> R.string.context_watch_live
    BrowseContextAction.RESUME -> R.string.context_resume
    BrowseContextAction.START_OVER -> R.string.context_start_over
    BrowseContextAction.PLAY -> R.string.play_recording
    BrowseContextAction.MARK_WATCHED -> R.string.context_mark_watched
    BrowseContextAction.MARK_UNWATCHED -> R.string.context_mark_unwatched
    BrowseContextAction.DELETE -> R.string.context_delete
    BrowseContextAction.MARK_ALL_WATCHED -> R.string.context_mark_all_watched
    BrowseContextAction.MARK_ALL_UNWATCHED -> R.string.context_mark_all_unwatched
    BrowseContextAction.DELETE_WATCHED -> R.string.context_delete_watched
    BrowseContextAction.DELETE_ALL -> R.string.context_delete_all
}

internal fun BrowseContextAction.icon(): Int = when (this) {
    BrowseContextAction.DETAILS -> R.drawable.ic_info
    BrowseContextAction.RECORD -> R.drawable.ic_fiber_manual_record
    BrowseContextAction.STOP, BrowseContextAction.CANCEL -> R.drawable.ic_stop
    BrowseContextAction.DELETE, BrowseContextAction.DELETE_WATCHED -> R.drawable.ic_delete_outlined
    BrowseContextAction.DELETE_ALL -> R.drawable.ic_delete_sweep_outlined
    BrowseContextAction.SHOW_GUIDE -> R.drawable.ic_event
    BrowseContextAction.CONTINUE_WATCHING -> R.drawable.ic_play_arrow
    BrowseContextAction.WATCH_LIVE -> R.drawable.ic_live_tv_outlined
    BrowseContextAction.START_OVER -> R.drawable.ic_replay
    BrowseContextAction.MARK_WATCHED, BrowseContextAction.MARK_ALL_WATCHED -> R.drawable.ic_check_circle_outlined
    BrowseContextAction.MARK_UNWATCHED, BrowseContextAction.MARK_ALL_UNWATCHED -> R.drawable.ic_unpublished_outlined
    else -> R.drawable.ic_play_arrow
}
