package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.player.LocalPlayerPanelMotion
import at.bernhardberger.tvhplayer.ui.player.animateShown
import at.bernhardberger.tvhplayer.ui.player.leaving
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.PlayerMotionFrame
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelEdgeInset
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelElevation
import at.bernhardberger.tvhplayer.ui.player.PlaybackPanelWidth

/** Floating side panel shared by player options and browse context menus. */
@Composable
internal fun PlaybackOptionsOverlayFrame(
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    panelTag: String = "playback-options-overlay",
    panelWidth: Dp = PlaybackPanelWidth,
    showScrim: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val motion = LocalPlayerPanelMotion.current
    val scrimShown = motion?.animateShown(
        enter = tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
        exit = tween(PlayerMotion.ShortMs, easing = PlayerMotion.EmphasizedAccelerate),
        label = "panel-scrim",
    )
    val panelShown = motion?.animateShown(
        enter = tween(PlayerMotion.PanelMs, easing = PlayerMotion.EmphasizedDecelerate),
        exit = tween(PlayerMotion.ShortMs, easing = PlayerMotion.EmphasizedAccelerate),
        label = "panel",
    )
    val frame: @Composable () -> Unit = {
        Box(modifier = modifier.fillMaxSize().focusGroup(), contentAlignment = Alignment.CenterEnd) {
            if (showScrim) Box(Modifier.matchParentSize()
                .graphicsLayer { alpha = scrimShown?.value ?: 1f }
                .background(Color.Black.copy(alpha = TvScrimModalAlpha)))
            Surface(
                modifier = Modifier.padding(PlaybackPanelEdgeInset).width(panelWidth).fillMaxHeight()
                    .graphicsLayer {
                        val shown = panelShown?.value ?: 1f
                        alpha = shown
                        translationX = PlayerMotion.PanelOffset.toPx() * (1f - shown)
                    }
                    .shadow(PlaybackPanelElevation, shape).clip(shape).testTag(panelTag)
                    .semantics { dialog(); paneTitle?.let { this.paneTitle = it } },
                shape = shape,
                colors = SurfaceDefaults.colors(
                    containerColor = TvSurfaceColors.container,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { content() }
        }
    }
    if (motion == null) frame() else PlayerMotionFrame(leaving = motion.leaving, content = frame)
}
