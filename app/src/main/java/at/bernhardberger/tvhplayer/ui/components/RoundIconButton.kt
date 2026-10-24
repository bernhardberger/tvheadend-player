package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvGhostFillAlpha
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import at.bernhardberger.tvhplayer.ui.TvSpacing8

/** The longest value an active header button shows before it ellipsizes. */
private val RoundIconButtonValueMaxWidth = 160.dp

/**
 * A header icon button on a faint resting disc. With a [value] it grows into a pill naming it,
 * after the [dot] when given: tonal while the value is [active], on the resting tone otherwise.
 * Focus takes the library's focused styling and scale either way. TalkBack reads the [label] with
 * the value.
 */
@Composable
fun RoundIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    dot: Color? = null,
    active: Boolean = value != null,
) {
    val description = value?.let { stringResource(R.string.header_button_value_description, label, it) } ?: label
    // One button for both states, so focus stays on it while its value comes and goes.
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(CircleShape),
        colors = if (!active) ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TvGhostFillAlpha),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) else ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        contentPadding = if (value == null) PaddingValues(0.dp) else PaddingValues(start = 12.dp, end = 16.dp),
        modifier = modifier
            .height(TvIconButtonSize)
            .then(if (value == null) Modifier.width(TvIconButtonSize) else Modifier)
            .focusRequester(focusRequester)
            .onFocusChanged { st ->
                if (st.isFocused) onFocused()
            }
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, Modifier.size(IconButtonDefaults.MediumIconSize))
        if (value != null) {
            Spacer(Modifier.width(TvSpacing8))
            if (dot != null) {
                Box(Modifier.size(12.dp).background(dot, CircleShape).testTag("header-button-dot"))
                Spacer(Modifier.width(TvSpacing8))
            }
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = RoundIconButtonValueMaxWidth),
            )
        }
    }
}
