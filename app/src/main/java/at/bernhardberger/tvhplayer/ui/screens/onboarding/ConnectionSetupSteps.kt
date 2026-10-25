package at.bernhardberger.tvhplayer.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text as FieldText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.LocalContentColor
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
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepBadge
import at.bernhardberger.tvhplayer.ui.components.GuidedStepChoiceTop
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout
import at.bernhardberger.tvhplayer.ui.components.GuidedStepper
import at.bernhardberger.tvhplayer.ui.components.TvOutlinedTextField
import at.bernhardberger.tvhplayer.ui.components.TvPasswordField

/** The welcome's choices start level with its title, under the 48dp mark. */
private val WelcomeChoiceTop = 64.dp

/** About four found servers; more scroll, so the address entry stays on screen below them. */
private val FoundServersMaxHeight = 272.dp

private const val HOST_FIELD = "setup-host"
private const val PORT_FIELD = "setup-port"
private const val USERNAME_FIELD = "setup-username"
private const val PASSWORD_FIELD = "setup-password"

/**
 * The setup's steps. Back goes one step back and keeps what was entered; on the welcome it is
 * left to the activity, which leaves for the TV home.
 */
@Composable
internal fun ConnectionSetupFlow(state: ConnectionSetupState) {
    BackHandler(enabled = state.step.previous(state.serverChoice) != null) { state.back() }
    when (state.step) {
        SetupStep.WELCOME -> WelcomeStep(state)
        SetupStep.FIND -> FindStep(state)
        SetupStep.ADDRESS -> AddressStep(state)
        // One branch, so the test runs in place on the sign-in it started from.
        SetupStep.SIGN_IN, SetupStep.CONNECTING -> SignInStep(state)
        SetupStep.FAILED -> FailureStep(state, state.failure ?: SetupFailure.OTHER)
        SetupStep.DONE -> DoneStep(state)
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

@Composable
private fun WelcomeStep(state: ConnectionSetupState) {
    val find = remember { FocusRequester() }
    val type = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        when (state.welcomeFocus) {
            WelcomeFocus.FIND -> find
            WelcomeFocus.TYPE -> type
        }.requestFocus()
    }
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = stringResource(R.string.onboarding_title),
                body = stringResource(R.string.onboarding_welcome_body),
                lead = {
                    Image(painterResource(R.drawable.startup_brand_symbol), null, Modifier.size(48.dp))
                },
            )
        },
        actionsTop = WelcomeChoiceTop,
    ) {
        GuidedStepAction(
            title = stringResource(R.string.onboarding_find_server),
            subtitle = stringResource(R.string.onboarding_find_server_detail),
            icon = R.drawable.ic_search,
            onClick = state::findServer,
            modifier = Modifier.focusRequester(find).testTag("setup-find-server"),
        )
        EnterAddressAction(onClick = state::enterAddress, modifier = Modifier.focusRequester(type))
    }
}

@Composable
private fun EnterAddressAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GuidedStepAction(
        title = stringResource(R.string.onboarding_enter_address),
        subtitle = stringResource(R.string.onboarding_enter_address_detail),
        icon = R.drawable.ic_keyboard,
        onClick = onClick,
        modifier = modifier.testTag("setup-enter-address"),
    )
}

/**
 * The network search. While nothing answered it says so beside "Enter address manually"; found
 * servers list above it; a search that ended empty, or could not run, offers the address entry
 * first and "Search again". Leaving the step stops the search.
 */
@Composable
private fun FindStep(state: ConnectionSetupState) {
    LaunchedEffect(state.searchRequest) { if (state.searching) state.search() }
    val found = state.servers.isNotEmpty()
    val title = if (found || state.searching) R.string.onboarding_find_title else R.string.onboarding_find_none_title
    val body = when {
        found -> R.string.onboarding_find_results_body
        state.searching -> R.string.onboarding_find_searching_body
        else -> R.string.onboarding_find_none_body
    }
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = stringResource(title),
                body = stringResource(body),
                stepper = { SetupStepper(SetupStage.SERVER.ordinal) },
            )
        },
    ) {
        when {
            found -> FoundServers(state)
            state.searching -> {
                SearchingNote()
                FindEnterAddressAction(state)
            }
            else -> {
                FindEnterAddressAction(state)
                GuidedStepAction(
                    title = stringResource(R.string.onboarding_search_again),
                    icon = R.drawable.ic_refresh,
                    onClick = state::findServer,
                    modifier = Modifier.testTag("setup-search-again"),
                )
            }
        }
    }
}

@Composable
private fun SearchingNote() {
    Row(
        modifier = Modifier.height(36.dp).padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchSpinner(18.dp)
        Text(
            text = stringResource(R.string.onboarding_find_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        )
    }
}

@Composable
private fun SearchSpinner(size: Dp, modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** "Enter address manually" on the search; it holds focus while nothing else is chosen. */
@Composable
private fun FindEnterAddressAction(state: ConnectionSetupState) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) { if (state.findFocus == null) requester.requestFocus() }
    EnterAddressAction(
        onClick = state::enterAddress,
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) state.onFindFocused(null) },
    )
}

