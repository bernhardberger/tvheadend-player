package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first

/**
 * A backdrop that follows the focused item [target] reads, with the browse screens' backdrop
 * timing ([BrowseMotionPolicy.backdropSettleMs]) and fade. Reading the target here keeps focus
 * steps from recomposing the caller.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun <T> FocusBackdrop(
    target: () -> T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val currentTarget by rememberUpdatedState(target)
    var shown by remember { mutableStateOf(target()) }
    LaunchedEffect(Unit) {
        val targets = snapshotFlow { currentTarget() }.debounce(BrowseMotionPolicy.backdropSettleMs)
        while (true) {
            shown = targets.first { it != shown }
            // The next change also waits for focus to settle, which completes the interval.
            delay(BrowseMotionPolicy.backdropIntervalMs - BrowseMotionPolicy.backdropSettleMs)
        }
    }
    Crossfade(shown, modifier, BrowseMotionPolicy.backdropFade(), label = "focusBackdrop", content = content)
}
