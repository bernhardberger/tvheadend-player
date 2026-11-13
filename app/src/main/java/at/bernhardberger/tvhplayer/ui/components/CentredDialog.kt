package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.TvTextColors

/** Safe choice first, in the same horizontal pill row as the resume prompt. */
@Composable
internal fun CentredDialog(
    title: String,
    message: String,
    firstLabel: String,
    secondLabel: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    onDismiss: () -> Unit,
    backEnabled: Boolean = true,
    firstTag: String = "two-column-first",
    secondTag: String = "two-column-second",
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    thirdLabel: String? = null,
    thirdTag: String = "two-column-third",
    onThird: () -> Unit = {},
    secondaryText: String? = null,
    onBack: (() -> Unit)? = null,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false).apply { targetState = true } },
) {
    val firstFocus = remember { FocusRequester() }
    val secondFocus = remember { FocusRequester() }
    val thirdFocus = remember { FocusRequester() }
    var chosen by remember { mutableStateOf<Int?>(null) }
    CentredDialog(title, message, onDismiss = {
        when (chosen) { 0 -> onFirst(); 1 -> onSecond(); else -> onDismiss() }
    }, backEnabled = backEnabled, visibility = visibility, secondaryText = secondaryText,
        onBack = onBack,
        modifier = Modifier.onPreviewKeyEvent(onPreviewKeyEvent)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }; firstFocus.requestFocus()
        }
        Button(onClick = { chosen = 0; visibility.targetState = false },
            modifier = Modifier.height(40.dp).focusRequester(firstFocus).focusProperties {
                left = FocusRequester.Cancel; right = secondFocus
            }.testTag(firstTag)) { Text(firstLabel) }
        OutlinedButton(onClick = { chosen = 1; visibility.targetState = false },
            modifier = Modifier.height(40.dp).focusRequester(secondFocus).focusProperties {
                left = firstFocus; right = if (thirdLabel != null) thirdFocus else FocusRequester.Cancel
            }.testTag(secondTag)) { Text(secondLabel) }
        if (thirdLabel != null) OutlinedButton(onClick = onThird,
            modifier = Modifier.height(40.dp).focusRequester(thirdFocus).focusProperties {
                left = secondFocus; right = FocusRequester.Cancel
            }.testTag(thirdTag)) { Text(thirdLabel) }
    }
}

/** A modal decision within the current flow, above the entire browse shell. */
@Composable
internal fun CentredDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    secondaryText: String? = null,
    eyebrow: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    visibility: MutableTransitionState<Boolean> = remember { MutableTransitionState(false).apply { targetState = true } },
    actions: @Composable RowScope.() -> Unit,
) {
    CentredDialogShell(paneTitle = title, onDismiss = onDismiss, modifier = modifier,
        backEnabled = backEnabled, onBack = onBack, visibility = visibility) {
        Layout(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(TvSurfaceColors.container)
            .testTag("centred-dialog-card").padding(24.dp),
            content = {
            Column(Modifier.fillMaxWidth().testTag("centred-dialog-content")) {
                Column(Modifier.fillMaxWidth().testTag("centred-dialog-text"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        eyebrow?.let {
                            it()
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!secondaryText.isNullOrBlank()) Text(secondaryText, style = MaterialTheme.typography.titleMedium,
                            color = TvTextColors.primary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge,
                        color = TvTextColors.secondary)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = actions)
        }) { measurables, constraints ->
            // Only actions determine the width; copy is measured afterwards so it wraps.
            // The FlowRow's maximum intrinsic width is its natural, unwrapped row.
            val padding = 48.dp.roundToPx()
            val width = constraints.constrainWidth(
                measurables[1].maxIntrinsicWidth(Constraints.Infinity).coerceIn(
                    528.dp.roundToPx() - padding,
                    (960.dp - TvGridHorizontalMargin * 2).roundToPx() - padding,
                ),
            )
            val childConstraints = constraints.copy(minWidth = width, maxWidth = width, minHeight = 0)
            val actionsPlaceable = measurables[1].measure(childConstraints)
            val textPlaceable = measurables[0].measure(childConstraints)
            val gap = 24.dp.roundToPx()
            layout(width, constraints.constrainHeight(textPlaceable.height + gap + actionsPlaceable.height)) {
                textPlaceable.placeRelative(0, 0)
                actionsPlaceable.placeRelative(0, textPlaceable.height + gap)
            }
        }
    }
}
