package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsPlayerUiState(
    val audioLanguages: List<String> = emptyList(),
    val audioFormat: AudioFormatPreference = AudioFormatPreference.AUTOMATIC,
    val audioDescription: Boolean = false,
    val subtitleLanguage: String? = null,
    val connected: Boolean = false,
    val profiles: StreamProfilesResult = StreamProfilesResult.NotReady,
    val selectedProfileId: StreamProfileId? = null,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
    val audioPassthroughEnabled: Boolean = true,
    val audioPassthroughChangeFailed: Boolean = false,
)

class SettingsPlayerViewModel(
    private val settingsStore: PlayerSettingsStore,
    private val playbackRuntime: AppPlaybackRuntime,
    session: TvheadendSession,
    private val profileOwner: AppProfileOwner,
) : ViewModel() {
    val ui = combine(
        settingsStore.playerSettings,
        session.observation,
        profileOwner.streamProfiles,
        profileOwner.selectedStreamProfileId,
        playbackRuntime.audioPassthroughChangeFailed,
    ) { settings, observation, profiles, selectedProfileId, audioPassthroughChangeFailed ->
        SettingsPlayerUiState(
            audioLanguages = settings.audioLanguages,
            audioFormat = settings.audioFormat,
            audioDescription = settings.audioDescription,
            subtitleLanguage = settings.subtitleLanguage,
            connected = observation.currentSession != null,
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            timeshiftEnabled = settings.timeshiftEnabled,
            refreshRateMatchingEnabled = settings.refreshRateMatchingEnabled,
            audioPassthroughEnabled = settings.audioPassthroughEnabled,
            audioPassthroughChangeFailed = audioPassthroughChangeFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsPlayerUiState())

    fun onAudioLanguageSelected(slot: Int, language: String?) {
        viewModelScope.launch { settingsStore.setAudioLanguageSlot(slot, language) }
    }

    fun onAudioFormatSelected(format: AudioFormatPreference) {
        viewModelScope.launch { settingsStore.setAudioFormat(format) }
    }

    fun onAudioDescriptionChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAudioDescription(enabled) }
    }

    fun onSubtitleLanguageSelected(language: String?) {
        viewModelScope.launch { settingsStore.setSubtitleLanguage(language) }
    }

    fun onProfileSelected(profileId: StreamProfileId?) {
        viewModelScope.launch {
            profileOwner.selectStreamProfile(profileId)
        }
    }

    fun onTimeshiftEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setTimeshiftEnabled(enabled)
        }
    }

    fun onRefreshRateMatchingEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setRefreshRateMatchingEnabled(enabled)
            playbackRuntime.setRefreshRateMatchingEnabled(enabled)
        }
    }

    fun onAudioPassthroughEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAudioPassthroughEnabled(enabled) }
    }
}
