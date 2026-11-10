package at.bernhardberger.tvhplayer.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.screens.ChannelsGroup

internal fun homeInput(
    channels: List<Channel>,
    now: Long,
    currentEvents: Map<ChannelId, EpgEvent> = emptyMap(),
    upcomingEvents: List<EpgEvent> = emptyList(),
    recentChannelIds: List<ChannelId> = emptyList(),
    dvrEntries: List<DvrEntry> = emptyList(),
    admission: (DvrEntry) -> RecordingPlaybackAdmission? = { null },
    playing: HomePlaying? = null,
    hiddenSections: Set<HomeSectionKind> = emptySet(),
) = HomeInput(channels, currentEvents, upcomingEvents, recentChannelIds, dvrEntries, admission, playing, now, hiddenSections)

@Composable
internal fun homeScreen(
    input: HomeInput,
    observation: SessionObservation = SessionObservation.create(),
    enabled: Boolean = true,
    onPlay: (ChannelId, String) -> Unit = { _, _ -> },
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit = { _, _ -> },
    onReturnToRecording: (DvrEntryId, () -> Unit) -> Boolean = { _, _ -> false },
    onShowGuide: (ChannelId, EpgEvent?) -> Unit = { _, _ -> },
    onGenre: (ProgrammeCategory) -> Unit = {},
    onRecordings: (DvrLibraryMode, DvrEntryId?) -> Unit = { _, _ -> },
    connectionUiState: ConnectionUiState = ConnectionUiState.Ready,
    onOpenConnectionSettings: () -> Unit = {},
    onBrowseChannels: () -> Unit = {},
    onWatchRecordingChannel: (ChannelId, String) -> Unit = onPlay,
    groups: List<ChannelsGroup> = emptyList(),
    activeTagId: ChannelTagId? = null,
    onSelectTag: (ChannelTagId?) -> Unit = {},
    onSectionVisibleChange: (HomeSectionKind, Boolean) -> Unit = { _, _ -> },
    catalogCurrent: Boolean = observation.channelState.channelCatalogAuthority == RetainedMetadataAuthority.CURRENT,
    focusMemory: MutableState<HomeFocusKey?> = remember { mutableStateOf(null) },
) {
    HomeScreenContent(input, observation, PaddingValues(), enabled,
        onPlay = onPlay, onPlayRecording = onPlayRecording, onReturnToRecording = onReturnToRecording,
        onShowGuide = onShowGuide, onGenre = onGenre, onRecordings = onRecordings,
        connectionUiState = connectionUiState, onOpenConnectionSettings = onOpenConnectionSettings,
        onBrowseChannels = onBrowseChannels, onWatchRecordingChannel = onWatchRecordingChannel,
        groups = groups, activeTagId = activeTagId, onSelectTag = onSelectTag,
        onSectionVisibleChange = onSectionVisibleChange, catalogCurrent = catalogCurrent, focusMemory = focusMemory)
}
