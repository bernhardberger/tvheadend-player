package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme

/**
 * A ghost header icon button: no container until focused, then the library's focused, pressed
 * and disabled styling and focus scale.
 */
@Composable
fun RoundIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .size(IconButtonDefaults.MediumButtonSize)
            .focusRequester(focusRequester)
            .onFocusChanged { st ->
                if (st.isFocused) onFocused()
            },
    ) {
        icon()
    }
}
