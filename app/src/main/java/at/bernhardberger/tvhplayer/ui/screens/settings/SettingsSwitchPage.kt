package at.bernhardberger.tvhplayer.ui.screens.settings

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.components.GuidedStepCenteredLayout
import at.bernhardberger.tvhplayer.ui.components.GuidedStepPill
import at.bernhardberger.tvhplayer.ui.components.ServerAvatar
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarHero
import at.bernhardberger.tvhplayer.ui.components.ServerAvatarStatus
import at.bernhardberger.tvhplayer.ui.components.arrivalFocus
import at.bernhardberger.tvhplayer.ui.components.serverAvatarLook

/** What the switch page says about a switch the user started: under way, or how it ended. */
internal sealed interface SwitchProgress {
    val target: ServerProfileSummary

    data class Connecting(override val target: ServerProfileSummary) : SwitchProgress

    /** The owner's outcome: [Notice.ServerSwitched], or the connection or sign-in failure. */
    data class Finished(override val target: ServerProfileSummary, val notice: Notice) : SwitchProgress
}

/** What the buttons of the switch page do; each is the page's own, none reaches past Settings. */
internal class SwitchPageActions(
    val onCancel: () -> Unit,
    val onHome: () -> Unit,
    val onBackToSettings: () -> Unit,
    val onTryAgain: () -> Unit,
    val onEdit: () -> Unit,
)

private const val REVEAL_MS = 600
private const val TITLE_FADE_END = 1f / 3f
private val SwitchAvatarSize = 120.dp
private val RevealHeldKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
)

/**
 * The page a switch runs on, in place of Settings: the target's avatar says where it stands, a ring
 * while connecting, a check once connected, an error badge when it failed, and the buttons are the
 * ways on. One of them holds focus throughout: Cancel while connecting (Back cancels too), then
 * Go to Home, or Try again or Edit connection for the failure that fits. Back leaves for Settings.
 * The success reveals over about [REVEAL_MS] ms, or at once with [motionEnabled] off; its buttons
 * appear, and take focus, only when it has settled.
 */
@Composable
internal fun SwitchPage(
    progress: SwitchProgress,
    inUse: ServerProfileSummary?,
    actions: SwitchPageActions,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    backEnabled: Boolean = true,
) {
    val name = serverProfileDisplayName(progress.target)
    val outcome = (progress as? SwitchProgress.Finished)?.notice
    val status = switchStatus(outcome)
    val connected = status == ServerAvatarStatus.CONNECTED
    val animated = connected && motionEnabled
    val rejected = outcome is Notice.ServerSignInRejected
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(animated) {
        if (animated) sweep.animateTo(1f, tween(REVEAL_MS, easing = LinearEasing))
    }
    val reveal = { if (!motionEnabled) 1f else sweep.value }
    val actionsShown by remember(animated) {
        derivedStateOf { !animated || sweep.value == 1f }
    }
    BackHandler(enabled = backEnabled) { if (outcome == null) actions.onCancel() else actions.onBackToSettings() }
    GuidedStepCenteredLayout(
        title = stringResource(switchPageTitle(status, rejected), name),
        body = switchPageBody(progress.target, name, outcome, connected, rejected),
        hero = {
            Box(if (!actionsShown) arrivalFocus().focusable().testTag("server-switch-reveal") else Modifier) {
                ServerAvatarHero(serverAvatarLook(progress.target), status, reveal)
            }
        },
        modifier = Modifier.testTag("server-switch-page").onPreviewKeyEvent { event ->
            isHeldConfirm(event) || (!actionsShown && event.key in RevealHeldKeys)
        },
        titleAlpha = { if (animated) (sweep.value / TITLE_FADE_END).coerceIn(0f, 1f) else 1f },
        detail = inUse?.takeIf { status == ServerAvatarStatus.FAILED }?.let { other -> { StillInUse(other) } },
        actions = when {
            outcome == null -> ({ SwitchPill(R.string.cancel, SWITCH_CANCEL, actions.onCancel, first = true) })
            !actionsShown -> null
            else -> ({ SwitchOutcomeButtons(connected, rejected, actions) })
        },
    )
}

private fun switchStatus(outcome: Notice?) = when (outcome) {
    null -> ServerAvatarStatus.CONNECTING
    is Notice.ServerSwitched -> ServerAvatarStatus.CONNECTED
    else -> ServerAvatarStatus.FAILED
}

/** A held OK that moves focus to the button the outcome brings must not press it as well. */
private fun isHeldConfirm(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 &&
        (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)

@StringRes
private fun switchPageTitle(status: ServerAvatarStatus, rejected: Boolean): Int = when {
    status == ServerAvatarStatus.CONNECTING -> R.string.onboarding_connecting_title
    status == ServerAvatarStatus.CONNECTED -> R.string.onboarding_done_title
    rejected -> R.string.error_103_title
    else -> R.string.error_101_title
}

/** The ways on once an attempt has ended; a failure puts first the fix it makes likelier. */
@Composable
private fun SwitchOutcomeButtons(connected: Boolean, rejected: Boolean, actions: SwitchPageActions) {
    if (connected) {
        SwitchPill(R.string.server_go_home, SWITCH_HOME, actions.onHome, first = true, primary = true)
    } else if (rejected) {
        SwitchPill(R.string.setup_connection, SWITCH_EDIT, actions.onEdit, first = true, primary = true)
        SwitchPill(R.string.connection_try_again, SWITCH_TRY_AGAIN, actions.onTryAgain)
    } else {
        SwitchPill(R.string.connection_try_again, SWITCH_TRY_AGAIN, actions.onTryAgain, first = true, primary = true)
        SwitchPill(R.string.setup_connection, SWITCH_EDIT, actions.onEdit)
    }
    SwitchPill(R.string.back_to_settings, SWITCH_BACK, actions.onBackToSettings)
}

@Composable
private fun switchPageBody(
    target: ServerProfileSummary,
    name: String,
    outcome: Notice?,
    connected: Boolean,
    rejected: Boolean,
): String = when {
    outcome == null -> target.username?.takeIf { target.authenticationMode == ServerProfileAuthenticationMode.PASSWORD }
        ?.let { stringResource(R.string.onboarding_connecting_body, it) }
        ?: stringResource(R.string.onboarding_connecting_body_anonymous)
    connected -> target.username?.takeIf { target.authenticationMode == ServerProfileAuthenticationMode.PASSWORD }
        ?.let { stringResource(R.string.onboarding_done_body, it) }
        ?: stringResource(R.string.onboarding_done_body_anonymous)
    rejected -> stringResource(R.string.server_sign_in_rejected, name)
    else -> stringResource(R.string.server_switch_unreachable_body, name)
}

/** The connection a failed attempt leaves in use, so the page says what is still working. */
@Composable
private fun StillInUse(other: ServerProfileSummary) {
    Row(
        Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerAvatar(serverAvatarLook(other), 24.dp)
        Text(
            stringResource(R.string.server_still_in_use, serverProfileDisplayName(other)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        )
    }
}

@Composable
private fun SwitchPill(
    @StringRes label: Int,
    tag: String,
    onClick: () -> Unit,
    first: Boolean = false,
    primary: Boolean = false,
) {
    GuidedStepPill(
        text = stringResource(label),
        onClick = onClick,
        modifier = (if (first) arrivalFocus() else Modifier).testTag(tag),
        primary = primary,
    )
}
