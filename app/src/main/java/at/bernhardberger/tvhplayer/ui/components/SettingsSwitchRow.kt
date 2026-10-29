package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text

/**
 * Settings switch row. On/off is communicated only by the Switch; the row never
 * uses the selected-container colour reserved for routes and chosen values.
 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    ListItem(
        // Compose maps ListItem's Selected to platform checked, even for a Switch.
        selected = checked,
        colors = ListItemDefaults.colors(
            // Checked is semantic state here, not the selected-container treatment.
            selectedContainerColor = Color.Transparent,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        enabled = enabled,
        onClick = onClick,
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { text ->
            { Text(text) }
        },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                // The row owns the toggle action and accessibility state.
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
    )
}
