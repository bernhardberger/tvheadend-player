package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelRows
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLayout
import at.bernhardberger.tvhplayer.touch.channels.ChannelsLoadingLine
import at.bernhardberger.tvhplayer.touch.channels.ChannelsSkeleton
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.ui.ListFailure
import at.bernhardberger.tvhplayer.touch.ui.ListMessage
import at.bernhardberger.tvhplayer.touch.ui.MessageKind
import at.bernhardberger.tvhplayer.touch.ui.OfflineBanner
import at.bernhardberger.tvhplayer.touch.ui.OfflineTexts

/** The same browse states under the picture and inside the side sheet, with no settings route. */
@Composable
internal fun PlayerChannelsContent(
    state: ChannelsState,
    layout: ChannelsLayout,
    onChannel: (String) -> Unit,
    onTag: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    val rows = ChannelsActions(onPlay = onChannel, onTag = onTag, onRetry = onRetry, onOpenConnectionSettings = {})
    when (state) {
        ChannelsState.Loading -> Column {
            ChannelsLoadingLine(layout)
            ChannelsSkeleton(layout)
        }
        ChannelsState.Unreachable -> ListMessage(MessageKind.Unreachable, onSettings = null, onRetry = onRetry)
        ChannelsState.SignInFailed -> ListMessage(MessageKind.SignInFailed, onSettings = null)
        ChannelsState.SignInNeeded -> ListMessage(MessageKind.SignInNeeded, onSettings = null)
        is ChannelsState.Failed -> ListFailure(state.error, onRetry, onSettings = null)
        is ChannelsState.Offline -> Column {
            OfflineBanner(!layout.tablet, PlayerChannelsOfflineTexts, onSettings = null, retryIn = state.retryIn)
            ChannelRows(state.list, layout, rows, offline = true, showColumnLabels = false)
        }
        is ChannelsState.Ready -> if (state.list.rows.isEmpty()) {
            val kind = if (state.list.selectedTagId == null) MessageKind.Empty else MessageKind.EmptyTag
            ListMessage(kind, onSettings = null, onShowAll = { onTag(null) })
        } else {
            ChannelRows(state.list, layout, rows, showColumnLabels = false)
        }
    }
}

private val PlayerChannelsOfflineTexts = OfflineTexts(R.string.channels_offline, R.string.channels_saved)
