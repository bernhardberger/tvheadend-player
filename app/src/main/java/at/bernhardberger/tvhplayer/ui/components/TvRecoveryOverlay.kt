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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvOverlayBoxAlpha
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.leaving

/** One button of [TvRecoveryOverlay]'s action row. */
data class TvRecoveryAction(val label: String, val onClick: () -> Unit)

/**
 * [actions], when given, replace the primary and secondary action: up to [MaxRecoveryActions]
 * buttons in order, the first filled and focused when they appear or change. [errorNumber] is a
 * small line under the hint. [status] is a line under [message] that may change while shown, such
 * as a countdown; the live region is [message] alone, so [status] changes outside it.
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
    errorNumber: String? = null,
    actions: List<TvRecoveryAction> = emptyList(),
    status: String? = null,
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
    val buttonFocus = remember { List(MaxRecoveryActions) { FocusRequester() } }
    val primaryVisible = buttons.isNotEmpty()
    val labels = buttons.map { it.label }
    val holderFocus = remember { FocusRequester() }
    var holding by remember { mutableStateOf(false) }
    var holderFocused by remember { mutableStateOf(false) }
    LaunchedEffect(visible, labels) {
        if (visible && primaryVisible) {
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
                if (!primaryVisible) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = if (primaryVisible) 0.dp else 24.dp)
                        .widthIn(max = 680.dp)
                        .semantics {
                            heading()
                            liveRegion = liveRegionMode
                        }
                        .testTag("tv-recovery-title"),
                )
                if (status != null) {
                    Text(
                        text = status,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .widthIn(max = 680.dp)
                            .testTag("tv-recovery-status"),
                    )
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .widthIn(max = 680.dp),
                    )
                }
                if (hint != null) {
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .widthIn(max = 680.dp),
                    )
                }
                if (errorNumber != null) {
                    Text(
                        text = errorNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("tv-recovery-number"),
                    )
                }
                if (primaryVisible) {
                    Row(
                        modifier = Modifier
                            .padding(top = 32.dp)
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEachIndexed { index, action ->
                            val buttonModifier = Modifier
                                .focusRequester(buttonFocus[index])
                                .focusProperties {
                                    left = if (index > 0) buttonFocus[index - 1] else FocusRequester.Cancel
                                    right = if (index < buttons.lastIndex) buttonFocus[index + 1] else FocusRequester.Cancel
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                }
                                .testTag(RecoveryActionTags[index])
                            if (index == 0) {
                                Button(onClick = action.onClick, enabled = primaryActionEnabled, modifier = buttonModifier) {
                                    Text(action.label)
                                }
                            } else {
                                OutlinedButton(onClick = action.onClick, modifier = buttonModifier) {
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
