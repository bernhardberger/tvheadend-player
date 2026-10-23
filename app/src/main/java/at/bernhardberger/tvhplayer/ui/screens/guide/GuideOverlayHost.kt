package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.focus.FocusRequester
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry
import at.bernhardberger.tvheadend.sdk.core.EpgSearchResult
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.GuideWindowBounds
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeDetailsHost
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope

/** The Guide's search dialog, programme details and jump-to-time dialog over the browse content. */
@Composable
internal fun GuideOverlayHost(
    contentPadding: PaddingValues,
    showSearchDialog: Boolean,
    detailsFromSearch: Boolean,
    searchQuery: String,
    searchResult: EpgSearchResult?,
    searching: Boolean,
    searchEnabled: Boolean,
    searchChannelName: (ChannelId?) -> String?,
    restoreSearchResultFocus: EventId?,
    onSearchFocusRestored: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenSearchDetails: (EpgEventEntry) -> Unit,
    onDismissSearch: () -> Unit,
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
    showJumpDialog: Boolean,
    windowStartSec: () -> Long,
    windowBounds: GuideWindowBounds,
    zoneId: ZoneId,
    nowSecProvider: () -> Long,
    onDismissJump: () -> Unit,
    onJump: (Long) -> Unit,
) {
    if (showSearchDialog && !detailsFromSearch) {
        EpgSearchDialog(
            contentPadding = contentPadding,
            query = searchQuery,
            result = searchResult,
            searching = searching,
            searchEnabled = searchEnabled,
            channelName = searchChannelName,
            restoreFocusTo = restoreSearchResultFocus,
            onFocusRestored = onSearchFocusRestored,
            onQueryChange = onSearchQueryChange,
            onSearch = onSearch,
            onOpenDetails = onOpenSearchDetails,
            onDismiss = onDismissSearch,
        )
    }

    detailsEvent?.let { openingEvent ->
        val openingObservation = detailsObservation ?: return@let
        ProgrammeDetailsHost(
            openingEvent = openingEvent, openingObservation = openingObservation,
            observation = observation, currentObservation = currentObservation,
            nowSecProvider = { System.currentTimeMillis() / 1000L }, fromGuide = true, showContextMenu = showContextMenu,
            mutationScope = mutationScope, mutationInFlight = mutationInFlight,
            onShowDetails = onShowDetails,
            mutations = mutations, notices = notices, onPlay = onPlay,
            onPlayRecording = onPlayRecording, onClose = onCloseDetails,
            returnFocusRequester = eventFocusRequesters[openingEvent.id].takeUnless { detailsFromSearch },
            onShowGuide = onShowGuide,
        )
    }

    if (showJumpDialog) {
        JumpToTimeDialog(
            initialSec = windowStartSec(),
            bounds = windowBounds,
            zoneId = zoneId,
            nowSecProvider = nowSecProvider,
            onDismiss = onDismissJump,
            onJump = onJump,
        )
    }
}
