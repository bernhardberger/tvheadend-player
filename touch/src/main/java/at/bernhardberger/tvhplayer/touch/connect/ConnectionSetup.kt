package at.bernhardberger.tvhplayer.touch.connect

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun FindServerScreen(
    state: ServerSearchUiState,
    onServerPicked: (DiscoveredTvheadendServer) -> Unit,
    onEnterManually: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    SetupLayout(
        intro = { compact ->
            SetupIntro(
                title = stringResource(SharedR.string.onboarding_find_title),
                description = stringResource(SharedR.string.onboarding_welcome_body),
                compact = compact,
                showBrand = true,
                detail = stringResource(SharedR.string.onboarding_find_body),
            )
        },
        actions = { _, _ -> DiscoveredServers(state, onServerPicked, onEnterManually, onSearchAgain) },
    )
}

@Composable
internal fun ConnectionAccountScreen(
    state: ConnectionUiState,
    onFieldsChange: (ConnectionFields) -> Unit,
    onConnect: (ConnectionFields) -> Unit,
    onBack: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val submit = {
        focus.clearFocus()
        onConnect(state.fields)
    }
    BackHandler(onBack = onBack)
    SetupLayout(
        intro = { compact ->
            SetupIntro(
                title = stringResource(SharedR.string.onboarding_sign_in_title),
                description = stringResource(SharedR.string.onboarding_sign_in_body),
                compact = compact,
                showBrand = !compact,
            )
        },
        actions = { compact, stacked ->
            ConnectionForm(state, onFieldsChange, compact, showSignInHelp = false)
            if (!stacked) {
                Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    ConnectAction(state.phase, submit)
                }
            }
        },
        onBack = onBack,
        bottomAction = { ConnectAction(state.phase, submit, Modifier.fillMaxWidth()) },
    )
}

@Composable
private fun SetupLayout(
    intro: @Composable (compact: Boolean) -> Unit,
    actions: @Composable (compact: Boolean, stacked: Boolean) -> Unit,
    onBack: (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
) {
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 600.dp
            val stacked = maxWidth < 840.dp
            val horizontal = if (compact) 24.dp else 80.dp
            val top = setupTopPadding(maxHeight, stacked, compact, hasBack = onBack != null)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                if (stacked && onBack != null) {
                    ConnectionIconButton(R.drawable.connect_back_outline, SharedR.string.back, onClick = onBack)
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = horizontal, end = horizontal, top = top, bottom = 8.dp),
                ) {
                    if (stacked) {
                        Column {
                            Column(Modifier.heightIn(min = if (compact) 0.dp else 240.dp)) {
                                intro(compact)
                            }
                            if (compact) Spacer(Modifier.height(28.dp))
                            actions(compact, true)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f)) {
                                Box(Modifier.widthIn(max = 440.dp).fillMaxWidth()) { intro(false) }
                            }
                            Column(Modifier.weight(1f)) { actions(false, false) }
                        }
                    }
                }
                if (stacked && bottomAction != null) {
                    Box(Modifier.fillMaxWidth().padding(start = horizontal, end = horizontal, bottom = 16.dp)) {
                        bottomAction()
                    }
                }
            }
            if (!stacked && onBack != null) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 8.dp)) {
                    ConnectionIconButton(R.drawable.connect_back_outline, SharedR.string.back, onClick = onBack)
                }
            }
        }
    }
}

private fun setupTopPadding(height: Dp, stacked: Boolean, compact: Boolean, hasBack: Boolean): Dp = when {
    !stacked -> (height - 592.dp).coerceIn(64.dp, 208.dp)
    !compact -> (height - 1112.dp).coerceIn(32.dp, 168.dp)
    hasBack -> 60.dp
    else -> 56.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionIconButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val description = stringResource(label)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        enableUserInput = enabled,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(painterResource(icon), description)
        }
    }
}

@Composable
private fun SetupIntro(
    title: String,
    description: String,
    compact: Boolean,
    showBrand: Boolean,
    detail: String? = null,
) {
    Column {
        if (showBrand) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    painterResource(R.drawable.startup_brand_symbol),
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 40.dp else 48.dp),
                )
                Text(
                    stringResource(R.string.connect_brand),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
        }
        Text(
            title,
            Modifier.semantics { heading() },
            style = if (compact) MaterialTheme.typography.headlineMedium
            else MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
        )
        val paragraphGap = if (compact) 12.dp else 16.dp
        SetupParagraph(description, Modifier.padding(top = paragraphGap))
        if (detail != null) SetupParagraph(detail, Modifier.padding(top = paragraphGap))
    }
}

@Composable
private fun SetupParagraph(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiscoveredServers(
    state: ServerSearchUiState,
    onServerPicked: (DiscoveredTvheadendServer) -> Unit,
    onEnterManually: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column {
        Text(
            stringResource(SharedR.string.onboarding_find_on_network),
            Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            state.servers.forEachIndexed { index, server ->
                ListItem(
                    onClick = { onServerPicked(server) },
                    supportingContent = {
                        Text(stringResource(SharedR.string.onboarding_found_server_detail, server.host, server.port))
                    },
                    leadingContent = {
                        Icon(painterResource(R.drawable.connect_server_outline), contentDescription = null)
                    },
                    trailingContent = {
                        Icon(painterResource(R.drawable.connect_next_outline), contentDescription = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shapes = ListItemDefaults.segmentedShapes(index, state.servers.size),
                ) {
                    Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (state.searching || state.servers.isEmpty()) {
            SetupParagraph(
                stringResource(
                    if (state.searching) {
                        SharedR.string.onboarding_find_searching
                    } else {
                        SharedR.string.onboarding_find_none_body
                    },
                ),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        FlowRow(
            Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onEnterManually) {
                Text(stringResource(SharedR.string.onboarding_enter_address_manually))
            }
            TextButton(onClick = onSearchAgain, enabled = !state.searching) {
                Icon(painterResource(R.drawable.connect_refresh_outline), null, Modifier.size(20.dp))
                Text(stringResource(SharedR.string.onboarding_search_again), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConnectAction(phase: ConnectionPhase, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (phase is ConnectionPhase.Connecting) {
        // While connecting the button is disabled but keeps its filled look, so the progress reads as the button's
        // own state rather than a greyed-out control.
        val scheme = MaterialTheme.colorScheme
        Button(
            onClick = {},
            modifier = modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
            enabled = false,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = scheme.primary, disabledContentColor = scheme.onPrimary,
            ),
        ) {
            LoadingIndicator(Modifier.size(24.dp), color = scheme.onPrimary)
            Text(
                stringResource(SharedR.string.onboarding_connecting_title, phase.address),
                Modifier.padding(start = 8.dp),
            )
        }
    } else {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
            Text(stringResource(SharedR.string.onboarding_connect))
        }
    }
}
