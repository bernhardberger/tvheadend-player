package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionFailureKind
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import kotlin.time.Duration.Companion.seconds

/**
 * Three screens: server, account and connection test. The last screen shows the running test
 * and its outcome in place. Network discovery belongs to the server screen. First run has an
 * unnumbered welcome before these screens; editing skips it.
 */
internal enum class SetupStep {
    WELCOME,
    ADDRESS,
    SIGN_IN,
    CONNECTING,
    FAILED,
    DONE,
}

/**
 * The same wizard, either empty or prefilled from the saved connection.
 */
internal enum class SetupMode {
    FIRST_RUN,
    EDIT,
    ;

    val firstStep: SetupStep
        get() = if (this == FIRST_RUN) SetupStep.WELCOME else SetupStep.ADDRESS
}

/** Null leaves the wizard. Test cancellation and results return to the Account screen. */
internal fun SetupStep.previous(mode: SetupMode = SetupMode.FIRST_RUN): SetupStep? = when (this) {
    SetupStep.WELCOME -> null
    SetupStep.ADDRESS -> if (mode == SetupMode.FIRST_RUN) SetupStep.WELCOME else null
    SetupStep.SIGN_IN -> SetupStep.ADDRESS
    SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStep.SIGN_IN
}

/**
 * How long a search looks for announcing servers. Discovery itself never ends, and most servers
 * that announce themselves answer within a few seconds, so "No servers found" follows this.
 */
internal val DiscoveryWindow = 10.seconds

/**
 * The step a recreated flow resumes on. The test and its result belong to a session the saved
 * state cannot restore, and the password is never saved, so those resume on Account.
 */
internal fun SetupStep.resumable(): SetupStep = when (this) {
    SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStep.SIGN_IN
    else -> this
}

/** The steps the step line shows: 1 Server, 2 Account, 3 Connect. */
internal enum class SetupStage { SERVER, SIGN_IN, CONNECT }

internal val SetupStep.stage: SetupStage?
    get() = when (this) {
        SetupStep.WELCOME -> null
        SetupStep.ADDRESS -> SetupStage.SERVER
        SetupStep.SIGN_IN -> SetupStage.SIGN_IN
        SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStage.CONNECT
    }

internal enum class SetupAction {
    TRY_AGAIN,
    CHANGE_ADDRESS,
    CHANGE_SIGN_IN,
    CONTINUE_ANYWAY,

    /** The editor's [CONTINUE_ANYWAY]: saves the untested connection in place of the current one. */
    SAVE_ANYWAY,
}

/**
 * A failed connection test. Its wording is the catalog [condition]'s; [actions] are in screen
 * order and the first one takes the initial focus.
 */
internal enum class SetupFailure(
    val condition: AppErrorCondition,
    val actions: List<SetupAction>,
    /** False where the catalog hint sends the user to Connection settings, which this flow is. */
    val showsHint: Boolean = true,
) {
    UNREACHABLE(
        AppErrorCondition.UNREACHABLE,
        listOf(SetupAction.TRY_AGAIN, SetupAction.CHANGE_ADDRESS, SetupAction.CONTINUE_ANYWAY),
    ),
    /** This TV has no network: nothing about the server can be fixed or saved from here. */
    NETWORK(AppErrorCondition.NO_NETWORK, listOf(SetupAction.TRY_AGAIN)),
    TIMEOUT(AppErrorCondition.TIMEOUT, listOf(SetupAction.TRY_AGAIN, SetupAction.CHANGE_ADDRESS)),
    NOT_TVHEADEND(
        AppErrorCondition.NOT_TVHEADEND,
        listOf(SetupAction.CHANGE_ADDRESS, SetupAction.TRY_AGAIN),
    ),
    VERSION(AppErrorCondition.INCOMPATIBLE_SERVER, listOf(SetupAction.TRY_AGAIN, SetupAction.CHANGE_ADDRESS)),
    SIGN_IN_REJECTED(
        AppErrorCondition.AUTHENTICATION_REJECTED,
        listOf(SetupAction.CHANGE_SIGN_IN, SetupAction.TRY_AGAIN),
        showsHint = false,
    ),
    NO_PERMISSION(
        AppErrorCondition.PERMISSION_DENIED,
        listOf(SetupAction.CHANGE_SIGN_IN, SetupAction.TRY_AGAIN),
    ),
    OTHER(
        AppErrorCondition.LOAD_FAILED,
        listOf(SetupAction.TRY_AGAIN, SetupAction.CHANGE_ADDRESS),
        showsHint = false,
    ),
}

/** The failure's actions in [mode]; editing saves anyway instead of continuing anyway. */
internal fun SetupFailure.actionsFor(mode: SetupMode): List<SetupAction> =
    if (mode == SetupMode.EDIT) {
        actions.map { if (it == SetupAction.CONTINUE_ANYWAY) SetupAction.SAVE_ANYWAY else it }
    } else {
        actions
    }

internal fun SessionFailure.toSetupFailure(): SetupFailure = when (kind) {
    SessionFailureKind.SERVER_UNREACHABLE,
    SessionFailureKind.TRANSPORT_UNAVAILABLE -> SetupFailure.UNREACHABLE
    SessionFailureKind.NETWORK_UNAVAILABLE -> SetupFailure.NETWORK
    SessionFailureKind.AUTHENTICATION_REJECTED -> SetupFailure.SIGN_IN_REJECTED
    SessionFailureKind.INCOMPATIBLE_SERVER -> SetupFailure.NOT_TVHEADEND
    SessionFailureKind.SERVER_UPGRADE_REQUIRED -> SetupFailure.VERSION
    SessionFailureKind.SYNCHRONIZATION_FAILED -> when (operationFailure) {
        TvheadendFailure.ACCESS_DENIED -> SetupFailure.NO_PERMISSION
        TvheadendFailure.TIMEOUT -> SetupFailure.TIMEOUT
        TvheadendFailure.TRANSPORT_UNAVAILABLE -> SetupFailure.UNREACHABLE
        TvheadendFailure.NOT_SUPPORTED -> SetupFailure.VERSION
        else -> SetupFailure.OTHER
    }
    else -> SetupFailure.OTHER
}

internal enum class AddressFocus { SEARCH, HOST, PORT, CONTINUE }

internal enum class SignInFocus { USERNAME, PASSWORD, CONNECT }
