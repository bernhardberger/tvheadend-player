package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.notices.NoticeSeverity
import at.bernhardberger.tvhplayer.notices.NoticeState
import at.bernhardberger.tvhplayer.notices.PresentedNotice
import at.bernhardberger.tvhplayer.notices.QueuedNotice
import at.bernhardberger.tvhplayer.notices.matches
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import kotlinx.coroutines.delay

/**
 * What [TouchFrame] draws in its snackbar place, at the bottom of its content area: beside the
 * rail, above the navigation bar. Nothing unless the app provides its notice host.
 */
internal val LocalFrameNotices = compositionLocalOf<@Composable (Modifier) -> Unit> { {} }

/** The active page inside [content] shows notices from [queue], for the connection in use. */
@Composable
internal fun FrameNotices(
    queue: NoticeCenter,
    owner: AppProfileOwner,
    session: TvheadendSession,
    content: @Composable () -> Unit,
) {
    val generation by owner.configurationGeneration.collectAsStateWithLifecycle()
    val observation by session.observation.collectAsStateWithLifecycle()
    val context = NoticeContext(generation, observation.currentSession?.generationIdentity)
    val host = remember(queue, context) {
        @Composable { modifier: Modifier -> TouchNoticeHost(queue, context, modifier) }
    }
    CompositionLocalProvider(LocalFrameNotices provides host, content)
}

/** The frame's notice host on a page with no Scaffold snackbar slot. Does not size the page. */
@Composable
internal fun BoxScope.BottomWindowNotices() {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    Box(
        Modifier.matchParentSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        contentAlignment = if (wide) Alignment.BottomStart else Alignment.BottomCenter,
    ) {
        LocalFrameNotices.current(if (wide) Modifier else Modifier.fillMaxWidth())
    }
}

/**
 * The one consumer of [queue] in a window. It shows the next notice only while the window can be
 * read (resumed, focused, no keyboard); the queue owns the time each one stays, so a notice hidden
 * meanwhile gets no fresh time when it returns. The player's window calls this with its own place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TouchNoticeHost(queue: NoticeCenter, context: NoticeContext, modifier: Modifier = Modifier) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val allowed = noticesReadable(lifecycle, LocalWindowInfo.current.isWindowFocused, WindowInsets.isImeVisible)
    val accessibility = LocalAccessibilityManager.current
    val state by queue.state.collectAsState()
    // Delays can stop in deep sleep while the queue's clock goes on: check again on every change.
    LaunchedEffect(queue, context, state.nextExpiry, allowed) {
        queue.prune()
        state.nextExpiry?.let {
            delay(queue.remaining(it))
            queue.prune()
        }
    }
    val next = noticeToShow(state, allowed)
    LaunchedEffect(queue, next?.id, context) {
        if (next == null) return@LaunchedEffect
        val millis = noticeDisplayMillis(next.notice.severity)
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
            millis, containsIcons = false, containsText = true, containsControls = false,
        ) ?: millis
        queue.show(next.id, timeout)
    }
    visibleNotice(state, allowed, context, queue::remaining)?.let {
        val formatter = NoticeFormatter(LocalContext.current)
        val formatted = remember(it.notice.id) { formatter.format(it.notice.notice) }
        TouchNotice(formatted.headline, formatted.detail, modifier)
    }
}

/** A notice as Material's snackbar: text only, a failure's reason on a second line, nothing to press. */
@Composable
internal fun TouchNotice(headline: String, detail: String?, modifier: Modifier = Modifier) {
    Snackbar(
        modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        // A lone Text keeps its baselines, which the snackbar needs to lay out one line at its height.
        if (detail == null) {
            Text(headline)
        } else {
            Column {
                Text(headline)
                Text(detail)
            }
        }
    }
}

internal fun noticesReadable(lifecycle: Lifecycle.State, windowFocused: Boolean, keyboardShown: Boolean): Boolean =
    lifecycle == Lifecycle.State.RESUMED && windowFocused && !keyboardShown

internal fun noticeDisplayMillis(severity: NoticeSeverity): Long = when (severity) {
    NoticeSeverity.INFO -> INFO_MILLIS
    NoticeSeverity.FAILURE -> FAILURE_MILLIS
}

/** The notice to start now: the first waiting one, once nothing is shown and the window can be read. */
internal fun noticeToShow(state: NoticeState, readable: Boolean): QueuedNotice? =
    state.pending.firstOrNull()?.takeIf { readable && state.active == null }

/** The shown notice while it belongs to the connection in use, has time left and the window can be read. */
internal fun visibleNotice(
    state: NoticeState,
    readable: Boolean,
    context: NoticeContext,
    remaining: (Long) -> Long,
): PresentedNotice? = state.active?.takeIf { readable && it.notice.matches(context) && remaining(it.expiresAt) > 0 }

private const val INFO_MILLIS = 4_000L
private const val FAILURE_MILLIS = 6_000L
