package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionFailureKind
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import kotlin.time.Duration.Companion.seconds

/**
 * The screens of the guided connection setup. [FIND] searches the network; [CONNECTING] is the
 * sign-in screen with its test running in place; [FAILED] shows the test's outcome.
 */
internal enum class SetupStep {
    WELCOME,
    FIND,
    ADDRESS,
    SIGN_IN,
    CONNECTING,
    FAILED,
    DONE,
}

/** How the server was chosen, which decides where Back goes from the address and the sign-in. */
internal enum class ServerChoice {
    /** "Enter address manually" on the welcome. */
    TYPED,

    /** "Enter address manually" on the search, or "Change address" after a found server failed. */
    TYPED_AFTER_SEARCH,

    /** A server the search found. */
    FOUND,
}

/** Where Back goes; null leaves the flow, so the activity's own Back reaches the TV home. */
internal fun SetupStep.previous(choice: ServerChoice): SetupStep? = when (this) {
    SetupStep.WELCOME -> null
    SetupStep.FIND -> SetupStep.WELCOME
    SetupStep.ADDRESS -> if (choice == ServerChoice.TYPED) SetupStep.WELCOME else SetupStep.FIND
    SetupStep.SIGN_IN -> if (choice == ServerChoice.FOUND) SetupStep.FIND else SetupStep.ADDRESS
    SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStep.SIGN_IN
}

/**
 * How long a search looks for announcing servers. Discovery itself never ends, and most servers
 * that announce themselves answer within a few seconds, so "No servers found" follows this.
 */
internal val DiscoveryWindow = 10.seconds

/**
 * The step a recreated flow resumes on. The test and its result belong to a session the saved
 * state cannot restore, and the password is never saved, so those resume on the sign-in.
 */
internal fun SetupStep.resumable(): SetupStep = when (this) {
    SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStep.SIGN_IN
    else -> this
}

/** The steps the step line shows: 1 Server, 2 Sign in, 3 Connect. */
internal enum class SetupStage { SERVER, SIGN_IN, CONNECT }

internal val SetupStep.stage: SetupStage
    get() = when (this) {
        SetupStep.WELCOME, SetupStep.FIND, SetupStep.ADDRESS -> SetupStage.SERVER
        SetupStep.SIGN_IN -> SetupStage.SIGN_IN
        SetupStep.CONNECTING, SetupStep.FAILED, SetupStep.DONE -> SetupStage.CONNECT
    }

internal enum class SetupAction {
    TRY_AGAIN,
    CHANGE_ADDRESS,
    CHANGE_SIGN_IN,
    CONTINUE_ANYWAY,
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

internal fun SessionFailure.toSetupFailure(): SetupFailure = when (kind) {
    SessionFailureKind.SERVER_UNREACHABLE,
    SessionFailureKind.NETWORK_UNAVAILABLE,
    SessionFailureKind.TRANSPORT_UNAVAILABLE -> SetupFailure.UNREACHABLE
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

internal enum class WelcomeFocus { FIND, TYPE }

internal enum class AddressFocus { HOST, PORT, CONTINUE }

internal enum class SignInFocus { USERNAME, PASSWORD, CONNECT }
