package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.RetainedMetadataAuthority
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.channelCatalogAuthority
import at.bernhardberger.tvheadend.sdk.core.channelCatalogForDisplay
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.toConnectionUiState
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppConnectionViewModel(
    private val session: TvheadendSession,
    profileOwner: AppProfileOwner,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = session.observation
        .map { it.sessionState.toConnectionState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)
    val uiState: StateFlow<ConnectionUiState> = combine(
        profileOwner.serverProfile,
        session.observation,
    ) { profile, observation ->
        when (profile) {
            null -> ConnectionUiState.Connecting
            ServerProfileReadResult.Missing -> ConnectionUiState.NeedsConfiguration
            ServerProfileReadResult.Unavailable -> ConnectionUiState.CredentialUnavailable
            is ServerProfileReadResult.Available -> observation.sessionState.toConnectionUiState()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUiState.Connecting)

    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = session.observation.map {
            observation ->
        val metadataReady = observation.channelCatalogAuthority == RetainedMetadataAuthority.CURRENT
        deriveCurrentChannelReadiness(
            connected = observation.sessionState is SessionState.Ready,
            metadataReady = metadataReady,
            channels = if (metadataReady) {
                observation.channelCatalogForDisplay?.channels.orEmpty()
            } else {
                emptyList()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CurrentChannelReadiness.Waiting)

    fun reconnectNow() {
        viewModelScope.launch {
            val state = session.observation.value.sessionState
            if (
                state is SessionState.Unavailable &&
                state.reason.recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY
            ) {
                session.retry()
            }
        }
    }
}
