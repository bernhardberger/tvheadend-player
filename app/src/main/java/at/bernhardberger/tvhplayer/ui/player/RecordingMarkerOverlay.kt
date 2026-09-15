package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration

/** Shared with the screen's ancestor Back interceptor; survives focus relocation. */
internal class RecordingMarkerNavigation {
    var selectedMs by mutableStateOf<Long?>(null)
        private set
    val open: Boolean get() = selectedMs != null
    var restoration by mutableIntStateOf(0)
        private set
    var ownerRevision: Long = 0L
        private set
    private val heldKeys = mutableSetOf<Key>()

    fun show(markers: List<Long>, positionMs: Long, openingKey: Key? = null, revision: Long = 0L) {
        selectedMs = markers.firstOrNull { it > positionMs } ?: markers.lastOrNull() ?: return
        ownerRevision = revision
        openingKey?.let(heldKeys::add)
    }

    fun dismiss() {
        if (!open) return
        selectedMs = null
        restoration++
    }

    fun move(markers: List<Long>, direction: Int) {
        val index = markers.indexOf(selectedMs)
        if (index >= 0) selectedMs = markers[(index + direction).coerceIn(0, markers.lastIndex)]
    }

    fun handle(event: KeyEvent, markers: List<Long>, onSeek: (Long) -> Unit): Boolean {
        if (event.key in heldKeys) {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                // A fresh press after a lost release (for example HOME) starts a new cycle.
                heldKeys.remove(event.key)
            } else {
                if (event.type == KeyEventType.KeyUp) heldKeys.remove(event.key)
                return true
            }
        }
        if (!open) return false
        if (event.key !in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                Key.DirectionRight, Key.Back, Key.Enter, Key.NumPadEnter, Key.DirectionCenter)) return false
        if (event.type == KeyEventType.KeyDown) {
            heldKeys.add(event.key)
            if (event.nativeKeyEvent.repeatCount == 0) {
                when (event.key) {
                    Key.DirectionLeft -> move(markers, -1)
                    Key.DirectionRight -> move(markers, 1)
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        selectedMs?.takeIf { it in markers }?.let(onSeek)
                        dismiss()
                    }
                    Key.DirectionDown, Key.Back -> dismiss()
                    else -> Unit
                }
            }
        }
        return true
    }
}

@Composable
internal fun RecordingMarkerOverlay(
    navigation: RecordingMarkerNavigation,
    markers: List<Long>,
    onSeek: (Long) -> Unit,
    displayDurationMs: Long,
    modifier: Modifier = Modifier,
) {
    val selected = navigation.selectedMs ?: return
    val index = markers.indexOf(selected)
    if (index < 0) return
    val focus = remember { FocusRequester() }
    val previousLabel = stringResource(R.string.recording_marker_previous)
    val nextLabel = stringResource(R.string.recording_marker_next)
    val closeLabel = stringResource(R.string.close)
    LaunchedEffect(focus) { focus.requestFocus() }
    val description = if (selected == 0L) stringResource(R.string.recording_marker_start)
        else stringResource(R.string.recording_marker_label,
            index + if (markers.firstOrNull() == 0L) 0 else 1, formatPlaybackDuration(selected))
    Layout(modifier = modifier, content = {
        Button(
            onClick = { onSeek(selected); navigation.dismiss() },
            modifier = Modifier.focusRequester(focus).testTag("recording-marker-target")
                 .semantics {
                    contentDescription = description
                    customActions = buildList {
                        if (index > 0) add(CustomAccessibilityAction(previousLabel) { navigation.move(markers, -1); true })
                        if (index < markers.lastIndex) add(CustomAccessibilityAction(nextLabel) { navigation.move(markers, 1); true })
                        add(CustomAccessibilityAction(closeLabel) { navigation.dismiss(); true })
                    }
                }
                .focusProperties {
                    up = FocusRequester.Cancel; down = FocusRequester.Cancel
                    left = FocusRequester.Cancel; right = FocusRequester.Cancel
                },
        ) {
            Text(
                formatPlaybackDuration(selected),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("recording-marker-overlay"),
            )
        }
    }) { measurables, constraints ->
        val label = measurables.single().measure(constraints.copy(
            minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity,
        ))
        val fraction = if (displayDurationMs > 0) (selected.toDouble() / displayDurationMs).coerceIn(0.0, 1.0) else 0.0
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (constraints.maxWidth * fraction - label.width / 2.0).roundToInt()
                .coerceIn(0, constraints.maxWidth - label.width)
            label.placeRelative(x, -label.height - 4.dp.roundToPx())
        }
    }
}
