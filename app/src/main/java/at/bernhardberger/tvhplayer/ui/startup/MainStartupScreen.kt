package at.bernhardberger.tvhplayer.ui.startup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvSpacing32

private val MainStartupContentMaxWidth = 800.dp
private const val MainStartupRootTag = "main-startup-root"
private const val MainStartupActionTagPrefix = "main-startup-action-"

/**
 * Opaque, state-driven startup status surface for appliance and autoplay entry.
 * The caller owns whether this screen is composed and provides its shell safe bounds.
 */
@Composable
fun MainStartupScreen(
    presentation: MainStartupPresentation,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onAction: (MainStartupActionId) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val actionable = presentation as? MainStartupPresentation.Actionable
    var wasActionable by remember { mutableStateOf(false) }

    LaunchedEffect(actionable != null) {
        if (wasActionable && actionable == null) {
            focusManager.clearFocus(force = true)
        }
        wasActionable = actionable != null
    }

    when (presentation) {
        is MainStartupPresentation.Passive -> MainStartupPassiveContent(
            messageKind = presentation.messageKind,
            contentPadding = contentPadding,
            modifier = modifier,
        )
        is MainStartupPresentation.Actionable -> MainStartupActionableContent(
            presentation = presentation,
            contentPadding = contentPadding,
            modifier = modifier,
            onAction = onAction,
        )
        MainStartupPresentation.Inactive,
        is MainStartupPresentation.Enter -> Unit
    }
}

@Composable
private fun MainStartupPassiveContent(
    messageKind: MainStartupMessageKind,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    MainStartupFrame(
        contentPadding = contentPadding,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(R.string.main_startup_passive_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(
            text = stringResource(mainStartupMessageResource(messageKind)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing24),
        )
        CircularProgressIndicator(
            modifier = Modifier.padding(top = TvSpacing32),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MainStartupActionableContent(
    presentation: MainStartupPresentation.Actionable,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onAction: (MainStartupActionId) -> Unit,
) {
    val retryFocus = remember { FocusRequester() }
    val connectionSettingsFocus = remember { FocusRequester() }
    val exitSimpleTvFocus = remember { FocusRequester() }
    var focusedActionId by remember { mutableStateOf<MainStartupActionId?>(null) }
    val title = stringResource(R.string.main_startup_actionable_title)
    val focusedOrFirstAction = focusedActionId
        ?.takeIf { it in presentation.actions }
        ?: presentation.actions.firstOrNull()

    LaunchedEffect(presentation.actions) {
        focusedOrFirstAction?.let { action ->
            mainStartupFocusRequester(
                action = action,
                retryFocus = retryFocus,
                connectionSettingsFocus = connectionSettingsFocus,
                exitSimpleTvFocus = exitSimpleTvFocus,
            ).requestFocus()
        }
    }

    MainStartupFrame(
        contentPadding = contentPadding,
        modifier = modifier.semantics {
            paneTitle = title
            dialog()
            isTraversalGroup = true
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Text(
            text = stringResource(mainStartupMessageResource(presentation.messageKind)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TvSpacing24),
        )
        if (presentation.actions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = TvSpacing32),
                horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presentation.actions.forEach { action ->
                    val actionModifier = Modifier
                        .focusRequester(
                            mainStartupFocusRequester(
                                action = action,
                                retryFocus = retryFocus,
                                connectionSettingsFocus = connectionSettingsFocus,
                                exitSimpleTvFocus = exitSimpleTvFocus,
                            ),
                        )
                        .focusProperties {
                            val graph = mainStartupFocusGraph(
                                action = action,
                                actions = presentation.actions,
                                retryFocus = retryFocus,
                                connectionSettingsFocus = connectionSettingsFocus,
                                exitSimpleTvFocus = exitSimpleTvFocus,
                            )
                            left = graph.left
                            right = graph.right
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                        }
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                focusedActionId = action
                            }
                        }
                        .testTag(mainStartupActionTag(action))
                    val label = stringResource(mainStartupActionResource(action))

                    if (action == MainStartupActionId.RETRY) {
                        Button(
                            onClick = { onAction(action) },
                            modifier = actionModifier,
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(action) },
                            modifier = actionModifier,
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainStartupFrame(
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(MainStartupRootTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .widthIn(max = MainStartupContentMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

private data class MainStartupFocusGraph(
    val left: FocusRequester,
    val right: FocusRequester,
)

private fun mainStartupFocusGraph(
    action: MainStartupActionId,
    actions: List<MainStartupActionId>,
    retryFocus: FocusRequester,
    connectionSettingsFocus: FocusRequester,
    exitSimpleTvFocus: FocusRequester,
): MainStartupFocusGraph = when (action) {
    MainStartupActionId.RETRY -> MainStartupFocusGraph(
        left = FocusRequester.Cancel,
        right = when {
            MainStartupActionId.CONNECTION_SETTINGS in actions -> connectionSettingsFocus
            MainStartupActionId.EXIT_SIMPLE_TV in actions -> exitSimpleTvFocus
            else -> FocusRequester.Cancel
        },
    )
    MainStartupActionId.CONNECTION_SETTINGS,
    MainStartupActionId.EXIT_SIMPLE_TV -> MainStartupFocusGraph(
        left = if (MainStartupActionId.RETRY in actions) retryFocus else FocusRequester.Cancel,
        right = FocusRequester.Cancel,
    )
}

private fun mainStartupFocusRequester(
    action: MainStartupActionId,
    retryFocus: FocusRequester,
    connectionSettingsFocus: FocusRequester,
    exitSimpleTvFocus: FocusRequester,
): FocusRequester = when (action) {
    MainStartupActionId.RETRY -> retryFocus
    MainStartupActionId.CONNECTION_SETTINGS -> connectionSettingsFocus
    MainStartupActionId.EXIT_SIMPLE_TV -> exitSimpleTvFocus
}

@StringRes
private fun mainStartupMessageResource(messageKind: MainStartupMessageKind): Int = when (messageKind) {
    MainStartupMessageKind.PREPARING -> R.string.main_startup_message_preparing
    MainStartupMessageKind.CONNECTING -> R.string.main_startup_message_connecting
    MainStartupMessageKind.SYNCING_CHANNELS -> R.string.main_startup_message_syncing_channels
    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA ->
        R.string.main_startup_message_waiting_for_current_channel_metadata
    MainStartupMessageKind.RECONNECTING -> R.string.main_startup_message_reconnecting
    MainStartupMessageKind.STARTING_TELEVISION -> R.string.main_startup_message_starting_television
    MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS -> R.string.main_startup_message_no_channels
    MainStartupMessageKind.RETRYABLE_FAILURE -> R.string.main_startup_message_retryable_failure
    MainStartupMessageKind.CONFIGURATION_REQUIRED ->
        R.string.main_startup_message_configuration_required
    MainStartupMessageKind.CREDENTIAL_UNAVAILABLE ->
        R.string.main_startup_message_credential_unavailable
    MainStartupMessageKind.SIMPLE_TV_FAILURE -> R.string.main_startup_message_simple_tv_failure
}

@StringRes
private fun mainStartupActionResource(action: MainStartupActionId): Int = when (action) {
    MainStartupActionId.RETRY -> R.string.main_startup_action_retry
    MainStartupActionId.CONNECTION_SETTINGS -> R.string.main_startup_action_connection_settings
    MainStartupActionId.EXIT_SIMPLE_TV -> R.string.main_startup_action_exit_simple_tv
}

private fun mainStartupActionTag(action: MainStartupActionId): String =
    "$MainStartupActionTagPrefix${action.name}"
