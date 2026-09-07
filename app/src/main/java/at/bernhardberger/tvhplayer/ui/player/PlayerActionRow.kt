package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
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
    val icon: ImageVector,
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
    atLive: Boolean? = null,
    onGoLive: () -> Unit = {},
    /** Toggles playback; null leaves the slot empty when pausing is not possible. */
    onTogglePause: (() -> Unit)? = null,
    paused: Boolean = false,
    pauseFocus: FocusRequester? = null,
) {
    var focusedTag by remember { mutableStateOf<String?>(null) }
    val playPause = stringResource(if (paused) R.string.play else R.string.pause)
    val info = stringResource(R.string.player_info)
    val settings = stringResource(R.string.nav_settings)
    val record = stringResource(R.string.record)
    val stop = stringResource(R.string.stop_playback)
    val goLive = stringResource(R.string.timeshift_go_live)
    val actions = listOf(
        PlayerAction("player-pause", playPause,
            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onTogglePause, pauseFocus),
        PlayerAction("player-stop", stop, Icons.Filled.Stop, onStop, null),
        PlayerAction("player-info", info, Icons.Filled.Info, onInfo, infoFocus),
        PlayerAction("player-record", record, Icons.Filled.FiberManualRecord, onRecord, recordFocus),
        PlayerAction("player-settings", settings, Icons.Filled.Settings, onSettings, settingsFocus),
    )
    val focusedLabel = if (focusedTag == "player-go-live") goLive else actions.firstOrNull { it.tag == focusedTag }?.label
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 24.dp)) {
            val index = actions.indexOfFirst { it.tag == focusedTag }.coerceAtLeast(0)
            val labelStart = if (focusedTag == "player-go-live") maxWidth - 144.dp else
                (TvOverlayActionButtonSize + TvOverlayActionGap) * index + if (index >= 2) 16.dp + TvOverlayActionGap else 0.dp
            Text(focusedLabel.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.width(180.dp)
                    .offset(x = labelStart.coerceIn(0.dp, (maxWidth - 180.dp).coerceAtLeast(0.dp)))
                    .testTag("player-action-context-label")
                    .semantics { hideFromAccessibility() })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { (tag, label, icon, action, focus) ->
                if (tag == "player-info") Spacer(Modifier.width(16.dp))
                if (action == null) {
                    Spacer(Modifier.size(TvOverlayActionButtonSize))
                } else {
                    IconButton(
                        onClick = { onInteraction(); action() },
                        colors = IconButtonDefaults.colors(
                            containerColor = if (tag == "player-pause") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tag == "player-pause") 1f else 0.78f),
                        ),
                        modifier = Modifier.size(TvOverlayActionButtonSize)
                            .testTag(tag)
                            .then(focus?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .onFocusChanged {
                                if (it.isFocused) { focusedTag = tag; onInteraction() }
                                else if (focusedTag == tag) focusedTag = null
                            },
                    ) { Icon(icon, contentDescription = label) }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(144.dp).height(TvOverlayActionButtonSize), contentAlignment = Alignment.CenterEnd) {
                when (atLive) {
                    true -> Text(stringResource(R.string.timeshift_live), color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge)
                    false -> Button(
                        onClick = { onInteraction(); onGoLive() },
                        modifier = Modifier.testTag("player-go-live").onFocusChanged {
                            if (it.isFocused) { focusedTag = "player-go-live"; onInteraction() }
                            else if (focusedTag == "player-go-live") focusedTag = null
                        },
                    ) { Text(goLive, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    null -> Unit
                }
            }
        }
    }
}
