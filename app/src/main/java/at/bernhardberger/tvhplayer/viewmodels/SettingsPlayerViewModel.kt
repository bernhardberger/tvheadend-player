package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.ProfileItem
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import at.bernhardberger.tvhplayer.player.PlaybackRuntime
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
    data class Ready(val items: List<ProfileItem>) : ProfilesUiState
    data class Error(val message: String) : ProfilesUiState
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
    private val playbackRuntime: PlaybackRuntime,
    private val client: TvheadendClient,
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
            client.connectionState.collect { st ->
                _ui.value = _ui.value.copy(connected = st is ConnectionState.Connected)
            }
        }

        viewModelScope.launch {
            client.connectionState
                .map { st -> (st as? ConnectionState.Connected)?.let { it.host to it.port } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null) {
                        _ui.value = _ui.value.copy(profiles = ProfilesUiState.Idle)
                        return@collectLatest
                    }

                    _ui.value = _ui.value.copy(profiles = ProfilesUiState.Loading)

                    val result = try {
                        Result.success(client.discoverProfiles())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    _ui.value = result.fold(
                        onSuccess = { items ->
                            val savedName = settingsStore.playerSettings.first().profile

                            val matchByName = items.firstOrNull { it.name == savedName }

                            val newSelectedUuid =
                                matchByName?.id
                                    ?: _ui.value.selectedProfileUuid
                                    ?: items.firstOrNull()?.id

                            _ui.value.copy(
                                profiles = ProfilesUiState.Ready(items),
                                selectedProfileUuid = newSelectedUuid
                            )
                        },
                        onFailure = { t ->
                            _ui.value.copy(
                                profiles = ProfilesUiState.Error(t.message ?: t.toString())
                            )
                        }
                    )
                }
        }
    }

    fun onProfileSelected(profile: ProfileItem) {
        _ui.value = _ui.value.copy(selectedProfileUuid = profile.id)

        viewModelScope.launch {
            settingsStore.setProfile(profile.name)
        }
    }

    fun onTimeshiftEnabledChanged(enabled: Boolean) {
        _ui.value = _ui.value.copy(timeshiftEnabled = enabled)
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
