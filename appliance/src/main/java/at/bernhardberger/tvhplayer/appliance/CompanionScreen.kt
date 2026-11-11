package at.bernhardberger.tvhplayer.appliance

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import android.os.SystemClock
import at.bernhardberger.tvhplayer.appliance.update.UpdateResult
import at.bernhardberger.tvhplayer.appliance.update.UpdateScreenState
import at.bernhardberger.tvhplayer.appliance.update.messageResource
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.GuidedStepAction
import at.bernhardberger.tvhplayer.ui.components.GuidedStepGuidance
import at.bernhardberger.tvhplayer.ui.components.GuidedStepLayout

private fun CompanionFunction.row(): CompanionRow = when (this) {
    CompanionFunction.STARTUP -> CompanionRow.STARTUP
    CompanionFunction.LIVE_TV -> CompanionRow.LIVE_TV
    CompanionFunction.GUIDE -> CompanionRow.GUIDE
    CompanionFunction.RECORDINGS -> CompanionRow.RECORDINGS
}

/** The function whose page a row opens; the startup switch has none. */
private fun CompanionRow.pageFunction(): CompanionFunction? =
    CompanionFunction.entries.firstOrNull { it != CompanionFunction.STARTUP && it.row() == this }

@get:StringRes
private val CompanionFunction.titleRes: Int
    get() = when (this) {
        CompanionFunction.STARTUP -> R.string.function_startup_title
        CompanionFunction.LIVE_TV -> R.string.function_live_tv_title
        CompanionFunction.GUIDE -> R.string.function_guide_title
        CompanionFunction.RECORDINGS -> R.string.function_recordings_title
    }

@get:StringRes
private val CompanionFunction.targetRes: Int
    get() = when (this) {
        CompanionFunction.STARTUP -> error("Startup has no button")
        CompanionFunction.LIVE_TV -> R.string.function_live_tv_target
        CompanionFunction.GUIDE -> R.string.function_guide_target
        CompanionFunction.RECORDINGS -> R.string.function_recordings_target
    }

private fun defaultLabel(function: CompanionFunction) = CompanionFunctions().buttonLabel(function)

/** "The TV button" / "Button 4001": the generic key name already starts like a sentence. */
private fun subject(name: String, capital: Boolean) =
    if (name.startsWith("Button ")) name else (if (capital) "The " else "the ") + name

/**
 * The companion's screens: the setup and status list, and one page per button function opened from it.
 * The open page survives recreation; an in-flight capture ends as cancelled when the activity stops.
 * [onRow] reports every row that is not a function page.
 */
@Composable
fun CompanionContent(
    state: CompanionScreenState,
    capture: CaptureState,
    onRow: (CompanionRow) -> Unit,
    onStartCapture: (CompanionFunction) -> Boolean,
    onCancelCapture: () -> Unit,
    onDismissCapture: () -> Unit,
    onAssign: (CompanionFunction, ButtonBinding) -> FunctionChange,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf<CompanionFunction?>(null) }
    // The function whose page just closed: the list returns focus to its row.
    var returnedFrom by rememberSaveable { mutableStateOf<CompanionFunction?>(null) }
    val open = page
    if (open == null) {
        CompanionScreen(
            state = state,
            onRow = { row -> row.pageFunction()?.let { page = it } ?: onRow(row) },
            modifier = modifier,
            initialFocus = returnedFrom?.row() ?: state.initialFocus,
        )
    } else {
        FunctionPage(
            function = open,
            state = state,
            capture = capture,
            onStartCapture = onStartCapture,
            onCancelCapture = onCancelCapture,
            onDismissCapture = onDismissCapture,
            onAssign = onAssign,
            onClose = { returnedFrom = open; page = null },
            modifier = modifier,
        )
    }
}

/**
 * The companion's setup and status screen: status and the accessibility disclosure on the left,
 * one focusable row per fact on the right. Stateless; [onRow] reports the activated row.
 */
