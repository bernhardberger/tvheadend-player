package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
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
        selected = false,
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
            )
        },
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
