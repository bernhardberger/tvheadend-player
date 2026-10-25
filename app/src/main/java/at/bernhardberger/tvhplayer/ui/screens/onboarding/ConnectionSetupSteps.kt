package at.bernhardberger.tvhplayer.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.core.DEFAULT_HTSP_PORT
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.ConnectionFormFeedback
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing80
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepBadge
import at.bernhardberger.tvhplayer.ui.components.GuidedStepChoiceTop
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.GuidedStepper
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField

/** The search's spinner, the same size and place while searching and beside the found list. */
private val SearchIndicatorSize = 22.dp

/** A TV button's height, so the step does not move as "Connecting…" takes the action's place. */
private val MainActionHeight = 40.dp

private const val HOST_FIELD = "setup-host"
private const val PORT_FIELD = "setup-port"
private const val USERNAME_FIELD = "setup-username"
private const val PASSWORD_FIELD = "setup-password"

/**
 * Back keeps the draft and unwinds the wizard; the first step leaves for TV home or Settings.
 */
@Composable
internal fun ConnectionSetupFlow(state: ConnectionSetupState) {
    BackHandler(enabled = state.step.previous(state.mode) != null) { state.back() }
    when (state.step) {
        SetupStep.WELCOME -> WelcomeStep(state)
        SetupStep.ADDRESS -> AddressStep(state)
        SetupStep.SIGN_IN -> SignInStep(state)
        SetupStep.CONNECTING -> ConnectStep(state)
        SetupStep.FAILED -> FailureStep(state, state.failure ?: SetupFailure.OTHER)
        SetupStep.DONE -> DoneStep(state)
    }
}

/** First-run introduction, outside the numbered Server / Account / Connect steps. */
@Composable
private fun WelcomeStep(state: ConnectionSetupState) {
    val next = remember { FocusRequester() }
    LaunchedEffect(Unit) { next.requestFocus() }
    GuidedStepLayout(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.onboarding_title),
                body = stringResource(R.string.onboarding_welcome_body),
                lead = { Image(painterResource(R.drawable.startup_brand_symbol), null, Modifier.size(48.dp)) },
            )
        },
        actionsTop = GuidedStepChoiceTop,
    ) {
        Button(
            onClick = state::enterAddress,
            modifier = Modifier.focusRequester(next).testTag("setup-welcome-continue"),
            colors = ButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) { Text(stringResource(R.string.onboarding_continue)) }
    }
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
 * The search's one status line at the column edge: the spinner while it looks, in the same place
 * before and after servers answered, then the found list's label, with "Still searching…" while
 * the search goes on.
 */
@Composable
private fun FindStatus(searching: Boolean, found: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.width(TvBrowseColumnWidth).height(SearchIndicatorSize),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            CircularProgressIndicator(
                modifier = Modifier.size(SearchIndicatorSize),
                strokeWidth = 3.dp,
                color = colors.primary,
            )
        }
        if (found) {
            Text(
                text = stringResource(R.string.onboarding_find_on_network),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
                modifier = Modifier.semantics { heading() },
            )
        }
        if (searching) {
            Text(
                text = stringResource(if (found) R.string.onboarding_find_still_searching else R.string.onboarding_find_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant.copy(alpha = if (found) TvTextTertiaryAlpha else TvTextSecondaryAlpha),
            )
        }
    }
}

