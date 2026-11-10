package at.bernhardberger.tvhplayer.ui.screens.onboarding

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The guided connection setup: which step shows, where focus lands on it, the network search,
 * the connection test and the final save. The test runs on the app's one [session] before
 * anything is saved, so a profile is only stored once it connected or the user chose to continue
 * anyway.
 *
 * Editing ([SetupMode.EDIT]), adding and entering a password test on the same session, which
 * interrupts the saved connection, so a failed or cancelled test reconnects it with [restoreSaved];
 * it stays the app's connection until the new one passed and is saved, or the user saved anyway.
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
    /** True while something plays that the test would stop; the test then asks first. */
    private val interruptsPlayback: () -> Boolean = { false },
    /**
     * Adding and editing: true when another stored server already has this host, port and
     * username. The caller then shows that server instead; nothing is stopped, tested or saved.
     */
    private val existingProfile: (host: String, port: Int, username: String) -> Boolean = { _, _, _ -> false },
    /** Adding only: offers "Add a name" next to "Start watching"; runs after that save instead of [onSaved]. */
    private val onAddName: (() -> Unit)? = null,
    /** Runs before each test connection, which replaces the session that playback uses. */
    private val beforeTest: suspend () -> Unit = {},
    /**
     * Held from a test's connect through its cleanup and restore. Settings shares one for the
     * app, so a closed setup's restore finishes before a newer setup or a switch changes the
     * session; the test's own save runs inside that hold and must not take it.
     */
    private val sessionLock: Mutex = Mutex(),
    /**
     * Changes whenever the app's saved connection changes. A test that sees it change before its
     * outcome no longer owns the session, so it neither accepts that outcome nor disconnects it.
     */
    private val connectionGeneration: () -> Long = { 0L },
    /** Recreated while the switch question was open. */
    confirmingSwitch: Boolean = false,
) {
    private val editing get() = mode.keepsSavedConnection

    /** True while the test waits for the user to confirm that it may stop what plays. */
    var confirmingSwitch by mutableStateOf(confirmingSwitch)
        private set

    /** The done step's second action, offered only while adding. */
    val offersName: Boolean get() = mode == SetupMode.ADD && onAddName != null

    var step by mutableStateOf(step)
        private set
    var failure by mutableStateOf<SetupFailure?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    /** The servers the search found so far, in the SDK's order. */
    var servers by mutableStateOf(emptyList<DiscoveredTvheadendServer>())
        private set

    /** True while the search screen's search is running. */
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

    /** First run and adding start on the search; editing starts on Continue, which keeps the saved address. */
    var addressFocus by mutableStateOf(if (mode == SetupMode.EDIT) AddressFocus.CONTINUE else AddressFocus.SEARCH)
        private set
    var signInFocus by mutableStateOf(SignInFocus.USERNAME)
        private set

    /** Changes whenever a step asks for its focus target again, even on the same step. */
    var focusRequest by mutableIntStateOf(0)
        private set

    private var addressErrorsShown by mutableStateOf(false)
    private var signInErrorShown by mutableStateOf(false)
    private var sessionWork: Job? = null
    private var accepted = false

    val hostError: Boolean get() = addressErrorsShown && form.hostMissing
    val portError: Boolean get() = addressErrorsShown && form.portInvalid
    val signInError: Boolean get() = signInErrorShown && form.credentialsIncomplete

    init {
        if (step == SetupStep.SIGN_IN) signInFocus = firstEmptySignInField() ?: SignInFocus.CONNECT
        // The search screen always shows a search; a recreated one starts its own.
        if (step == SetupStep.FIND_SERVER) startSearch()
    }

    /**
     * Fills the form with the saved connection as editing starts. A recreated editor keeps the
     * restored address and port, which may have been changed, and loads only the sign-in.
     */
    suspend fun loadSaved() {
        if (restored) form.loadSignInFrom(profiles) else form.loadFrom(profiles)
        // Entering a password opens on Account with the stored username, so focus goes to the password.
        if (mode == SetupMode.PASSWORD && step == SetupStep.SIGN_IN) {
            focusSignIn(firstEmptySignInField() ?: SignInFocus.CONNECT)
        }
    }

    fun enterAddress() {
        focusAddress(AddressFocus.SEARCH)
        step = SetupStep.ADDRESS
    }

    /** Opens the search screen, which searches right away. */
    fun openSearch() {
        startSearch()
        step = SetupStep.FIND_SERVER
    }

    /** Searches again on the search screen, without moving focus away from the action. */
    fun findServer() {
        startSearch()
    }

    /** Leaves a search that found nothing for the address field. */
    fun enterAddressManually() {
        focusAddress(AddressFocus.HOST)
        step = SetupStep.ADDRESS
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
        // Focus only moves when the server it was on has gone.
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
        step = SetupStep.ADDRESS
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

    /**
     * Starts the test when the sign-in is complete; otherwise shows why and focuses the empty field.
     * While something plays, the test first asks, unless the user already [confirmed] it.
     */
    fun connect(confirmed: Boolean = false) {
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
        connect(profile, confirmed)
    }

    private fun connect(profile: ServerProfile, confirmed: Boolean) {
        if (mode.keepsSavedConnection &&
            existingProfile(form.host.trim(), form.port.trim().toInt(), form.username.trim())
        ) return
        if (!confirmed && interruptsPlayback()) {
            confirmingSwitch = true
            return
        }
        confirmingSwitch = false
        failure = null
        step = SetupStep.CONNECTING
        replaceSessionWork { test(profile) }
    }

    /** The confirmation's "Switch": the test runs and stops what plays. */
    fun confirmSwitch() {
        confirmingSwitch = false
        connect(confirmed = true)
    }

    /** The confirmation's "Keep watching" or Back: nothing changes and focus returns to Continue. */
    fun keepWatching() {
        confirmingSwitch = false
        focusSignIn(SignInFocus.CONNECT)
    }

    /**
     * Holds a passed connection for the final action. Cancellation, Back and disposal restore
     * the old connection until that action has committed; no draft can outlive this flow.
     */
    private suspend fun test(profile: ServerProfile) {
        var settled = false
        var passed = false
        accepted = false
        val generation = connectionGeneration()
        val owned = { connectionGeneration() == generation }
        try {
            beforeTest()
            val result = session.connectForSetup(profile, owned)
            passed = result == null
            if (result != null) {
                // Reconnected before the failure shows, so "Save anyway" cannot land before it.
                if (editing && owned()) withContext(NonCancellable) { restoreSaved() }
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
                // Publishing a save ends first run, which cancels this test before the save returns,
                // so only the owner can say whether a profile now keeps this connection. This also
                // waits out a save in progress; one it never took leaves nothing saved.
                val saved = profiles.serverSettings.firstOrNull()?.host?.isNotBlank() == true
                if (shouldDisconnectTest(passed, saved) && owned()) session.disconnect()
                if (editing && owned()) restoreSaved()
            }
        }
    }

    private fun shouldDisconnectTest(passed: Boolean, saved: Boolean): Boolean = passed && (editing || !saved)

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
     * leaves the first-run flow, and [onSaved] the editor. With [addName] ("Add a name") the name
     * step follows instead.
     */
    fun save(addName: Boolean = false) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                commit(addName)
            } finally {
                saving = false
            }
        }
    }

    private suspend fun commit(addName: Boolean): Boolean {
        val generation = connectionGeneration()
        var saved = false
        // Hand the tested connection to the save, releasing the shared lock before the owner's
        // command needs it. The save's finally owns cleanup if admission or persistence fails.
        accepted = true
        try {
            sessionWork?.cancelAndJoin()
            saved = form.submit(profiles) == ConnectionFormFeedback.SAVED
            if (saved) {
                form.clearPassword()
                val named = onAddName
                if (addName && named != null) named() else onSaved()
            }
            return saved
        } finally {
            if (!saved) {
                accepted = false
                if (currentCoroutineContext().isActive && step == SetupStep.DONE) {
                    // Persistence failed, not the connection. Keep the tested connection for
                    // retry or Back, still behind the same app-wide cleanup barrier.
                    sessionWork = scope.launch {
                        sessionLock.withLock {
                            try {
                                awaitCancellation()
                            } finally {
                                if (!accepted) withContext(NonCancellable) { cleanupUncommitted(generation) }
                            }
                        }
                    }
                } else withContext(NonCancellable) {
                    sessionLock.withLock { cleanupUncommitted(generation) }
                }
            }
        }
    }

    private suspend fun cleanupUncommitted(generation: Long) {
        val configured = profiles.serverSettings.firstOrNull()?.host?.isNotBlank() == true
        if (connectionGeneration() == generation) {
            if (editing || !configured) session.disconnect()
            if (editing) restoreSaved()
        }
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
            SetupStep.ADDRESS -> focusAddress(if (step == SetupStep.FIND_SERVER) AddressFocus.SEARCH else AddressFocus.CONTINUE)
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
            interruptsPlayback: () -> Boolean = { false },
            existingProfile: (String, Int, String) -> Boolean = { _, _, _ -> false },
            onAddName: (() -> Unit)? = null,
            beforeTest: suspend () -> Unit = {},
            sessionLock: Mutex = Mutex(),
            connectionGeneration: () -> Long = { 0L },
        ): Saver<ConnectionSetupState, Any> = listSaver(
            // Only an anonymous question can survive the form's credential-free restoration.
            // Otherwise Account reopens without the question, so sign-in must be entered again.
            save = { listOf(it.step.resumable().name,
                it.confirmingSwitch && it.form.username.isEmpty() &&
                    it.form.password.isEmpty() && !it.form.hasSavedPassword) },
            restore = {
                ConnectionSetupState(
                    form, session, profiles, discovery, scope, mode,
                    step = SetupStep.valueOf(it[0] as String),
                    restoreSaved = restoreSaved,
                    onSaved = onSaved,
                    restored = true,
                    interruptsPlayback = interruptsPlayback,
                    existingProfile = existingProfile,
                    onAddName = onAddName,
                    beforeTest = beforeTest,
                    sessionLock = sessionLock,
                    connectionGeneration = connectionGeneration,
                    confirmingSwitch = it[1] as Boolean,
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
 * Once [owned] turns false, the app's saved connection changed under the test: its outcome is not
 * this test's, so the test fails without disconnecting the session it no longer owns.
 */
internal suspend fun TvheadendSession.connectForSetup(
    profile: ServerProfile,
    owned: () -> Boolean = { true },
): SetupFailure? {
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
            if (!owned()) return SetupFailure.OTHER
            passed = outcome is SessionState.Ready
            return (outcome as? SessionState.Unavailable)?.reason?.toSetupFailure()
        }
        return SetupFailure.OTHER
    } finally {
        if (!passed && owned()) withContext(NonCancellable) { disconnect() }
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
    interruptsPlayback: () -> Boolean = { false },
    existingProfile: (host: String, port: Int, username: String) -> Boolean = { _, _, _ -> false },
    onAddName: (() -> Unit)? = null,
    beforeTest: suspend () -> Unit = {},
    sessionLock: Mutex = remember { Mutex() },
    connectionGeneration: () -> Long = { 0L },
): ConnectionSetupState {
    val scope = rememberCoroutineScope()
    val currentRestoreSaved by rememberUpdatedState(restoreSaved)
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentInterrupts by rememberUpdatedState(interruptsPlayback)
    val currentExisting by rememberUpdatedState(existingProfile)
    val currentAddName by rememberUpdatedState(onAddName)
    val currentBeforeTest by rememberUpdatedState(beforeTest)
    val currentGeneration by rememberUpdatedState(connectionGeneration)
    val generation = { currentGeneration() }
    val prepareTest: suspend () -> Unit = { currentBeforeTest() }
    val restore: suspend () -> Unit = { currentRestoreSaved() }
    val saved = { currentOnSaved() }
    val interrupts = { currentInterrupts() }
    val existing = { host: String, port: Int, username: String -> currentExisting(host, port, username) }
    val addName: (() -> Unit)? = if (onAddName != null) { { currentAddName?.invoke() } } else null
    return rememberSaveable(
        saver = ConnectionSetupState.saver(form, session, profiles, discovery, scope, mode, restore, saved,
            interrupts, existing, addName, prepareTest, sessionLock, generation),
    ) {
        ConnectionSetupState(
            form, session, profiles, discovery, scope, mode,
            restoreSaved = restore,
            onSaved = saved,
            interruptsPlayback = interrupts,
            existingProfile = existing,
            onAddName = addName,
            beforeTest = prepareTest,
            sessionLock = sessionLock,
            connectionGeneration = generation,
        )
    }
}
