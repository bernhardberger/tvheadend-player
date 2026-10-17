package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.rememberCloseBehindPlayer
import at.bernhardberger.tvhplayer.ui.rememberPlayerEntryPending
import androidx.compose.ui.input.key.onPreviewKeyEvent
import at.bernhardberger.tvhplayer.ui.components.BrowseContextMenu
import at.bernhardberger.tvhplayer.ui.components.ProgrammeArtwork
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject

@Composable
internal fun ChannelContextMenu(
    channelId: ChannelId,
    observation: SessionObservation,
    nowSecProvider: () -> Long = { System.currentTimeMillis() / 1000L },
    mutationScope: CoroutineScope,
    mutationInFlight: MutableState<DvrMutationAction?>,
    onPlay: (ChannelId, String) -> Unit,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onClose: () -> Unit,
    showDetails: Boolean = false,
    onShowDetails: () -> Unit = {},
    onPlayRecording: ((RecordingPlaybackSelection) -> Unit)? = null,
    returnFocusRequester: FocusRequester? = null,
) {
    val opening = remember { observation }
    val channel = observation.channel(channelId)
    LaunchedEffect(channel) { if (channel == null) onClose() }
    if (channel == null) return
    val event = remember { opening.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(nowSecProvider())) }
    val capability = opening.currentSession?.takeIf { it === observation.currentSession }
    val session: TvheadendSession = koinInject()
    val notices: NoticeCenter = koinInject()
    val mutations = remember(session) { DvrMutationActions(session.dvrRepository) }
    val closeBehindPlayer = rememberCloseBehindPlayer()
    val entryPending = rememberPlayerEntryPending()
    // Opened before Ready it stays: Watch needs only the channel. A session it opened with is gone.
    LaunchedEffect(capability) { if (opening.currentSession != null && capability == null) onClose() }
    if (event != null) {
        ProgrammeDetailsHost(event, opening, observation, { session.observation.value }, nowSecProvider,
            fromGuide = false, showContextMenu = !showDetails, onShowDetails = onShowDetails,
            mutations = mutations, notices = notices,
            mutationScope = mutationScope, mutationInFlight = mutationInFlight,
            onPlay = onPlay, onPlayRecording = onPlayRecording, onShowGuide = onShowGuide, onClose = onClose,
            returnFocusRequester = returnFocusRequester)
    } else BrowseContextMenu(
        title = at.bernhardberger.tvhplayer.ui.components.channelTitleText(channel.number, channel.name.orEmpty()),
        channel = "", episode = "", time = "", watchChannel = true,
        actions = listOf(BrowseContextAction.WATCH, BrowseContextAction.SHOW_GUIDE),
        onClose = onClose,
        modifier = Modifier.onPreviewKeyEvent { entryPending() },
        onAction = { action ->
            when (action) {
                BrowseContextAction.WATCH -> {
                    closeBehindPlayer(onClose)
                    onPlay(channelId, channel.name.orEmpty())
                }
                BrowseContextAction.SHOW_GUIDE -> { onClose(); onShowGuide(channelId, null) }
                else -> Unit
            }
        },
        artwork = { ProgrammeArtwork(null, channel.icon, null, capability,
            Modifier.fillMaxSize().testTag("browse-context-picon")) },
    )
}
