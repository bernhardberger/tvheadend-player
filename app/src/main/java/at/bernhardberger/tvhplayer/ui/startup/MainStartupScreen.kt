package at.bernhardberger.tvhplayer.ui.startup

import android.animation.ValueAnimator
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TvSpacing32

private val MainStartupContentMaxWidth = 560.dp
private val MainStartupMarkSize = 80.dp
private val MainStartupBackground = Color(0xFF0F1014)
private val MainStartupBrandFont = FontFamily(Font(R.font.outfit_550, FontWeight(550)))
private const val MainStartupRootTag = "main-startup-root"
private const val MainStartupMarkTag = "main-startup-mark"
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
    brandMillis: () -> Float = { StartupBrandDurationMillis },
    loadingFeedback: MainStartupLoadingFeedback = MainStartupLoadingFeedback.WAITING,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    brandingVisible: Boolean = true,
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
            presentation = presentation,
            contentPadding = contentPadding,
            modifier = modifier,
            brandMillis = if (motionEnabled) brandMillis else { { StartupBrandDurationMillis } },
            loadingFeedback = loadingFeedback,
            motionEnabled = motionEnabled,
            brandingVisible = brandingVisible,
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
    presentation: MainStartupPresentation.Passive,
    contentPadding: PaddingValues,
    modifier: Modifier,
    brandMillis: () -> Float,
    loadingFeedback: MainStartupLoadingFeedback,
    motionEnabled: Boolean,
    brandingVisible: Boolean,
) {
    MainStartupFrame(
        contentPadding = contentPadding,
        modifier = modifier,
        brandMillis = brandMillis,
        backgroundMotionEnabled = motionEnabled &&
            (brandingVisible || loadingFeedback == MainStartupLoadingFeedback.WAITING),
        brandingVisible = brandingVisible,
    ) {
        when (loadingFeedback) {
            MainStartupLoadingFeedback.HIDDEN -> Unit
            MainStartupLoadingFeedback.WAITING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (brandingVisible) Spacer(Modifier.height(TvSpacing32))
                    if (motionEnabled) {
                        if (brandingVisible) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp).testTag("main-startup-loader"),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            at.bernhardberger.tvhplayer.ui.player.PlayerBusyRing(
                                modifier = Modifier.testTag("main-startup-loader"),
                            )
                        }
                    } else {
                        // Keep the status anchor without a frozen spinner.
                        Spacer(Modifier.height(44.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    MainStartupStatus(presentation.messageKind)
                }
            }
        }
    }
}

