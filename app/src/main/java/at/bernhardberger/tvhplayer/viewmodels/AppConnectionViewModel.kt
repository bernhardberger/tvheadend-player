package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.android.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.android.TvheadendServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.StreamProfileDiscovery
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerProfileMigration
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppConnectionViewModel(
    private val session: TvheadendSession,
    private val profileStore: TvheadendServerProfileStore,
    private val profileMigration: ServerProfileMigration,
    private val serverSettings: ServerSettingsStore,
    private val playerSettings: PlayerSettingsStore,
    private val streamProfileDiscovery: StreamProfileDiscovery,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = session.observation
        .map { it.sessionState.toConnectionState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)
    private val localState = MutableStateFlow<ConnectionUiState?>(ConnectionUiState.Connecting)
    val uiState: StateFlow<ConnectionUiState> = combine(localState, session.observation) {
            local, observation ->
        local ?: observation.sessionState.toConnectionUiState()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUiState.Connecting)

    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = session.observation.map {
            observation ->
        val channelState = observation.channelState
        deriveCurrentChannelReadiness(
            connected = observation.sessionState is SessionState.Ready,
            metadataReady = channelState is ChannelRepositoryState.Current,
            channels = (channelState as? ChannelRepositoryState.Current)?.catalog?.channels.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CurrentChannelReadiness.Waiting)

    init {
        viewModelScope.launch {
            profileMigration.await()
            serverSettings.profileRevision.collectLatest {
                when (val stored = profileStore.loadProfile()) {
                is ServerProfileReadResult.Available -> {
                    localState.value = ConnectionUiState.Connecting
                    session.connect(stored.profile)
                    localState.value = null
                }
                    ServerProfileReadResult.Missing -> {
                        session.disconnect()
                        localState.value = ConnectionUiState.NeedsConfiguration
                    }
                    ServerProfileReadResult.Unavailable -> {
                        session.disconnect()
                        localState.value = ConnectionUiState.CredentialUnavailable
                    }
                }
            }
        }
        viewModelScope.launch {
            collectReadyStreamProfileMigrations(
                observations = session.observation,
                currentObservation = { session.observation.value },
                discover = streamProfileDiscovery::discover,
                migrate = playerSettings::migrateLegacyProfileSelection,
            )
        }
    }

    fun reconnectNow() {
        viewModelScope.launch {
            if (session.observation.value.sessionState is SessionState.Unavailable) session.retry()
        }
    }
}
