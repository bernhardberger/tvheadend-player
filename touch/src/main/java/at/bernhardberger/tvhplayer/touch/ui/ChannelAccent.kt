package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.core.NEUTRAL_ACCENT_RGB
import at.bernhardberger.tvhplayer.images.ChannelAccents

internal val LocalChannelAccents = staticCompositionLocalOf<ChannelAccents?> { null }

/** Use [animated] for the ground and [settled] for artwork keyed by the ground. */
internal data class ChannelAccent(val animated: State<Color>, val settled: Color)

@Composable
internal fun rememberChannelAccent(model: Any?): ChannelAccent? {
    val accents = LocalChannelAccents.current
    if (model == null || accents == null) return null
    val context = LocalContext.current
    return key(model, accents) {
        var rgb by remember { mutableIntStateOf(accents.stored(model) ?: NEUTRAL_ACCENT_RGB) }
        LaunchedEffect(context) {
            rgb = accents.stored(model) ?: accents.sample(context, model) ?: NEUTRAL_ACCENT_RGB
        }
        val settled = Color(0xFF000000.toInt() or rgb)
        val animated = animateColorAsState(settled, tween(CHANNEL_ACCENT_FADE_MILLIS), label = "channelAccent")
        ChannelAccent(animated, settled)
    }
}

private const val CHANNEL_ACCENT_FADE_MILLIS = 320
