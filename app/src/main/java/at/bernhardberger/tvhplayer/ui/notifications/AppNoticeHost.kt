package at.bernhardberger.tvhplayer.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.notices.NoticeSeverity
import at.bernhardberger.tvhplayer.R
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
internal fun AppShellNoticeHost(
    queue: NoticeCenter = koinInject(),
    profileOwner: AppProfileOwner = koinInject(),
    session: TvheadendSession = koinInject(),
) {
    val generation by profileOwner.configurationGeneration.collectAsStateWithLifecycle()
    val observation by session.observation.collectAsStateWithLifecycle()
    AppNoticeHost(queue, NoticeContext(generation, observation.currentSession?.generationIdentity))
}

/** One persistent shell consumer. Navigation never controls delivery or restarts a display budget. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppNoticeHost(queue: NoticeCenter, context: NoticeContext) {
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
        val displayMillis = when (next.notice.severity) { NoticeSeverity.INFO -> 4_000L; NoticeSeverity.FAILURE -> 6_000L }
        val timeout = accessibility?.calculateRecommendedTimeoutMillis(displayMillis,
            containsIcons = true, containsText = true, containsControls = false) ?: displayMillis
        queue.show(next.id, timeout)
    }
    if (allowed) state.active?.takeIf {
        it.notice.context == context && queue.remaining(it.expiresAt) > 0
    }?.let {
        val formatted = NoticeFormatter(LocalContext.current).format(it.notice.notice)
        AppNoticePresentation(formatted.headline, detail = formatted.detail, icon = formatted.icon)
    }
}

/** Plain kit variant, bottom center. No focus node, click semantics, or key handler. */
@Composable
internal fun AppNoticePresentation(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: AppNoticeIcon? = null,
) {
    val largeText = LocalDensity.current.fontScale > 1f
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
    val direction = LocalLayoutDirection.current
    Box(modifier.fillMaxSize().padding(start = TvFullScreenPadding.calculateStartPadding(direction),
        end = TvFullScreenPadding.calculateEndPadding(direction), bottom = TvGridVerticalMargin),
        contentAlignment = Alignment.BottomCenter) {
        Surface(modifier = Modifier
            .widthIn(max = 556.dp)
            .heightIn(min = 44.dp)
            .testTag("app-notice")
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = listOfNotNull(message, detail).joinToString(". ")
            },
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface)) {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Box(Modifier.size(32.dp)
                    .background(LocalContentColor.current.copy(alpha = .12f), CircleShape)
                    .testTag("app-notice-icon"), contentAlignment = Alignment.Center) {
                    Icon(painterResource(when (icon) {
                        AppNoticeIcon.SCHEDULE -> R.drawable.ic_schedule
                        AppNoticeIcon.RECORDING -> R.drawable.ic_fiber_manual_record
                        AppNoticeIcon.CHECK -> R.drawable.ic_check
                        AppNoticeIcon.STOP -> R.drawable.ic_stop
                        AppNoticeIcon.WARNING -> R.drawable.ic_error_outlined
                        AppNoticeIcon.CANCEL -> R.drawable.ic_close
                        AppNoticeIcon.DELETE -> R.drawable.ic_delete_outlined
                    }), contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (icon == AppNoticeIcon.RECORDING) TvRecordingColor else LocalContentColor.current)
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(message, style = labelStyle,
                        maxLines = if (detail.isNullOrBlank()) 2 else 1, overflow = TextOverflow.Ellipsis)
                    if (!detail.isNullOrBlank()) Text(detail, style = labelStyle,
                        color = LocalContentColor.current.copy(alpha = .76f),
                        maxLines = if (largeText) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
