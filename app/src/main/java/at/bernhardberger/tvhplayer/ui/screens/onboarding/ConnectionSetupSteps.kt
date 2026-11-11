package at.bernhardberger.tvhplayer.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon as FieldIcon
import androidx.compose.material3.Text as FieldText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.components.serverAddress
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.core.DEFAULT_HTSP_PORT
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.compactServerName
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepCenteredLayout
import at.bernhardberger.tvhplayer.ui.components.GuidedStepPill
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarHero
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarStatus
import at.bernhardberger.tvhplayer.ui.components.serverAvatarLook
import at.bernhardberger.tvhplayer.ui.components.arrivalFocus
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.GuidedStepper
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField

private const val HOST_FIELD = "setup-host"
private const val PORT_FIELD = "setup-port"
private const val USERNAME_FIELD = "setup-username"
private const val PASSWORD_FIELD = "setup-password"

/**
 * Back keeps the draft and unwinds the wizard; the first step leaves for TV home or Settings.
 * [profileName] names the stored server while a password is entered again. [confirmSwitch] draws
 * the question asked before a test that stops what plays (see [ConnectionSetupState.confirmingSwitch]).
 */
@Composable
internal fun ConnectionSetupFlow(
    state: ConnectionSetupState,
    profileName: String? = null,
    confirmSwitch: (@Composable (form: ConnectionFormState, onSwitch: () -> Unit, onKeep: () -> Unit) -> Unit)? = null,
) {
    BackHandler(enabled = state.step.previous(state.mode) != null) { state.back() }
    Box(Modifier.fillMaxSize()) {
        when (state.step) {
            // Stays on top of every other step while it morphs away, so it isn't one of them.
            SetupStep.WELCOME -> Unit
            SetupStep.ADDRESS -> AddressStep(state)
            SetupStep.FIND_SERVER -> FindServerStep(state)
            SetupStep.SIGN_IN -> SignInStep(state, profileName)
            SetupStep.CONNECTING -> ConnectStep(state)
            SetupStep.FAILED -> FailureStep(state, state.failure ?: SetupFailure.OTHER)
            SetupStep.DONE -> DoneStep(state)
        }
        if (state.mode == SetupMode.FIRST_RUN) {
            SetupSignature()
            SetupWelcome(state)
        }
    }
    // Over the step, so it takes focus and Back until the user answers.
    if (state.confirmingSwitch && confirmSwitch != null)
        confirmSwitch(state.form, state::confirmSwitch, state::keepWatching)
}

@Composable
private fun SetupStepper(current: Int, failed: Boolean = false) {
    GuidedStepper(
        labels = listOf(
            stringResource(R.string.onboarding_step_server),
            stringResource(R.string.onboarding_step_sign_in),
            stringResource(R.string.onboarding_connect),
        ),
        current = current,
        failed = failed,
    )
}

/**
 * The network search, on its own screen so a list of servers can use all of it. It starts as the
 * screen opens and results don't move focus. Choosing a server fills in its address and goes on to
 * Account; a search that found nothing offers the address field directly.
 */
@Composable
private fun FindServerStep(state: ConnectionSetupState) {
    val search = remember { FocusRequester() }
    LaunchedEffect(state.findFocusRequest) {
        if (state.findFocus == null && state.takeFindFocusRequest(state.findFocusRequest)) search.requestFocus()
    }
    val request = state.searchRequest
    LaunchedEffect(request) { state.search(request) }
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.onboarding_find_title),
                body = stringResource(R.string.onboarding_find_body),
                stepper = { SetupStepper(SetupStage.SERVER.ordinal) },
            )
        },
    ) {
        GuidedStepAction(
            title = stringResource(if (state.searching) R.string.onboarding_find_searching else R.string.onboarding_search_again),
            icon = R.drawable.ic_search,
            // Stays enabled while it runs: it holds focus then, and a disabled action can't show it.
            leadingIcon = if (state.searching) {
                { CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 3.dp) }
            } else null,
            onClick = { if (!state.searching) state.findServer() },
            modifier = arrivalFocus(search).onFocusChanged {
                if (it.isFocused) state.onFindFocused(null)
            }.semantics {
                if (state.searching) disabled()
            }.testTag("setup-search-again"),
        )
        if (state.servers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_find_on_network),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
                modifier = Modifier.semantics { heading() },
            )
        }
        state.servers.forEachIndexed { index, server ->
            androidx.compose.runtime.key(server) { FoundServerRow(state, server, index) }
        }
        if (!state.searching && state.servers.isEmpty()) {
            Text(stringResource(R.string.onboarding_find_none_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            GuidedStepAction(
                title = stringResource(R.string.onboarding_enter_address_manually),
                icon = R.drawable.ic_edit,
                onClick = state::enterAddressManually,
                modifier = Modifier.testTag("setup-enter-manually"),
            )
        }
    }
}