@Composable
private fun MainStartupStatus(messageKind: MainStartupMessageKind) {
    Text(
        text = stringResource(mainStartupMessageResource(messageKind)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("main-startup-status")
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
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
    var focusedActionId by remember { mutableStateOf<MainStartupActionId?>(null) }
    val title = stringResource(mainStartupProblemTitleResource(presentation.messageKind))
    val focusedOrFirstAction = focusedActionId
        ?.takeIf { it in presentation.actions }
        ?: presentation.actions.firstOrNull()

    MainStartupFrame(
        contentPadding = contentPadding,
        modifier = modifier.semantics {
            paneTitle = title
            dialog()
            isTraversalGroup = true
            liveRegion = LiveRegionMode.Polite
        },
        recovery = true,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(mainStartupMessageResource(presentation.messageKind)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth(),
        )
        if (presentation.actions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = TvSpacing32),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presentation.actions.forEach { action ->
                    // Preserve the native interaction source when another action is removed.
                    key(action) {
                        val actionModifier = Modifier
                            .focusRequester(
                                mainStartupFocusRequester(
                                    action = action,
                                    retryFocus = retryFocus,
                                    connectionSettingsFocus = connectionSettingsFocus,
                                ),
                            )
                            .focusProperties {
                                val graph = mainStartupFocusGraph(
                                    action = action,
                                    actions = presentation.actions,
                                    retryFocus = retryFocus,
                                    connectionSettingsFocus = connectionSettingsFocus,
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
        // The subcomposed controls must register their native interaction collectors first.
        LaunchedEffect(presentation.actions) {
            focusedOrFirstAction?.let { action ->
                mainStartupFocusRequester(
                    action = action,
                    retryFocus = retryFocus,
                    connectionSettingsFocus = connectionSettingsFocus,
                ).requestFocus()
            }
        }
    }
}

@Composable
private fun MainStartupFrame(
    contentPadding: PaddingValues,
    modifier: Modifier,
    brandMillis: () -> Float = { StartupBrandDurationMillis },
    recovery: Boolean = false,
    backgroundMotionEnabled: Boolean = false,
    brandingVisible: Boolean = true,
    bodyContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MainStartupBackground)
            .testTag(MainStartupRootTag),
        contentAlignment = Alignment.Center,
    ) {
        StartupBrandBackground(
            millis = if (brandingVisible) brandMillis else { { StartupBrandDurationMillis } },
            enabled = backgroundMotionEnabled && !recovery,
        )
        BoxWithConstraints(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val density = LocalDensity.current
            // Preserve the accepted brand anchor; reserve room for the larger ring and long status.
            val passiveReserve = 130.dp + 80.dp + (88.dp * density.fontScale)
            val passiveTop = (maxHeight / 2 - 114.dp)
                .coerceAtMost((maxHeight - passiveReserve).coerceAtLeast(0.dp))
                .coerceAtLeast(0.dp)
            // A brand-free return retains the player-aligned indicator.
            val ringTop = maxHeight / 2 - 22.dp
            Column(
                modifier = Modifier
                    .align(if (recovery) Alignment.Center else Alignment.TopCenter)
                    .padding(top = if (recovery) 0.dp else passiveTop)
                    .widthIn(max = MainStartupContentMaxWidth)
                    .fillMaxWidth()
                    // Recovery can grow on smaller canvases/large type without losing actions.
                    .then(if (recovery) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(if (recovery) 8.dp else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (brandingVisible || recovery) {
                    StartupBrandSymbol(
                        millis = brandMillis,
                        modifier = Modifier
                            .size(MainStartupMarkSize)
                            .testTag(MainStartupMarkTag),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFE3E3E8))) { append("Tvheadend ") }
                                withStyle(SpanStyle(color = Color(0xFFFA7F00))) { append("Player") }
                            },
                            fontFamily = MainStartupBrandFont,
                            fontWeight = FontWeight(550),
                            fontSize = 28.sp,
                            lineHeight = 36.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .graphicsLayer {
                                    val frame = startupBrandFrame(brandMillis())
                                    alpha = frame.wordmark
                                    translationY = 2.dp.toPx() * (1f - frame.wordmark)
                                }
                                .semantics { heading() },
                        )
                    }
                }
                if (bodyContent != null && (recovery || brandingVisible)) {
                    bodyContent()
                }
            }
            if (!recovery && !brandingVisible && bodyContent != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = ringTop)
                        .widthIn(max = MainStartupContentMaxWidth)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { bodyContent() }
            }
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
): MainStartupFocusGraph = when (action) {
    MainStartupActionId.RETRY -> MainStartupFocusGraph(
        left = FocusRequester.Cancel,
        right = when {
            MainStartupActionId.CONNECTION_SETTINGS in actions -> connectionSettingsFocus
            else -> FocusRequester.Cancel
        },
    )
    MainStartupActionId.CONNECTION_SETTINGS -> MainStartupFocusGraph(
        left = if (MainStartupActionId.RETRY in actions) retryFocus else FocusRequester.Cancel,
        right = FocusRequester.Cancel,
    )
}

private fun mainStartupFocusRequester(
    action: MainStartupActionId,
    retryFocus: FocusRequester,
    connectionSettingsFocus: FocusRequester,
): FocusRequester = when (action) {
    MainStartupActionId.RETRY -> retryFocus
    MainStartupActionId.CONNECTION_SETTINGS -> connectionSettingsFocus
}

@StringRes
private fun mainStartupMessageResource(messageKind: MainStartupMessageKind): Int = when (messageKind) {
    MainStartupMessageKind.PREPARING -> R.string.main_startup_message_preparing
    MainStartupMessageKind.CONNECTING -> R.string.main_startup_message_connecting
    MainStartupMessageKind.SYNCING_METADATA -> R.string.main_startup_message_syncing_metadata
    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA ->
        R.string.main_startup_message_waiting_for_current_channel_metadata
    MainStartupMessageKind.RECONNECTING -> R.string.main_startup_message_reconnecting
    MainStartupMessageKind.STARTING_TELEVISION -> R.string.main_startup_message_starting_television
    MainStartupMessageKind.RESUMING_PLAYBACK -> R.string.main_startup_message_resuming_playback
    MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS -> R.string.main_startup_message_no_channels
    MainStartupMessageKind.RETRYABLE_FAILURE -> R.string.main_startup_message_retryable_failure
    MainStartupMessageKind.AUTHENTICATION_FAILURE -> R.string.main_startup_message_authentication_failure
    MainStartupMessageKind.PERMISSION_DENIED -> R.string.main_startup_message_permission_denied
    MainStartupMessageKind.INCOMPATIBLE_SERVER -> R.string.main_startup_message_incompatible_server
    MainStartupMessageKind.TIMEOUT_FAILURE -> R.string.main_startup_message_timeout_failure
    MainStartupMessageKind.SYNCHRONIZATION_FAILURE -> R.string.main_startup_message_synchronization_failure
    MainStartupMessageKind.CONFIGURATION_REQUIRED ->
        R.string.main_startup_message_configuration_required
    MainStartupMessageKind.CREDENTIAL_UNAVAILABLE ->
        R.string.main_startup_message_credential_unavailable
}

@StringRes
private fun mainStartupProblemTitleResource(messageKind: MainStartupMessageKind): Int = when (messageKind) {
    MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS -> R.string.main_startup_title_no_channels
    MainStartupMessageKind.RETRYABLE_FAILURE -> R.string.main_startup_title_retryable_failure
    MainStartupMessageKind.AUTHENTICATION_FAILURE -> R.string.main_startup_title_authentication_failure
    MainStartupMessageKind.PERMISSION_DENIED -> R.string.main_startup_title_permission_denied
    MainStartupMessageKind.INCOMPATIBLE_SERVER -> R.string.main_startup_title_incompatible_server
    MainStartupMessageKind.TIMEOUT_FAILURE -> R.string.main_startup_title_timeout_failure
    MainStartupMessageKind.SYNCHRONIZATION_FAILURE -> R.string.main_startup_title_synchronization_failure
    MainStartupMessageKind.CONFIGURATION_REQUIRED -> R.string.main_startup_title_configuration_required
    MainStartupMessageKind.CREDENTIAL_UNAVAILABLE -> R.string.main_startup_title_credential_unavailable
    MainStartupMessageKind.PREPARING,
    MainStartupMessageKind.CONNECTING,
    MainStartupMessageKind.SYNCING_METADATA,
    MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA,
    MainStartupMessageKind.RECONNECTING,
    MainStartupMessageKind.STARTING_TELEVISION,
    MainStartupMessageKind.RESUMING_PLAYBACK -> R.string.main_startup_actionable_title
}

@StringRes
private fun mainStartupActionResource(action: MainStartupActionId): Int = when (action) {
    MainStartupActionId.RETRY -> R.string.main_startup_action_retry
    MainStartupActionId.CONNECTION_SETTINGS -> R.string.main_startup_action_connection_settings
}

private fun mainStartupActionTag(action: MainStartupActionId): String =
    "$MainStartupActionTagPrefix${action.name}"
