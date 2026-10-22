package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvOverlayBoxAlpha
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.leaving

/** One button of [TvRecoveryOverlay]'s action row. */
data class TvRecoveryAction(val label: String, val onClick: () -> Unit)

/**
 * [actions], when given, replace the primary and secondary action: up to [MaxRecoveryActions]
 * buttons in order, the first focused when they appear or change. [icon] heads the alert; [busy]
 * puts a progress ring there instead, as does an alert without actions. [status] is a line under the
 * text that may change while shown, such as a countdown; the live region holds [message], [detail]
 * and [hint], not [status]. [footnote] is a faint line under them for a bug report, such as the
 * player's own error, also outside the live region. [actionFocus], when given, is the first
 * action's requester for a caller that moves focus there itself, as a browse screen does after
 * its navigation rail; the overlay then takes no focus on its own. [leadingExit] is where Left from
 * the first action goes and [upExit] where Up from any action goes; both stay inside by default.
 */
@Composable
fun TvRecoveryOverlay(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    hint: String? = null,
    opaque: Boolean = true,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    primaryActionEnabled: Boolean = true,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    liveRegionMode: LiveRegionMode = LiveRegionMode.Polite,
    actions: List<TvRecoveryAction> = emptyList(),
    status: String? = null,
    icon: Painter? = null,
    busy: Boolean = false,
    footnote: String? = null,
    actionFocus: FocusRequester? = null,
    leadingExit: FocusRequester = FocusRequester.Cancel,
    upExit: FocusRequester = FocusRequester.Cancel,
) {
    require(actions.size <= MaxRecoveryActions) { "At most $MaxRecoveryActions recovery actions" }
    val buttons = actions.ifEmpty {
        val primary = if (primaryActionLabel != null && onPrimaryAction != null) {
            TvRecoveryAction(primaryActionLabel, onPrimaryAction)
        } else {
            null
        }
        val secondary = if (secondaryActionLabel != null && onSecondaryAction != null) {
            TvRecoveryAction(secondaryActionLabel, onSecondaryAction)
        } else {
            null
        }
        // As before, a secondary action shows only beside a primary one.
        if (primary == null) emptyList() else listOfNotNull(primary, secondary)
    }
    val ownFocus = remember { List(MaxRecoveryActions) { FocusRequester() } }
    val buttonFocus = if (actionFocus == null) ownFocus else listOf(actionFocus) + ownFocus.drop(1)
    val primaryVisible = buttons.isNotEmpty()
    val labels = buttons.map { it.label }
    val holderFocus = remember { FocusRequester() }
    var holding by remember { mutableStateOf(false) }
    var holderFocused by remember { mutableStateOf(false) }
    LaunchedEffect(visible, labels) {
        if (visible && primaryVisible && actionFocus == null) {
            // Take focus from anything behind the overlay at once, then wait one frame
            // so the action is ready to draw its focused state.
            holding = true
            try {
                runCatching { holderFocus.requestFocus() }
                withFrameNanos { }
                runCatching { buttonFocus[0].requestFocus() }
            } finally {
                holding = false
            }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
        modifier = modifier,
    ) {
        PlayerMotionFrame(leaving = leaving) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (opaque) MaterialTheme.colorScheme.background else Color.Black.copy(alpha = TvOverlayBoxAlpha))
                    .padding(48.dp)
                    // Holds focus without a visible indication until the action takes it;
                    // OK and directions there do nothing, so none reaches what is behind.
                    .focusRequester(holderFocus)
                    .onFocusChanged { holderFocused = it.isFocused }
                    .onKeyEvent { event -> holderFocused && event.key in HeldKeys }
                    .focusProperties { canFocus = holding }
                    .focusable()
                    .focusGroup()
                    .semantics {
                        paneTitle = message
                        if (primaryVisible) dialog()
                        isTraversalGroup = true
                    }
                    .testTag("tv-recovery-overlay"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The kit's Full Screen dialog: icon, 32 dp, headline, 16 dp, body, 48 dp, actions.
                val ring = busy || !primaryVisible
                if (ring) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(4.dp).size(RecoveryIconSize - 8.dp),
                    )
                } else if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(RecoveryIconSize),
                    )
                }
                // One live region for the alert's text; a changing [status] stays outside it.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = if (ring || icon != null) 32.dp else 0.dp)
                        .widthIn(max = RecoveryTextWidth)
                        .semantics { liveRegion = liveRegionMode }
                        .testTag("tv-recovery-announcement"),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("tv-recovery-title"),
                    )
                    val body = MaterialTheme.colorScheme.onSurface.copy(alpha = RecoveryBodyAlpha)
                    if (detail != null) {
                        Text(
                            text = detail,
                            color = body,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    if (hint != null) {
                        Text(
                            text = hint,
                            color = body,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = if (detail != null) 0.dp else 16.dp),
                        )
                    }
                }
                if (status != null) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .widthIn(max = RecoveryTextWidth)
                            .testTag("tv-recovery-status"),
                    )
                }
                if (footnote != null) {
                    Text(
                        text = footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .widthIn(max = RecoveryTextWidth)
                            .testTag("tv-recovery-footnote"),
                    )
                }
                if (primaryVisible) {
                    Row(
                        modifier = Modifier
                            .padding(top = 48.dp)
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEachIndexed { index, action ->
                            val buttonModifier = Modifier
                                .focusRequester(buttonFocus[index])
                                .focusProperties {
                                    left = if (index > 0) buttonFocus[index - 1] else leadingExit
                                    right = if (index < buttons.lastIndex) buttonFocus[index + 1] else FocusRequester.Cancel
                                    up = upExit
                                    down = FocusRequester.Cancel
                                }
                                .testTag(RecoveryActionTags[index])
                            if (index == 0) {
                                Button(onClick = action.onClick, enabled = primaryActionEnabled, modifier = buttonModifier) {
                                    Text(action.label)
                                }
                            } else {
                                // The kit's default fill: only the focused action stands out.
                                Button(onClick = action.onClick, modifier = buttonModifier) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

const val MaxRecoveryActions = 3

private val RecoveryIconSize = 48.dp
private val RecoveryTextWidth = 480.dp
private const val RecoveryBodyAlpha = 0.8f

private val RecoveryActionTags = listOf("tv-recovery-primary", "tv-recovery-secondary", "tv-recovery-tertiary")

private val HeldKeys = setOf(
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
)
