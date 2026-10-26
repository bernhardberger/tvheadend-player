package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppVideoPresentation

/**
 * One unboxed ring over video. Its caller centres it independently of the chrome. With the live
 * [cover] it fades to nothing once the covered target's sound plays and its picture still does not
 * move ([rememberBusyRingQuiet]); the node and its description stay, so nothing is announced again.
 */
@Composable
internal fun PlayerBusyIndicator(
    status: PlayerBusyStatus?,
    modifier: Modifier = Modifier,
    cover: AppVideoPresentation? = null,
) {
    val quiet by rememberBusyRingQuiet(cover)
    val ringAlpha by animateFloatAsState(
        if (quiet) 0f else 1f,
        tween(180, easing = PlayerMotion.Standard),
        label = "busyRingAlpha",
    )
    val description = status?.let {
        stringResource(if (it == PlayerBusyStatus.TUNING) R.string.player_tuning else R.string.player_buffering)
    }
    AnimatedVisibility(
        visible = status != null,
        enter = fadeIn(tween(180, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(180, easing = PlayerMotion.Standard)),
        modifier = modifier,
    ) {
        // Only this stable node speaks. The primitive's progress semantics are cleared, so neither
        // its animation nor chrome changes publish a second buffering/tuning description.
        Box(Modifier.testTag("player-busy-indicator").clearAndSetSemantics {
            if (description != null) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
        }) {
            PlayerBusyRing(Modifier.graphicsLayer { alpha = ringAlpha })
        }
    }
}

/** Shared visual only; visibility, timing and announcements remain caller-owned. */
@Composable
internal fun PlayerBusyRing(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(44.dp),
        color = Color.White,
        trackColor = Color.Transparent,
        strokeWidth = 3.dp,
    )
}