@Composable
private fun ServerSearch(state: ConnectionSetupState, search: FocusRequester) {
    LaunchedEffect(state.findFocusRequest) {
        if (state.findFocusRequest > 0 && state.findFocus == null) search.requestFocus()
    }
    val request = state.searchRequest
    LaunchedEffect(request) { if (request > 0 && state.searching) state.search() }
    GuidedStepAction(
        title = stringResource(when {
            state.searching -> R.string.onboarding_find_searching
            state.searchRequest == 0 -> R.string.onboarding_find_server
            else -> R.string.onboarding_search_again
        }),
        icon = R.drawable.ic_search,
        onClick = { if (!state.searching) state.findServer() },
        modifier = Modifier.focusRequester(search).onFocusChanged {
            if (it.isFocused) state.onFindFocused(null)
        }.semantics { if (state.searching) disabled() }.testTag("setup-find-server"),
    )
    if (state.searchRequest > 0) {
        FindStatus(searching = state.searching, found = state.servers.isNotEmpty())
        if (!state.searching && state.servers.isEmpty()) {
            Text(stringResource(R.string.onboarding_find_none_body), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        state.servers.forEachIndexed { index, server ->
            androidx.compose.runtime.key(server) { FoundServerRow(state, server, index) }
        }
    }
}

@Composable
private fun FoundServerRow(state: ConnectionSetupState, server: DiscoveredTvheadendServer, index: Int) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.findFocusRequest) { if (state.findFocus == server) requester.requestFocus() }
    GuidedStepAction(
        title = server.name,
        subtitle = stringResource(R.string.onboarding_found_server_detail, server.host, server.port),
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
    LaunchedEffect(Unit) { state.onServerOpened() }
    val search = remember { FocusRequester() }
    val host = remember { FocusRequester() }
    val port = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.addressFocus) {
            AddressFocus.SEARCH -> search
            AddressFocus.HOST -> host
            AddressFocus.PORT -> port
            AddressFocus.CONTINUE -> next
        }.requestFocus()
    }
    GuidedStepLayout(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.onboarding_address_title),
                body = stringResource(R.string.onboarding_address_body),
                stepper = { SetupStepper(SetupStage.SERVER.ordinal) },
            )
        },
    ) {
        ServerSearch(state, search)
        AddressFields(state, host, port, onPortDone = { state.focusAddress(AddressFocus.CONTINUE) })
        MainActionButton(
            label = R.string.onboarding_continue,
            onClick = state::continueFromAddress,
            modifier = Modifier.focusRequester(next).onFocusChanged {
                if (it.isFocused) state.onFindFocused(null)
            }.testTag("setup-continue"),
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
        modifier = Modifier.focusRequester(hostFocus).onFocusChanged {
            if (it.isFocused) state.onFindFocused(null)
        },
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
        modifier = Modifier.focusRequester(portFocus).onFocusChanged {
            if (it.isFocused) state.onFindFocused(null)
        },
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
private fun SignInStep(state: ConnectionSetupState) {
    val username = remember { FocusRequester() }
    val password = remember { FocusRequester() }
    val connect = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.signInFocus) {
            SignInFocus.USERNAME -> username
            SignInFocus.PASSWORD -> password
            SignInFocus.CONNECT -> connect
        }.requestFocus()
    }
    GuidedStepLayout(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp), guidance = {
        GuidedStepGuidance(
            title = stringResource(R.string.onboarding_sign_in_title),
            body = stringResource(R.string.onboarding_sign_in_body),
            stepper = { SetupStepper(SetupStage.SIGN_IN.ordinal) },
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

@Composable
private fun ConnectStep(state: ConnectionSetupState) {
    val form = state.form
    val host = form.host.trim()
    val title = stringResource(R.string.onboarding_connecting_title, host)
    val body = when {
        form.username.isBlank() -> stringResource(R.string.onboarding_connecting_body_anonymous)
        else -> stringResource(R.string.onboarding_connecting_body, form.username)
    }
    GuidedStepLayout(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp), guidance = {
        GuidedStepGuidance(title = title, body = body, stepper = { SetupStepper(SetupStage.CONNECT.ordinal) },
            meta = stringResource(R.string.onboarding_change_address_detail, host, form.port))
    }) {
        ConnectingStatus(Modifier.testTag("setup-connecting"))
    }
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

/**
 * The running test in the main action's place: a spinner and "Connecting…" as plain text, nothing
 * to press. It holds the focus the action had, so Back, which cancels, stays with the step and
 * accessibility services read the status.
 */
@Composable
private fun ConnectingStatus(modifier: Modifier) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Row(
        modifier = modifier
            .focusRequester(requester)
            .focusable()
            .heightIn(min = MainActionHeight)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SearchIndicatorSize),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.onboarding_connecting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        )
    }
}

