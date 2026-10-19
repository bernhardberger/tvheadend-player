package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind

/** Where a condition is presented: see the error vocabulary rules for the surface each kind uses. */
enum class AppErrorKind {
    CONNECTION,

    /** Centred passive message in the player; nothing focusable. */
    CHANNEL,

    /** Full-screen alert in the live player. */
    LIVE_TV,

    /** Full-screen alert in the recording player. */
    RECORDING,
}

/**
 * Every user-visible failure with its stable internal number, never shown: 1xx connection, 2xx live
 * TV, 3xx recording playback. One condition has one text and one number everywhere. Numbers never
 * change when wording or language does; add a condition with a new number instead of reusing one.
 */
enum class AppErrorCondition(val number: Int, val kind: AppErrorKind) {
    UNREACHABLE(101, AppErrorKind.CONNECTION),
    TIMEOUT(102, AppErrorKind.CONNECTION),
    AUTHENTICATION_REJECTED(103, AppErrorKind.CONNECTION),
    PERMISSION_DENIED(104, AppErrorKind.CONNECTION),
    INCOMPATIBLE_SERVER(105, AppErrorKind.CONNECTION),
    CREDENTIAL_UNAVAILABLE(106, AppErrorKind.CONNECTION),
    NOT_CONFIGURED(107, AppErrorKind.CONNECTION),
    NO_CHANNELS(108, AppErrorKind.CONNECTION),
    LOAD_FAILED(109, AppErrorKind.CONNECTION),

    /** The SDK retries by itself and there is nothing saved to show instead. */
    RECONNECTING(110, AppErrorKind.CONNECTION),

    NO_FREE_ADAPTER(201, AppErrorKind.CHANNEL),
    NO_SIGNAL(202, AppErrorKind.CHANNEL),
    MUX_NOT_ENABLED(203, AppErrorKind.CHANNEL),
    SCRAMBLED(204, AppErrorKind.CHANNEL),
    CHANNEL_ACCESS_DENIED(205, AppErrorKind.CHANNEL),
    CHANNEL_TAKEN_OVER(206, AppErrorKind.CHANNEL),

    /** The server stopped the stream, or any live failure without a more specific cause. */
    STREAM_STOPPED(207, AppErrorKind.CHANNEL),
    USER_LIMIT(208, AppErrorKind.LIVE_TV),

    /** This device's player failed while the server reported no problem. */
    CHANNEL_PLAYBACK_FAILED(210, AppErrorKind.CHANNEL),

    RECORDING_INTERRUPTED(301, AppErrorKind.RECORDING),
    RECORDING_NOT_STARTED(302, AppErrorKind.RECORDING),
    RECORDING_FILE_MISSING(303, AppErrorKind.RECORDING),
    RECORDING_UNAVAILABLE(304, AppErrorKind.RECORDING),

    /** A running recording whose file cannot be played while it grows. */
    RECORDING_DEFERRED(305, AppErrorKind.RECORDING),

    /** This device's player failed on a recording the server still offers. */
    RECORDING_PLAYBACK_FAILED(306, AppErrorKind.RECORDING),
}

/** Buttons of a full-screen alert, in display order; the first is focused. */
enum class AppErrorAction { RETRY, CONNECTION_SETTINGS, CLOSE }

data class AppError(val condition: AppErrorCondition, val actions: List<AppErrorAction>)

fun AppErrorCondition.toAppError(
    recovery: ConnectionRecoveryAction? = null,
    closable: Boolean = false,
): AppError = AppError(this, actions(recovery, closable))

/**
 * Buttons follow the SDK's recovery guidance as [primaryRecoveryAction] reads it: Retry offers Retry
 * and Connection settings; Settings or None offers Connection settings only. Without guidance
 * ([recovery] null) the condition's own default applies. A connection that retries by itself (110)
 * offers Connection settings only. [closable] is a connection condition shown in a player: an alert
 * has at most two buttons, so it keeps only the first fix and adds Close; one that retries by itself
 * has nothing to fix yet and offers Close only. Live and recording alerts always carry their own
 * Close; channel messages have no buttons.
 */
