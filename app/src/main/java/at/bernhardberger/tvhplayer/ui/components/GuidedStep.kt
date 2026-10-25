package at.bernhardberger.tvhplayer.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing12
import at.bernhardberger.tvhplayer.ui.TvSpacing80
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvTextTertiaryAlpha

/** The first field or button of a field step lines up with the step line's text. */
val GuidedStepFieldTop = 6.dp

/** Choice buttons start level with the title under the step line. */
val GuidedStepChoiceTop = 38.dp

private val GuidedStepActionShape = RoundedCornerShape(12.dp)

/**
 * A guided step: guidance on the left, the step's fields or choices on the right, both
 * [TvBrowseColumnWidth] wide and centred as a pair. Only [actions] holds focusable content.
 */
@Composable
fun GuidedStepLayout(
    guidance: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    actionsTop: Dp = GuidedStepFieldTop,
    actions: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().padding(top = TvSpacing80), contentAlignment = Alignment.TopCenter) {
        Row(horizontalArrangement = Arrangement.spacedBy(TvBrowseColumnGap)) {
            Column(Modifier.width(TvBrowseColumnWidth), content = guidance)
            Column(
                Modifier.width(TvBrowseColumnWidth).padding(top = actionsTop),
                verticalArrangement = Arrangement.spacedBy(TvSpacing12),
                content = actions,
            )
        }
    }
}

/**
 * The guidance column: the step line, an optional [lead] mark, the title as the screen's
 * heading, the explanation and an optional [meta] line naming what the step used.
 */
@Composable
fun GuidedStepGuidance(
    title: String,
    body: String,
    stepper: (@Composable () -> Unit)? = null,
    lead: (@Composable () -> Unit)? = null,
    meta: String? = null,
) {
    if (stepper != null) {
        stepper()
        Spacer(Modifier.height(20.dp))
    }
    if (lead != null) {
        lead()
        Spacer(Modifier.height(16.dp))
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha),
        modifier = Modifier.padding(top = TvSpacing12),
    )
    if (meta != null) {
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha),
            modifier = Modifier.padding(top = TvSpacing12),
        )
    }
}

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
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (index == current) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    index == current -> MaterialTheme.colorScheme.onSurface
                    index < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextSecondaryAlpha)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TvTextTertiaryAlpha)
                },
                modifier = Modifier.padding(start = 6.dp),
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

/**
 * A step's choice in the kit Wide button's look, with an optional explanation and leading icon.
 * TV Material 1.1's WideButton lays its content out at its 240dp minimum and never grows past
 * its minimum height, so it would cut a long or translated explanation; this one wraps it.
 */
@Composable
fun GuidedStepAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.width(TvBrowseColumnWidth).semantics { role = Role.Button },
        shape = ClickableSurfaceDefaults.shape(GuidedStepActionShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.surfaceVariant.copy(alpha = 0.4f),
            contentColor = colors.onSurface,
            focusedContainerColor = colors.onSurface,
            focusedContentColor = colors.inverseOnSurface,
            pressedContainerColor = colors.onSurface,
            pressedContentColor = colors.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (subtitle == null) 48.dp else 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = TvTextSecondaryAlpha),
                    )
                }
            }
        }
    }
}

/** The round mark above a centred result, such as the check after a successful setup. */
@Composable
fun GuidedStepBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(40.dp),
        )
    }
}

