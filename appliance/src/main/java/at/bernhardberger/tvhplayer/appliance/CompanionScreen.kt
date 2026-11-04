package at.bernhardberger.tvhplayer.appliance

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState
import at.bernhardberger.tvhplayer.appliance.update.messageResource
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout

/**
 * The companion's setup and status screen: status and the accessibility disclosure on the left,
 * one focusable row per fact on the right. Stateless; [onRow] reports the activated row.
 */
@Composable
fun CompanionScreen(
    state: CompanionScreenState,
    onRow: (CompanionRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { CompanionRow.entries.associateWith { FocusRequester() } }
    // The first composition's state picks the focus. Later refreshes, such as returning from
    // Android settings, keep focus on the row that opened them.
    LaunchedEffect(Unit) {
        // Wait for the first layout, so focus on a row below the fold scrolls it into view.
        withFrameNanos { }
        focus.getValue(state.initialFocus).requestFocus()
    }
    fun row(row: CompanionRow) = Modifier.focusRequester(focus.getValue(row))

    GuidedStepLayout(guidance = { Guidance(state) }, modifier = modifier) {
        SectionHeading(R.string.section_this_tv)
        PlayerRow(state, row(CompanionRow.PLAYER)) { onRow(CompanionRow.PLAYER) }
        AccessibilityRow(state.accessibilityEnabled, row(CompanionRow.ACCESSIBILITY)) { onRow(CompanionRow.ACCESSIBILITY) }
        HomeRow(state, row(CompanionRow.HOME)) { onRow(CompanionRow.HOME) }
        if (state.showSystemHome) {
            GuidedStepAction(
                title = stringResource(R.string.google_tv_home),
                onClick = { onRow(CompanionRow.SYSTEM_HOME) },
                modifier = row(CompanionRow.SYSTEM_HOME),
                icon = R.drawable.ic_home,
                trailingIcon = R.drawable.ic_chevron_right,
            )
        }
        SectionHeading(R.string.section_updates, Modifier.padding(top = TvSpacing12))
        InstallUnknownRow(state.installAllowed, row(CompanionRow.UNKNOWN_APPS)) { onRow(CompanionRow.UNKNOWN_APPS) }
        AutomaticUpdatesRow(state.update.automatic, row(CompanionRow.AUTOMATIC)) { onRow(CompanionRow.AUTOMATIC) }
        UpdateRow(state, row(CompanionRow.UPDATE)) { onRow(CompanionRow.UPDATE) }
    }
}

@Composable
private fun Guidance(state: CompanionScreenState) {
    val overall = state.overall
    GuidedStepGuidance(
        title = when (overall) {
            CompanionOverall.PlayerMissing -> stringResource(R.string.status_player_missing)
            CompanionOverall.Updating -> stringResource(R.string.status_updating)
            is CompanionOverall.NeedsFix ->
                pluralStringResource(R.plurals.status_things_to_fix, overall.count, overall.count)
            CompanionOverall.AllSet -> stringResource(R.string.status_all_set)
        },
        body = stringResource(when (state.body) {
            CompanionBody.PLAYER_MISSING -> R.string.body_player_missing
            CompanionBody.UPDATING -> R.string.body_updating
            CompanionBody.ACCESSIBILITY_OFF -> R.string.body_accessibility_off
            CompanionBody.INSTALL_UNKNOWN_OFF -> R.string.body_install_unknown_off
            CompanionBody.SEVERAL_OPEN -> R.string.body_several_open
            CompanionBody.ALL_SET -> R.string.body_all_set
        }),
        lead = { Lead() },
    )
    if (state.updateMode == UpdateRowMode.AVAILABLE) {
        // An indicator, not a control: the update row below is the action.
        Text(
            stringResource(R.string.update_available_pill, state.update.latestVersion),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(top = TvSpacing16)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                .padding(horizontal = TvSpacing16, vertical = 6.dp),
        )
    }
    Column(
        Modifier.padding(top = TvSpacing24).fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(TvSpacing16),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.disclosure_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.appliance_accessibility_disclosure),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The companion's own launcher mark next to its name. */
@Composable
private fun Lead() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing16)) {
        // The adaptive foreground leaves a wide margin around the mark; crop it to the mark.
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.requiredSize(112.dp).align(Alignment.Center))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
            )
        }
    }
}

@Composable
private fun SectionHeading(title: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        modifier = modifier.padding(horizontal = TvSpacing16).semantics { heading() },
    )
}

