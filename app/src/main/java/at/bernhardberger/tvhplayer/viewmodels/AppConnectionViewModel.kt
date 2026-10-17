package at.bernhardberger.tvhplayer.viewmodels

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
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
import at.bernhardberger.tvhplayer.core.isDegraded
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connection and channel readiness come from one atomic SDK observation. */
data class AppConnectionPresentation(
    val connection: ConnectionUiState = ConnectionUiState.Connecting,
    val currentChannelReadiness: CurrentChannelReadiness = CurrentChannelReadiness.Waiting,
    val degraded: Boolean = false,
)

class AppConnectionViewModel(
    private val session: TvheadendSession,
    profileOwner: AppProfileOwner,
    connectionNotices: ConnectionNoticeSource,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = session.observation
        .map { it.sessionState.toConnectionState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)
    val uiState: StateFlow<AppConnectionPresentation> = combine(
        profileOwner.serverProfile,
        session.observation,
        connectionNotices.lossAnnounced,
    ) { profile, observation, lossAnnounced ->
        val connection = when (profile) {
            null -> ConnectionUiState.Connecting
            ServerProfileReadResult.Missing -> ConnectionUiState.NeedsConfiguration
            ServerProfileReadResult.Unavailable -> ConnectionUiState.CredentialUnavailable
            is ServerProfileReadResult.Available -> observation.sessionState.toConnectionUiState()
        }
        AppConnectionPresentation(
            connection = connection,
            degraded = profile != null && connection.isDegraded(lossAnnounced),
            currentChannelReadiness = deriveCurrentChannelReadiness(
                connected = observation.sessionState is SessionState.Ready,
                authority = observation.channelCatalogAuthority,
                channels = observation.channelCatalogForDisplay?.channels.orEmpty(),
            ),
        )
    }.scan(AppConnectionPresentation()) { previous, next ->
        // A failure keeps its warning through the automatic attempts after it, until one succeeds.
        next.copy(
            degraded = next.degraded ||
                next.connection.isDegraded(lossAnnounced = false, degradedBefore = previous.degraded),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppConnectionPresentation())

    init {
        if (at.bernhardberger.tvhplayer.client.BuildConfig.PROFILE_TRACE) {
            // Profiling builds only: one trace marker per startup stage change, for cold-start breakdowns.
            viewModelScope.launch {
                combine(profileOwner.serverProfile, session.observation) { profile, observation ->
                    val profileStage = when (profile) {
                        null -> "loading"
                        ServerProfileReadResult.Missing -> "missing"
                        ServerProfileReadResult.Unavailable -> "unavailable"
                        is ServerProfileReadResult.Available -> "available"
                    }
                    val sessionStage = when (observation.sessionState) {
                        SessionState.Disconnected -> "disconnected"
                        SessionState.Connecting -> "connecting"
                        SessionState.Synchronizing -> "synchronizing"
                        is SessionState.Ready -> "ready"
                        is SessionState.Unavailable -> "unavailable"
                    }
                    val epgStage = when (observation.epgState) {
                        EpgRepositoryState.Empty -> "empty"
                        is EpgRepositoryState.Synchronizing -> "synchronizing"
                        is EpgRepositoryState.Current -> "current"
                        is EpgRepositoryState.Stale -> "stale"
                    }
                    // Trace section names are limited to 127 characters.
                    "P51:startup:$profileStage:$sessionStage" +
                        ":catalog:${observation.channelCatalogAuthority.ordinal}" +
                        ":channels:${observation.channelCatalogForDisplay?.channels?.size ?: 0}" +
                        ":events:${observation.epgSnapshotForDisplay?.events?.size ?: 0}" +
                        ":epg:$epgStage"
                }.distinctUntilChanged().collect { stage ->
                    profileTrace("$stage:uptime:${SystemClock.uptimeMillis()}") { }
                }
            }
        }
    }

    fun reconnectNow(allowAutomaticBackoff: Boolean = false) {
        viewModelScope.launch {
            val state = session.observation.value.sessionState
            if (
                state is SessionState.Unavailable &&
                (state.reason.recoveryDisposition == SessionRecoveryDisposition.EXPLICIT_RETRY ||
                    allowAutomaticBackoff && state.reason.recoveryDisposition == SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
            ) {
                session.retry()
            }
        }
    }
}
