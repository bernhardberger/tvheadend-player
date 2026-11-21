package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun playingContainerColor(): Color = ListItemDefaults.segmentedColors().selectedContainerColor

@Composable
internal fun watchedProgressColor(): Color = MaterialTheme.colorScheme.tertiary

@Composable
internal fun PlayingBars() {
    val animation = rememberInfiniteTransition(label = "Playing")
    val level by animation.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PLAYING_HALF_CYCLE_MS), RepeatMode.Reverse), label = "Bar levels",
    )
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    val playing = stringResource(R.string.channels_playing)
    Canvas(Modifier.size(MarkSize).semantics { stateDescription = playing }) {
        BarLevels.forEachIndexed { index, (base, swing) ->
            val x = size.width * (index + 1) / (BarLevels.size + 1)
            val bottom = size.height * BAR_BASELINE
            val height = base + swing * level
            drawLine(ink, Offset(x, bottom), Offset(x, bottom - size.height * height), BarWidth.toPx(), StrokeCap.Round)
        }
    }
}

private val MarkSize = 22.dp
private val BarWidth = 3.dp
private val BarLevels = listOf(0.32f to 0.16f, 0.82f to -0.22f, 0.47f to 0.17f)
private const val BAR_BASELINE = 0.9f
private const val PLAYING_HALF_CYCLE_MS = 480
