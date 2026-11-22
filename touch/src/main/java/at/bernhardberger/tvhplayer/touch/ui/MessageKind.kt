package at.bernhardberger.tvhplayer.touch.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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

/*
 * Section 12's message form in place of a list that has nothing to show, as Channels drew it first;
 * Channels, Recordings and Guide share it.
 */

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

/**
 * A list's stand-in message: icon, title, the reason with its hint, and the kind's buttons.
 * [onSettings] opens connection settings, or the sign-in editor for [MessageKind.SignInNeeded];
 * [onShowAll] is the empty tag's "All channels".
 */
@Composable
internal fun ListMessage(
    kind: MessageKind,
    onSettings: (() -> Unit)?,
    onRetry: () -> Unit = {},
    horizontalMargin: Dp = LocalBrowseMargin.current,
    onShowAll: () -> Unit = {},
) {
    val reason = stringResource(kind.reason)
    val hint = kind.hint?.let { stringResource(it) }
    val hasButtons = onSettings != null || kind == MessageKind.Unreachable || kind == MessageKind.EmptyTag
    MessageLayout(
        kind.icon, stringResource(kind.title), if (hint == null) reason else "$reason $hint", horizontalMargin,
        buttons = if (hasButtons) ({ MessageButtons(kind, onSettings, onRetry, onShowAll) }) else null,
    )
}

/** A catalog failure in the same form, with the catalog's words and its actions in order. */
@Composable
internal fun ListFailure(error: AppError, onRetry: () -> Unit, onSettings: (() -> Unit)?) {
    val condition = error.condition
    val text = AppErrorText(LocalContext.current)
    val hint = condition.touchHintRes()?.let { stringResource(it) }
    val icon = when (condition) {
        AppErrorCondition.AUTHENTICATION_REJECTED, AppErrorCondition.PERMISSION_DENIED,
        AppErrorCondition.CREDENTIAL_UNAVAILABLE,
        -> R.drawable.channels_lock
        else -> R.drawable.channels_cloud_off
    }
    val actions = error.actions.filter {
        it == AppErrorAction.RETRY || it != AppErrorAction.CLOSE && onSettings != null
    }
    val buttons: (@Composable FlowRowScope.() -> Unit)? = if (actions.isEmpty() && onSettings == null) null else ({
        actions.forEachIndexed { index, action ->
            val onClick = if (action == AppErrorAction.RETRY) onRetry else onSettings
            onClick ?: return@forEachIndexed
            if (index == 0) {
                Button(onClick = onClick) { Text(text.label(action)) }
            } else {
                OutlinedButton(onClick = onClick) { Text(text.label(action)) }
            }
        }
    })
    MessageLayout(
        icon, stringResource(condition.touchTitleRes()),
        listOfNotNull(text.reason(condition), hint).joinToString(" "), LocalBrowseMargin.current, buttons,
    )
}

@Composable
private fun MessageLayout(
    @DrawableRes icon: Int,
    title: String,
    paragraph: String,
    horizontalMargin: Dp,
    buttons: (@Composable FlowRowScope.() -> Unit)?,
) {
    Box(
        Modifier.fillMaxSize().padding(horizontal = horizontalMargin, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
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
            if (buttons != null) {
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    content = buttons,
                )
            }
        }
    }
}

@Composable
private fun FlowRowScope.MessageButtons(
    kind: MessageKind,
    onSettings: (() -> Unit)?,
    onRetry: () -> Unit,
    onShowAll: () -> Unit,
) {
    val settings = stringResource(R.string.channels_connection_settings)
    when (kind) {
        MessageKind.Unreachable -> {
            Button(onClick = onRetry) { Text(stringResource(R.string.channels_retry)) }
            if (onSettings != null) OutlinedButton(onClick = onSettings) { Text(settings) }
        }
        MessageKind.SignInFailed, MessageKind.SignInNeeded -> {
            if (onSettings != null) Button(onClick = onSettings) { Text(settings) }
        }
        MessageKind.Empty -> Unit
        MessageKind.EmptyTag -> Button(onClick = onShowAll) { Text(stringResource(R.string.channels_all)) }
    }
}
