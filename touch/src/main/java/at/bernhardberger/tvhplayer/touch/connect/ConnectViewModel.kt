package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.SaveServerProfileResult
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupFailure
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Keeps first run and the connection editor through configuration changes, so typed values and
 * a running test or save outlive a rotation. Only memory holds them: after process death the
 * editor reloads the saved connection and first run starts at the search.
 */
internal class ConnectViewModel(
    private val session: TvheadendSession,
    private val owner: AppProfileOwner,
) : ViewModel() {
    /** The current activity's server search; replaced on every composition, never kept past it. */
    var discovery: () -> Flow<TvheadendDiscoveryState> = { emptyFlow() }

    /** True while a removal runs, when the owner briefly reports no connection before it settles. */
    var removing by mutableStateOf(false)
        private set

    private var firstRun: ConnectionSetupHolder? = null
    private var editor: ConnectionSetupHolder? = null

    fun firstRun(): ConnectionSetupHolder = firstRun ?: ConnectionSetupHolder(
        session, owner, SetupMode.FIRST_RUN, viewModelScope, { discovery() }, target = null, name = "",
        requireSignIn = false,
    ).also { firstRun = it }

    /** First run is settling while its save runs or failed; it stays until it finished. */
    val firstRunSettling: Boolean get() = firstRun?.settling == true

    fun releaseFirstRun() {
        firstRun?.close()
        firstRun = null
    }

    /**
     * The editor of connection [id]; a holder edits one connection only and is never reused after
     * its release. [restoreSaved] reconnects the saved connection after a test; it holds no activity.
     */
    fun editor(
        id: ServerProfileId,
        name: String,
        requireSignIn: Boolean,
        restoreSaved: suspend () -> Unit,
    ): ConnectionSetupHolder = editor?.takeIf { it.target == id } ?: run {
        releaseEditor()
        ConnectionSetupHolder(
            session, owner, SetupMode.EDIT, viewModelScope, { emptyFlow() }, id, name, requireSignIn, restoreSaved,
        ).also { editor = it }
    }

    /** The editor open now, if any; it stays on its connection while the owner can't list connections. */
    val openEditor: ConnectionSetupHolder? get() = editor

    fun releaseEditor() {
        editor?.close()
        editor = null
    }

    fun remove(id: ServerProfileId) {
        if (removing) return
        removing = true
        releaseEditor()
        viewModelScope.launch {
            try {
                // A failed removal leaves the connection where it was; the owner restores it.
                owner.remove(id)
            } finally {
                removing = false
            }
        }
    }

    override fun onCleared() {
        releaseFirstRun()
        releaseEditor()
    }
}

/**
 * One run of the shared setup flow with its form, the name the screen adds, and its own scope
 * under the view model's. A passed test saves at once, writing the connection and its name in
 * one owner command for [target], or as a new connection in first run.
 */
