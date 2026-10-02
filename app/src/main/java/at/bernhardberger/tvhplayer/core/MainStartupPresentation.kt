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

private fun mainStartupFailureMessage(kind: ConnectionFailureKind): MainStartupMessageKind = when (kind) {
    ConnectionFailureKind.AUTHENTICATION -> MainStartupMessageKind.AUTHENTICATION_FAILURE
    ConnectionFailureKind.PERMISSION_DENIED -> MainStartupMessageKind.PERMISSION_DENIED
    ConnectionFailureKind.INCOMPATIBLE_SERVER -> MainStartupMessageKind.INCOMPATIBLE_SERVER
    ConnectionFailureKind.TIMEOUT -> MainStartupMessageKind.TIMEOUT_FAILURE
    ConnectionFailureKind.ZERO_CHANNELS -> MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS
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
