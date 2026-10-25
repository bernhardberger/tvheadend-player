package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.input.key.*
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
internal fun PlayerDetailsSurface(onBackKeyDown: () -> Unit = {}, content: @Composable () -> Unit) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { key ->
        if (key.key != Key.Back) false else {
            if (key.type == KeyEventType.KeyDown && key.nativeKeyEvent.repeatCount == 0) {
                onBackKeyDown()
                dispatcher?.onBackPressed()
            }
            true
        }
    }) {
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
    onShowGuide: (ChannelId, EpgEvent?) -> Unit,
    onClose: () -> Unit,
    onBackKeyDown: () -> Unit = {},
    fromPlayer: Boolean = true,
) {
    // Freeze the watched-position programme at entry; the host refreshes its data by ID.
    val openingEvent = remember { event }
    val openingObservation = remember { observation }
    val latestNow = rememberUpdatedState(nowSec)
    val inFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
    PlayerDetailsSurface(onBackKeyDown) {
        if (openingEvent != null) {
            ProgrammeDetailsHost(
                openingEvent = openingEvent,
                openingObservation = openingObservation,
                observation = observation,
                currentObservation = currentObservation,
                nowSecProvider = { latestNow.value },
                fromGuide = false,
                showContextMenu = false,
                onShowDetails = {},
                mutations = mutations,
                notices = notices,
                mutationScope = mutationScope,
                mutationInFlight = inFlight,
                onPlay = onPlay,
                onPlayRecording = null,
                onShowGuide = onShowGuide,
                onClose = onClose,
                settingsStore = settingsStore,
                fromPlayer = fromPlayer,
                retainPlayerSnapshot = true,
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
    channel: Channel?,
    nowSec: Long,
    onClose: () -> Unit,
    onBackKeyDown: () -> Unit = {},
) {
    PlayerDetailsSurface(onBackKeyDown) {
        ProgrammeDetailsPage(null, recording, channel, nowSec,
            actions = recordingDetailsActions(recording, null, false, false, fromPlayer = true),
            onAction = { onClose() }, onClose = onClose)
    }
}
