package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.screens.onboarding.ConnectionSetupState
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupFailure
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep
import at.bernhardberger.tvhplayer.ui.screens.onboarding.rememberConnectionSetupState
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
}

/**
 * The editor for the connection in use, in the shared edit mode: Save tests the change, and a
 * passed test saves it. Cancel or a failed test reconnects the saved connection.
 */
@Composable
internal fun EditConnectionFlow(
    session: TvheadendSession,
    owner: AppProfileOwner,
    name: String,
    onClose: () -> Unit,
    onSaved: (name: String) -> Unit,
) {
    var draftName by rememberSaveable { mutableStateOf(name) }
    val state = rememberConnectionSetupState(
        session = session,
        profiles = owner,
        discovery = { emptyFlow() },
        mode = SetupMode.EDIT,
        restoreSaved = {
            val active = owner.serverProfile.value as? ServerProfileReadResult.Available
            if (active != null) session.connect(active.profile) else session.disconnect()
            owner.setSetupTrialConnection(false)
        },
        onSaved = { onSaved(draftName) },
        beforeTest = { owner.setSetupTrialConnection(true) },
        sessionLock = owner.connectionChanges,
        connectionGeneration = { owner.configurationGeneration.value },
    )
    var original by remember { mutableStateOf<ConnectionFields?>(null) }
    LaunchedEffect(state) {
        state.loadSaved()
        original = state.uiState(draftName).fields
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
        )
    }
}

@Composable
private fun SaveWhenPassed(state: ConnectionSetupState) {
    LaunchedEffect(state, state.step) { if (state.step == SetupStep.DONE) state.save() }
}

/** The form's values, with the name kept by the screen: the shared form has no name field. */
private fun ConnectionSetupState.uiState(name: String): ConnectionUiState = ConnectionUiState(
    fields = ConnectionFields(form.host, form.port, form.username, form.password, name),
    phase = when {
        step == SetupStep.CONNECTING || step == SetupStep.DONE || saving -> ConnectionPhase.Connecting(form.host.trim())
        step == SetupStep.FAILED && failure == SetupFailure.SIGN_IN_REJECTED -> ConnectionPhase.Refused
        else -> ConnectionPhase.Editing
    },
    addressError = hostError,
    portError = portError,
    signInError = signInError,
)

/** Hands edited fields to the shared form and returns the name, which stays with the screen. */
private fun ConnectionSetupState.update(fields: ConnectionFields): String {
    if (fields.address != form.host) form.updateHost(fields.address)
    if (fields.port != form.port) form.updatePort(fields.port)
    if (fields.username != form.username) form.updateUsername(fields.username)
    if (fields.password != form.password) form.updatePassword(fields.password)
    return fields.name
}
