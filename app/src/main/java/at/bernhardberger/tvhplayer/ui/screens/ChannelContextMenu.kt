package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader
import org.koin.compose.koinInject

@Composable
internal fun ChannelContextMenu(
    channelId: ChannelId,
    observation: SessionObservation,
    nowSec: Long,
    imageLoader: ImageLoader,
    onPlay: (LivePlaybackSelection, String) -> Unit,
    onShowGuide: (ChannelId) -> Unit,
    onClose: () -> Unit,
    showDetails: Boolean = false,
    onShowDetails: () -> Unit = {},
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)? = null,
) {
    val opening = remember { observation }
    val channel = observation.channel(channelId)
    LaunchedEffect(channel) { if (channel == null) onClose() }
    if (channel == null) return
    val event = remember { opening.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSec)) }
    val capability = opening.currentSession?.takeIf { it === observation.currentSession }
    val session: TvheadendSession = koinInject()
    val notices: NoticeCenter = koinInject()
    val mutations = remember(session) { DvrMutationActions(session.dvrRepository) }
    LaunchedEffect(capability) { if (capability == null) onClose() }
    if (event != null) {
        ProgrammeDetailsHost(event, opening, observation, { session.observation.value }, { nowSec },
            fromGuide = false, showContextMenu = !showDetails, onShowDetails = onShowDetails,
            imageLoader = imageLoader, mutations = mutations, notices = notices,
            onPlay = onPlay, onPlayRecording = onPlayRecording, onShowGuide = onShowGuide, onClose = onClose)
    } else BrowseContextMenu(
        title = channel.name.orEmpty(), metadata = channel.number?.toString().orEmpty(), timeRange = "",
        actions = listOf(BrowseContextAction.WATCH, BrowseContextAction.SHOW_GUIDE),
        onClose = onClose,
        onAction = { action ->
            when (action) {
                BrowseContextAction.WATCH -> capability?.let {
                    onClose()
                    onPlay(LivePlaybackSelection(it, channelId), channel.name.orEmpty())
                }
                BrowseContextAction.SHOW_GUIDE -> { onClose(); onShowGuide(channelId) }
                else -> Unit
            }
        },
        logo = { PiconBox(imageLoader, channel.icon, Modifier.size(56.dp, 40.dp), capability) },
    )
}
