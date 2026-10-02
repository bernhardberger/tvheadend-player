package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors

/** Trial: stands in for the current channel's programme, which OK on the channel card opens. */
@Composable
internal fun ChannelProgrammePlaceholder(channelName: String, onDismiss: () -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha)), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.width(640.dp).testTag("player-channel-programme"),
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(containerColor = TvSurfaceColors.containerHigh),
            ) {
                Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.player_channel_programme_title), style = MaterialTheme.typography.headlineSmall)
                    Text(channelName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.player_channel_programme_placeholder), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).focusRequester(close)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
