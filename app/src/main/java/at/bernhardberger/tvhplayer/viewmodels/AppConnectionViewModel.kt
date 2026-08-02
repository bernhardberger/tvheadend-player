package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import at.bernhardberger.tvhplayer.htsp.TvheadendConnection
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
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
        client.frontendState,
    ) { local, runtime -> local ?: runtime }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ConnectionUiState.Connecting,
        )

    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = combine(
        client.connectionState,
        client.metadataReady,
        client.channels,
        ::deriveCurrentChannelReadiness,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CurrentChannelReadiness.Waiting,
    )

    @Volatile
    private var lastConnection: TvheadendConnection? = null

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

                    val connection = TvheadendConnection(
                        host = server.host,
                        port = server.htspPort,
                        username = server.username,
                        password = passwordValue,
                    )
                    val previous = lastConnection
                    if (previous == connection) {
                        localState.value = null
                        return@collectLatest
                    }
                    lastConnection = connection
                    localState.value = null
                    client.connect(
                        connection = connection,
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
                connection = connection,
                preservePublishedMetadata = true,
            )
        }
    }

    fun connectOnceFromUi(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        val connection = TvheadendConnection(host, htspPort, username, password)
        lastConnection = connection
        localState.value = null
        viewModelScope.launch(Dispatchers.IO) {
            client.connect(
                connection = connection,
                reuseMatchingConnection = false,
                preservePublishedMetadata = false,
            )
        }
    }
}
