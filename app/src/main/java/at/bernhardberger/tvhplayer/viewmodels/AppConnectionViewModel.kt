package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionPolicy
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind
import at.bernhardberger.tvhplayer.core.connectionAttemptState
import at.bernhardberger.tvhplayer.core.connectionFailureKind
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private data class ServerCfg(
        val host: String,
        val htspPort: Int,
        val username: String,
        val password: String
    )

    @Volatile
    private var lastCfg: ServerCfg? = null
    private var reconnectJob: Job? = null
    private var autoJob: Job? = null

    private val subs = mutableMapOf<Int, SubscriptionStatus>()

    init {
        repo.startIfNeeded()

        autoJob = viewModelScope.launch(Dispatchers.IO) {
            combine(
                settings.serverSettings,
                passwords.passwordState,
            ) { server, password -> server to password }
                .collectLatest { (server, password) ->
                    if (!ConnectionPolicy.isAutoConnectReady(server.host, server.htspPort)) {
                        _uiState.value = ConnectionUiState.NeedsConfiguration
                        return@collectLatest
                    }

                    val value = when (password) {
                        StoredPassword.Empty -> ""
                        is StoredPassword.Available -> password.value
                        StoredPassword.Unavailable -> {
                            lastCfg = null
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
                        _uiState.value = ConnectionUiState.Reconnecting
                        repo.onDisconnected()
                        startOrRestartReconnectLoop(
                            reuseMatchingConnection = false,
                            preservePublishedChannels = true,
                        )
                    }

                    is HtspEvent.ServerMessage -> {
                        val msg = e.msg

                        msg.toSubStatusOrNull()?.let { st ->
                            subs[st.id] = st
                            publishSubsStatus()
                            return@collectLatest
                        }

                        msg.subStopIdOrNull()?.let { id ->
                            subs.remove(id)
                            publishSubsStatus()
                            return@collectLatest
                        }
                    }
                }
            }
        }
    }

    private fun publishSubsStatus() {
        val failure = subs.values.computeFailureKind()
        if (failure == null) {
            if (_uiState.value is ConnectionUiState.SubscriptionError) {
                _uiState.value = ConnectionUiState.Ready
            }
        } else {
            _uiState.value = ConnectionUiState.SubscriptionError(failure)
        }
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

    private fun Collection<SubscriptionStatus>.computeFailureKind(): SubscriptionFailureKind? {
        if (isEmpty()) return null

        fun norm(v: String?): String =
            v?.lowercase()?.replace(" ", "") ?: ""

        for (s in this) {
            val code = norm(s.subscriptionError ?: s.state)

            return when {
                "invalidtarget" in code -> SubscriptionFailureKind.INVALID_TARGET
                "nofreeadapter" in code -> SubscriptionFailureKind.NO_FREE_ADAPTER
                "muxnotenabled" in code -> SubscriptionFailureKind.MUX_NOT_ENABLED
                "tuningfailed" in code -> SubscriptionFailureKind.TUNING_FAILED
                "badsignal" in code -> SubscriptionFailureKind.BAD_SIGNAL
                "scrambled" in code -> SubscriptionFailureKind.SCRAMBLED
                "subscriptionoverridden" in code -> SubscriptionFailureKind.OVERRIDDEN
                "noinput" in code -> SubscriptionFailureKind.NO_INPUT
                else -> continue
            }
        }

        return null
    }

    private fun startOrRestartReconnectLoop(
        reuseMatchingConnection: Boolean,
        preservePublishedChannels: Boolean,
    ) {
        reconnectJob?.cancel()
        _uiState.value = connectionAttemptState(
            hasPublishedChannels = preservePublishedChannels && repo.channelsUi.value.isNotEmpty(),
        )

        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            var mayReuseConnection = reuseMatchingConnection
            while (true) {
                val cfg = lastCfg ?: return@launch

                val ok = connectInternal(
                    cfg.host,
                    cfg.htspPort,
                    cfg.username,
                    cfg.password,
                    reuseMatchingConnection = mayReuseConnection,
                    preservePublishedChannels = preservePublishedChannels,
                )
                if (ok) return@launch

                mayReuseConnection = false
                delay(5_000)
            }
        }
    }

    fun reconnectNow() = startOrRestartReconnectLoop(
        reuseMatchingConnection = false,
        preservePublishedChannels = true,
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
        host: String,
        port: Int,
        username: String,
        password: String,
        reuseMatchingConnection: Boolean,
        preservePublishedChannels: Boolean,
    ): Boolean {
        return try {
            val connected = htsp.state.value as? ConnectionState.Connected
            if (reuseMatchingConnection && ConnectionPolicy.isSameEndpoint(
                    connectedHost = connected?.host,
                    connectedPort = connected?.port,
                    requestedHost = host,
                    requestedPort = port,
                )
            ) {
                _uiState.value = ConnectionUiState.Ready
                return true
            }

            repo.onNewConnectionStarting(
                preservePublishedChannels = preservePublishedChannels,
            )
            dvrRepository.onNewConnectionStarting(
                preservePublished = preservePublishedChannels,
            )

            htsp.connect(
                host = host,
                port = port,
                username = username,
                password = password,
                forceReconnect = true,
                connectTimeoutMs = 10_000,
                responseTimeoutMs = 5_000
            )
            _uiState.value = ConnectionUiState.SyncingChannels
            htsp.enableAsyncMetadataAndWaitInitialSync()

            repo.awaitChannelsReady()
            repo.startEpgWorker()
            _uiState.value = ConnectionUiState.Ready

            true
        } catch (e: Exception) {
            Timber.e(e, "Connect failed")
            _uiState.value = ConnectionUiState.Error(connectionFailureKind(e))
            false
        }
    }
}
