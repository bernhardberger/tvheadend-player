package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupFailure
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
import at.bernhardberger.tvhplayer.ui.screens.onboarding.rememberConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.settings.rememberSavedConnectionRestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * First run: "Find your server", then the account form. A passed test saves right away, since no
 * finishing step is drawn; [onSaved] gets the name typed for the connection.
 */
@Composable
internal fun FirstRunFlow(
    session: TvheadendSession,
    owner: AppProfileOwner,
    discovery: () -> Flow<TvheadendDiscoveryState>,
    onSaved: (name: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val state = rememberConnectionSetupState(
        session = session,
        profiles = owner,
        discovery = discovery,
        onSaved = { onSaved(name) },
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
    )
    // The shared flow opens on a welcome step; here the search is the first screen.
    LaunchedEffect(state) { if (state.step == SetupStep.WELCOME) state.openSearch() }
    SaveWhenPassed(state)
    DisposableEffect(state) { onDispose(state.form::clearCredentials) }
    when (state.step) {
        SetupStep.WELCOME, SetupStep.FIND_SERVER -> {
            LaunchedEffect(state.searchRequest) { state.search(state.searchRequest) }
            FindServerScreen(
                ServerSearchUiState(state.servers, state.searching),
                onServerPicked = state::chooseServer,
                onEnterManually = state::enterAddressManually,
                onSearchAgain = state::findServer,
            )
        }
        else -> ConnectionAccountScreen(
            state.uiState(name),
            onFieldsChange = { name = state.update(it) },
            onConnect = { state.connect() },
            onBack = {
                if (!state.saving) {
                    // Back cancels a running test; the account form and the address are one screen here.
                    if (state.step == SetupStep.CONNECTING || state.step == SetupStep.DONE) state.back()
                    state.openSearch()
                }
            },
        )
    }
    FailureDialog(state, SetupMode.FIRST_RUN)
}

/**
 * The editor for the connection in use, in the shared edit mode: Save tests the change, and a
 * passed test saves it. Cancel or a failed test reconnects the saved connection. [focusPassword]
 * opens it on Password, for a saved password that can no longer be read.
 */
@Composable
internal fun EditConnectionFlow(
    session: TvheadendSession,
    owner: AppProfileOwner,
    name: String,
    onClose: () -> Unit,
    onSaved: (name: String) -> Unit,
    focusPassword: Boolean = false,
) {
    var draftName by rememberSaveable { mutableStateOf(name) }
    val state = rememberConnectionSetupState(
        session = session,
        profiles = owner,
        discovery = { emptyFlow() },
        mode = SetupMode.EDIT,
        restoreSaved = rememberSavedConnectionRestore(session, owner),
        onSaved = { onSaved(draftName) },
        beforeTest = { owner.setSetupTrialConnection(true) },
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
    )
    // The unsaved-change baseline survives rotation; a typed password always counts as a change.
    var original by rememberSaveable(stateSaver = BaselineSaver) { mutableStateOf<ConnectionFields?>(null) }
    LaunchedEffect(state) {
        state.loadSaved()
        if (original == null) original = state.uiState(draftName).fields.copy(password = "")
    }
    SaveWhenPassed(state)
    DisposableEffect(state) { onDispose(state.form::clearCredentials) }
    original?.let { loaded ->
        ConnectionEditor(
            state.uiState(draftName),
            original = loaded,
            onFieldsChange = { draftName = state.update(it) },
            onSave = { state.connect() },
            onCancel = onClose,
            focusPassword = focusPassword,
        )
        FailureDialog(state, SetupMode.EDIT)
    }
}

@Composable
private fun SaveWhenPassed(state: ConnectionSetupState) {
    LaunchedEffect(state, state.step) { if (state.step == SetupStep.DONE) state.save() }
}

/** Cancel returns to the form with what was typed; the dialog's other button is the shared action. */
@Composable
private fun FailureDialog(state: ConnectionSetupState, mode: SetupMode) {
    val failure = state.failure?.takeIf { state.step == SetupStep.FAILED && !state.saving && it.showsDialog }
    if (failure != null) ConnectionFailureDialog(failure, mode, onCancel = { state.back() }, onAction = state::onAction)
}

/** Holds no password: the baseline's password is always empty. */
private val BaselineSaver: Saver<ConnectionFields?, Any> = listSaver<ConnectionFields?, String>(
    save = { fields -> fields?.let { listOf(it.address, it.port, it.username, it.name) }.orEmpty() },
    restore = { values ->
        if (values.isEmpty()) null else ConnectionFields(values[0], values[1], values[2], name = values[3])
    },
)

/** The form's values, with the name kept by the screen: the shared form has no name field. */
private fun ConnectionSetupState.uiState(name: String): ConnectionUiState = ConnectionUiState(
    fields = ConnectionFields(form.host, form.port, form.username, form.password, name),
    phase = when {
        step == SetupStep.CONNECTING || step == SetupStep.DONE || saving -> ConnectionPhase.Connecting(form.host.trim())
        step == SetupStep.FAILED && failure == SetupFailure.SIGN_IN_REJECTED -> ConnectionPhase.Refused
        step == SetupStep.FAILED && failure == SetupFailure.NOT_TVHEADEND -> ConnectionPhase.NotTvheadend
        else -> ConnectionPhase.Editing
    },
    addressError = hostError,
    portError = portError,
    signInError = signInError,
    passwordSaved = form.hasSavedPassword,
)

/** Hands edited fields to the shared form and returns the name, which stays with the screen. */
private fun ConnectionSetupState.update(fields: ConnectionFields): String {
    if (fields.address != form.host) form.updateHost(fields.address)
    if (fields.port != form.port) form.updatePort(fields.port)
    if (fields.username != form.username) form.updateUsername(fields.username)
    if (fields.password != form.password) form.updatePassword(fields.password)
    return fields.name
}
