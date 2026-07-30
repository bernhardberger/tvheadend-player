package at.bernhardberger.tvhplayer.core

enum class MainStartupActionId {
    RETRY,
    CONNECTION_SETTINGS,
    EXIT_SIMPLE_TV,
}

enum class MainStartupMessageKind {
    PREPARING,
    CONNECTING,
    SYNCING_CHANNELS,
    WAITING_FOR_CURRENT_CHANNEL_METADATA,
    RECONNECTING,
    STARTING_TELEVISION,
    AUTHORITATIVE_NO_CHANNELS,
    RETRYABLE_FAILURE,
    CONFIGURATION_REQUIRED,
    CREDENTIAL_UNAVAILABLE,
    SIMPLE_TV_FAILURE,
}

sealed interface MainStartupPresentation {
    data object Inactive : MainStartupPresentation

    data class Passive(val messageKind: MainStartupMessageKind) : MainStartupPresentation

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
    simpleTvActive: Boolean,
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
            MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_CHANNELS)
        ConnectionUiState.Reconnecting ->
            MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        ConnectionUiState.Ready -> when (currentChannelReadiness) {
            CurrentChannelReadiness.Waiting ->
                MainStartupPresentation.Passive(
                    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA,
                )
            is CurrentChannelReadiness.Ready -> if (currentChannelReadiness.channels.isEmpty()) {
                actionableFailure(
                    normalMessageKind = MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                    simpleTvActive = simpleTvActive,
                )
            } else {
                MainStartupPresentation.Enter(pendingLaunch.request)
            }
        }
        ConnectionUiState.NeedsConfiguration -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CONFIGURATION_REQUIRED,
            simpleTvActive = simpleTvActive,
            normalActions = connectionSettingsAction,
        )
        ConnectionUiState.CredentialUnavailable -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
            simpleTvActive = simpleTvActive,
            normalActions = connectionSettingsAction,
        )
        is ConnectionUiState.Error,
        is ConnectionUiState.SubscriptionError -> actionableFailure(
            normalMessageKind = MainStartupMessageKind.RETRYABLE_FAILURE,
            simpleTvActive = simpleTvActive,
        )
    }
}

private fun actionableFailure(
    normalMessageKind: MainStartupMessageKind,
    simpleTvActive: Boolean,
    normalActions: List<MainStartupActionId> = retryAndConnectionSettingsActions,
): MainStartupPresentation.Actionable = if (simpleTvActive) {
    MainStartupPresentation.Actionable(
        messageKind = MainStartupMessageKind.SIMPLE_TV_FAILURE,
        actions = retryAndExitSimpleTvActions,
    )
} else {
    MainStartupPresentation.Actionable(
        messageKind = normalMessageKind,
        actions = normalActions,
    )
}

private val retryAndConnectionSettingsActions = listOf(
    MainStartupActionId.RETRY,
    MainStartupActionId.CONNECTION_SETTINGS,
)
private val connectionSettingsAction = listOf(MainStartupActionId.CONNECTION_SETTINGS)
private val retryAndExitSimpleTvActions = listOf(
    MainStartupActionId.RETRY,
    MainStartupActionId.EXIT_SIMPLE_TV,
)