@Composable
fun CompanionScreen(
    state: CompanionScreenState,
    onRow: (CompanionRow) -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: CompanionRow = state.initialFocus,
) {
    val focus = remember { CompanionRow.entries.associateWith { FocusRequester() } }
    // The first composition's state picks the focus. Later refreshes, such as returning from
    // Android settings, keep focus on the row that opened them.
    LaunchedEffect(Unit) {
        // Wait for the first layout, so focus on a row below the fold scrolls it into view.
        withFrameNanos { }
        focus.getValue(initialFocus).requestFocus()
    }
    fun row(row: CompanionRow) = Modifier.focusRequester(focus.getValue(row))

    GuidedStepLayout(guidance = { Guidance(state) }, modifier = modifier) {
        SectionHeading(R.string.section_this_tv)
        PlayerRow(state, row(CompanionRow.PLAYER)) { onRow(CompanionRow.PLAYER) }
        AccessibilityRow(state.accessibilityStatus, row(CompanionRow.ACCESSIBILITY)) { onRow(CompanionRow.ACCESSIBILITY) }
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
        SectionHeading(
            R.string.section_functions,
            Modifier.padding(top = TvSpacing12),
            trailing = when (state.accessibilityStatus) {
                CompanionServiceStatus.ON -> null
                CompanionServiceStatus.OFF -> { { PausedText(R.string.functions_paused_off) } }
                CompanionServiceStatus.BLOCKED -> { { PausedText(R.string.functions_paused_blocked) } }
            },
        )
        StartupRow(state.functions.startup, row(CompanionRow.STARTUP)) { onRow(CompanionRow.STARTUP) }
        for (function in CompanionFunction.entries.filter { it != CompanionFunction.STARTUP }) {
            FunctionRow(function, state.functions, row(function.row())) { onRow(function.row()) }
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
        title = when {
            state.body == CompanionBody.BLOCKED -> stringResource(R.string.status_blocked_by_tv)
            else -> when (overall) {
                CompanionOverall.PlayerMissing -> stringResource(R.string.status_player_missing)
                CompanionOverall.Updating -> stringResource(R.string.status_updating)
                is CompanionOverall.NeedsFix ->
                    pluralStringResource(R.plurals.status_things_to_fix, overall.count, overall.count)
                CompanionOverall.AllSet -> stringResource(R.string.status_all_set)
            }
        },
        body = when (state.body) {
            CompanionBody.PLAYER_MISSING -> stringResource(R.string.body_player_missing)
            CompanionBody.UPDATING -> stringResource(R.string.body_updating)
            CompanionBody.ACCESSIBILITY_OFF -> stringResource(R.string.body_accessibility_off)
            CompanionBody.BLOCKED -> stringResource(R.string.body_accessibility_blocked)
            CompanionBody.INSTALL_UNKNOWN_OFF -> stringResource(R.string.body_install_unknown_off)
            CompanionBody.SEVERAL_OPEN -> stringResource(R.string.body_several_open)
            CompanionBody.ALL_SET -> state.functions.allSetBody(state.defaultHome)
        },
        lead = { Lead(stringResource(R.string.setup_subtitle)) },
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
    InfoCard(stringResource(R.string.disclosure_heading), stringResource(R.string.appliance_accessibility_disclosure))
}

/** The rounded panel under the guidance text and the capture card's body. */
@Composable
private fun CardBox(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(TvSpacing16),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** A heading and a paragraph on the left pane. [titleColor] marks a notice, such as a moved button. */
@Composable
private fun InfoCard(title: String, body: String, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    CardBox(Modifier.padding(top = TvSpacing24)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The companion's own launcher mark next to its name. */
@Composable
private fun Lead(subtitle: String) {
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
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
            )
        }
    }
}

/** A section title; [trailing] adds a status at the end of the line, such as why a section is paused. */
@Composable
private fun SectionHeading(title: Int, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = TvSpacing16).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        )
        trailing?.invoke(this)
    }
}

@Composable
private fun RowScope.PausedText(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f),
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
private fun AccessibilityRow(status: CompanionServiceStatus, modifier: Modifier, onClick: () -> Unit) {
    val enabled = status == CompanionServiceStatus.ON
    val (rowModifier, focused) = rememberRowFocus(modifier)
    GuidedStepAction(
        title = stringResource(R.string.accessibility_title),
        subtitle = stringResource(when (status) {
            CompanionServiceStatus.ON -> R.string.accessibility_on_hint
            CompanionServiceStatus.OFF -> R.string.accessibility_off_hint
            CompanionServiceStatus.BLOCKED -> R.string.accessibility_blocked_hint
        }),
        onClick = onClick,
        modifier = rowModifier,
        leadingIcon = { StatusIcon(statusIcon(enabled), enabled, focused) },
        // Only the off state leads to a settings page; blocked is checked again in place.
        trailingIcon = if (status == CompanionServiceStatus.OFF) R.drawable.ic_chevron_right else null,
        trailingContent = { StatusText(stringResource(when (status) {
            CompanionServiceStatus.ON -> R.string.status_on
            CompanionServiceStatus.OFF -> R.string.status_turn_on
            CompanionServiceStatus.BLOCKED -> R.string.status_blocked
        })) },
    )
}

