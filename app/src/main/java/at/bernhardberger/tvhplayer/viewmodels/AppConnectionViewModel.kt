package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvheadend.client.ConnectionState
import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvheadend.client.TvheadendConnection
import at.bernhardberger.tvheadend.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
import at.bernhardberger.tvhplayer.settings.ServerConnectionConfiguration
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.StoredPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppConnectionViewModel(
    private val client: TvheadendClient,
    private val settings: ServerSettingsStore,
    private val passwords: SecurePasswordStore,
) : ViewModel() {
    val connectionState = client.connectionState

    private val localState = MutableStateFlow<ConnectionUiState?>(null)
    val uiState: StateFlow<ConnectionUiState> = combine(
        localState,
        client.clientState,
    ) { local, runtime -> local ?: runtime.toConnectionUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConnectionUiState.Connecting,
        )

    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = combine(
        client.connectionState,
        client.metadataReady,
        client.channels,
    ) { connectionState, metadataReady, channels ->
        deriveCurrentChannelReadiness(
            connected = connectionState is ConnectionState.Connected,
            metadataReady = metadataReady,
            channels = channels,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CurrentChannelReadiness.Waiting,
    )

    @Volatile
    private var lastConnection: ServerConnectionConfiguration? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                settings.serverSettings,
                passwords.passwordState,
            ) { server, password -> server to password }
                .collectLatest { (server, password) ->
                    if (!ConnectionPolicy.isAutoConnectReady(server.host, server.htspPort)) {
                        lastConnection = null
                        client.invalidateConnection(preservePublishedMetadata = true)
                        localState.value = ConnectionUiState.NeedsConfiguration
                        return@collectLatest
                    }

                    val passwordValue = when (password) {
                        StoredPassword.Empty -> ""
                        is StoredPassword.Available -> password.value
                        StoredPassword.Unavailable -> {
                            lastConnection = null
                            client.invalidateConnection(preservePublishedMetadata = true)
                            localState.value = ConnectionUiState.CredentialUnavailable
                            return@collectLatest
                        }
                    }

                    val connection = ServerConnectionConfiguration(
                        host = server.host,
                        htspPort = server.htspPort,
                        username = server.username,
                        password = passwordValue,
                    )
                    val previous = lastConnection
                    if (!connectionRequiresReplacement(previous, connection)) {
                        localState.value = null
                        return@collectLatest
                    }
                    lastConnection = connection
                    localState.value = null
                    client.connect(
                        connection = connection.toPredecessorConnection(),
                        reuseMatchingConnection = previous == null,
                        preservePublishedMetadata = previous == null,
                    )
                }
        }
    }

    fun reconnectNow() {
        val connection = lastConnection ?: return
        viewModelScope.launch(Dispatchers.IO) {
            client.reconnect(
                connection = connection.toPredecessorConnection(),
                preservePublishedMetadata = true,
            )
        }
    }

    private fun ServerConnectionConfiguration.toPredecessorConnection() = TvheadendConnection(
        host = host,
        port = htspPort,
        username = username,
        password = password,
    )
}
