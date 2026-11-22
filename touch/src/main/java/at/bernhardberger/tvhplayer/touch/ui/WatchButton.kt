package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.preferKeepClear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R

/** Keeps the watch actions in the order their shared policy supplied. */
internal fun watchWays(actions: List<BrowseContextAction>): List<BrowseContextAction> =
    actions.filter { it.watchLabel() != null }

/**
 * Accepts the ordered result of [watchWays] and a stable [subject] key for the channel, program,
 * or recording. Changing the key or leaving composition closes the menu.
 * In a bottom-end overlay, apply the host's 16 dp end/bottom inset only to the single FAB;
 * the stock menu already provides that inset.
 */
@Composable
internal fun WatchButton(
    ways: List<BrowseContextAction>,
    subject: Any?,
    modifier: Modifier = Modifier,
    onWatch: (BrowseContextAction) -> Unit,
) {
    if (ways.isEmpty()) return
    val onlyWay = ways.singleOrNull()
    if (onlyWay != null) {
        val label = stringResource(checkNotNull(onlyWay.watchLabel()))
        ExtendedFloatingActionButton(
            // This stock overload clears the text slot's semantics during its expand/collapse animation.
            modifier = modifier.preferKeepClear().semantics { contentDescription = label },
            onClick = { onWatch(onlyWay) },
            icon = { Icon(painterResource(R.drawable.channels_play), contentDescription = null) },
            text = { Text(label) },
        )
    } else {
        WatchMenu(ways, subject, modifier, onWatch)
    }
}

@Composable
private fun WatchMenu(
    ways: List<BrowseContextAction>,
    subject: Any?,
    modifier: Modifier,
    onWatch: (BrowseContextAction) -> Unit,
) {
    var expanded by remember(subject) { mutableStateOf(false) }
    val toggleFocus = remember { FocusRequester() }
    val close = { expanded = false }
    val closeLabel = stringResource(R.string.watch_options_close_menu)

    BackHandler(enabled = expanded, onBack = close)

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = { WatchMenuToggle(expanded, { expanded = it }, toggleFocus) },
    ) {
        ways.forEachIndexed { index, action ->
            FloatingActionButtonMenuItem(
                modifier = Modifier
                    .semantics {
                        isTraversalGroup = true
                        if (index == ways.lastIndex) {
                            customActions = listOf(CustomAccessibilityAction(closeLabel) {
                                close()
                                true
                            })
                        }
                    }
                    .then(if (index == 0) Modifier.returnFocusToToggle(toggleFocus) else Modifier),
                onClick = {
                    close()
                    onWatch(action)
                },
                icon = { Icon(painterResource(action.watchIcon()), contentDescription = null) },
                text = { WatchLabel(action) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchMenuToggle(expanded: Boolean, onToggle: (Boolean) -> Unit, focus: FocusRequester) {
    val name = stringResource(R.string.watch_options)
    val state = stringResource(if (expanded) R.string.watch_options_expanded else R.string.watch_options_collapsed)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (expanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(name) } },
        state = rememberTooltipState(),
    ) {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = onToggle,
            modifier = Modifier
                .preferKeepClear()
                .semantics {
                    traversalIndex = -1f
                    contentDescription = name
                    stateDescription = state
                }
                .focusRequester(focus),
        ) {
            val icon by remember {
                derivedStateOf {
                    if (checkedProgress > 0.5f) R.drawable.connect_close_outline else R.drawable.channels_play
                }
            }
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.animateIcon(checkedProgress = { checkedProgress }),
            )
        }
    }
}

private fun Modifier.returnFocusToToggle(focus: FocusRequester): Modifier = onKeyEvent {
    val goingBack = it.key == Key.DirectionUp || it.key == Key.NumPadDirectionUp ||
        (it.isShiftPressed && it.key == Key.Tab)
    if (it.type == KeyEventType.KeyDown && goingBack) {
        focus.requestFocus()
        true
    } else {
        false
    }
}

@Composable
private fun WatchLabel(action: BrowseContextAction) {
    Text(stringResource(checkNotNull(action.watchLabel())))
}

@StringRes
private fun BrowseContextAction.watchLabel(): Int? = when (this) {
    BrowseContextAction.CONTINUE_WATCHING -> SharedR.string.player_continue_watching
    BrowseContextAction.WATCH_LIVE -> SharedR.string.context_watch_live
    BrowseContextAction.WATCH -> SharedR.string.context_watch
    BrowseContextAction.START_OVER -> SharedR.string.context_start_over
    BrowseContextAction.PLAY -> SharedR.string.play_recording
    BrowseContextAction.RESUME -> SharedR.string.context_resume
    else -> null
}

private fun BrowseContextAction.watchIcon(): Int = when (this) {
    BrowseContextAction.WATCH_LIVE -> R.drawable.watch_live
    BrowseContextAction.START_OVER -> R.drawable.watch_replay
    else -> R.drawable.channels_play
}
