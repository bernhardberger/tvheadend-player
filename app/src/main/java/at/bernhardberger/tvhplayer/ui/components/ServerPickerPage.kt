package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R

/** The approved avatar selector has no existing equivalent: shared by browse and cold startup. */
@Composable
internal fun ServerPickerPage(
    profiles: List<ServerProfileSummary>,
    activeId: ServerProfileId?,
    onChoose: (ServerProfileSummary) -> Unit,
    onAdd: () -> Unit,
    initialFocusEnabled: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
    contentFocusEnabled: Boolean = true,
    drawerOpen: Boolean = false,
) {
    val add = remember { FocusRequester() }
    val requesters = remember(profiles.map { it.id }) { profiles.associate { it.id.value to FocusRequester() } }
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var addWasFocused by rememberSaveable { mutableStateOf(false) }
    var returnId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnToAdd by rememberSaveable { mutableStateOf(false) }
    SideEffect {
        if (!initialFocusEnabled) { returnId = focusedId; returnToAdd = addWasFocused }
    }
    val entryId = focusedId?.takeIf { it in requesters } ?: activeId?.value?.takeIf { it in requesters }
        ?: profiles.firstOrNull()?.id?.value
    LaunchedEffect(initialFocusEnabled, profiles.map { it.id }) {
        if (initialFocusEnabled) {
            val target = if (returnToAdd || (returnId == null && addWasFocused)) add else requesters[returnId ?: entryId] ?: add
            withFrameNanos { }
            withFrameNanos { }
            target.requestFocus()
            returnId = null
            returnToAdd = false
        }
    }
    Column(modifier.fillMaxSize().padding(end = if (drawerOpen) 168.dp else 0.dp).padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.server_picker_title), style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(40.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(264.dp), contentAlignment = Alignment.TopCenter) {
            if (profiles.isEmpty() && message != null) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val overflow = profiles.size * 152.dp.value + (if (initialFocusEnabled && focusedId != null) 34 else 0) + 24 > maxWidth.value
            Row(Modifier.then(if (overflow) Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                .requiredWidth(maxWidth + 58.dp).align(Alignment.TopStart) else Modifier)
                .horizontalScroll(rememberScrollState()).focusGroup().padding(horizontal = 12.dp)) {
                profiles.forEach { profile ->
                    key(profile.id) {
                        ServerPickerChoice(profile, profile.id == activeId, { onChoose(profile) },
                            onFocused = { if (initialFocusEnabled) { focusedId = profile.id.value; addWasFocused = false } },
                            Modifier.focusRequester(requesters.getValue(profile.id.value))
                                .focusProperties { down = add; canFocus = contentFocusEnabled })
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.focusRequester(add)
            .onFocusChanged { if (it.isFocused && initialFocusEnabled && focusedId != null) addWasFocused = true }
            .focusProperties { canFocus = contentFocusEnabled; requesters[entryId]?.let { up = it } }.testTag("server-picker-add"),
            colors = OutlinedButtonDefaults.colors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Text(stringResource(R.string.server_add))
        }
    }
}

/** Keeps focus growth on the avatar only; the caption must not scale with a TV card container. */
@Composable
private fun ServerPickerChoice(profile: ServerProfileSummary, active: Boolean, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    LaunchedEffect(focused) {
        withFrameNanos { }
        // The unfocused next server may peek beyond the safe edge; the focused circle may not.
        if (focused) bring.bringIntoView(with(density) { Rect(-12.dp.toPx(), 0f, 239.dp.toPx(), 176.dp.toPx()) })
    }
    val account = profile.authenticationMode == ServerProfileAuthenticationMode.PASSWORD
    val host = serverAddress(profile.host, profile.port.toString())
    val name = profile.label ?: profile.username?.takeIf { account } ?: host
    val showAddress = host != name
    val status = when {
        active -> stringResource(R.string.server_in_use)
        account && !profile.credentialsAvailable -> stringResource(R.string.server_password_needed)
        else -> null
    }
    val announcement = listOfNotNull(name, host.takeIf { showAddress }, status).joinToString(", ")
    Surface(onClick = onClick, modifier = modifier.width(if (focused) 186.dp else 152.dp)
        .requiredWidth(if (focused) 186.dp else 176.dp).bringIntoViewRequester(bring)
        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }.semantics { contentDescription = announcement }
        .testTag("server-picker-${profile.id.value}"),
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent)) {
        Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(176.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val size = if (focused) 160.dp else 120.dp
                ServerAvatar(serverAvatarLook(profile), size, Modifier.requiredSize(size)
                    .then(if (focused) Modifier.shadow(16.dp, CircleShape).border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    else Modifier))
            }
            Text(name.replace("-", "-\u200B"), fontSize = if (focused) 22.sp else 16.sp, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
                maxLines = if (focused) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (showAddress) Text(host, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