fun AppErrorCondition.actions(
    recovery: ConnectionRecoveryAction? = null,
    closable: Boolean = false,
): List<AppErrorAction> {
    val own = when (this) {
        AppErrorCondition.RECONNECTING -> SETTINGS
        AppErrorCondition.UNREACHABLE,
        AppErrorCondition.TIMEOUT,
        AppErrorCondition.NO_CHANNELS,
        AppErrorCondition.LOAD_FAILED -> recovery.buttons(default = RETRY_AND_SETTINGS)
        AppErrorCondition.AUTHENTICATION_REJECTED,
        AppErrorCondition.PERMISSION_DENIED,
        AppErrorCondition.INCOMPATIBLE_SERVER,
        AppErrorCondition.CREDENTIAL_UNAVAILABLE,
        AppErrorCondition.NOT_CONFIGURED -> recovery.buttons(default = SETTINGS)
        AppErrorCondition.NO_FREE_ADAPTER,
        AppErrorCondition.NO_SIGNAL,
        AppErrorCondition.MUX_NOT_ENABLED,
        AppErrorCondition.SCRAMBLED,
        AppErrorCondition.CHANNEL_ACCESS_DENIED,
        AppErrorCondition.CHANNEL_TAKEN_OVER,
        AppErrorCondition.STREAM_STOPPED,
        AppErrorCondition.CHANNEL_PLAYBACK_FAILED -> emptyList()
        AppErrorCondition.USER_LIMIT,
        AppErrorCondition.RECORDING_INTERRUPTED,
        AppErrorCondition.RECORDING_PLAYBACK_FAILED -> listOf(AppErrorAction.RETRY, AppErrorAction.CLOSE)
        AppErrorCondition.RECORDING_NOT_STARTED,
        AppErrorCondition.RECORDING_FILE_MISSING,
        AppErrorCondition.RECORDING_UNAVAILABLE,
        AppErrorCondition.RECORDING_DEFERRED -> listOf(AppErrorAction.CLOSE)
    }
    return when {
        kind != AppErrorKind.CONNECTION || !closable -> own
        this == AppErrorCondition.RECONNECTING -> listOf(AppErrorAction.CLOSE)
        else -> own.take(1) + AppErrorAction.CLOSE
    }
}

private val RETRY_AND_SETTINGS = listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)
private val SETTINGS = listOf(AppErrorAction.CONNECTION_SETTINGS)

private fun ConnectionRecoveryAction?.buttons(default: List<AppErrorAction>): List<AppErrorAction> = when (this) {
    ConnectionRecoveryAction.RETRY -> RETRY_AND_SETTINGS
    ConnectionRecoveryAction.SETTINGS,
    ConnectionRecoveryAction.NONE -> SETTINGS
    null -> default
}

/**
 * A connection that is still working or Ready has no error. Unknown or future SDK values land on
 * [AppErrorCondition.LOAD_FAILED] through [ConnectionFailureKind.OTHER]; the buttons follow
 * [primaryRecoveryAction].
 */
fun ConnectionUiState.toAppError(closable: Boolean = false): AppError? {
    val condition = when (this) {
        ConnectionUiState.NeedsConfiguration -> AppErrorCondition.NOT_CONFIGURED
        ConnectionUiState.CredentialUnavailable -> AppErrorCondition.CREDENTIAL_UNAVAILABLE
        is ConnectionUiState.Error -> kind.toAppErrorCondition(recoveryDisposition)
        ConnectionUiState.Connecting,
        ConnectionUiState.SyncingChannels,
        ConnectionUiState.Ready,
        ConnectionUiState.Reconnecting -> return null
    }
    return condition.toAppError(primaryRecoveryAction(), closable)
}

/** A Ready session whose authoritative channel catalog is empty; Ready gives no guidance, so 108's default applies. */
fun noChannelsAppError(closable: Boolean = false): AppError =
    AppErrorCondition.NO_CHANNELS.toAppError(closable = closable)

/** While the SDK retries on its own, reachability and timeout failures read as one reconnecting state. */
fun ConnectionFailureKind.toAppErrorCondition(
    disposition: SessionRecoveryDisposition,
): AppErrorCondition {
    val retryingByItself = disposition == SessionRecoveryDisposition.AUTOMATIC_BACKOFF
    return when (this) {
        ConnectionFailureKind.AUTHENTICATION -> AppErrorCondition.AUTHENTICATION_REJECTED
        ConnectionFailureKind.PERMISSION_DENIED -> AppErrorCondition.PERMISSION_DENIED
        ConnectionFailureKind.INCOMPATIBLE_SERVER -> AppErrorCondition.INCOMPATIBLE_SERVER
        ConnectionFailureKind.UNREACHABLE ->
            if (retryingByItself) AppErrorCondition.RECONNECTING else AppErrorCondition.UNREACHABLE
        ConnectionFailureKind.TIMEOUT ->
            if (retryingByItself) AppErrorCondition.RECONNECTING else AppErrorCondition.TIMEOUT
        ConnectionFailureKind.OTHER ->
            if (retryingByItself) AppErrorCondition.RECONNECTING else AppErrorCondition.LOAD_FAILED
    }
}
