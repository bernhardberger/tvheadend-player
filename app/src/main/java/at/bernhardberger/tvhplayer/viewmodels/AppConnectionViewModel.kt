package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.core.ConnectionAttemptState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.ReconnectAttemptPhase
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind
import at.bernhardberger.tvhplayer.core.SubscriptionFailureTrackerState
import at.bernhardberger.tvhplayer.core.beginConnectionAttempt
import at.bernhardberger.tvhplayer.core.beginReconnectAttemptPhase
import at.bernhardberger.tvhplayer.core.completeReconnectAttemptPhase
import at.bernhardberger.tvhplayer.core.connectionAttemptMayPublish
import at.bernhardberger.tvhplayer.core.connectionAttemptState
import at.bernhardberger.tvhplayer.core.connectionFailureKind
import at.bernhardberger.tvhplayer.core.deriveCurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.invalidateConnectionAttempts
import at.bernhardberger.tvhplayer.core.invalidateReconnectAttemptPhase
import at.bernhardberger.tvhplayer.core.shouldRestartConnectionRetry
import at.bernhardberger.tvhplayer.core.updateSubscriptionFailure
import at.bernhardberger.tvhplayer.core.removeSubscriptionFailure
import at.bernhardberger.tvhplayer.htsp.HtspEvent
import at.bernhardberger.tvhplayer.htsp.HtspMessage
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.htsp.SubscriptionStatus
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.settings.SecurePasswordStore
import at.bernhardberger.tvhplayer.settings.ServerSettingsStore
import at.bernhardberger.tvhplayer.settings.StoredPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class AppConnectionViewModel(
    private val htsp: HtspService,
    private val repo: TvhRepository,
    private val dvrRepository: DvrRepository,
    private val settings: ServerSettingsStore,
    private val passwords: SecurePasswordStore,
) : ViewModel() {

    val connectionState = htsp.state
    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Connecting)
    val uiState = _uiState.asStateFlow()
    val currentChannelReadiness: StateFlow<CurrentChannelReadiness> = combine(
        htsp.state,
        repo.metadataReady,
        repo.channelsUi,
        ::deriveCurrentChannelReadiness,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CurrentChannelReadiness.Waiting,
    )

    private data class ServerCfg(
        val host: String,
        val htspPort: Int,
        val username: String,
        val password: String
    )

    @Volatile
    private var lastCfg: ServerCfg? = null
    private var reconnectJob: Job? = null
    private var reconnectAttemptPhase = ReconnectAttemptPhase()
    private var autoJob: Job? = null
    private val connectionAttemptLock = Any()
    private var connectionAttempts = ConnectionAttemptState()

    private val subscriptionStatusLock = Any()
    private var subscriptionFailureState = SubscriptionFailureTrackerState()

    init {
        repo.startIfNeeded()

        autoJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                settings.serverSettings,
                passwords.passwordState,
            ) { server, password -> server to password }
                .collectLatest { (server, password) ->
                    if (!ConnectionPolicy.isAutoConnectReady(server.host, server.htspPort)) {
                        invalidateReconnectAttempts()
                        reconnectJob?.cancel()
                        reconnectJob = null
                        _uiState.value = ConnectionUiState.NeedsConfiguration
                        return@collectLatest
                    }

                    val value = when (password) {
                        StoredPassword.Empty -> ""
                        is StoredPassword.Available -> password.value
                        StoredPassword.Unavailable -> {
                            lastCfg = null
                            invalidateReconnectAttempts()
                            reconnectJob?.cancel()
                            reconnectJob = null
                            _uiState.value = ConnectionUiState.CredentialUnavailable
                            return@collectLatest
                        }
                    }

                    val cfg = ServerCfg(
                        host = server.host,
                        htspPort = server.htspPort,
                        username = server.username,
                        password = value,
                    )
                    val previousCfg = lastCfg
                    if (previousCfg == cfg) return@collectLatest
                    lastCfg = cfg
                    startOrRestartReconnectLoop(
                        reuseMatchingConnection = previousCfg == null,
                        preservePublishedChannels = previousCfg == null,
                    )
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            htsp.controlEvents.collectLatest { e ->
                when (e) {
                    is HtspEvent.ConnectionError -> {
                        if (!repo.onDisconnected(e.connectionAttemptId)) {
                            return@collectLatest
                        }
                        htsp.commitIfCurrentConnectionAttempt(e.connectionAttemptId) {
                            _uiState.value = ConnectionUiState.Reconnecting
                            startOrRestartReconnectLoop(
                                reuseMatchingConnection = false,
                                preservePublishedChannels = true,
                            )
                        }
                    }

                    is HtspEvent.ServerMessage -> {
                        val msg = e.msg

                        msg.toSubStatusOrNull()?.let { st ->
                            htsp.commitIfCurrentConnectionAttempt(e.connectionAttemptId) {
                                updateSubscriptionStatus(st)
                            }
                            return@collectLatest
                        }

                        msg.subStopIdOrNull()?.let { id ->
                            htsp.commitIfCurrentConnectionAttempt(e.connectionAttemptId) {
                                removeSubscriptionStatus(id)
                            }
                            return@collectLatest
                        }
                    }
                }
            }
        }
    }

    private fun publishSubsStatus(failure: SubscriptionFailureKind?) {
        if (failure == null) {
            if (_uiState.value is ConnectionUiState.SubscriptionError) {
                _uiState.value = ConnectionUiState.Ready
            }
        } else {
            _uiState.value = ConnectionUiState.SubscriptionError(failure)
        }
    }

    private fun updateSubscriptionStatus(status: SubscriptionStatus) {
        val failure = synchronized(subscriptionStatusLock) {
            subscriptionFailureState = updateSubscriptionFailure(
                state = subscriptionFailureState,
                subscriptionId = status.id,
                subscriptionError = status.subscriptionError,
                status = status.state,
            )
            subscriptionFailureState.currentFailure
        }
        publishSubsStatus(failure)
    }

    private fun removeSubscriptionStatus(id: Int) {
        val failure = synchronized(subscriptionStatusLock) {
            subscriptionFailureState = removeSubscriptionFailure(
                subscriptionFailureState,
                subscriptionId = id,
            )
            subscriptionFailureState.currentFailure
        }
        publishSubsStatus(failure)
    }

    private fun HtspMessage.toSubStatusOrNull(): SubscriptionStatus? {
        val m = method ?: return null
        if (m != "subscriptionStatus" && m != "subscriptionStart") return null

        val id = int("subscriptionId") ?: int("id") ?: return null

        return SubscriptionStatus(
            id = id,
            state = str("state") ?: str("status"),
            subscriptionError = str("subscriptionError") ?: str("error")
        )
    }

    private fun HtspMessage.subStopIdOrNull(): Int? {
        val m = method ?: return null
        if (m != "subscriptionStop") return null
        return int("subscriptionId") ?: int("id")
    }

    @Synchronized
    private fun startOrRestartReconnectLoop(
        reuseMatchingConnection: Boolean,
        preservePublishedChannels: Boolean,
        coalesceWhileConnecting: Boolean = false,
    ) {
        if (
            coalesceWhileConnecting &&
            !shouldRestartConnectionRetry(
                reconnectJobActive = reconnectJob?.isActive == true,
                connectionIsConnecting = reconnectAttemptPhase.inFlightAttemptId != null,
            )
        ) return
        val attemptId = beginReconnectAttempt()
        synchronized(subscriptionStatusLock) {
            subscriptionFailureState = SubscriptionFailureTrackerState()
        }
        reconnectAttemptPhase = beginReconnectAttemptPhase(reconnectAttemptPhase, attemptId)
        reconnectJob?.cancel()
        publishConnectionState(
            attemptId,
            connectionAttemptState(
                hasPublishedChannels =
                    preservePublishedChannels && repo.channelsUi.value.isNotEmpty(),
            ),
        )

        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            var mayReuseConnection = reuseMatchingConnection
            var firstAttempt = true
            while (true) {
                if (!firstAttempt && !markReconnectAttemptInFlight(attemptId)) return@launch
                firstAttempt = false
                val cfg = lastCfg ?: run {
                    clearReconnectAttemptInFlight(attemptId)
                    return@launch
                }

                val ok = try {
                    connectInternal(
                        attemptId = attemptId,
                        host = cfg.host,
                        port = cfg.htspPort,
                        username = cfg.username,
                        password = cfg.password,
                        reuseMatchingConnection = mayReuseConnection,
                        preservePublishedChannels = preservePublishedChannels,
                    )
                } finally {
                    clearReconnectAttemptInFlight(attemptId)
                }
                if (ok) return@launch

                mayReuseConnection = false
                delay(5_000)
            }
        }
    }

    fun reconnectNow() = startOrRestartReconnectLoop(
        reuseMatchingConnection = false,
        preservePublishedChannels = true,
        coalesceWhileConnecting = true,
    )

    fun connectOnceFromUi(
        host: String,
        htspPort: Int,
        username: String,
        password: String
    ) {
        lastCfg = ServerCfg(host, htspPort, username, password)
        startOrRestartReconnectLoop(
            reuseMatchingConnection = false,
            preservePublishedChannels = false,
        )
    }

    private suspend fun connectInternal(
        attemptId: Long,
        host: String,
        port: Int,
        username: String,
        password: String,
        reuseMatchingConnection: Boolean,
        preservePublishedChannels: Boolean,
    ): Boolean {
        return try {
            ensureCurrentConnectionAttempt(attemptId)
            val connected = htsp.state.value as? ConnectionState.Connected
            if (reuseMatchingConnection && ConnectionPolicy.isSameEndpoint(
                    connectedHost = connected?.host,
                    connectedPort = connected?.port,
                    requestedHost = host,
                    requestedPort = port,
                )
            ) {
                publishConnectionState(attemptId, ConnectionUiState.Ready)
                return true
            }

            ensureCurrentConnectionAttempt(attemptId)
            repo.onNewConnectionStarting(
                preservePublishedChannels = preservePublishedChannels,
                attemptId = attemptId,
            )
            ensureCurrentConnectionAttempt(attemptId)
            dvrRepository.onNewConnectionStarting(
                preservePublished = preservePublishedChannels,
                attemptId = attemptId,
            )
            ensureCurrentConnectionAttempt(attemptId)

            htsp.connect(
                host = host,
                port = port,
                username = username,
                password = password,
                forceReconnect = true,
                connectTimeoutMs = 10_000,
                responseTimeoutMs = 5_000
            )
            ensureCurrentConnectionAttempt(attemptId)
            val connectedAfterAuth = htsp.state.value as? ConnectionState.Connected
            dvrRepository.applyAuthenticatedDvrAccess(
                dvrAccess = connectedAfterAuth?.dvrAccess,
                attemptId = attemptId,
            )

            publishConnectionState(attemptId, ConnectionUiState.SyncingChannels)
            htsp.enableAsyncMetadataAndWaitInitialSync()
            ensureCurrentConnectionAttempt(attemptId)

            repo.awaitChannelsReady()
            ensureCurrentConnectionAttempt(attemptId)
            // Transport failure keeps Unknown/Denied; never optimistically Allowed.
            try {
                dvrRepository.refreshConfigs(attemptId = attemptId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "DVR configurations unavailable")
            }
            ensureCurrentConnectionAttempt(attemptId)
            runForCurrentConnectionAttempt(attemptId) {
                repo.startEpgWorker()
                _uiState.value = ConnectionUiState.Ready
            }

            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ensureCurrentConnectionAttempt(attemptId)
            Timber.e(e, "Connect failed")
            publishConnectionState(
                attemptId,
                ConnectionUiState.Error(connectionFailureKind(e)),
            )
            false
        }
    }

    private fun beginReconnectAttempt(): Long = synchronized(connectionAttemptLock) {
        beginConnectionAttempt(connectionAttempts).also {
            connectionAttempts = it.state
        }.attemptId.also(::advanceRepositoryConnectionAttempt)
    }

    private fun invalidateReconnectAttempts() {
        val attemptId = synchronized(connectionAttemptLock) {
            connectionAttempts = invalidateConnectionAttempts(connectionAttempts)
            connectionAttempts.currentAttemptId
        }
        synchronized(this) {
            reconnectAttemptPhase = invalidateReconnectAttemptPhase(reconnectAttemptPhase)
        }
        synchronized(subscriptionStatusLock) {
            subscriptionFailureState = SubscriptionFailureTrackerState()
        }
        advanceRepositoryConnectionAttempt(attemptId)
    }

    @Synchronized
    private fun markReconnectAttemptInFlight(attemptId: Long): Boolean {
        if (!isCurrentConnectionAttempt(attemptId)) return false
        reconnectAttemptPhase = beginReconnectAttemptPhase(reconnectAttemptPhase, attemptId)
        return true
    }

    @Synchronized
    private fun clearReconnectAttemptInFlight(attemptId: Long) {
        reconnectAttemptPhase = completeReconnectAttemptPhase(reconnectAttemptPhase, attemptId)
    }

    private fun advanceRepositoryConnectionAttempt(attemptId: Long) {
        repo.advanceConnectionAttempt(attemptId)
        dvrRepository.advanceConnectionAttempt(attemptId)
    }

    private fun ensureCurrentConnectionAttempt(attemptId: Long) {
        if (!isCurrentConnectionAttempt(attemptId)) {
            throw CancellationException("Superseded connection attempt")
        }
    }

    private fun isCurrentConnectionAttempt(attemptId: Long): Boolean =
        synchronized(connectionAttemptLock) {
            connectionAttemptMayPublish(connectionAttempts, attemptId)
        }

    private fun publishConnectionState(attemptId: Long, state: ConnectionUiState) {
        runForCurrentConnectionAttempt(attemptId) { _uiState.value = state }
    }

    private fun runForCurrentConnectionAttempt(attemptId: Long, block: () -> Unit) {
        synchronized(connectionAttemptLock) {
            if (!connectionAttemptMayPublish(connectionAttempts, attemptId)) {
                throw CancellationException("Superseded connection attempt")
            }
            block()
        }
    }
}
