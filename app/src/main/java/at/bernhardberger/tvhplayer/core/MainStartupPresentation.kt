package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.data.ConnectionFailureKind

enum class MainStartupActionId {
    RETRY,
    CONNECTION_SETTINGS,
}

enum class MainStartupMessageKind {
    PREPARING,
    CONNECTING,
    SYNCING_METADATA,
    WAITING_FOR_CURRENT_CHANNEL_METADATA,
    RECONNECTING,
    STARTING_TELEVISION,
    RESUMING_PLAYBACK,
    AUTHORITATIVE_NO_CHANNELS,
    RETRYABLE_FAILURE,
    AUTHENTICATION_FAILURE,
    PERMISSION_DENIED,
    INCOMPATIBLE_SERVER,
    TIMEOUT_FAILURE,
    SYNCHRONIZATION_FAILURE,
    CONFIGURATION_REQUIRED,
    CREDENTIAL_UNAVAILABLE,
}

sealed interface MainStartupPresentation {
    data object Inactive : MainStartupPresentation

    data class Passive(
        val messageKind: MainStartupMessageKind,
    ) : MainStartupPresentation

    data class Actionable(
        val messageKind: MainStartupMessageKind,
        val actions: List<MainStartupActionId>,
    ) : MainStartupPresentation

    data class Enter(val request: ApplianceLaunchRequest) : MainStartupPresentation
}

fun mainStartupPresentation(
    startupState: MainStartupState,
    launchState: ApplianceLaunchState,
    connectionState: ConnectionUiState,
    currentChannelReadiness: CurrentChannelReadiness,
): MainStartupPresentation {
    if (startupState is MainStartupState.ResolvingLocal) {
        return MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING)
    }
    val pendingLaunch = when (launchState) {
        ApplianceLaunchState.Idle -> return MainStartupPresentation.Inactive
        is ApplianceLaunchState.Entering ->
            return MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION)
        is ApplianceLaunchState.Pending -> launchState
    }

    return when (connectionState) {
        ConnectionUiState.Connecting ->
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        ConnectionUiState.SyncingChannels ->
            MainStartupPresentation.Passive(
                MainStartupMessageKind.SYNCING_METADATA,
            )
        ConnectionUiState.Reconnecting ->
            MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        ConnectionUiState.Ready -> when (currentChannelReadiness) {
            CurrentChannelReadiness.Waiting,
            is CurrentChannelReadiness.Browsable ->
                MainStartupPresentation.Passive(
                    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA,
                )
            is CurrentChannelReadiness.Ready -> if (currentChannelReadiness.channels.isEmpty()) {
                actionableFailure(
                    normalMessageKind = MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                )
            } else {
                MainStartupPresentation.Enter(pendingLaunch.request)
            }
        }
        ConnectionUiState.NeedsConfiguration -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CONFIGURATION_REQUIRED,
            normalActions = connectionSettingsAction,
        )
        ConnectionUiState.CredentialUnavailable -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
            normalActions = connectionSettingsAction,
        )
        is ConnectionUiState.Error -> when (connectionState.primaryRecoveryAction()) {
            ConnectionRecoveryAction.RETRY -> actionableFailure(
                normalMessageKind = mainStartupFailureMessage(connectionState.kind),
            )
            ConnectionRecoveryAction.SETTINGS -> actionableFailure(
                normalMessageKind = mainStartupFailureMessage(connectionState.kind),
                normalActions = connectionSettingsAction,
            )
            ConnectionRecoveryAction.NONE ->
                MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        }
    }
}

/**
 * A cold start without autoplay waits on the startup screen only while no channel can be shown:
 * saved or current channels, or an authoritative empty catalog (the no-channels screen), end it.
 * [failure] is the [waitingConnectionFailure] it keeps showing while automatic attempts run.
 */
fun coldStartPresentation(
    connectionState: ConnectionUiState,
    currentChannelReadiness: CurrentChannelReadiness,
    failure: ConnectionUiState?,
): MainStartupPresentation {
    if (currentChannelReadiness.channelsAvailable() ||
        currentChannelReadiness is CurrentChannelReadiness.Ready
    ) return MainStartupPresentation.Inactive
    failure?.let { return waitingConnectionFailurePresentation(it) }
    return when (connectionState) {
        ConnectionUiState.Connecting -> MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        ConnectionUiState.SyncingChannels -> MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA)
        ConnectionUiState.Reconnecting -> MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        ConnectionUiState.Ready ->
            MainStartupPresentation.Passive(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA)
        // Failures show only as [failure]: none here is the viewer's retry leaving the old one.
        ConnectionUiState.NeedsConfiguration,
        ConnectionUiState.CredentialUnavailable,
        is ConnectionUiState.Error -> MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
    }
}

