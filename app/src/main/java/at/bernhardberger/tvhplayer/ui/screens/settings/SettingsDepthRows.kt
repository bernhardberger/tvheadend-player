package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.SettingsDepthHeadingHeight
import at.bernhardberger.tvhplayer.ui.SettingsDepthRowMinHeight
import at.bernhardberger.tvhplayer.ui.components.depth.*

internal fun settingsLevel(
    id: String,
    title: String,
    rows: List<DepthRow>,
    activeContent: (@Composable (androidx.compose.ui.focus.FocusRequester, (android.view.KeyEvent) -> Boolean) -> Unit)? = null,
    initialItemId: String? = null,
) = DepthLevel(id, rows, heading = { _ ->
    // Same heading in the active and preview slots (AOSP TvSettings parity): no
    // back chevron, so the title never shifts when a column changes role.
    Row(Modifier.fillMaxWidth().heightIn(min = SettingsDepthHeadingHeight).padding(bottom = TvSpacing24),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() })
    }
}, activeContent = activeContent, initialItemId = initialItemId)

internal fun settingsRow(
    id: String,
    title: String,
    supporting: String? = null,
    child: String? = null,
    section: String? = null,
    icon: Int? = null,
    checked: Boolean? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    announcement: String? = null,
    busy: Boolean = false,
    trailingIcon: Int? = null,
    onClick: () -> Unit = {},
) = DepthRow(DepthItem(id, child), { if (enabled) onClick() }) { modifier, activate ->
    Column {
        if (section != null) {
            Text(section, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = TvSpacing8, bottom = TvSpacing8).semantics { heading() })
        }
        ListItem(
            selected = selected == true,
            // Keep the last visible scope reachable even when its switch cannot be turned off.
            onClick = { if (enabled) activate() },
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            leadingContent = icon?.let { { Icon(painterResource(it), null, Modifier.size(24.dp)) } },
            trailingContent = when {
                busy -> ({ androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 2.dp,
                ) })
                checked != null -> ({ Switch(checked = checked, onCheckedChange = null, enabled = enabled) })
                selected != null -> ({ RadioButton(selected = selected, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = LocalContentColor.current,
                        unselectedColor = LocalContentColor.current)) })
                trailingIcon != null -> ({ Icon(painterResource(trailingIcon), null, Modifier.size(24.dp)) })
                child != null && icon == null -> ({ Icon(painterResource(R.drawable.ic_keyboard_arrow_right), null, Modifier.size(24.dp)) })
                else -> null
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                focusedSelectedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedSelectedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
            modifier = modifier.fillMaxWidth().padding(bottom = TvSpacing8)
                .heightIn(min = if (supporting == null) 48.dp else SettingsDepthRowMinHeight).semantics {
                if (!enabled) disabled()
                if (checked != null) { role = Role.Switch; toggleableState = ToggleableState(checked) }
                if (selected != null) role = Role.RadioButton
                if (announcement != null) {
                    stateDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                }
            },
        )
    }
}
