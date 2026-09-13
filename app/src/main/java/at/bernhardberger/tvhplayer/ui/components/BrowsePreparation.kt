package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

internal data class PreparedBrowseData<I, T>(val input: I, val value: T, val requestKey: Any?)

// A single CPU lane leaves capacity for input, composition and ongoing playback.
private val preparationDispatcher = Dispatchers.Default.limitedParallelism(1)

/** Publish complete projections, then catch up to the newest queued input.
 * Metadata churn does not cancel every calculation and starve the first display.
 * Changing the authority cancels publication and immediately hides the old result.
 * Changing only requestKey cancels obsolete work but retains the last complete
 * display. Its requestKey lets consumers distinguish retained from current data.
 * An explicit requestKey must stay stable within a request and have fresh identity
 * for each replacement, including A→B→A, so retained data cannot appear current.
 */
@Composable
internal fun <I, T> rememberPreparedBrowseData(
    authority: Any?,
    input: I,
    requestKey: Any? = authority,
    prepare: suspend (I) -> T,
): PreparedBrowseData<I, T>? {
    val latestInput = rememberUpdatedState(input)
    val latestPrepare = rememberUpdatedState(prepare)
    var result by remember(authority) { mutableStateOf<PreparedBrowseData<I, T>?>(null) }
    LaunchedEffect(authority, requestKey) {
        snapshotFlow { latestInput.value }.conflate().collect { captured ->
            val calculate = latestPrepare.value
            val prepared = withContext(preparationDispatcher) { calculate(captured) }
            result = PreparedBrowseData(captured, prepared, requestKey)
        }
    }
    return result
}

/** A temporary, static focus anchor; the real screen takes over when ready. */
@Composable
internal fun BrowsePreparationPending(
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    ready: Boolean = false,
    onPendingFocus: () -> Unit = {},
    onReadyFocus: () -> Boolean = { false },
    handoffKey: Any? = Unit,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val latestReadyFocus = rememberUpdatedState(onReadyFocus)
    LaunchedEffect(initialFocusEnabled, ready) {
        if (initialFocusEnabled && !ready) runCatching { focus.requestFocus() }
    }
    LaunchedEffect(ready, focused, initialFocusEnabled, handoffKey) {
        if (ready && focused && initialFocusEnabled) {
            repeat(4) {
                withFrameNanos { }
                if (latestReadyFocus.value()) return@LaunchedEffect
            }
        }
    }
    // Keep a focused anchor attached until the real screen has taken focus.
    // Disposing it first lets the native drawer reclaim focus during the vacancy.
    if (ready && !focused) return
    Box(Modifier.fillMaxSize().padding(contentPadding).zIndex(1f), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.loading),
            modifier = Modifier
                .testTag("browse-preparing")
                .focusRequester(focus)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused && !ready) onPendingFocus()
                }
                .border(if (focused) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface)
                .padding(16.dp)
                .focusable(),
        )
    }
}
