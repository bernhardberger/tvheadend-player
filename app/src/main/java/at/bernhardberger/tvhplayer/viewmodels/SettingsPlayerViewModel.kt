package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.core.StreamProfileSelectionOption
import at.bernhardberger.tvhplayer.core.selectedStreamProfileUuid
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfilesUiState {
    data object Idle : ProfilesUiState
    data object Loading : ProfilesUiState
    data class Ready(val items: List<StreamProfileSelectionOption>) : ProfilesUiState
    data object Error : ProfilesUiState
}

data class SettingsPlayerUiState(
    val connected: Boolean = false,
    val profiles: ProfilesUiState = ProfilesUiState.Idle,
    val selectedProfileUuid: String? = null,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
)

class SettingsPlayerViewModel(
    private val settingsStore: PlayerSettingsStore,
    private val playbackRuntime: AppPlaybackRuntime,
    private val session: TvheadendSession,
    private val streamProfileDiscovery: StreamProfileDiscovery,
) : ViewModel() {


    private val _ui = MutableStateFlow(SettingsPlayerUiState())
    val ui: StateFlow<SettingsPlayerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.playerSettings
                .map { it.timeshiftEnabled to it.refreshRateMatchingEnabled }
                .distinctUntilChanged()
                .collect { (timeshiftEnabled, refreshRateMatchingEnabled) ->
                    _ui.update {
                        it.copy(
                            timeshiftEnabled = timeshiftEnabled,
                            refreshRateMatchingEnabled = refreshRateMatchingEnabled,
                        )
                    }
                }
        }

        viewModelScope.launch {
            session.observation.collect { observation ->
                _ui.update { it.copy(connected = observation.currentSession != null) }
            }
        }

        viewModelScope.launch {
            session.observation
                .map { it.currentSession }
                .distinctUntilChanged()
                .collectLatest { currentSession ->
                    if (currentSession == null) {
                        _ui.update { it.copy(profiles = ProfilesUiState.Idle) }
                        return@collectLatest
                    }

                    _ui.update { it.copy(profiles = ProfilesUiState.Loading) }

                    val result = try {
                        when (val profiles = streamProfileDiscovery.discover(currentSession)) {
                            is StreamProfilesResult.Available -> Result.success(profiles.profiles)
                            else -> Result.failure(IllegalStateException(profiles::class.simpleName))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    result.fold(
                        onSuccess = { items ->
                            if (session.observation.value.currentSession !== currentSession) {
                                return@fold
                            }
                            val discoveredProfiles = items.map { profile ->
                                StreamProfileSelectionOption(
                                    id = profile.id.value,
                                    name = profile.name,
                                )
                            }
                            settingsStore.migrateLegacyProfileSelection(discoveredProfiles)
                            val savedSelection = settingsStore.playerSettings.first()
                            _ui.update { current ->
                                current.copy(
                                    profiles = ProfilesUiState.Ready(discoveredProfiles),
                                    selectedProfileUuid = selectedStreamProfileUuid(
                                        persistedUuid = savedSelection.profile,
                                        legacyName = savedSelection.legacyProfileName,
                                        currentUuid = current.selectedProfileUuid,
                                        discoveredProfiles = discoveredProfiles,
                                    ),
                                )
                            }
                        },
                        onFailure = { t ->
                            _ui.update {
                                it.copy(profiles = profileDiscoveryFailureState(t))
                            }
                        }
                    )
                }
        }
    }

    fun onProfileSelected(profile: StreamProfileSelectionOption) {
        _ui.update { it.copy(selectedProfileUuid = profile.id) }

        viewModelScope.launch {
            settingsStore.setProfile(
                profileUuid = profile.id,
                legacyProfileName = profile.name,
            )
        }
    }

    fun onTimeshiftEnabledChanged(enabled: Boolean) {
        _ui.update { it.copy(timeshiftEnabled = enabled) }
        viewModelScope.launch {
            settingsStore.setTimeshiftEnabled(enabled)
        }
    }

    fun onRefreshRateMatchingEnabledChanged(enabled: Boolean) {
        _ui.update { it.copy(refreshRateMatchingEnabled = enabled) }
        viewModelScope.launch {
            settingsStore.setRefreshRateMatchingEnabled(enabled)
            playbackRuntime.setRefreshRateMatchingEnabled(enabled)
        }
    }
}
