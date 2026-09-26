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
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.leaving

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
) {
    val primaryFocus = remember { FocusRequester() }
    val secondaryFocus = remember { FocusRequester() }
    val primaryVisible = primaryActionLabel != null && onPrimaryAction != null
    val secondaryVisible = secondaryActionLabel != null && onSecondaryAction != null
    val holderFocus = remember { FocusRequester() }
    var holding by remember { mutableStateOf(false) }
    var holderFocused by remember { mutableStateOf(false) }
    LaunchedEffect(visible, primaryActionLabel, primaryVisible) {
        if (visible && primaryVisible) {
            // Take focus from anything behind the overlay at once, then wait one frame
            // so the action is ready to draw its focused state.
            holding = true
            try {
                runCatching { holderFocus.requestFocus() }
                withFrameNanos { }
                runCatching { primaryFocus.requestFocus() }
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
                    .background(if (opaque) MaterialTheme.colorScheme.background else Color.Black.copy(alpha = 0.86f))
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
                        liveRegion = liveRegionMode
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
                        .semantics { heading() },
                )
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
                if (primaryVisible) {
                    Row(
                        modifier = Modifier
                            .padding(top = 32.dp)
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = requireNotNull(onPrimaryAction),
                            enabled = primaryActionEnabled,
                            modifier = Modifier
                                .focusRequester(primaryFocus)
                                .focusProperties {
                                    left = FocusRequester.Cancel
                                    right = if (secondaryVisible) {
                                        secondaryFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
                                    up = FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                }
                                .testTag("tv-recovery-primary"),
                        ) {
                            Text(requireNotNull(primaryActionLabel))
                        }
                        if (secondaryVisible) {
                            OutlinedButton(
                                onClick = requireNotNull(onSecondaryAction),
                                modifier = Modifier
                                    .focusRequester(secondaryFocus)
                                    .focusProperties {
                                        left = primaryFocus
                                        right = FocusRequester.Cancel
                                        up = FocusRequester.Cancel
                                        down = FocusRequester.Cancel
                                    }
                                    .testTag("tv-recovery-secondary"),
                            ) {
                                Text(requireNotNull(secondaryActionLabel))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HeldKeys = setOf(
    Key.DirectionCenter,
    Key.Enter,
    Key.NumPadEnter,
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
)