@Composable
private fun StatusText(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

private fun statusIcon(ok: Boolean) = if (ok) R.drawable.ic_status_ok else R.drawable.ic_status_problem

/**
 * A row's leading status icon. A resolved check is brand primary while the row rests; a focused
 * row keeps its inverse content colour, and the other icons always follow the content colour.
 */
@Composable
private fun StatusIcon(@DrawableRes icon: Int, resolved: Boolean, focused: Boolean) {
    Icon(
        painterResource(icon),
        null,
        Modifier.size(24.dp),
        tint = if (resolved && !focused) MaterialTheme.colorScheme.primary else LocalContentColor.current,
    )
}

/** Tracks whether the row below this modifier holds focus. */
@Composable
private fun rememberRowFocus(modifier: Modifier): Pair<Modifier, Boolean> {
    var focused by remember { mutableStateOf(false) }
    return modifier.onFocusChanged { focused = it.hasFocus } to focused
}

@Composable
private fun PlayerRow(state: CompanionScreenState, modifier: Modifier, onClick: () -> Unit) {
    val installed = state.playerInstalled
    val (rowModifier, focused) = rememberRowFocus(modifier)
    GuidedStepAction(
        title = stringResource(R.string.player_name),
        subtitle = if (installed) stringResource(R.string.player_version, state.playerVersion.orEmpty())
            else stringResource(R.string.player_not_installed_hint),
        onClick = onClick,
        modifier = rowModifier,
        leadingIcon = { StatusIcon(statusIcon(installed), installed, focused) },
        trailingIcon = if (installed) null else R.drawable.ic_chevron_right,
        trailingContent = { StatusText(stringResource(if (installed) R.string.status_ready else R.string.status_install)) },
    )
}

@Composable
private fun AccessibilityRow(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (rowModifier, focused) = rememberRowFocus(modifier)
    GuidedStepAction(
        title = stringResource(R.string.accessibility_title),
        subtitle = stringResource(if (enabled) R.string.accessibility_on_hint else R.string.accessibility_off_hint),
        onClick = onClick,
        modifier = rowModifier,
        leadingIcon = { StatusIcon(statusIcon(enabled), enabled, focused) },
        trailingIcon = if (enabled) null else R.drawable.ic_chevron_right,
        trailingContent = { StatusText(stringResource(if (enabled) R.string.status_on else R.string.status_turn_on)) },
    )
}

@Composable
private fun HomeRow(state: CompanionScreenState, modifier: Modifier, onClick: () -> Unit) {
    val (rowModifier, focused) = rememberRowFocus(modifier)
    GuidedStepAction(
        title = stringResource(R.string.home_title),
        subtitle = stringResource(when {
            !state.defaultHome -> R.string.home_not_set_hint
            state.playerInstalled -> R.string.home_set_hint
            else -> R.string.home_waiting_hint
        }),
        onClick = onClick,
        modifier = rowModifier,
        leadingIcon = {
            StatusIcon(
                if (state.defaultHome) R.drawable.ic_status_ok else R.drawable.ic_status_neutral,
                state.defaultHome,
                focused,
            )
        },
        trailingIcon = if (state.defaultHome) null else R.drawable.ic_chevron_right,
        trailingContent = { StatusText(stringResource(if (state.defaultHome) R.string.status_set else R.string.status_not_set)) },
    )
}

@Composable
private fun InstallUnknownRow(allowed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (rowModifier, focused) = rememberRowFocus(modifier)
    GuidedStepAction(
        title = stringResource(R.string.install_unknown_title),
        subtitle = stringResource(
            if (allowed) R.string.install_unknown_allowed_hint else R.string.install_unknown_off_hint),
        onClick = onClick,
        modifier = rowModifier,
        leadingIcon = { StatusIcon(statusIcon(allowed), allowed, focused) },
        trailingIcon = if (allowed) null else R.drawable.ic_chevron_right,
        trailingContent = { StatusText(stringResource(if (allowed) R.string.status_allowed else R.string.status_allow)) },
    )
}

@Composable
private fun AutomaticUpdatesRow(automatic: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GuidedStepAction(
        title = stringResource(R.string.automatic_updates),
        subtitle = stringResource(R.string.automatic_updates_description),
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(automatic)
        },
        icon = R.drawable.ic_refresh,
        trailingContent = {
            Switch(checked = automatic, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        },
    )
}

/** One row for the whole update flow. While busy it keeps focus and ignores the click. */
@Composable
private fun UpdateRow(state: CompanionScreenState, modifier: Modifier, onClick: () -> Unit) {
    val update = state.update
    val mode = state.updateMode
    val message = stringResource(update.result.messageResource()).let {
        if (update.installerMessage.isBlank()) it else "$it: ${update.installerMessage}"
    }
    val busy = mode == UpdateRowMode.CHECKING || mode == UpdateRowMode.INSTALLING
    GuidedStepAction(
        title = when (mode) {
            UpdateRowMode.CHECK -> stringResource(when (state.updateFailure) {
                UpdateFailure.COULD_NOT_CHECK -> R.string.update_title_could_not_check
                UpdateFailure.UPDATE_FAILED -> R.string.update_title_failed
                null -> R.string.update_title_check
            })
            UpdateRowMode.AVAILABLE -> stringResource(R.string.update_title_available)
            UpdateRowMode.CHECKING -> stringResource(R.string.update_title_checking)
            UpdateRowMode.INSTALLING ->
                if (update.latestVersion.isEmpty()) stringResource(R.string.status_updating)
                else stringResource(R.string.update_title_installing, update.latestVersion)
        },
        // Available reads only the versions: the waiting result adds nothing the badge and pill omit.
        subtitle = when (mode) {
            UpdateRowMode.CHECKING, UpdateRowMode.INSTALLING -> message
            UpdateRowMode.AVAILABLE -> updateVersions(update, null)
            UpdateRowMode.CHECK -> updateVersions(update, message)
        },
        onClick = if (busy) ({}) else onClick,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (busy) disabled()
        },
        icon = if (state.updateFailure != null) R.drawable.ic_status_problem else R.drawable.ic_download,
        // Busy stays enabled so it keeps its focus, as the onboarding search action does.
        leadingIcon = if (busy) {
            { CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 3.dp) }
        } else null,
        trailingContent = if (mode == UpdateRowMode.AVAILABLE) ({ NewBadge() }) else null,
    )
}

@Composable
private fun updateVersions(update: UpdateScreenState, message: String?): String = listOfNotNull(
    stringResource(R.string.update_installed, BuildConfig.VERSION_NAME),
    update.latestVersion.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.update_latest, it) },
    message,
).joinToString(" · ")

@Composable
private fun NewBadge() {
    Text(
        stringResource(R.string.update_badge_new),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = TvSpacing12, vertical = 4.dp),
    )
}
