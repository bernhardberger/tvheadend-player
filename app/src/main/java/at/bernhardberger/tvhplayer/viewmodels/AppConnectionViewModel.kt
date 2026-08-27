package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.android.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.data.TvheadendDataRuntime
import at.bernhardberger.tvhplayer.settings.ServerProfileMigration
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.core.StreamProfileSelectionOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppConnectionViewModel(
    private val runtime: TvheadendDataRuntime,
    private val profileStore: TvheadendServerProfileStore,
    private val profileMigration: ServerProfileMigration,
    private val serverSettings: ServerSettingsStore,
    private val playerSettings: PlayerSettingsStore,
) : ViewModel() {
    val connectionState = runtime.connectionState
    private val localState = MutableStateFlow<ConnectionUiState?>(ConnectionUiState.Connecting)
    val uiState: StateFlow<ConnectionUiState> = combine(localState, runtime.session.state) { local, state ->
        local ?: state.toConnectionUiState()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUiState.Connecting)

    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = combine(
        runtime.connectionState,
        runtime.metadataReady,
        runtime.channels,
    ) { connection, metadataReady, channels ->
        deriveCurrentChannelReadiness(
            connected = connection == ConnectionState.Connected,
            metadataReady = metadataReady,
            channels = channels,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CurrentChannelReadiness.Waiting)

    init {
        viewModelScope.launch {
            profileMigration.await()
            serverSettings.profileRevision.collectLatest {
                when (val stored = profileStore.loadProfile()) {
                is ServerProfileReadResult.Available -> {
                    localState.value = ConnectionUiState.Connecting
                    runtime.session.connect(stored.profile)
                    localState.value = null
                }
                    ServerProfileReadResult.Missing -> {
                        runtime.session.disconnect()
                        localState.value = ConnectionUiState.NeedsConfiguration
                    }
                    ServerProfileReadResult.Unavailable -> {
                        runtime.session.disconnect()
                        localState.value = ConnectionUiState.CredentialUnavailable
                    }
                }
            }
        }
        viewModelScope.launch {
            runtime.session.state.filterIsInstance<SessionState.Ready>().collectLatest {
                val profiles = runtime.session.getStreamProfiles()
                if (profiles is StreamProfilesResult.Available) {
                    playerSettings.migrateLegacyProfileSelection(
                        profiles.profiles.map { profile ->
                            StreamProfileSelectionOption(profile.id.value, profile.name)
                        },
                    )
                }
            }
        }
    }

    fun reconnectNow() {
        viewModelScope.launch {
            if (runtime.session.state.value is SessionState.Unavailable) runtime.session.retry()
        }
    }
}
