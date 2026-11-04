package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.WarmPlaybackScrim
import at.bernhardberger.tvhplayer.ui.components.ProgrammeDetailsPage
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeDetailsHost
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationAction
import at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions
import kotlinx.coroutines.CoroutineScope

/** The browse details surface over the existing player, without a playback request. */
@Composable
internal fun PlayerDetailsSurface(
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        WarmPlaybackScrim()
        CompositionLocalProvider(LocalWarmPlayback provides true, content = content)
    }
}

@Composable
internal fun LivePlayerProgrammeDetails(
    event: EpgEvent?,
    channel: Channel?,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSec: Long,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    mutationScope: CoroutineScope,
    settingsStore: UiSettingsStore,
    onPlay: (ChannelId, String) -> Unit,
    onPlayRecording: ((at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection) -> Unit)? = null,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onClose: () -> Unit,
    onContinueWatching: (() -> Unit)? = null,
    showContextMenu: Boolean = false,
    onShowDetails: () -> Unit = {},
) {
    // Freeze the watched-position programme at entry; the host refreshes its data by ID.
    val openingEvent = remember { event }
    val openingObservation = remember { observation }
    val latestNow = rememberUpdatedState(nowSec)
    val inFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    PlayerDetailsSurface {
        if (openingEvent != null) {
            ProgrammeDetailsHost(
                openingEvent = openingEvent,
                openingObservation = openingObservation,
                observation = observation,
                currentObservation = currentObservation,
                nowSecProvider = { latestNow.value },
                showContextMenu = showContextMenu,
                onShowDetails = onShowDetails,
                mutations = mutations,
                notices = notices,
                mutationScope = mutationScope,
                mutationInFlight = inFlight,
                onPlay = onPlay,
                onPlayRecording = onPlayRecording,
                onShowGuide = onShowGuide,
                onClose = onClose,
                settingsStore = settingsStore,
                retainPlayerSnapshot = true,
                onContinueWatching = onContinueWatching,
            )
        } else {
            ProgrammeDetailsPage(null, null, channel, nowSec,
                actions = listOf(BrowseContextAction.CONTINUE_WATCHING),
                onAction = { onClose() }, onClose = onClose)
        }
    }
}

@Composable
internal fun RecordingPlayerDetails(
    recording: DvrEntry,
    openingObservation: SessionObservation,
    observation: SessionObservation,
    currentObservation: () -> SessionObservation,
    nowSec: Long,
    mutations: DvrMutationActions,
    notices: NoticeCenter,
    mutationScope: CoroutineScope,
    onPlayRecording: (at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection, at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart) -> Unit,
    onClose: () -> Unit,
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
) {
    PlayerDetailsSurface {
        at.bernhardberger.tvhplayer.ui.screens.RecordingDetailsHost(
            recording, openingObservation, observation, currentObservation, nowSec,
            mutations, notices, mutationScope,
            onPlayRecording = onPlayRecording, onClose = onClose, onContinueWatching = onClose,
            onShowGuide = onShowGuide,
        )
    }
}
