package at.bernhardberger.tvhplayer.ui.screens.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscovery
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The guided connection setup: which step shows, where focus lands on it, the network search,
 * the connection test and the final save. The test runs on the app's one [session] before
 * anything is saved, so a profile is only stored once it connected or the user chose to continue
 * anyway.
 */
@Stable
internal class ConnectionSetupState(
    val form: ConnectionFormState,
    private val session: TvheadendSession,
    private val profiles: ConnectionProfileEditor,
    /** Starts one network search; collecting it runs discovery until cancelled. */
    private val discovery: () -> Flow<TvheadendDiscoveryState>,
    private val scope: CoroutineScope,
    step: SetupStep = SetupStep.WELCOME,
    choice: ServerChoice = ServerChoice.TYPED,
) {
    var step by mutableStateOf(step)
        private set
    var serverChoice by mutableStateOf(choice)
        private set
    var failure by mutableStateOf<SetupFailure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var welcomeFocus by mutableStateOf(WelcomeFocus.FIND)
        private set

    /** The servers the search found so far, in the SDK's order. */
    var servers by mutableStateOf(emptyList<DiscoveredTvheadendServer>())
        private set

    /** True while [search] still looks; a restored search step starts looking again. */
    var searching by mutableStateOf(step == SetupStep.FIND)
        private set

    /** Changes whenever a new search should start. */
    var searchRequest by mutableIntStateOf(0)
        private set

    /** The found server that holds or takes focus on the search step; null is "Enter address manually". */
    var findFocus by mutableStateOf<DiscoveredTvheadendServer?>(null)
        private set
    private var chosenServer: DiscoveredTvheadendServer? = null

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
        when {
            step == SetupStep.WELCOME -> {
                welcomeFocus = WelcomeFocus.TYPE
                serverChoice = ServerChoice.TYPED
            }
            step == SetupStep.FIND || serverChoice == ServerChoice.FOUND -> {
                serverChoice = ServerChoice.TYPED_AFTER_SEARCH
            }
        }
        focusAddress(AddressFocus.HOST)
        step = SetupStep.ADDRESS
    }

    /** Opens the search step and starts looking ("Find my server", "Search again"). */
    fun findServer() {
        welcomeFocus = WelcomeFocus.FIND
        startSearch()
        step = SetupStep.FIND
    }

    /**
     * Looks for servers for [DiscoveryWindow], keeping what it finds. The search step runs it, so
     * leaving the step stops it; an unavailable discovery ends it with what it had.
     */
    suspend fun search() {
        try {
            withTimeoutOrNull(DiscoveryWindow) {
                discovery()
                    .transformWhile { found ->
                        if (found is TvheadendDiscoveryState.Discovering) emit(found.servers)
                        found is TvheadendDiscoveryState.Discovering
                    }
                    .collect(::showServers)
            }
        } finally {
            searching = false
        }
    }

    private fun showServers(found: List<DiscoveredTvheadendServer>) {
        val target = findFocus
        // The first find takes focus, and a server that left the list hands it on.
        if ((servers.isEmpty() && found.isNotEmpty()) || (target != null && target !in found)) {
            focusFind(found.firstOrNull())
        }
        servers = found
    }

    /** Remembers which choice the user moved to, so a changed list can tell whether it vanished. */
    fun onFindFocused(server: DiscoveredTvheadendServer?) {
        findFocus = server
    }

    /** Takes a found server's address and port and goes on to the sign-in. */
    fun chooseServer(server: DiscoveredTvheadendServer) {
        chosenServer = server
        serverChoice = ServerChoice.FOUND
        form.updateHost(server.host)
        form.updatePort(server.port.toString())
        addressErrorsShown = false
        focusSignIn(firstEmptySignInField() ?: SignInFocus.CONNECT)
        step = SetupStep.SIGN_IN
    }

    private fun startSearch() {
        servers = emptyList()
        searching = true
        searchRequest += 1
        focusFind(null)
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
        val previous = step.previous(serverChoice) ?: return false
        when (step) {
            SetupStep.CONNECTING -> sessionWork?.cancel()
            // The tested profile stays connected only to be saved by "Start watching".
            SetupStep.DONE -> replaceSessionWork { session.disconnect() }
            else -> Unit
        }
        when (previous) {
            SetupStep.WELCOME -> welcomeFocus = if (step == SetupStep.FIND) WelcomeFocus.FIND else WelcomeFocus.TYPE
            SetupStep.FIND -> returnToSearch()
            SetupStep.ADDRESS -> focusAddress(AddressFocus.CONTINUE)
            SetupStep.SIGN_IN -> focusSignIn(SignInFocus.CONNECT)
            else -> Unit
        }
        step = previous
        return true
    }

    /** Back on the search: the chosen server again, or a fresh search where the last one found nothing. */
    private fun returnToSearch() {
        when {
            servers.isEmpty() -> startSearch()
            serverChoice == ServerChoice.FOUND -> focusFind(chosenServer?.takeIf { it in servers } ?: servers.first())
            else -> focusFind(null)
        }
    }

    private fun focusFind(target: DiscoveredTvheadendServer?) {
        findFocus = target
        focusRequest += 1
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
            discovery: () -> Flow<TvheadendDiscoveryState>,
            scope: CoroutineScope,
        ): Saver<ConnectionSetupState, Any> = listSaver(
            save = { listOf(it.step.resumable().name, it.serverChoice.name) },
            restore = {
                ConnectionSetupState(
                    form, session, profiles, discovery, scope,
                    step = SetupStep.valueOf(it[0]),
                    choice = ServerChoice.valueOf(it[1]),
                )
            },
        )
    }
}

/** NSD discovery of announcing servers; a device without the NSD service has none to offer. */
internal fun discoverServers(context: Context): Flow<TvheadendDiscoveryState> = try {
    TvheadendDiscovery(context).state
} catch (_: IllegalArgumentException) {
    flowOf(TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.START_FAILED))
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
    discovery: () -> Flow<TvheadendDiscoveryState>,
): ConnectionSetupState {
    val form = rememberConnectionFormState()
    val scope = rememberCoroutineScope()
    return rememberSaveable(saver = ConnectionSetupState.saver(form, session, profiles, discovery, scope)) {
        ConnectionSetupState(form, session, profiles, discovery, scope)
    }
}
