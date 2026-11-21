package at.bernhardberger.tvhplayer.touch.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupMode
import at.bernhardberger.tvhplayer.ui.screens.onboarding.SetupStep

/**
 * First run: "Find your server", then the account form. The [setup] lives in the view model, so
 * a rotation keeps what was typed and a running test or save; the app root leaves first run once
 * the setup has finished.
 */
@Composable
internal fun FirstRunFlow(setup: ConnectionSetupHolder) {
    val state = setup.state
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
        // Back cancels a running test; the account form and the address are one screen here.
        else -> ConnectionAccountScreen(
            setup.uiState(),
            onFieldsChange = setup::update,
            onConnect = { setup.submit() },
            onBack = setup::backToSearch,
        )
    }
    FailureDialog(setup, SetupMode.FIRST_RUN)
}

/**
 * The editor for the connection in use, in the shared edit mode: Save tests the change, and a
 * passed test saves it. Cancel or a failed test reconnects the saved connection. [focusPassword]
 * opens it on Password, for a saved password that can no longer be read. It shows once the saved
 * connection has loaded.
 */
@Composable
internal fun EditConnectionFlow(setup: ConnectionSetupHolder, onClose: () -> Unit, focusPassword: Boolean = false) {
    if (!setup.loaded) return
    ConnectionEditor(
        setup.uiState(),
        original = setup.baseline,
        onFieldsChange = setup::update,
        onSave = { setup.submit() },
        // Back, Cancel and the discard prompt all close here: never while a save is being written.
        onCancel = { if (!setup.saving) onClose() },
        focusPassword = focusPassword,
    )
    FailureDialog(setup, SetupMode.EDIT)
}

/** Cancel returns to the form with what was typed; the dialog's other button is the shared action. */
@Composable
private fun FailureDialog(setup: ConnectionSetupHolder, mode: SetupMode) {
    val state = setup.state
    val failure = state.failure?.takeIf { state.step == SetupStep.FAILED && setup.showsFailureDialog && it.showsDialog }
    if (failure != null) ConnectionFailureDialog(failure, mode, onCancel = { state.back() }, onAction = state::onAction)
}
