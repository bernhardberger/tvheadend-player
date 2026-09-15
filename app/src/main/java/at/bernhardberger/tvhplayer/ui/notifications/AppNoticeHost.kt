package at.bernhardberger.tvhplayer.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
internal fun AppShellNoticeHost(
    queue: AppNoticeQueue = koinInject(),
    profileOwner: AppProfileOwner = koinInject(),
    session: TvheadendSession = koinInject(),
) {
    val generation by profileOwner.configurationGeneration.collectAsStateWithLifecycle()
    val observation by session.observation.collectAsStateWithLifecycle()
    AppNoticeHost(queue, AppNoticeContext(generation, observation.currentSession))
}

/** One persistent shell consumer. Navigation never controls delivery or restarts a display budget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppNoticeHost(queue: AppNoticeQueue, context: Any) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val allowed = lifecycle == Lifecycle.State.RESUMED &&
        LocalWindowInfo.current.isWindowFocused && !WindowInsets.isImeVisible
    val accessibility = LocalAccessibilityManager.current
    val state by queue.state.collectAsStateWithLifecycle()
    // Android delay timing can pause during deep sleep while elapsedRealtime advances.
    // Reconcile on window/lifecycle/IME changes without granting a new display budget.
    LaunchedEffect(queue, context, state.nextExpiry, allowed) {
        queue.prune()
        state.nextExpiry?.let {
            delay(queue.remaining(it))
            queue.prune()
        }
    }
    val next = state.pending.firstOrNull()
    LaunchedEffect(queue, next?.id, state.active?.notice?.id, allowed, context) {
        queue.prune()
        if (next == null || state.active != null || !allowed) return@LaunchedEffect
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(next.kind.displayMillis,
            containsIcons = false, containsText = true, containsControls = false) ?: next.kind.displayMillis
        queue.show(next.id, timeout)
    }
    if (allowed) state.active?.takeIf {
        it.notice.context == context && queue.remaining(it.expiresAt) > 0
    }?.let {
        AppNoticePresentation(stringResource(it.notice.message))
    }
}

/** Plain kit variant, bottom center. No focus node, click semantics, or key handler. */
@Composable
internal fun AppNoticePresentation(message: String) {
    Box(Modifier.fillMaxSize().padding(TvFullScreenPadding), contentAlignment = Alignment.BottomCenter) {
        Surface(modifier = Modifier.widthIn(max = 324.dp).heightIn(min = 44.dp).testTag("app-notice"),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface)) {
            Text(message, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