@Composable
private fun FailureStep(state: ConnectionSetupState, failure: SetupFailure) {
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    val first = remember { FocusRequester() }
    LaunchedEffect(failure) { first.requestFocus() }
    val form = state.form
    val host = form.host.trim()
    val port = form.port.trim()
    val meta = if (form.username.isBlank()) {
        stringResource(R.string.onboarding_failure_meta_anonymous, host, port)
    } else {
        stringResource(R.string.onboarding_failure_meta, host, port, form.username)
    }
    val reason = if (failure.showsHint) text.paragraph(failure.condition) else text.reason(failure.condition)
    val body = if (state.mode == SetupMode.EDIT) stringResource(R.string.onboarding_edit_failure_body, reason) else reason
    GuidedStepLayout(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
        guidance = {
            GuidedStepGuidance(
                title = text.title(failure.condition),
                body = body,
                stepper = { SetupStepper(SetupStage.CONNECT.ordinal, failed = true) },
                meta = meta,
            )
        },
        actionsTop = GuidedStepChoiceTop,
    ) {
        failure.actionsFor(state.mode).forEachIndexed { index, action ->
            SetupActionButton(
                action = action,
                form = form,
                onClick = { state.onAction(action) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
        SaveFailedText(form.feedback)
    }
}

@Composable
private fun SetupActionButton(
    action: SetupAction,
    form: ConnectionFormState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = form.host.trim()
    val user = form.username
    @StringRes val title: Int
    @DrawableRes val icon: Int
    val subtitle: String?
    when (action) {
        SetupAction.TRY_AGAIN -> {
            title = R.string.connection_try_again
            icon = R.drawable.ic_refresh
            subtitle = null
        }
        SetupAction.CHANGE_ADDRESS -> {
            title = R.string.onboarding_change_address
            icon = R.drawable.ic_edit
            subtitle = stringResource(R.string.onboarding_change_address_detail, host, form.port.trim())
        }
        SetupAction.CHANGE_SIGN_IN -> {
            title = R.string.onboarding_change_sign_in
            icon = R.drawable.ic_person
            subtitle = if (user.isBlank()) {
                stringResource(R.string.onboarding_change_sign_in_anonymous)
            } else {
                stringResource(R.string.onboarding_change_sign_in_detail, user)
            }
        }
        SetupAction.CONTINUE_ANYWAY -> {
            title = R.string.onboarding_continue_anyway
            icon = R.drawable.ic_arrow_forward
            subtitle = stringResource(R.string.onboarding_continue_anyway_detail)
        }
        SetupAction.SAVE_ANYWAY -> {
            title = R.string.onboarding_save_anyway
            icon = R.drawable.ic_cloud_off
            subtitle = stringResource(R.string.onboarding_save_anyway_detail)
        }
    }
    GuidedStepAction(
        title = stringResource(title),
        subtitle = subtitle,
        icon = icon,
        onClick = onClick,
        modifier = modifier.testTag("setup-action-${action.name.lowercase()}"),
    )
}

/** The centred result after a passed test; "Start watching" saves and the app takes over. */
@Composable
private fun DoneStep(state: ConnectionSetupState) {
    val start = remember { FocusRequester() }
    LaunchedEffect(Unit) { start.requestFocus() }
    val form = state.form
    val body = if (form.username.isBlank()) {
        stringResource(R.string.onboarding_done_body_anonymous)
    } else {
        stringResource(R.string.onboarding_done_body, form.username)
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TvSpacing80, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SetupStepper(SetupStage.entries.size)
        Spacer(Modifier.height(32.dp))
        GuidedStepBadge(R.drawable.ic_check)
        Text(
            text = stringResource(R.string.onboarding_done_title, form.host.trim()),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 700.dp).semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp),
        )
        // Keep the button's own layout unpadded: Column can otherwise constrain it to zero
        // after the success copy grows (notably at 1.3x fonts), leaving no focusable target.
        Spacer(Modifier.height(24.dp))
        Button(onClick = state::save, modifier = Modifier.focusRequester(start).testTag("setup-start-watching")) {
            Text(stringResource(if (state.mode == SetupMode.EDIT) R.string.onboarding_save_connection else R.string.onboarding_start_watching))
        }
        SaveFailedText(form.feedback)
    }
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
