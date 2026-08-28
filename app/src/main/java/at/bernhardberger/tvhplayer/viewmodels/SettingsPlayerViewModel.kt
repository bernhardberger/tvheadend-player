package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsPlayerUiState(
    val connected: Boolean = false,
    val profiles: StreamProfilesResult = StreamProfilesResult.NotReady,
    val selectedProfileId: StreamProfileId? = null,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
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
    ) { settings, observation, profiles, selectedProfileId ->
        SettingsPlayerUiState(
            connected = observation.currentSession != null,
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            timeshiftEnabled = settings.timeshiftEnabled,
            refreshRateMatchingEnabled = settings.refreshRateMatchingEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsPlayerUiState())

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
}
