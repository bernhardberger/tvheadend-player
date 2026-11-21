package at.bernhardberger.tvhplayer.ui.startup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.InlineLogo
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion

/** Artwork joins the startup status line only once its trimmed size is known. */
@Composable
internal fun TuningStatus(channel: Channel, channelName: String, motionEnabled: Boolean, announce: Boolean) {
    val title = channelTitleText(channel.visibleChannelNumber, channelName)
    val phrase = stringResource(R.string.main_startup_message_channel_starting, title)
    var ready by remember(channel.id) { mutableStateOf(channel.icon == null) }
    var failed by remember(channel.id, channel.icon) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f,
        tween(if (motionEnabled) PlayerMotion.ShortMs else 0), label = "tuning-picon")
    Row(Modifier.fillMaxWidth().paddingFromBaseline(top = 32.dp).testTag("main-startup-tuning").clearAndSetSemantics {
        contentDescription = phrase
        if (announce) liveRegion = LiveRegionMode.Polite else hideFromAccessibility()
    }, horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        val icon = channel.icon
        if (icon == null || failed) PiconBox(null, Modifier.size(20.dp).testTag("main-startup-tuning-placeholder"))
        else InlineLogo(icon, Modifier.graphicsLayer { this.alpha = alpha },
            collapseUntilLoaded = true, onLogoReady = { ready = true }, onLogoError = { failed = true; ready = true })
        if (ready) Spacer(Modifier.width(8.dp))
        Text(phrase, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
