package at.bernhardberger.tvhplayer.touch.channels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.connect.touchHintRes
import at.bernhardberger.tvhplayer.touch.connect.touchTitleRes
import at.bernhardberger.tvhplayer.ui.common.AppErrorText

internal enum class MessageKind(
    @param:DrawableRes val icon: Int,
    @param:StringRes val title: Int,
    @param:StringRes val reason: Int,
    /** Follows the reason in the same paragraph, as the catalog's alerts and messages join them. */
    @param:StringRes val hint: Int? = null,
) {
    Unreachable(R.drawable.channels_cloud_off, R.string.channels_unreachable, R.string.channels_unreachable_reason),
    SignInFailed(R.drawable.channels_lock, R.string.channels_sign_in_failed, R.string.channels_sign_in_reason),

    /** The saved password can't be read: the catalog's credential-unavailable message. */
    SignInNeeded(
        R.drawable.channels_lock,
        SharedR.string.error_106_title,
        SharedR.string.error_106_reason,
        SharedR.string.error_hint_enter_sign_in_again,
    ),
    Empty(R.drawable.play_tv_outline, R.string.channels_empty, R.string.channels_empty_reason),
    EmptyTag(R.drawable.channels_tag, R.string.channels_empty, R.string.channels_empty_tag),
}

@Composable
internal fun ChannelsMessage(kind: MessageKind, actions: ChannelsActions) {
    val reason = stringResource(kind.reason)
    val hint = kind.hint?.let { stringResource(it) }
    MessageLayout(kind.icon, stringResource(kind.title), if (hint == null) reason else "$reason $hint") {
        MessageActions(kind, actions)
    }
}

/** A catalog failure in the same form, with the catalog's words and its actions in order. */
@Composable
internal fun ChannelsFailure(error: AppError, actions: ChannelsActions) {
    val condition = error.condition
    val text = AppErrorText(LocalContext.current)
    val hint = condition.touchHintRes()?.let { stringResource(it) }
    val icon = when (condition) {
        AppErrorCondition.AUTHENTICATION_REJECTED, AppErrorCondition.PERMISSION_DENIED,
        AppErrorCondition.CREDENTIAL_UNAVAILABLE,
        -> R.drawable.channels_lock
        else -> R.drawable.channels_cloud_off
    }
    MessageLayout(
        icon, stringResource(condition.touchTitleRes()),
        listOfNotNull(text.reason(condition), hint).joinToString(" "),
    ) {
        error.actions.filter { it != AppErrorAction.CLOSE }.forEachIndexed { index, action ->
            val onClick = if (action == AppErrorAction.RETRY) actions.onRetry else actions.onOpenConnectionSettings
            if (index == 0) {
                Button(onClick = onClick) { Text(text.label(action)) }
            } else {
                OutlinedButton(onClick = onClick) { Text(text.label(action)) }
            }
        }
    }
}

@Composable
private fun MessageLayout(
    @DrawableRes icon: Int,
    title: String,
    paragraph: String,
    buttons: @Composable FlowRowScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 340.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Text(
                title, Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                paragraph, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                content = buttons,
            )
        }
    }
}

@Composable
private fun FlowRowScope.MessageActions(kind: MessageKind, actions: ChannelsActions) {
    val settings = stringResource(R.string.channels_connection_settings)
    when (kind) {
        MessageKind.Unreachable -> {
            Button(onClick = actions.onRetry) { Text(stringResource(R.string.channels_retry)) }
            OutlinedButton(onClick = actions.onOpenConnectionSettings) { Text(settings) }
        }
        MessageKind.SignInFailed -> Button(onClick = actions.onOpenConnectionSettings) { Text(settings) }
        MessageKind.SignInNeeded -> Button(onClick = actions.onSignIn ?: actions.onOpenConnectionSettings) {
            Text(settings)
        }
        MessageKind.Empty -> Unit
        MessageKind.EmptyTag -> Button(onClick = { actions.onTag(null) }) {
            Text(stringResource(R.string.channels_all))
        }
    }
}

@Composable
internal fun OfflineBanner(layout: ChannelsLayout, retryIn: String?, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.channels_cloud_off), null)
        if (layout.tablet) {
            FlowRow(
                Modifier.weight(1f).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.channels_offline), style = MaterialTheme.typography.bodySmall)
                val reconnect = if (retryIn == null) {
                    stringResource(R.string.channels_reconnecting)
                } else {
                    stringResource(R.string.channels_reconnect_in, retryIn)
                }
                Text(reconnect, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            TextButton(onClick = onSettings) { Text(stringResource(R.string.channels_connection_settings)) }
        } else {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(stringResource(R.string.channels_unreachable), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.channels_saved), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onSettings) {
                Icon(
                    painterResource(R.drawable.channels_settings),
                    stringResource(R.string.channels_connection_settings),
                )
            }
        }
    }
}

@Composable
internal fun ChannelsLoadingLine(layout: ChannelsLayout) {
    LinearProgressIndicator(
        Modifier.fillMaxWidth().padding(horizontal = if (layout.tablet) layout.margin else 0.dp),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

@Composable
internal fun ChannelsSkeleton(layout: ChannelsLayout) {
    Column(
        Modifier.fillMaxSize().clipToBounds().padding(horizontal = layout.margin),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        Text(
            stringResource(R.string.channels_loading),
            Modifier.padding(top = 12.dp, bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
        )
        val count = if (layout.tablet) TABLET_SKELETON_ROWS else PHONE_SKELETON_ROWS
        repeat(count) { index ->
            SkeletonRow(layout, index, count)
        }
    }
}

@Composable
private fun SkeletonRow(layout: ChannelsLayout, index: Int, count: Int) {
    // The same segment corners as the channel rows the skeleton stands in for.
    val shape = ListItemDefaults.segmentedShapes(index, count).shape
    Row(
        Modifier.fillMaxWidth().height(layout.rowHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(if (layout.tablet) 112.dp else 88.dp, if (layout.tablet) 56.dp else 48.dp)
        SkeletonLines(Modifier.weight(1f), index, supportingLine = !layout.tablet)
        if (layout.tablet) {
            SkeletonLines(Modifier.weight(2f), index, supportingLine = true)
            if (layout.showNext) SkeletonLines(Modifier.weight(2f), index + 1, supportingLine = true)
        }
    }
}

@Composable
private fun SkeletonLines(modifier: Modifier, index: Int, supportingLine: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(if (index % 2 == 0) 116.dp else 144.dp, 12.dp)
        if (supportingLine) SkeletonBlock(if (index % 2 == 0) 164.dp else 128.dp, 10.dp)
    }
}

@Composable
private fun SkeletonBlock(width: Dp, height: Dp) {
    Spacer(
        Modifier.width(width).height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)),
    )
}

private const val TABLET_SKELETON_ROWS = 10
private const val PHONE_SKELETON_ROWS = 8
