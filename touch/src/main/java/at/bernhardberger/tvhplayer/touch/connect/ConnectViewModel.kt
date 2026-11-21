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
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
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
        session, owner, SetupMode.FIRST_RUN, viewModelScope, { discovery() }, name = "", requireSignIn = false,
    ).also { firstRun = it }

    /** First run is settling while its save or naming runs or failed; it stays until it finished. */
    val firstRunSettling: Boolean get() = firstRun?.settling == true

    fun releaseFirstRun() {
        firstRun?.close()
        firstRun = null
    }

    /** [restoreSaved] reconnects the saved connection after a test; it holds no activity. */
    fun editor(name: String, requireSignIn: Boolean, restoreSaved: suspend () -> Unit): ConnectionSetupHolder =
        editor ?: ConnectionSetupHolder(
            session, owner, SetupMode.EDIT, viewModelScope, { emptyFlow() }, name, requireSignIn, restoreSaved,
        ).also { editor = it }

    fun releaseEditor() {
        editor?.close()
        editor = null
    }

    fun remove(id: ServerProfileId) {
        if (removing) return
        removing = true
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
 * under the view model's. A passed test saves at once; the typed name is then written to the
 * profile that save produced or edited, and [finished] turns true only once both are done.
 */
internal class ConnectionSetupHolder(
    session: TvheadendSession,
    private val owner: AppProfileOwner,
    mode: SetupMode,
    parent: CoroutineScope,
    discovery: () -> Flow<TvheadendDiscoveryState>,
    name: String,
    /** A saved sign-in that can no longer be read: a password connection stays one. */
    private val requireSignIn: Boolean,
    restoreSaved: suspend () -> Unit = {},
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext.job))
    private val form = ConnectionFormState()
    private var name by mutableStateOf(name)
    private var naming by mutableStateOf(false)
    private var nameFailed by mutableStateOf(false)
    private var signInRequired by mutableStateOf(false)
    private var savedId: ServerProfileId? = null
    private var savedLabel: String? = null

    /** The editor shows once the saved connection is in the form, so a late load overwrites nothing. */
    var loaded by mutableStateOf(mode == SetupMode.FIRST_RUN)
        private set

    /** What the editor counts as unchanged; never holds a password. */
    var baseline by mutableStateOf(ConnectionFields())
        private set

    var finished by mutableStateOf(false)
        private set

    val state = ConnectionSetupState(
        form, session, owner, discovery, scope, mode,
        restoreSaved = restoreSaved,
        onSaved = ::onSaved,
        beforeTest = if (mode == SetupMode.EDIT) ({ owner.setSetupTrialConnection(true) }) else ({}),
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
    )

    val saving: Boolean get() = state.saving || naming

    val settling: Boolean get() = !finished && (saving || nameFailed)

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

    fun uiState(): ConnectionUiState {
        val saveFailed = !saving && (nameFailed ||
            (state.step == SetupStep.DONE && form.feedback == ConnectionFormFeedback.SAVE_FAILED))
        return ConnectionUiState(
            fields = fields(),
            phase = if (saveFailed) ConnectionPhase.Editing else phase(),
            addressError = state.hostError,
            portError = state.portError,
            signInError = state.signInError || (signInRequired && signInMissing),
            passwordSaved = form.hasSavedPassword,
            saving = saving,
            saveFailed = saveFailed,
        )
    }

    /** Hands edited fields to the shared form; a changed connection drops a passed test that failed to save. */
    fun update(fields: ConnectionFields) {
        name = fields.name
        val changed = fields.address != form.host || fields.port != form.port ||
            fields.username != form.username || fields.password != form.password
        if (!changed) return
        if (state.step == SetupStep.DONE && !saving) {
            state.back()
            form.clearFeedback()
            nameFailed = false
        }
        if (fields.address != form.host) form.updateHost(fields.address)
        if (fields.port != form.port) form.updatePort(fields.port)
        if (fields.username != form.username) form.updateUsername(fields.username)
        if (fields.password != form.password) form.updatePassword(fields.password)
    }

    /**
     * Connect or Save: retries the name or the write that failed, without testing again, or tests
     * the connection, which then saves.
     */
    fun submit() {
        when {
            saving -> Unit
            nameFailed -> {
                nameFailed = false
                applyName()
            }
            state.step == SetupStep.DONE && form.feedback == ConnectionFormFeedback.SAVE_FAILED -> state.save()
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
        nameFailed = false
        state.openSearch()
    }

    fun close() {
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

    private val signInMissing: Boolean
        get() = form.username.isBlank() || (form.password.isBlank() && !form.hasSavedPassword)

    private fun fields() = ConnectionFields(form.host, form.port, form.username, form.password, name)

    /** The shared save's success: [ConnectionSetupState] calls this before it ends saving. */
    private fun onSaved() {
        naming = true
        // The owner publishes the saved profile before the save returns, so it is the one in use.
        savedId = (owner.serverProfile.value as? ServerProfileReadResult.Available)?.id
        savedLabel = (owner.profiles.value as? ServerProfileListResult.Available)
            ?.profiles?.find { it.id == savedId }?.label
        applyName()
    }

    private fun applyName() {
        naming = true
        scope.launch {
            try {
                val id = savedId
                val applied = when {
                    id == null -> false
                    name.trim() == savedLabel.orEmpty().trim() -> true
                    else -> owner.rename(id, name) is ServerProfileWriteResult.Applied
                }
                if (applied) finished = true else nameFailed = true
            } finally {
                naming = false
            }
        }
    }
}