/** Channels a browse screen can draw now: saved ones before the session is ready, or current ones. */
fun CurrentChannelReadiness.channelsAvailable(): Boolean = when (this) {
    CurrentChannelReadiness.Waiting -> false
    is CurrentChannelReadiness.Browsable -> channels.isNotEmpty()
    is CurrentChannelReadiness.Ready -> channels.isNotEmpty()
}

/**
 * The failure a surface waiting for its first connection shows: the current one, else the one
 * before while the automatic attempts after it run, so its actions stay put. Synchronizing or
 * Ready ends it.
 */
fun waitingConnectionFailure(
    connectionState: ConnectionUiState,
    previous: ConnectionUiState?,
): ConnectionUiState? = when (connectionState) {
    ConnectionUiState.NeedsConfiguration,
    ConnectionUiState.CredentialUnavailable,
    is ConnectionUiState.Error -> connectionState
    ConnectionUiState.Connecting,
    ConnectionUiState.Reconnecting -> previous
    ConnectionUiState.SyncingChannels,
    ConnectionUiState.Ready -> null
}

/**
 * [waitingConnectionFailure] across observations. The viewer's retry hides the failure it retries
 * until the connection leaves it, so the next attempt shows as connecting, not as the old failure.
 */
data class WaitingConnectionFailure(
    val latched: ConnectionUiState? = null,
    val retrying: Boolean = false,
) {
    fun shown(connectionState: ConnectionUiState): ConnectionUiState? =
        if (retrying) null else waitingConnectionFailure(connectionState, latched)

    fun observe(connectionState: ConnectionUiState): WaitingConnectionFailure = when {
        retrying && waitingConnectionFailure(connectionState, null) != null -> this
        else -> WaitingConnectionFailure(latched = waitingConnectionFailure(connectionState, latched.takeUnless { retrying }))
    }

    fun retry(): WaitingConnectionFailure = WaitingConnectionFailure(retrying = true)
}

/**
 * Startup's recovery for a [waitingConnectionFailure]. Unlike a pending autoplay, an automatic
 * backoff is actionable too: the viewer is waiting on this screen, so Retry starts the next
 * attempt now.
 */
fun waitingConnectionFailurePresentation(failure: ConnectionUiState): MainStartupPresentation.Actionable =
    when (failure) {
        ConnectionUiState.CredentialUnavailable -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
            normalActions = connectionSettingsAction,
        )
        is ConnectionUiState.Error -> actionableFailure(
            normalMessageKind = mainStartupFailureMessage(failure.kind),
            normalActions = if (failure.primaryRecoveryAction() == ConnectionRecoveryAction.SETTINGS) {
                connectionSettingsAction
            } else {
                retryAndConnectionSettingsActions
            },
        )
        else -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CONFIGURATION_REQUIRED,
            normalActions = connectionSettingsAction,
        )
    }

/** Shown cold-start screens stay at least this long, so they can be read; one never shown waits for nothing. */
internal const val ColdStartMinimumVisibleMillis = 500L

/** How much longer a cold-start screen first shown at [shownAtMillis] stays before it may leave. */
fun coldStartRemainingVisibleMillis(shownAtMillis: Long?, nowMillis: Long): Long =
    shownAtMillis?.let { (it + ColdStartMinimumVisibleMillis - nowMillis).coerceAtLeast(0L) } ?: 0L

private fun mainStartupFailureMessage(kind: ConnectionFailureKind): MainStartupMessageKind = when (kind) {
    ConnectionFailureKind.AUTHENTICATION -> MainStartupMessageKind.AUTHENTICATION_FAILURE
    ConnectionFailureKind.PERMISSION_DENIED -> MainStartupMessageKind.PERMISSION_DENIED
    ConnectionFailureKind.INCOMPATIBLE_SERVER -> MainStartupMessageKind.INCOMPATIBLE_SERVER
    ConnectionFailureKind.TIMEOUT -> MainStartupMessageKind.TIMEOUT_FAILURE
    ConnectionFailureKind.UNREACHABLE -> MainStartupMessageKind.RETRYABLE_FAILURE
    ConnectionFailureKind.OTHER -> MainStartupMessageKind.SYNCHRONIZATION_FAILURE
}

private fun actionableFailure(
    normalMessageKind: MainStartupMessageKind,
    normalActions: List<MainStartupActionId> = retryAndConnectionSettingsActions,
): MainStartupPresentation.Actionable =
    MainStartupPresentation.Actionable(
        messageKind = normalMessageKind,
        actions = normalActions,
    )
private val retryAndConnectionSettingsActions = listOf(
    MainStartupActionId.RETRY,
    MainStartupActionId.CONNECTION_SETTINGS,
)
private val connectionSettingsAction = listOf(MainStartupActionId.CONNECTION_SETTINGS)
