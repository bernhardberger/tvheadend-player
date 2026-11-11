package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.focus.FocusRequester
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeDetailsHost
import kotlinx.coroutines.CoroutineScope

/** The Guide's programme details over the browse content. */
@Composable
internal fun GuideOverlayHost(
    detailsEvent: EpgEventEntry?,
    detailsObservation: SessionObservation?,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    showContextMenu: Boolean,
    mutationScope: CoroutineScope,
    mutationInFlight: MutableState<DvrMutationAction?>,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    onShowDetails: () -> Unit,
    onPlay: (channelId: ChannelId, channelName: String) -> Unit,
    onPlayRecording: (RecordingPlaybackSelection) -> Unit,
    onCloseDetails: () -> Unit,
    eventFocusRequesters: Map<EventId, FocusRequester>,
    onShowGuide: (ChannelId, EpgEventEntry?) -> Unit,
    initialAction: at.bernhardberger.tvhplayer.core.BrowseContextAction? = null,
) {
    detailsEvent?.let { openingEvent ->
        val openingObservation = detailsObservation ?: return@let
        ProgrammeDetailsHost(
            openingEvent = openingEvent, openingObservation = openingObservation,
            observation = observation, currentObservation = currentObservation,
            nowSecProvider = { System.currentTimeMillis() / 1000L }, showContextMenu = showContextMenu,
            mutationScope = mutationScope, mutationInFlight = mutationInFlight,
            onShowDetails = onShowDetails,
            mutations = mutations, notices = notices, onPlay = onPlay,
            onPlayRecording = onPlayRecording, onClose = onCloseDetails,
            returnFocusRequester = eventFocusRequesters[openingEvent.id],
            onShowGuide = onShowGuide,
            initialAction = initialAction,
        )
    }
}
