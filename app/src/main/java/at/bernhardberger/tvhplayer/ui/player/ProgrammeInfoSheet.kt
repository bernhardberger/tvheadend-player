package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.TvSpacing48

/** Share of the screen height the sheet's content takes, from the bottom. */
private const val ProgrammeInfoSheetHeightFraction = 0.66f

/**
 * Trial: programme info as a full-screen sheet. A scrim that deepens towards the bottom
 * covers the still-playing video, and the content rises from below into the lower part of
 * the screen where the chrome was.
 */
@Composable
internal fun ProgrammeInfoSheetFrame(
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    panelTag: String = "live-info-panel",
    content: @Composable () -> Unit,
) {
    val motion = LocalPlayerPanelMotion.current
    val scrimShown = motion?.animateShown(
        enter = tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
        exit = tween(PlayerMotion.ShortMs, easing = PlayerMotion.EmphasizedAccelerate),
        label = "sheet-scrim",
    )
    val sheetShown = motion?.animateShown(
        enter = tween(PlayerMotion.PanelMs, easing = PlayerMotion.EmphasizedDecelerate),
        exit = tween(PlayerMotion.ShortMs, easing = PlayerMotion.EmphasizedAccelerate),
        label = "sheet",
    )
    val frame: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .fillMaxSize()
                .focusGroup(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = scrimShown?.value ?: 1f }
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.4f to Color.Black.copy(alpha = 0.80f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(ProgrammeInfoSheetHeightFraction)
                    .graphicsLayer {
                        val shown = sheetShown?.value ?: 1f
                        alpha = shown
                        translationY = PlayerMotion.PanelOffset.toPx() * (1f - shown)
                    }
                    .padding(horizontal = TvOverlaySidePadding)
                    .padding(bottom = TvSpacing48)
                    .testTag(panelTag)
                    .semantics {
                        dialog()
                        paneTitle?.let { this.paneTitle = it }
                    },
            ) {
                // No Surface here, so the sheet provides the panel's text colour itself.
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    content()
                }
            }
        }
    }
    if (motion == null) frame() else PlayerMotionFrame(leaving = motion.leaving, content = frame)
}
