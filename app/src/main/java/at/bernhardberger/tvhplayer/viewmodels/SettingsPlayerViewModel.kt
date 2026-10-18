package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.StartupBufferPolicy
import at.bernhardberger.tvhplayer.playback.startupBufferIdentity
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsPlayerUiState(
    val autoStartPlayback: Boolean = false,
    val audioLanguages: List<String> = emptyList(),
    val audioFormat: AudioFormatPreference = AudioFormatPreference.AUTOMATIC,
    val audioDescription: Boolean = false,
    val subtitleLanguage: String? = null,
    val connected: Boolean = false,
    val profiles: StreamProfilesResult = StreamProfilesResult.Failed(TvheadendFailure.NOT_READY),
    val selectedProfileId: StreamProfileId? = null,
    val timeshiftEnabled: Boolean = true,
    val frameRateSwitching: FrameRateSwitching = FrameRateSwitching.ALWAYS,
    val tunnelingEnabled: Boolean = false,
    val audioPassthroughEnabled: Boolean = true,
    val audioPassthroughChangeFailed: Boolean = false,
    val keepChannelMinutes: Int = 20,
    val startupBufferMillis: Int = STARTUP_BUFFER_AUTOMATIC,
    /** Level Automatic currently uses for this server. */
    val startupBufferLearnedMillis: Int = 500,
)

class SettingsPlayerViewModel(
    private val settingsStore: PlayerSettingsStore,
    private val playbackRuntime: AppPlaybackRuntime,
    session: TvheadendSession,
    private val profileOwner: AppProfileOwner,
    private val uiSettingsStore: UiSettingsStore,
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
            frameRateSwitching = settings.frameRateSwitching,
            tunnelingEnabled = settings.tunnelingEnabled,
            audioPassthroughEnabled = settings.audioPassthroughEnabled,
            audioPassthroughChangeFailed = audioPassthroughChangeFailed,
            keepChannelMinutes = settings.keepChannelMinutes,
            startupBufferMillis = settings.startupBufferMillis,
        )
    }.combine(
        combine(settingsStore.startupBufferLearning, profileOwner.startupBufferIdentity()) { learning, identity ->
            StartupBufferPolicy.learningFor(learning, identity).levelMillis
        },
    ) { state, learnedMillis ->
        state.copy(startupBufferLearnedMillis = learnedMillis)
    }.combine(uiSettingsStore.settings) { state, settings ->
        state.copy(autoStartPlayback = settings.autoStartPlayback)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsPlayerUiState())

    fun onAutoStartPlaybackChanged(enabled: Boolean) {
        viewModelScope.launch { uiSettingsStore.setAutoStartPlayback(enabled) }
    }

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

    fun onFrameRateSwitchingChanged(switching: FrameRateSwitching) {
        viewModelScope.launch {
            settingsStore.setFrameRateSwitching(switching)
            playbackRuntime.setFrameRateSwitching(switching)
        }
    }

    fun onTunnelingEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setTunnelingEnabled(enabled) }
    }

    fun onAudioPassthroughEnabledChanged(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAudioPassthroughEnabled(enabled) }
    }

    fun onKeepChannelMinutesChanged(minutes: Int) {
        viewModelScope.launch { settingsStore.setKeepChannelMinutes(minutes) }
    }

    fun onStartupBufferMillisChanged(millis: Int) {
        viewModelScope.launch { settingsStore.setStartupBufferMillis(millis) }
    }
}