@Composable
private fun FoundServerRow(state: ConnectionSetupState, server: DiscoveredTvheadendServer, index: Int) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.findFocusRequest) {
        if (state.findFocus == server && state.takeFindFocusRequest(state.findFocusRequest)) requester.requestFocus()
    }
    GuidedStepAction(
        title = server.name,
        subtitle = serverAddress(server.host, server.port.toString()),
        icon = R.drawable.ic_dns,
        onClick = { state.chooseServer(server) },
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) state.onFindFocused(server) }
            .testTag("setup-server-$index"),
    )
}

@Composable
private fun AddressStep(state: ConnectionSetupState) {
    val search = remember { FocusRequester() }
    val host = remember { FocusRequester() }
    val port = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        withFrameNanos { }
        when (state.addressFocus) {
            AddressFocus.SEARCH -> search
            AddressFocus.HOST -> host
            AddressFocus.PORT -> port
            AddressFocus.CONTINUE -> next
        }.requestFocus()
    }
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.onboarding_address_title),
                body = stringResource(R.string.onboarding_address_body),
                stepper = { SetupStepper(SetupStage.SERVER.ordinal) },
            )
        },
    ) {
        GuidedStepAction(
            title = stringResource(R.string.onboarding_find_server),
            icon = R.drawable.ic_search,
            onClick = state::openSearch,
            modifier = Modifier.focusRequester(search).testTag("setup-find-server"),
        )
        AddressFields(state, host, port, onPortDone = { state.focusAddress(AddressFocus.CONTINUE) })
        MainActionButton(
            label = R.string.onboarding_continue,
            onClick = state::continueFromAddress,
            modifier = Modifier.focusRequester(next).testTag("setup-continue"),
        )
    }
}

@Composable
private fun AddressFields(
    state: ConnectionSetupState,
    hostFocus: FocusRequester,
    portFocus: FocusRequester,
    onPortDone: () -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val form = state.form
    SetupTextField(
        id = HOST_FIELD,
        editingId = editingId,
        setEditingId = { editingId = it },
        value = form.host,
        onValueChange = form::updateHost,
        label = R.string.onboarding_server_address,
        support = stringResource(R.string.onboarding_server_address_support),
        error = stringResource(R.string.onboarding_server_address_missing).takeIf { state.hostError },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
        ),
        onImeAction = { editingId = PORT_FIELD },
        modifier = Modifier.focusRequester(hostFocus),
    )
    SetupTextField(
        id = PORT_FIELD,
        editingId = editingId,
        setEditingId = { editingId = it },
        value = form.port,
        onValueChange = form::updatePort,
        label = R.string.port_htsp,
        support = stringResource(R.string.onboarding_port_support, DEFAULT_HTSP_PORT),
        error = stringResource(R.string.onboarding_port_invalid).takeIf { state.portError },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        onImeAction = {
            editingId = null
            onPortDone()
        },
        modifier = Modifier.focusRequester(portFocus),
    )
}

/**
 * A setup field under its [label]: its help below it, replaced by [error] while that applies.
 * The help uses the field's own (mobile) text, so it takes the field's error and disabled colours.
 */
@Composable
private fun SetupTextField(
    id: String,
    editingId: String?,
    setEditingId: (String?) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    support: String?,
    error: String?,
    keyboardOptions: KeyboardOptions,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val name = stringResource(label)
    val help = error ?: support
    LabelledField(label = name, enabled = enabled, isError = error != null) {
        TvOutlinedTextField(
            id = id,
            editingId = editingId,
            setEditingId = setEditingId,
            value = value,
            onValueChange = onValueChange,
            label = null,
            keyboardOptions = keyboardOptions,
            enabled = enabled,
            isError = error != null,
            supportingText = help?.let { { FieldHelp(it, isError = error != null) } },
            onImeAction = onImeAction,
            modifier = modifier
                .width(TvBrowseColumnWidth)
                .testTag(id)
                .semantics {
                    contentDescription = name
                    if (error != null) error(error)
                },
        )
    }
}

/**
 * A field's help under it. As an error it takes the field's error colour (the help is the field's
 * own mobile text) and an error mark, so it does not rely on colour alone.
 */
@Composable
private fun FieldHelp(text: String, isError: Boolean) {
    if (!isError) {
        FieldText(text)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        FieldIcon(painterResource(R.drawable.ic_error_outlined), contentDescription = null, Modifier.size(16.dp))
        FieldText(text)
    }
}

/**
 * A field with its label above the outline, so an empty field shows nothing where a value would
 * go. The label follows the field's focus, error and disabled colours; accessibility services
 * get it as the field's description instead.
 */
