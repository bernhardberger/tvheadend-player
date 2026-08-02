package at.bernhardberger.tvhplayer.core

sealed interface ConnectionUiState {
    data object NeedsConfiguration : ConnectionUiState
    data object Connecting : ConnectionUiState
    data object SyncingChannels : ConnectionUiState
    data object Ready : ConnectionUiState
    data object Reconnecting : ConnectionUiState
    data object CredentialUnavailable : ConnectionUiState
    data class Error(val kind: ConnectionFailureKind) : ConnectionUiState
    data class SubscriptionError(val kind: SubscriptionFailureKind) : ConnectionUiState
}

enum class SubscriptionFailureKind {
    INVALID_TARGET,
    NO_FREE_ADAPTER,
    MUX_NOT_ENABLED,
    TUNING_FAILED,
    BAD_SIGNAL,
    SCRAMBLED,
    OVERRIDDEN,
    NO_INPUT,
}

fun subscriptionFailureKind(
    subscriptionError: String?,
    state: String?,
): SubscriptionFailureKind? {
    val code = (subscriptionError ?: state)
        ?.lowercase()
        ?.filter(Char::isLetterOrDigit)
        .orEmpty()
    return when {
        "invalidtarget" in code -> SubscriptionFailureKind.INVALID_TARGET
        "nofreeadapter" in code -> SubscriptionFailureKind.NO_FREE_ADAPTER
        "muxnotenabled" in code -> SubscriptionFailureKind.MUX_NOT_ENABLED
        "tuningfailed" in code -> SubscriptionFailureKind.TUNING_FAILED
        "badsignal" in code -> SubscriptionFailureKind.BAD_SIGNAL
        "scrambled" in code -> SubscriptionFailureKind.SCRAMBLED
        "subscriptionoverridden" in code -> SubscriptionFailureKind.OVERRIDDEN
        "noinput" in code -> SubscriptionFailureKind.NO_INPUT
        else -> null
    }
}

data class SubscriptionFailureTrackerState(
    val newestSeenSubscriptionId: Int? = null,
    val stoppedThroughSubscriptionId: Int? = null,
    val currentFailure: SubscriptionFailureKind? = null,
)

fun updateSubscriptionFailure(
    state: SubscriptionFailureTrackerState,
    subscriptionId: Int,
    subscriptionError: String?,
    status: String?,
): SubscriptionFailureTrackerState {
    val stoppedThroughId = state.stoppedThroughSubscriptionId
    if (stoppedThroughId != null && subscriptionId <= stoppedThroughId) return state
    val newestId = state.newestSeenSubscriptionId
    if (newestId != null && subscriptionId < newestId) return state
    return SubscriptionFailureTrackerState(
        newestSeenSubscriptionId = subscriptionId,
        currentFailure = subscriptionFailureKind(subscriptionError, status),
    )
}

fun removeSubscriptionFailure(
    state: SubscriptionFailureTrackerState,
    subscriptionId: Int,
): SubscriptionFailureTrackerState {
    val stoppedThroughId = maxOf(state.stoppedThroughSubscriptionId ?: Int.MIN_VALUE, subscriptionId)
    return state.copy(
        newestSeenSubscriptionId = maxOf(
            state.newestSeenSubscriptionId ?: Int.MIN_VALUE,
            subscriptionId,
        ),
        stoppedThroughSubscriptionId = stoppedThroughId,
        currentFailure = if (
            subscriptionId >= (state.newestSeenSubscriptionId ?: Int.MIN_VALUE)
        ) {
            null
        } else {
            state.currentFailure
        },
    )
}

fun connectionAttemptState(hasPublishedChannels: Boolean): ConnectionUiState =
    if (hasPublishedChannels) ConnectionUiState.Reconnecting else ConnectionUiState.Connecting
