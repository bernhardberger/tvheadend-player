package at.bernhardberger.tvhplayer.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap

private data class PlayerAction(
    val tag: String,
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: (() -> Unit)?,
    val focus: FocusRequester?,
)

@Composable
internal fun PlayerActionRow(
    infoFocus: FocusRequester,
    settingsFocus: FocusRequester,
    onInfo: () -> Unit,
    onSettings: () -> Unit,
    onStop: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onRecord: (() -> Unit)? = null,
    recordFocus: FocusRequester? = null,
    /** Toggles playback; null omits the control when pausing is not possible. */
    onTogglePause: (() -> Unit)? = null,
    paused: Boolean = false,
    pauseFocus: FocusRequester? = null,
    goLiveFocus: FocusRequester? = null,
    onActionFocused: (String) -> Unit = {},
) {
    val playPause = stringResource(if (paused) R.string.play else R.string.pause)
    val info = stringResource(R.string.player_info)
    val settings = stringResource(R.string.nav_settings)
    val record = stringResource(R.string.record)
    val stop = stringResource(R.string.stop_playback)
    val actions = listOf(
        PlayerAction("player-pause", playPause,
            if (paused) R.drawable.ic_play_arrow else R.drawable.ic_pause, onTogglePause, pauseFocus),
        PlayerAction("player-stop", stop, R.drawable.ic_stop, onStop, null),
        PlayerAction("player-info", info, R.drawable.ic_info, onInfo, infoFocus),
        PlayerAction("player-record", record, R.drawable.ic_fiber_manual_record, onRecord, recordFocus),
        PlayerAction("player-settings", settings, R.drawable.ic_settings, onSettings, settingsFocus),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TvOverlayActionButtonSize),
        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { (tag, label, icon, action, focus) ->
            key(tag) {
                if (tag == "player-info") Spacer(Modifier.weight(1f))
                if (action != null) {
                    val actionModifier = Modifier
                        .testTag(tag)
                        .then(focus?.let { Modifier.focusRequester(it) } ?: Modifier)
                        .then(if (tag == "player-settings" && goLiveFocus != null) {
                            Modifier.focusProperties { right = goLiveFocus }
                        } else Modifier)
                        .onFocusChanged { if (it.isFocused) { onActionFocused(tag); onInteraction() } }
                    if (tag == "player-info") {
                        Button(
                            onClick = { onInteraction(); action() },
                            modifier = actionModifier.height(TvOverlayActionButtonSize),
                            colors = ButtonDefaults.colors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp).testTag("player-info-icon"))
                                Spacer(Modifier.width(8.dp))
                                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { onInteraction(); action() },
                            colors = IconButtonDefaults.colors(
                                containerColor = if (tag == "player-pause") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tag == "player-pause") 1f else 0.88f),
                            ),
                            modifier = actionModifier.size(TvOverlayActionButtonSize),
                        ) { Icon(painterResource(icon), contentDescription = label) }
                    }
                }
            }
        }
    }
}