@Composable
private fun LabelledField(label: String, enabled: Boolean, isError: Boolean, field: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val color = when {
        isError -> colors.error
        focused -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Column(
        modifier = Modifier.onFocusChanged { focused = it.hasFocus },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
            color = color.copy(alpha = if (enabled) 1f else TvTextDisabledAlpha),
            modifier = Modifier.clearAndSetSemantics {},
        )
        field()
    }
}

@Composable
private fun SignInStep(state: ConnectionSetupState, profileName: String?) {
    val username = remember { FocusRequester() }
    val password = remember { FocusRequester() }
    val connect = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        withFrameNanos { }
        when (state.signInFocus) {
            SignInFocus.USERNAME -> username
            SignInFocus.PASSWORD -> password
            SignInFocus.CONNECT -> connect
        }.requestFocus()
    }
    GuidedStepLayout(guidance = {
        GuidedStepGuidance(
            title = stringResource(
                if (state.mode == SetupMode.PASSWORD) R.string.server_password_title
                else R.string.onboarding_sign_in_title,
            ),
            body = if (state.mode == SetupMode.PASSWORD) {
                val account = compactServerName(
                    state.form.host.trim(), state.form.port.trim().toIntOrNull() ?: DEFAULT_HTSP_PORT,
                    state.form.username.trim(),
                )
                stringResource(R.string.server_password_body, account, profileName ?: account)
            } else {
                stringResource(R.string.onboarding_sign_in_body)
            },
            stepper = { SetupStepper(SetupStage.SIGN_IN.ordinal) },
            meta = serverAddress(state.form.host, state.form.port),
        )
    }) {
        SignInFields(
            state = state,
            enabled = true,
            usernameFocus = username,
            passwordFocus = password,
            onPasswordDone = { state.focusSignIn(SignInFocus.CONNECT) },
        )
        MainActionButton(
            label = R.string.onboarding_continue,
            onClick = state::continueFromSignIn,
            modifier = Modifier.focusRequester(connect).testTag("setup-continue"),
        )
        if (state.form.hasSavedPassword) {
            GuidedStepAction(
                title = stringResource(R.string.clear_saved_password),
                icon = R.drawable.ic_delete_outlined,
                onClick = state::clearSavedPassword,
                modifier = Modifier.testTag("setup-clear-password"),
            )
        }
    }
}

/** The server being tested as it will look in Settings; a port that doesn't parse shows the default's color. */
private fun ConnectionFormState.avatarLook() = serverAvatarLook(
    host.trim(), port.trim().toIntOrNull() ?: DEFAULT_HTSP_PORT, null, username.ifBlank { null },
)

/**
 * The running test, centred on the server's avatar with its progress ring. Nothing to press, but the
 * hero holds the focus the action had, so Back, which cancels, stays with the step and accessibility
 * services read the status.
 */
@Composable
private fun ConnectStep(state: ConnectionSetupState) {
    val form = state.form
    val host = form.host.trim()
    val title = stringResource(R.string.onboarding_connecting_title, host)
    val body = when {
        form.username.isBlank() -> stringResource(R.string.onboarding_connecting_body_anonymous)
        else -> stringResource(R.string.onboarding_connecting_body, form.username)
    }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    GuidedStepCenteredLayout(
        title = title,
        body = body,
        stepper = { SetupStepper(SetupStage.CONNECT.ordinal) },
        hero = {
            Box(
                Modifier.testTag("setup-connecting").focusRequester(requester).focusable()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) { ServerAvatarHero(form.avatarLook(), ServerAvatarStatus.CONNECTING) }
        },
    )
}

@Composable
private fun SignInFields(
    state: ConnectionSetupState,
    enabled: Boolean,
    usernameFocus: FocusRequester,
    passwordFocus: FocusRequester,
    onPasswordDone: () -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val form = state.form
    SetupTextField(
        id = USERNAME_FIELD,
        editingId = editingId,
        setEditingId = { editingId = it },
        value = form.username,
        onValueChange = form::updateUsername,
        label = R.string.username,
        support = null,
        error = null,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        enabled = enabled,
        onImeAction = { editingId = PASSWORD_FIELD },
        modifier = Modifier.focusRequester(usernameFocus),
    )
    val pair = stringResource(R.string.onboarding_sign_in_pair)
    val name = stringResource(R.string.password)
    LabelledField(label = name, enabled = enabled, isError = state.signInError) {
        TvPasswordField(
            id = PASSWORD_FIELD,
            editingId = editingId,
            setEditingId = { editingId = it },
            value = form.password,
            presentationValue = if (form.hasSavedPassword) stringResource(R.string.onboarding_password_saved) else null,
            onValueChange = form::updatePassword,
            label = null,
            enabled = enabled,
            isError = state.signInError,
            supportingText = { FieldHelp(pair, isError = state.signInError) },
            onImeAction = {
                editingId = null
                onPasswordDone()
            },
            modifier = Modifier
                .width(TvBrowseColumnWidth)
                .focusRequester(passwordFocus)
                .testTag(PASSWORD_FIELD)
                .semantics {
                    contentDescription = name
                    if (state.signInError) error(pair)
                },
        )
    }
}

