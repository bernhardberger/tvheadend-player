package at.bernhardberger.tvhplayer.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionCommandResult
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The guided connection setup: which step shows, where focus lands on it, the connection test
 * and the final save. The test runs on the app's one [session] before anything is saved, so a
 * profile is only stored once it connected or the user chose to continue anyway.
 */
@Stable
internal class ConnectionSetupState(
    val form: ConnectionFormState,
    private val session: TvheadendSession,
    private val profiles: ConnectionProfileEditor,
    private val scope: CoroutineScope,
    step: SetupStep = SetupStep.WELCOME,
) {
    var step by mutableStateOf(step)
        private set
    var failure by mutableStateOf<SetupFailure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var addressFocus by mutableStateOf(AddressFocus.HOST)
        private set
    var signInFocus by mutableStateOf(SignInFocus.USERNAME)
        private set

    /** Changes whenever a step asks for its focus target again, even on the same step. */
    var focusRequest by mutableIntStateOf(0)
        private set

    private var addressErrorsShown by mutableStateOf(false)
    private var signInErrorShown by mutableStateOf(false)
    private var sessionWork: Job? = null

    val hostError: Boolean get() = addressErrorsShown && form.hostMissing
    val portError: Boolean get() = addressErrorsShown && form.portInvalid
    val signInError: Boolean get() = signInErrorShown && form.credentialsIncomplete

    init {
        if (step == SetupStep.SIGN_IN) signInFocus = firstEmptySignInField() ?: SignInFocus.CONNECT
    }

    fun enterAddress() {
        focusAddress(AddressFocus.HOST)
        step = SetupStep.ADDRESS
    }

    /** Goes on to the sign-in when the address is valid; otherwise shows why and focuses it. */
    fun continueFromAddress() {
        addressErrorsShown = true
        when {
            form.hostMissing -> focusAddress(AddressFocus.HOST)
            form.portInvalid -> focusAddress(AddressFocus.PORT)
            else -> {
                focusSignIn(firstEmptySignInField() ?: SignInFocus.CONNECT)
                step = SetupStep.SIGN_IN
            }
        }
    }

    /** Starts the test when the sign-in is complete; otherwise shows why and focuses the empty field. */
    fun connect() {
        signInErrorShown = true
        val profile = form.serverProfile()
        if (profile == null) {
            focusSignIn(firstEmptySignInField() ?: SignInFocus.USERNAME)
            return
        }
        failure = null
        focusSignIn(SignInFocus.CONNECT)
        step = SetupStep.CONNECTING
        replaceSessionWork {
            val result = session.testConnection(profile)
            failure = result
            step = if (result == null) SetupStep.DONE else SetupStep.FAILED
        }
    }

    fun onAction(action: SetupAction) {
        when (action) {
            SetupAction.TRY_AGAIN -> connect()
            SetupAction.CHANGE_ADDRESS -> enterAddress()
            SetupAction.CHANGE_SIGN_IN -> {
                focusSignIn(firstEmptySignInField() ?: SignInFocus.PASSWORD)
                step = SetupStep.SIGN_IN
            }
            SetupAction.CONTINUE_ANYWAY -> save()
        }
    }

    /** Saves the tested profile ("Start watching", "Continue anyway"); the app root then leaves the flow. */
    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val result = form.submit(profiles) { CredentialEditLease {} }
                if (result == ConnectionFormFeedback.SAVED) form.clearPassword()
            } finally {
                saving = false
            }
        }
    }

    /** One step back, keeping what was entered. False on the first step, where the flow lets Back go. */
    fun back(): Boolean {
        if (saving) return true
        val previous = step.previous() ?: return false
        when (step) {
            SetupStep.CONNECTING -> sessionWork?.cancel()
            // The tested profile stays connected only to be saved by "Start watching".
            SetupStep.DONE -> replaceSessionWork { session.disconnect() }
            else -> Unit
        }
        when (previous) {
            SetupStep.ADDRESS -> focusAddress(AddressFocus.CONTINUE)
            SetupStep.SIGN_IN -> focusSignIn(SignInFocus.CONNECT)
            else -> Unit
        }
        step = previous
        return true
    }

    private fun firstEmptySignInField(): SignInFocus? = when {
        form.username.isBlank() -> SignInFocus.USERNAME
        form.password.isBlank() -> SignInFocus.PASSWORD
        else -> null
    }

    fun focusAddress(target: AddressFocus) {
        addressFocus = target
        focusRequest += 1
    }

    fun focusSignIn(target: SignInFocus) {
        signInFocus = target
        focusRequest += 1
    }

    /** Session commands run one after another, so a cancelled test's disconnect never lands after the next connect. */
    private fun replaceSessionWork(work: suspend () -> Unit) {
        val previous = sessionWork
        sessionWork = scope.launch {
            previous?.cancelAndJoin()
            work()
        }
    }

    companion object {
        fun saver(
            form: ConnectionFormState,
            session: TvheadendSession,
            profiles: ConnectionProfileEditor,
            scope: CoroutineScope,
        ): Saver<ConnectionSetupState, String> = Saver(
            save = { it.step.resumable().name },
            restore = { ConnectionSetupState(form, session, profiles, scope, SetupStep.valueOf(it)) },
        )
    }
}

/**
 * Connects [profile] and waits for its first outcome: null when the session is ready, else why
 * it failed. A failed or cancelled test disconnects, so the SDK does not keep retrying a profile
 * nobody saved; a passed one stays connected for the save to adopt.
 */
internal suspend fun TvheadendSession.testConnection(profile: ServerProfile): SetupFailure? {
    var passed = false
    try {
        disconnect()
        if (connect(profile) == SessionCommandResult.SHUT_DOWN) return SetupFailure.OTHER
        val outcome = observation
            .map { it.sessionState }
            .first { it is SessionState.Ready || it is SessionState.Unavailable }
        passed = outcome is SessionState.Ready
        return (outcome as? SessionState.Unavailable)?.reason?.toSetupFailure()
    } finally {
        if (!passed) withContext(NonCancellable) { disconnect() }
    }
}

@Composable
internal fun rememberConnectionSetupState(
    session: TvheadendSession,
    profiles: ConnectionProfileEditor,
): ConnectionSetupState {
    val form = rememberConnectionFormState()
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = ConnectionSetupState.saver(form, session, profiles, scope)) {
        ConnectionSetupState(form, session, profiles, scope)
    }
}
