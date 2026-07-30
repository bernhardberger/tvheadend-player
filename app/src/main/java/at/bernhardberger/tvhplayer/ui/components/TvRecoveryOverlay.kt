package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
    LaunchedEffect(visible, primaryActionLabel, primaryVisible) {
        if (visible && primaryVisible) {
            runCatching { primaryFocus.requestFocus() }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (opaque) Color.Black else Color.Black.copy(alpha = 0.86f))
                .padding(48.dp)
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
                CircularProgressIndicator(color = Color.White)
            }
            Text(
                text = message,
                color = Color.White,
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
                    color = Color.White,
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
                    color = Color.White.copy(alpha = 0.72f),
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
