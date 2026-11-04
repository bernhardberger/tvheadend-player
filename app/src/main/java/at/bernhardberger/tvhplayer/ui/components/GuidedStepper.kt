package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha

/**
 * The step line. Steps before [current] show a check, [current] its number in an outline (or
 * a filled "!" when [failed]), later ones a dim number; [current] equal to the number of
 * [labels] marks every step done. It reads as one sentence to accessibility services.
 */
@Composable
fun GuidedStepper(
    labels: List<String>,
    current: Int,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
) {
    val description = if (current >= labels.size) {
        stringResource(R.string.guided_step_all_done)
    } else {
        stringResource(R.string.guided_step_progress, current + 1, labels.size, labels[current])
    }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 10.dp)
                        .size(width = 16.dp, height = 1.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            GuidedStepMarker(
                number = index + 1,
                state = when {
                    index < current -> GuidedStepState.DONE
                    index > current -> GuidedStepState.UPCOMING
                    failed -> GuidedStepState.FAILED
                    else -> GuidedStepState.CURRENT
                },
            )
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (index == current) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    index == current -> MaterialTheme.colorScheme.onSurface
                    index < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha)
                },
                modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp),
            )
        }
    }
}

private enum class GuidedStepState { DONE, CURRENT, FAILED, UPCOMING }

@Composable
private fun GuidedStepMarker(number: Int, state: GuidedStepState) {
    val colors = MaterialTheme.colorScheme
    val shape = Modifier.size(18.dp).let { base ->
        when (state) {
            GuidedStepState.DONE -> base.background(colors.primary, CircleShape)
            GuidedStepState.FAILED -> base.background(colors.error, CircleShape)
            GuidedStepState.CURRENT -> base.border(2.dp, colors.primary, CircleShape)
            GuidedStepState.UPCOMING -> base.border(1.dp, colors.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
        }
    }
    Box(shape, contentAlignment = Alignment.Center) {
        when (state) {
            GuidedStepState.DONE -> Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(12.dp),
            )
            GuidedStepState.FAILED -> MarkerText("!", colors.onError)
            GuidedStepState.CURRENT -> MarkerText(number.toString(), colors.primary)
            GuidedStepState.UPCOMING -> MarkerText(
                number.toString(),
                colors.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha),
            )
        }
    }
}

@Composable
private fun MarkerText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
    )
}