@Composable
private fun StartupRow(startup: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GuidedStepAction(
        title = stringResource(R.string.function_startup_title),
        subtitle = stringResource(R.string.function_startup_hint),
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(startup)
        },
        icon = R.drawable.ic_power,
        trailingContent = {
            Switch(checked = startup, onCheckedChange = null, modifier = Modifier.clearAndSetSemantics { })
        },
    )
}

/** A button function: a set button is a check, a function without a button is off. */
@Composable
private fun FunctionRow(function: CompanionFunction, functions: CompanionFunctions, modifier: Modifier, onClick: () -> Unit) {
    val binding = functions.binding(function)
    val set = binding != ButtonBinding.NotSet
    val (rowModifier, focused) = rememberRowFocus(modifier)
    val state = stringResource(if (set) R.string.status_set else R.string.status_not_set)
    GuidedStepAction(
        title = stringResource(function.titleRes),
        subtitle = when (binding) {
            ButtonBinding.Default -> stringResource(R.string.function_hint_default)
            is ButtonBinding.Custom -> stringResource(R.string.function_hint_changed, defaultLabel(function))
            ButtonBinding.NotSet -> stringResource(R.string.function_hint_off)
        },
        onClick = onClick,
        modifier = rowModifier.semantics { stateDescription = state },
        leadingIcon = { StatusIcon(if (set) R.drawable.ic_status_ok else R.drawable.ic_status_neutral, set, focused) },
        trailingIcon = R.drawable.ic_chevron_right,
        trailingContent = { StatusText(functions.buttonLabel(function)) },
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
    val message = stringResource(update.messageResource()).let {
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
        // Manual updates need a foreground retry; the New badge alone cannot explain that.
        subtitle = when (mode) {
            UpdateRowMode.CHECKING, UpdateRowMode.INSTALLING -> message
            UpdateRowMode.AVAILABLE -> updateVersions(update,
                message.takeIf { !update.automatic && update.result == UpdateResult.WAITING_STANDBY })
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

/** What the page confirms after an action; the rows below already show the new state. */
private sealed interface PageResult {
    data class Saved(val keyCode: Int, val moved: MovedButton?) : PageResult
    data class Restored(val moved: MovedButton?) : PageResult
    data object Removed : PageResult
}

private enum class PagePhase { Rows, Capturing, TimedOut }

/**
 * One button function's page: the current button, and the actions that change it. Capture is a
 * state of this page. While the service listens nothing here is focusable, so no key can activate
 * a row. Back leaves a capture first, then the page.
 */
@Composable
private fun FunctionPage(
    function: CompanionFunction,
    state: CompanionScreenState,
    capture: CaptureState,
    onStartCapture: (CompanionFunction) -> Boolean,
    onCancelCapture: () -> Unit,
    onDismissCapture: () -> Unit,
    onAssign: (CompanionFunction, ButtonBinding) -> FunctionChange,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val functions = state.functions
    val binding = functions.binding(function)
    val serviceOn = state.accessibilityStatus == CompanionServiceStatus.ON
    var started by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PageResult?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }

    val live = if (started) capture else null
    val phase = when (live) {
        is CaptureState.Waiting, is CaptureState.Refused -> PagePhase.Capturing
        is CaptureState.TimedOut -> PagePhase.TimedOut
        else -> PagePhase.Rows
    }
    val shown = (live as? CaptureState.Captured)?.let { PageResult.Saved(it.keyCode, it.moved) } ?: result

    val dismissCapture by rememberUpdatedState(onDismissCapture)
    DisposableEffect(function) {
        onDispose { dismissCapture() }
    }
    LaunchedEffect(live) {
        when (live) {
            is CaptureState.Captured -> {
                result = PageResult.Saved(live.keyCode, live.moved)
                started = false
                onDismissCapture()
            }
            CaptureState.Cancelled -> {
                started = false
                onDismissCapture()
            }
            else -> Unit
        }
    }
    // The first row of every state takes focus: Set or Change, Try again, or the row after an action.
    LaunchedEffect(phase, shown, focusTick) {
        if (phase != PagePhase.Capturing) {
            withFrameNanos { }
            first.requestFocus()
        }
    }
    BackHandler {
        if (started) {
            onCancelCapture()
            onDismissCapture()
            started = false
            focusTick++
        } else {
            onClose()
        }
    }

    fun startCapture() {
        if (onStartCapture(function)) {
            started = true
            result = null
        } else {
            started = false
        }
    }

    GuidedStepLayout(
        guidance = { PageGuidance(function, functions, phase, shown, live) },
        modifier = modifier,
    ) {
        SectionHeading(R.string.section_button)
        when (phase) {
            PagePhase.Capturing -> live?.let { CaptureCard(it) }
            PagePhase.TimedOut -> {
                GuidedStepAction(
                    title = stringResource(R.string.row_try_again),
                    subtitle = stringResource(R.string.row_try_again_hint),
                    onClick = ::startCapture,
                    modifier = Modifier.focusRequester(first),
                    icon = R.drawable.ic_refresh,
                    trailingIcon = R.drawable.ic_chevron_right,
                )
                GuidedStepAction(
                    title = stringResource(R.string.row_cancel),
                    subtitle = stringResource(R.string.row_cancel_hint),
                    onClick = { onDismissCapture(); started = false; focusTick++ },
                    icon = R.drawable.ic_close,
                )
            }
            PagePhase.Rows -> {
                val notSet = binding == ButtonBinding.NotSet
                // Without the service nothing could capture: keep the row focusable, but off.
                GuidedStepAction(
                    title = stringResource(if (notSet) R.string.row_set else R.string.row_change),
                    subtitle = stringResource(when {
                        !serviceOn -> R.string.row_needs_service
                        notSet -> R.string.row_set_hint
                        else -> R.string.row_change_hint
                    }, functions.buttonLabel(function)),
                    onClick = if (serviceOn) ::startCapture else ({}),
                    modifier = Modifier.focusRequester(first).then(
                        if (serviceOn) Modifier else Modifier.alpha(0.6f).semantics { disabled() }),
                    icon = R.drawable.ic_remote,
                    trailingIcon = if (serviceOn) R.drawable.ic_chevron_right else null,
                )
                if (binding != ButtonBinding.Default) {
                    GuidedStepAction(
                        title = stringResource(R.string.row_restore),
                        subtitle = stringResource(
                            if (binding == ButtonBinding.NotSet) R.string.row_restore_hint else R.string.row_restore_hint_again,
                            defaultLabel(function)),
                        onClick = {
                            result = PageResult.Restored(onAssign(function, ButtonBinding.Default).moved.firstOrNull())
                            focusTick++
                        },
                        icon = R.drawable.ic_restore,
                    )
                }
                if (!notSet) {
                    GuidedStepAction(
                        title = stringResource(R.string.row_remove),
                        subtitle = stringResource(R.string.row_remove_hint),
                        onClick = {
                            onAssign(function, ButtonBinding.NotSet)
                            result = PageResult.Removed
                            focusTick++
                        },
                        icon = R.drawable.ic_remove,
                    )
                }
            }
        }
    }
}

/** The left pane of a function page for each of its states. */
@Composable
private fun PageGuidance(
    function: CompanionFunction,
    functions: CompanionFunctions,
    phase: PagePhase,
    result: PageResult?,
    capture: CaptureState?,
) {
    val name = stringResource(function.titleRes)
    val target = stringResource(function.targetRes)
    val binding = functions.binding(function)
    val label = functions.buttonLabel(function)
    val howItWorks = stringResource(R.string.card_how_title) to stringResource(R.string.card_how_body, target)
    val title: String
    val body: String
    when {
        phase == PagePhase.Capturing -> {
            title = stringResource(R.string.capture_title)
            body = stringResource(R.string.capture_body, target)
        }
        phase == PagePhase.TimedOut -> {
            title = stringResource(R.string.timed_out_title)
            body = stringResource(R.string.timed_out_body)
        }
        result is PageResult.Saved -> {
            title = stringResource(
                if (result.moved != null) R.string.result_moved_title else R.string.result_saved_title)
            body = stringResource(R.string.result_opens_body, subject(buttonName(result.keyCode), true), target)
        }
        result is PageResult.Restored -> {
            title = stringResource(R.string.result_restored_title)
            body = stringResource(R.string.result_restored_body, subject(label, true), target)
        }
        result is PageResult.Removed -> {
            title = stringResource(R.string.result_removed_title)
            body = stringResource(R.string.result_removed_body, name)
        }
        else -> {
            title = name
            body = when (binding) {
                ButtonBinding.Default -> stringResource(R.string.page_body_default, subject(label, true), target)
                is ButtonBinding.Custom ->
                    stringResource(R.string.page_body_custom, subject(label, true), target, defaultLabel(function))
                ButtonBinding.NotSet -> stringResource(R.string.page_body_not_set)
            }
        }
    }
    GuidedStepGuidance(
        title = title,
        body = body,
        lead = { Lead(stringResource(R.string.page_subtitle, name)) },
    )
    val moved = when (result) {
        is PageResult.Saved -> result.moved
        is PageResult.Restored -> result.moved
        else -> null
    }
    when {
        phase != PagePhase.Rows -> InfoCard(howItWorks.first, howItWorks.second)
        moved != null -> MovedCard(moved, functions)
        else -> when (binding) {
            ButtonBinding.NotSet -> InfoCard(howItWorks.first, howItWorks.second)
            ButtonBinding.Default -> InfoCard(
                stringResource(R.string.card_changes_title),
                stringResource(R.string.card_changes_default, subject(label, true)))
            is ButtonBinding.Custom -> InfoCard(
                stringResource(R.string.card_changes_title),
                stringResource(R.string.card_changes_custom, subject(label, true)))
        }
    }
}

/** Says which function lost a button to this one, and how to get it back. */
@Composable
private fun MovedCard(moved: MovedButton, functions: CompanionFunctions) {
    val from = stringResource(moved.function.titleRes)
    val name = buttonName(moved.keyCode)
    val left = functions.keys(moved.function)
    if (left.isEmpty()) {
        InfoCard(
            title = stringResource(R.string.moved_off_title, from),
            body = stringResource(R.string.moved_off_body, subject(name, true), from, subject(name, false)),
            titleColor = MaterialTheme.colorScheme.tertiary,
        )
    } else {
        val leftKey = left.first()
        // Two codes with one name (the TV button) read as one button unless the codes are shown.
        val shared = buttonName(leftKey) == name
        val movedName = if (shared) buttonNameWithCode(moved.keyCode) else name
        val leftName = if (shared) buttonNameWithCode(leftKey) else buttonName(leftKey)
        InfoCard(
            title = stringResource(R.string.moved_partial_title, from),
            body = stringResource(R.string.moved_partial_body, subject(movedName, true), from,
                subject(leftName, false), subject(movedName, false)),
            titleColor = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * The capture's status: waiting, or the refusal of the last key. Not focusable, and a polite live
 * region so a screen reader follows the refusals. The bar shows the time left to the deadline.
 */
@Composable
private fun CaptureCard(state: CaptureState) {
    val deadline: Long
    val refused: String?
    when (state) {
        is CaptureState.Waiting -> { deadline = state.deadline; refused = null }
        is CaptureState.Refused -> { deadline = state.deadline; refused = state.refusedKeyName }
        else -> return
    }
    val total = ButtonCapture.TIMEOUT_MILLIS
    fun left() = (deadline - SystemClock.elapsedRealtime()).coerceIn(0L, total)
    val timeLeft = remember(deadline) { Animatable(left() / total.toFloat()) }
    LaunchedEffect(deadline) {
        timeLeft.animateTo(0f, tween(left().toInt(), easing = LinearEasing))
    }
    val colors = MaterialTheme.colorScheme
    CardBox(
        Modifier.width(TvBrowseColumnWidth).padding(vertical = 0.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.padding(top = 12.dp).size(148.dp).border(2.dp, colors.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(112.dp).border(2.dp, colors.primary.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(72.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_remote), null, Modifier.size(36.dp), tint = colors.onPrimary)
                }
            }
        }
        // Sized for the refused layout, so a refusal does not move the bar, the hint or the card's edge.
        val typography = MaterialTheme.typography
        val statusHeight = with(LocalDensity.current) {
            typography.titleLarge.lineHeight.toDp() + typography.titleMedium.lineHeight.toDp()
        }
        Column(
            Modifier.padding(top = TvSpacing12).heightIn(min = statusHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (refused == null) stringResource(R.string.capture_waiting)
                else stringResource(R.string.capture_refused, refused),
                style = typography.titleLarge,
                color = if (refused == null) colors.onSurface else colors.error,
                textAlign = TextAlign.Center,
            )
            if (refused != null) {
                Text(
                    stringResource(R.string.capture_refused_detail),
                    style = typography.titleMedium,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LinearProgressIndicator(
            progress = { timeLeft.value },
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvSpacing24, vertical = 8.dp).height(6.dp),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text(
            stringResource(if (refused == null) R.string.capture_waiting_hint else R.string.capture_refused_hint),
            // One step above the row subtitles. The waiting hint needs three lines at this size, so both
            // states reserve three and the card keeps its height; balanced breaks avoid a lone last word.
            style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
            minLines = 3,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}