/**
 * Continue uses the same full-width guided action as discovery. Invalid fields are explained
 * only after activation rather than disabling the action.
 */
@Composable
internal fun MainActionButton(
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GuidedStepAction(
        title = stringResource(label),
        icon = R.drawable.ic_arrow_forward,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun FailureStep(state: ConnectionSetupState, failure: SetupFailure) {
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    val form = state.form
    val reason = if (failure.showsHint) text.paragraph(failure.condition) else text.reason(failure.condition)
    val body = if (state.mode.keepsSavedConnection)
        stringResource(R.string.onboarding_edit_failure_body, reason) else reason
    val actions = failure.actionsFor(state.mode)
    var focused by remember(actions) { mutableStateOf(actions.firstOrNull()) }
    GuidedStepCenteredLayout(
        title = text.title(failure.condition),
        body = body,
        stepper = { SetupStepper(SetupStage.CONNECT.ordinal, failed = true) },
        hero = { ServerAvatarHero(form.avatarLook(), ServerAvatarStatus.FAILED) },
        detail = { SaveFailedText(form.feedback) },
        actions = {
            actions.forEachIndexed { index, action ->
                val pill = Modifier.onFocusChanged { if (it.isFocused) focused = action }
                    .testTag("setup-action-${action.name.lowercase()}")
                GuidedStepPill(
                    text = stringResource(action.title()),
                    onClick = { state.onAction(action) },
                    modifier = if (index == 0) arrivalFocus().then(pill) else pill,
                    primary = index == 0,
                )
            }
        },
        footer = {
            // What the focused action would do, under the buttons so they stay put and the line keeps its room.
            Text(
                text = focused?.let { actionSubtitle(it, form) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp).heightIn(min = 40.dp).widthIn(max = 560.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
    )
}

@StringRes
private fun SetupAction.title(): Int = when (this) {
    SetupAction.TRY_AGAIN -> R.string.connection_try_again
    SetupAction.CHANGE_ADDRESS -> R.string.onboarding_change_address
    SetupAction.CHANGE_SIGN_IN -> R.string.onboarding_change_sign_in
    SetupAction.CONTINUE_ANYWAY -> R.string.onboarding_continue_anyway
    SetupAction.SAVE_ANYWAY -> R.string.onboarding_save_anyway
}

/** What an action would use or do, which its short label leaves out. */
@Composable
private fun actionSubtitle(action: SetupAction, form: ConnectionFormState): String? = when (action) {
    SetupAction.TRY_AGAIN -> null
    SetupAction.CHANGE_ADDRESS -> serverAddress(form.host.trim(), form.port)
    SetupAction.CHANGE_SIGN_IN ->
        if (form.username.isBlank()) stringResource(R.string.onboarding_change_sign_in_anonymous)
        else stringResource(R.string.onboarding_change_sign_in_detail, form.username)
    SetupAction.CONTINUE_ANYWAY -> stringResource(R.string.onboarding_continue_anyway_detail)
    SetupAction.SAVE_ANYWAY -> stringResource(R.string.onboarding_save_anyway_detail)
}

/** The centred result after a passed test; "Start watching" saves and the app takes over. */
@Composable
private fun DoneStep(state: ConnectionSetupState) {
    val form = state.form
    val body = if (form.username.isBlank()) {
        stringResource(R.string.onboarding_done_body_anonymous)
    } else {
        stringResource(R.string.onboarding_done_body, form.username)
    }
    GuidedStepCenteredLayout(
        title = stringResource(R.string.onboarding_done_title, form.host.trim()),
        body = body,
        stepper = { SetupStepper(SetupStage.entries.size) },
        hero = { ServerAvatarHero(form.avatarLook(), ServerAvatarStatus.CONNECTED) },
        detail = { SaveFailedText(form.feedback) },
        actions = {
            GuidedStepPill(
                text = stringResource(
                    if (state.mode == SetupMode.EDIT) {
                        R.string.onboarding_save_connection
                    } else {
                        R.string.onboarding_start_watching
                    },
                ),
                onClick = { state.save() },
                modifier = arrivalFocus().testTag("setup-start-watching"),
                primary = true,
            )
            if (state.offersName) {
                GuidedStepPill(
                    text = stringResource(R.string.server_add_name),
                    onClick = { state.save(addName = true) },
                    modifier = Modifier.testTag("setup-add-name"),
                )
            }
        },
    )
}

@Composable
internal fun SaveFailedText(feedback: ConnectionFormFeedback?) {
    if (feedback != ConnectionFormFeedback.SAVE_FAILED) return
    Text(
        text = stringResource(R.string.connection_save_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
