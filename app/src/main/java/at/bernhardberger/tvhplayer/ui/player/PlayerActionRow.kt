package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap

/** Material disabled content alpha, used for a pause control that stays focusable. */
internal const val PauseUnavailableIconAlpha = TvTextDisabledAlpha

private data class PlayerAction(
    val tag: String,
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: (() -> Unit)?,
    val focus: FocusRequester?,
)

@Composable
internal fun PlayerActionRow(
    pauseFocus: FocusRequester,
    settingsFocus: FocusRequester,
    /** Play/Pause is always in the row; the caller decides what it does when playback cannot pause. */
    onTogglePause: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    stopFocus: FocusRequester? = null,
    onActionFocused: (String) -> Unit = {},
    /** Dims the pause control, which stays focusable and clickable, and announces why it cannot pause. */
    pauseUnavailableReason: String? = null,
) {
    val playPause = stringResource(if (paused) R.string.play else R.string.pause)
    val settings = stringResource(R.string.nav_settings)
    val stop = stringResource(R.string.stop_playback)
    val actions = listOf(
        PlayerAction("player-pause", playPause,
            if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause, onTogglePause, pauseFocus),
        PlayerAction("player-stop", stop, R.drawable.ic_stop, onStop, stopFocus),
        PlayerAction("player-settings", settings, R.drawable.ic_settings, onSettings, settingsFocus),
    )
    // Play/Pause and Stop stand at the start; Settings at the end.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvIconButtonSize),
        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (tag, label, icon, action, focus) ->
            key(tag) {
                if (tag == "player-settings") Spacer(Modifier.weight(1f))
                if (action != null) {
                    val actionModifier = Modifier
                        .testTag(tag)
                        .then(focus?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .onFocusChanged { if (it.isFocused) { onActionFocused(tag); onInteraction() } }
                        .then(if (tag == "player-pause" && pauseUnavailableReason != null) {
                            Modifier.semantics {
                                stateDescription = pauseUnavailableReason
                            }
                        } else Modifier)
                    IconButton(
                        onClick = { onInteraction(); action() },
                        colors = IconButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tag == "player-pause") 1f else 0.88f),
                        ),
                        modifier = actionModifier.size(TvIconButtonSize),
                    ) {
                        val glyphModifier = if (tag == "player-pause" && pauseUnavailableReason != null) {
                            Modifier.graphicsLayer { alpha = PauseUnavailableIconAlpha }
                        } else Modifier
                        Icon(painterResource(icon), contentDescription = label, modifier = glyphModifier.size(IconButtonDefaults.MediumIconSize))
                    }
                }
            }
        }
    }
}
