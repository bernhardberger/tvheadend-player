package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap

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
) {
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    val info = stringResource(R.string.player_info)
    val settings = stringResource(R.string.nav_settings)
    val record = stringResource(R.string.record)
    val stop = stringResource(R.string.stop_playback)
    val goLive = stringResource(R.string.timeshift_go_live)
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            focusedLabel?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("player-action-context-label")
                        .semantics { hideFromAccessibility() })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val actions = listOf(
                Triple(info, Icons.Filled.Info, onInfo),
                Triple(settings, Icons.Filled.Settings, onSettings),
                Triple(record, Icons.Filled.FiberManualRecord, onRecord),
                Triple(stop, Icons.Filled.Stop, onStop),
            )
            actions.forEachIndexed { index, (label, icon, action) ->
                if (action == null) {
                    Spacer(Modifier.size(TvOverlayActionButtonSize))
                } else {
                    IconButton(
                        onClick = { onInteraction(); action() },
                        modifier = Modifier.size(TvOverlayActionButtonSize)
                            .testTag(listOf("player-info", "player-settings", "player-record", "player-stop")[index])
                            .then(when (index) {
                                0 -> Modifier.focusRequester(infoFocus)
                                1 -> Modifier.focusRequester(settingsFocus)
                                2 -> recordFocus?.let { Modifier.focusRequester(it) } ?: Modifier
                                else -> Modifier
                            })
                            .onFocusChanged {
                                if (it.isFocused) { focusedLabel = label; onInteraction() }
                                else if (focusedLabel == label) focusedLabel = null
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
                            if (it.isFocused) { focusedLabel = goLive; onInteraction() }
                            else if (focusedLabel == goLive) focusedLabel = null
                        },
                    ) { Text(goLive, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    null -> Unit
                }
            }
        }
    }
}