@Composable
private fun FoundServers(state: ConnectionSetupState) {
    val searching = stringResource(R.string.onboarding_find_searching)
    Row(Modifier.width(TvBrowseColumnWidth), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.onboarding_find_on_network),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (state.searching) {
            SearchSpinner(14.dp, Modifier.semantics { contentDescription = searching })
        }
    }
    // Room for the focused row's scale inside the list's clip.
    Column(
        modifier = Modifier
            .heightIn(max = FoundServersMaxHeight)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.servers.forEachIndexed { index, server ->
            key(server) { FoundServerRow(state, server, index) }
        }
    }
    Box(
        Modifier
            .width(TvBrowseColumnWidth)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTrackAlpha)),
    )
    FindEnterAddressAction(state)
}

@Composable
private fun FoundServerRow(state: ConnectionSetupState, server: DiscoveredTvheadendServer, index: Int) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) { if (state.findFocus == server) requester.requestFocus() }
    GuidedStepAction(
        title = server.name,
        subtitle = stringResource(R.string.onboarding_found_server_detail, server.host, server.port),
        icon = R.drawable.ic_dns,
        containerColor = Color.Transparent,
        onClick = { state.chooseServer(server) },
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) state.onFindFocused(server) }
            .testTag("setup-server-$index"),
    )
}

@Composable
private fun AddressStep(state: ConnectionSetupState) {
    val host = remember { FocusRequester() }
    val port = remember { FocusRequester() }
    val next = remember { FocusRequester() }
    LaunchedEffect(state.focusRequest) {
        when (state.addressFocus) {
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
        AddressFields(state, host, port, onPortDone = { state.focusAddress(AddressFocus.CONTINUE) })
        Button(
            onClick = state::continueFromAddress,
            modifier = Modifier.focusRequester(next).testTag("setup-continue"),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
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
            supportingText = help?.let { { FieldText(it) } },
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
    val connecting = state.step == SetupStep.CONNECTING
    GuidedStepLayout(guidance = { SignInGuidance(state.form, connecting) }) {
        SignInFields(
            state = state,
            enabled = !connecting,
            usernameFocus = username,
            passwordFocus = password,
            onPasswordDone = { state.focusSignIn(SignInFocus.CONNECT) },
        )
        ConnectButton(
            connecting = connecting,
            onClick = state::connect,
            modifier = Modifier.focusRequester(connect),
        )
    }
}

@Composable
private fun SignInGuidance(form: ConnectionFormState, connecting: Boolean) {
    val host = form.host.trim()
    val title = if (connecting) {
        stringResource(R.string.onboarding_connecting_title, host)
    } else {
        stringResource(R.string.onboarding_sign_in_title, host)
    }
    val body = when {
        !connecting -> stringResource(R.string.onboarding_sign_in_body)
        form.username.isBlank() -> stringResource(R.string.onboarding_connecting_body_anonymous)
        else -> stringResource(R.string.onboarding_connecting_body, form.username)
    }
    val stage = if (connecting) SetupStage.CONNECT else SetupStage.SIGN_IN
    GuidedStepGuidance(title = title, body = body, stepper = { SetupStepper(stage.ordinal) })
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
            onValueChange = form::updatePassword,
            label = null,
            enabled = enabled,
            isError = state.signInError,
            supportingText = { FieldText(pair) },
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

/** Connect; while the test runs it stays focused as a busy "Connecting" that Back cancels. */
@Composable
private fun ConnectButton(connecting: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { if (!connecting) onClick() },
        modifier = modifier.testTag("setup-connect"),
    ) {
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        }
        Text(stringResource(if (connecting) R.string.onboarding_connecting else R.string.onboarding_connect))
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
    GuidedStepLayout(
        guidance = {
            GuidedStepGuidance(
                title = text.title(failure.condition),
                body = if (failure.showsHint) text.paragraph(failure.condition) else text.reason(failure.condition),
                stepper = { SetupStepper(SetupStage.CONNECT.ordinal, failed = true) },
                meta = meta,
            )
        },
        actionsTop = GuidedStepChoiceTop,
    ) {
        failure.actions.forEachIndexed { index, action ->
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
        modifier = Modifier.fillMaxSize().padding(top = TvSpacing80),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SetupStepper(SetupStage.entries.size)
        Spacer(Modifier.height(64.dp))
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
        Button(
            onClick = state::save,
            modifier = Modifier.padding(top = 32.dp).focusRequester(start).testTag("setup-start-watching"),
        ) {
            Text(stringResource(R.string.onboarding_start_watching))
        }
        SaveFailedText(form.feedback)
    }
}

@Composable
private fun SaveFailedText(feedback: ConnectionFormFeedback?) {
    if (feedback != ConnectionFormFeedback.SAVE_FAILED) return
    Text(
        text = stringResource(R.string.connection_save_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
