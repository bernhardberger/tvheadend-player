package at.bernhardberger.tvhplayer.ui.screens.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import at.bernhardberger.tvhplayer.settings.rememberConnectionFormState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The guided connection setup: which step shows, where focus lands on it, the network search,
 * the connection test and the final save. The test runs on the app's one [session] before
 * anything is saved, so a profile is only stored once it connected or the user chose to continue
 * anyway.
 *
 * Editing ([SetupMode.EDIT]) tests on the same session, which interrupts the saved connection, so
 * a failed or cancelled test reconnects it with [restoreSaved]; it stays the app's connection until
 * the new one passed and is saved, or the user saved anyway.
 */
@Stable
internal class ConnectionSetupState(
    val form: ConnectionFormState,
    private val session: TvheadendSession,
    private val profiles: ConnectionProfileEditor,
    /** Starts one network search; collecting it runs discovery until cancelled. */
    private val discovery: () -> Flow<TvheadendDiscoveryState>,
    private val scope: CoroutineScope,
    val mode: SetupMode = SetupMode.FIRST_RUN,
    step: SetupStep = mode.firstStep,
    private val restoreSaved: suspend () -> Unit = {},
    /** Runs once the connection is saved; the editor leaves with it. */
    private val onSaved: () -> Unit = {},
    /** True when recreated from saved state, which keeps the address but not the sign-in. */
    private val restored: Boolean = false,
) {
    private val editing get() = mode == SetupMode.EDIT

    var step by mutableStateOf(step)
        private set
    var failure by mutableStateOf<SetupFailure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    /** The servers the search found so far, in the SDK's order. */
    var servers by mutableStateOf(emptyList<DiscoveredTvheadendServer>())
        private set

    /** True while the Server screen's inline search is running. */
    var searching by mutableStateOf(false)
        private set

    /** Changes whenever a new search should start. */
    var searchRequest by mutableIntStateOf(0)
        private set

    /** The focused discovery result, or null for the search action. */
    var findFocus by mutableStateOf<DiscoveredTvheadendServer?>(null)
        private set
    var findFocusRequest by mutableIntStateOf(0)
        private set
    private var handledFindFocusRequest = 0

    /** A removed result's focus correction belongs to that visit, not a later Server entry. */
    fun takeFindFocusRequest(request: Int): Boolean {
        if (request <= handledFindFocusRequest) return false
        handledFindFocusRequest = request
        return true
    }

    var addressFocus by mutableStateOf(AddressFocus.SEARCH)
        private set
    var signInFocus by mutableStateOf(SignInFocus.USERNAME)
        private set

    /** Changes whenever a step asks for its focus target again, even on the same step. */
    var focusRequest by mutableIntStateOf(0)
        private set

    private var addressErrorsShown by mutableStateOf(false)
    private var signInErrorShown by mutableStateOf(false)
    private var sessionWork: Job? = null
    private val sessionLock = Mutex()
    private var accepted = false

    val hostError: Boolean get() = addressErrorsShown && form.hostMissing
    val portError: Boolean get() = addressErrorsShown && form.portInvalid
    val signInError: Boolean get() = signInErrorShown && form.credentialsIncomplete

    init {
        if (step == SetupStep.SIGN_IN) signInFocus = firstEmptySignInField() ?: SignInFocus.CONNECT
    }

    /**
     * Fills the form with the saved connection as editing starts. A recreated editor keeps the
     * restored address and port, which may have been changed, and loads only the sign-in.
     */
    suspend fun loadSaved() {
        if (restored) form.loadSignInFrom(profiles) else form.loadFrom(profiles)
    }

    fun enterAddress() {
        focusAddress(AddressFocus.SEARCH)
        step = SetupStep.ADDRESS
    }

    /** Both entry points search automatically without changing the entered or saved fields. */
    fun onServerOpened() {
        findServer()
    }

    /** Starts discovery inside the Server step without moving focus away from the action. */
    fun findServer() {
        startSearch()
    }

    /**
     * Looks for servers for [DiscoveryWindow], keeping what it finds. The search step runs it, so
     * leaving the step stops it; an unavailable discovery ends it with what it had.
     */
    suspend fun search(request: Int) {
        if (request != searchRequest) return
        searching = true
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
            if (request == searchRequest) searching = false
        }
    }

    private fun showServers(found: List<DiscoveredTvheadendServer>) {
        val target = findFocus
        // Never steal focus from an address being typed while discovery answers.
        if (target != null && target !in found) {
            focusFind(found.firstOrNull())
        }
        servers = found
    }

    /** Remembers which choice the user moved to, so a changed list can tell whether it vanished. */
    fun onFindFocused(server: DiscoveredTvheadendServer?) {
        findFocus = server
    }

    /** Choosing an announced server fills its address and advances to Account. */
    fun chooseServer(server: DiscoveredTvheadendServer) {
        form.updateHost(server.host)
        form.updatePort(server.port.toString())
        addressErrorsShown = false
        continueFromAddress()
    }

    private fun startSearch() {
        servers = emptyList()
        searching = true
        searchRequest += 1
        findFocus = null
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

    /** Account is complete with both credentials or neither; a retained password also counts. */
    fun continueFromSignIn() {
        signInErrorShown = true
        val empty = firstEmptySignInField()
        if (form.credentialsIncomplete && empty != null) focusSignIn(empty) else connect()
    }

    /** Starts the test when the sign-in is complete; otherwise shows why and focuses the empty field. */
    fun connect() {
        if (step == SetupStep.CONNECTING || saving) return
        signInErrorShown = true
        val profile = form.serverProfile()
        if (profile == null) {
            if (form.hostMissing || form.portInvalid) {
                enterAddress()
                addressErrorsShown = true
                focusAddress(if (form.hostMissing) AddressFocus.HOST else AddressFocus.PORT)
            } else {
                step = SetupStep.SIGN_IN
                focusSignIn(firstEmptySignInField() ?: SignInFocus.USERNAME)
            }
            return
        }
        failure = null
        step = SetupStep.CONNECTING
        replaceSessionWork { test(profile) }
    }

    /**
     * Holds a passed connection for the final action. Cancellation, Back and disposal restore
     * the old connection until that action has committed; no draft can outlive this flow.
     */
    private suspend fun test(profile: ServerProfile) {
        var settled = false
        var passed = false
        accepted = false
        try {
            val result = session.connectForSetup(profile)
            passed = result == null
            if (result != null) {
                // Reconnected before the failure shows, so "Save anyway" cannot land before it.
                if (editing) withContext(NonCancellable) { restoreSaved() }
                settled = true
                // Back during the restore dismissed this test; a newer one may already be running.
                currentCoroutineContext().ensureActive()
                failure = result
                step = SetupStep.FAILED
                return
            }
            currentCoroutineContext().ensureActive()
            step = SetupStep.DONE
            awaitCancellation()
        } finally {
            if (!settled && !accepted) withContext(NonCancellable) {
                if (passed) session.disconnect()
                if (editing) restoreSaved()
            }
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
            SetupAction.CONTINUE_ANYWAY, SetupAction.SAVE_ANYWAY -> save()
        }
    }

    /**
     * Saves the profile ("Start watching", "Continue anyway", "Save anyway"); the app root then
     * leaves the first-run flow, and [onSaved] the editor.
     */
    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            try {
                commit()
            } finally {
                saving = false
            }
        }
    }

    private suspend fun commit(): Boolean {
        val saved = form.submit(profiles) == ConnectionFormFeedback.SAVED
        if (saved) {
            accepted = true
            sessionWork?.cancel()
            form.clearPassword()
            onSaved()
        }
        return saved
    }

    /** Draft-only, like every other edit. Back must not erase the saved connection's credentials. */
    fun clearSavedPassword() {
        form.clearCredentials()
        focusSignIn(SignInFocus.USERNAME)
    }

    /** One step back, keeping what was entered. False on the first step, where the flow lets Back go. */
    fun back(): Boolean {
        if (saving) return true
        val previous = step.previous(mode) ?: return false
        when (step) {
            // An edit's cancelled test reconnects the saved connection as it ends.
            SetupStep.CONNECTING, SetupStep.DONE -> sessionWork?.cancel()
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

    private fun focusFind(target: DiscoveredTvheadendServer?) {
        findFocus = target
        findFocusRequest += 1
    }

    private fun firstEmptySignInField(): SignInFocus? = when {
        form.username.isBlank() -> SignInFocus.USERNAME
        form.password.isBlank() && !form.hasSavedPassword -> SignInFocus.PASSWORD
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

    /**
     * Cancels the session work before and queues [work] behind it. Each piece holds [sessionLock]
     * through its cleanup, so a cancelled test's disconnect and reconnect of the saved connection
     * finish before the next test connects, also when a test queued in between was cancelled
     * while it waited.
     */
    private fun replaceSessionWork(work: suspend () -> Unit) {
        sessionWork?.cancel()
        sessionWork = scope.launch { sessionLock.withLock { work() } }
    }

    companion object {
        fun saver(
            form: ConnectionFormState,
            session: TvheadendSession,
            profiles: ConnectionProfileEditor,
            discovery: () -> Flow<TvheadendDiscoveryState>,
            scope: CoroutineScope,
            mode: SetupMode,
            restoreSaved: suspend () -> Unit,
            onSaved: () -> Unit,
        ): Saver<ConnectionSetupState, Any> = listSaver(
            save = { listOf(it.step.resumable().name) },
            restore = {
                ConnectionSetupState(
                    form, session, profiles, discovery, scope, mode,
                    step = SetupStep.valueOf(it[0]),
                    restoreSaved = restoreSaved,
                    onSaved = onSaved,
                    restored = true,
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
 * Connects [profile] and waits for this attempt's first outcome: null when the session is ready,
 * else why it failed. A failed or cancelled test disconnects, so the SDK does not keep retrying a
 * profile nobody saved; a passed one stays connected for the save to adopt.
 *
 * `connect` returns once the SDK admitted the attempt and runs it asynchronously. The disconnect
 * before it leaves the session [SessionState.Disconnected]; should it still show an earlier Ready
 * or Unavailable, that outcome is not this attempt's and is skipped, together with any repeat of
 * it, until the session shows any state that is not an outcome (Disconnected, Connecting or
 * Synchronizing). The first outcome after that counts.
 * [SessionCommandResult.NO_CHANGE] means someone else selected this configuration between the
 * disconnect and the connect, whose outcome may already be past; the test starts over once.
 */
internal suspend fun TvheadendSession.connectForSetup(profile: ServerProfile): SetupFailure? {
    var passed = false
    try {
        repeat(2) {
            disconnect()
            val stale = observation.value.sessionState.isOutcome
            when (connect(profile)) {
                SessionCommandResult.STARTED -> Unit
                SessionCommandResult.NO_CHANGE -> return@repeat
                else -> return SetupFailure.OTHER
            }
            val outcome = observation
                .map { it.sessionState }
                .dropWhile { stale && it.isOutcome }
                .first { it.isOutcome }
            passed = outcome is SessionState.Ready
            return (outcome as? SessionState.Unavailable)?.reason?.toSetupFailure()
        }
        return SetupFailure.OTHER
    } finally {
        if (!passed) withContext(NonCancellable) { disconnect() }
    }
}

private val SessionState.isOutcome: Boolean
    get() = this is SessionState.Ready || this is SessionState.Unavailable

@Composable
internal fun rememberConnectionSetupState(
    session: TvheadendSession,
    profiles: ConnectionProfileEditor,
    discovery: () -> Flow<TvheadendDiscoveryState>,
    mode: SetupMode = SetupMode.FIRST_RUN,
    restoreSaved: suspend () -> Unit = {},
    onSaved: () -> Unit = {},
    form: ConnectionFormState = rememberConnectionFormState(),
): ConnectionSetupState {
    val scope = rememberCoroutineScope()
    val currentRestoreSaved by rememberUpdatedState(restoreSaved)
    val currentOnSaved by rememberUpdatedState(onSaved)
    val restore: suspend () -> Unit = { currentRestoreSaved() }
    val saved = { currentOnSaved() }
    return rememberSaveable(
        saver = ConnectionSetupState.saver(form, session, profiles, discovery, scope, mode, restore, saved),
    ) {
        ConnectionSetupState(
            form, session, profiles, discovery, scope, mode,
            restoreSaved = restore,
            onSaved = saved,
        )
    }
}