@Suppress("LongParameterList") // The setup's owners and what it edits, handed in by the view model.
internal class ConnectionSetupHolder(
    session: TvheadendSession,
    private val owner: AppProfileOwner,
    private val mode: SetupMode,
    parent: CoroutineScope,
    discovery: () -> Flow<TvheadendDiscoveryState>,
    /** The connection edited; first run's save sets it, so a retry updates what an applied write stored. */
    target: ServerProfileId?,
    name: String,
    /** A saved sign-in that can no longer be read: a password connection stays one. */
    private val requireSignIn: Boolean,
    restoreSaved: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext.job))
    private val form = ConnectionFormState()
    private var name by mutableStateOf(name)
    private var signInRequired by mutableStateOf(false)
    private var released = false
    private val profiles = SetupProfileEditor(owner, mode, target) { this.name }

    /** The connection this holder edits, or the one first run's applied write stored. */
    val target: ServerProfileId? get() = profiles.target

    /** The editor shows once the saved connection is in the form, so a late load overwrites nothing. */
    var loaded by mutableStateOf(mode == SetupMode.FIRST_RUN)
        private set

    /** What the editor counts as unchanged; never holds a password. */
    var baseline by mutableStateOf(ConnectionFields())
        private set

    var finished by mutableStateOf(false)
        private set

    val state = ConnectionSetupState(
        form, session, profiles, discovery, scope, mode,
        restoreSaved = restoreSaved,
        onSaved = { finished = true },
        beforeTest = if (mode == SetupMode.EDIT) ({ owner.setSetupTrialConnection(true) }) else ({}),
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
    )

    val saving: Boolean get() = state.saving

    private val saveFailed: Boolean get() = !saving && form.feedback == ConnectionFormFeedback.SAVE_FAILED

    val settling: Boolean get() = !finished && (saving || saveFailed)

    init {
        // The shared flow opens on a welcome step; here the search is the first screen.
        if (state.step == SetupStep.WELCOME) state.openSearch()
        if (mode == SetupMode.EDIT) scope.launch {
            state.loadSaved()
            baseline = fields().copy(password = "")
            loaded = true
        }
        // No finishing step is drawn, so a passed test saves right away.
        scope.launch { snapshotFlow { state.step }.collect { if (it == SetupStep.DONE) state.save() } }
    }

    fun uiState(): ConnectionUiState = ConnectionUiState(
        fields = fields(),
        phase = if (saveFailed) ConnectionPhase.Editing else phase(),
        addressError = state.hostError,
        portError = state.portError,
        signInError = state.signInError || (signInRequired && signInMissing),
        passwordSaved = form.hasSavedPassword,
        saving = saving,
        saveFailed = saveFailed,
    )

    /** The failure dialog stays away while the write, not the connection, is what failed. */
    val showsFailureDialog: Boolean get() = !saving && !saveFailed

    /** Hands edited fields to the shared form; a changed connection drops a test or write that ended. */
    fun update(fields: ConnectionFields) {
        if (released) return
        name = fields.name
        val changed = fields.address != form.host || fields.port != form.port ||
            fields.username != form.username || fields.password != form.password
        if (!changed) return
        if (!saving && ended) {
            state.back()
            form.clearFeedback()
        }
        if (fields.address != form.host) form.updateHost(fields.address)
        if (fields.port != form.port) form.updatePort(fields.port)
        if (fields.username != form.username) form.updateUsername(fields.username)
        if (fields.password != form.password) form.updatePassword(fields.password)
    }

    /** Connect or Save: retries a failed write without testing again, or tests the connection, which then saves. */
    fun submit() {
        when {
            released || saving -> Unit
            saveFailed -> state.save()
            requireSignIn && signInMissing -> signInRequired = true
            else -> {
                form.clearFeedback()
                state.connect()
            }
        }
    }

    /** Back on first run's account form: cancels a running test and returns to the search. */
    fun backToSearch() {
        if (saving) return
        if (state.step == SetupStep.CONNECTING || state.step == SetupStep.DONE) state.back()
        form.clearFeedback()
        state.openSearch()
    }

    /** Cancels a running test; a write the owner already admitted completes, and nothing new starts. */
    fun close() {
        released = true
        scope.cancel()
        form.clearCredentials()
    }

    private fun phase(): ConnectionPhase {
        val failure = state.failure.takeIf { state.step == SetupStep.FAILED }
        return when {
            state.step == SetupStep.CONNECTING || state.step == SetupStep.DONE || saving ->
                ConnectionPhase.Connecting(form.host.trim())
            failure == SetupFailure.SIGN_IN_REJECTED -> ConnectionPhase.Refused
            failure == SetupFailure.NOT_TVHEADEND -> ConnectionPhase.NotTvheadend
            else -> ConnectionPhase.Editing
        }
    }

    /** A passed test, or an untested connection, whose write failed: an edit starts over. */
    private val ended: Boolean
        get() = state.step == SetupStep.DONE || (state.step == SetupStep.FAILED && saveFailed)

    private val signInMissing: Boolean
        get() = form.username.isBlank() || (form.password.isBlank() && !form.hasSavedPassword)

    private fun fields() = ConnectionFields(form.host, form.port, form.username, form.password, name)
}

/**
 * The setup's writes for one connection: host, port, sign-in and [name] in one owner command for
 * [target], or a new connection while it is null. Any outcome but a write the owner then reports
 * in use throws, which the shared form shows as not saved.
 */
private class SetupProfileEditor(
    private val owner: AppProfileOwner,
    private val mode: SetupMode,
    target: ServerProfileId?,
    private val name: () -> String,
) : ConnectionProfileEditor {
    var target: ServerProfileId? = target
        private set

    override val serverSettings: Flow<ServerSettings> get() = owner.serverSettings

    override suspend fun loadServerForEditing(
        applyAvailable: (host: String, port: Int, username: String, password: String) -> Unit,
    ) {
        owner.loadServerForEditing(target ?: return, applyAvailable)
    }

    override suspend fun saveServer(host: String, htspPort: Int) = write { id ->
        if (id == null) owner.addAnonymous(host, htspPort, name())
        else owner.updateAnonymous(id, host, htspPort, name())
    }

    override suspend fun savePasswordServer(host: String, htspPort: Int, username: String, password: String) =
        write { id ->
            if (id == null) owner.addPassword(host, htspPort, username, password, name())
            else owner.updatePassword(id, host, htspPort, username, password, name())
        }

    /** The setup offers no removal; the Connections page removes a connection. */
    override suspend fun clearProfile() = Unit

    /**
     * First run finds this connection already stored when an earlier write applied but was never
     * read back: it completes that one as an update with the typed values and name, not a second add.
     */
    private suspend fun write(command: suspend (ServerProfileId?) -> SaveServerProfileResult) {
        val result = command(target)
        val existing = (result as? SaveServerProfileResult.AlreadyExists)?.id
        if (existing != null && target == null && mode == SetupMode.FIRST_RUN) {
            target = existing
            stored(command(existing))
        } else stored(result)
    }

    /**
     * Any outcome but a write the owner then reports in use fails the save, which the shared form
     * shows as not saved. An applied write is kept as [target], so the retry updates it.
     */
    private fun stored(result: SaveServerProfileResult) {
        val id = (result as? SaveServerProfileResult.Applied)?.id ?: error("Connection not saved")
        target = id
        val active = (owner.profiles.value as? ServerProfileListResult.Available)?.activeId
        val published = (owner.serverProfile.value as? ServerProfileReadResult.Available)?.id
        val inUse = active == id && (mode == SetupMode.EDIT || published == id)
        check(inUse) { "Connection saved but not read back" }
        owner.setSetupTrialConnection(false)
    }
}
