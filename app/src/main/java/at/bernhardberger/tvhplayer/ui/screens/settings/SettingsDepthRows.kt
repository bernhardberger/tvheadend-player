package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvBrowseHeaderHeight
import at.bernhardberger.tvhplayer.ui.SettingsDepthRowMinHeight
import androidx.compose.ui.platform.LocalConfiguration
import at.bernhardberger.tvhplayer.ui.components.depth.*

/** The preview-slot title reads at the row title size: titleMedium 16sp of headlineMedium 28sp. */
internal const val SettingsHeadingPreviewScale = 16f / 28f

internal fun settingsLevel(
    id: String,
    title: String,
    rows: List<DepthRow>,
    activeContent: (@Composable (androidx.compose.ui.focus.FocusRequester, (android.view.KeyEvent) -> Boolean) -> Unit)? = null,
    initialItemId: String? = null,
    description: String? = null,
) = DepthLevel(id, rows, heading = { emphasis ->
    // Same heading in the active and preview slots (AOSP TvSettings parity): no
    // back chevron. It uses the browse header band, so the title sits where the
    // Channels, Guide and Recordings titles sit. In the preview slot it reads at the
    // row title size and grows to full size as its column slides into the active slot.
    val titleBand = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = TvBrowseHeaderHeight),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    scaleX = lerp(SettingsHeadingPreviewScale, 1f, emphasis())
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0f, 1f)
                }.semantics { heading() })
        }
    }
    if (description == null) {
        Box(Modifier.padding(bottom = TvSpacing4)) { titleBand() }
    } else {
        // A level-wide explanation sits under the title, outside every row, so no
        // single option carries it. It aligns with the option text and wraps.
        Column(Modifier.fillMaxWidth().padding(bottom = TvSpacing4)) {
            titleBand()
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = TvSpacing16))
        }
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
    titleMaxLines: Int = 1,
    sectionBottomSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit = {},
) = DepthRow(DepthItem(id, child), { if (enabled) onClick() }) { modifier, activate ->
    Column {
        if (section != null) {
            // Group subheader: the design kit's "List / Subheader" type (12sp medium,
            // uppercase, at the row's own 16dp content inset), but not its colour or its
            // symmetric 8dp box. That board was authored standalone, so it carries the
            // kit's flat neutral and no notion of neighbours; dropped into a real list it
            // read as a competing row title floating between two groups. onSurfaceVariant
            // seats it below the titles, and 16dp above with none below beats the
            // ListItem's own 12dp padding into a clear bias toward the group it heads.
            // Announce the untransformed label so TalkBack is unaffected by the casing.
            Text(section.uppercase(LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TvSpacing16, top = TvSpacing16, bottom = sectionBottomSpacing)
                    .semantics { heading(); contentDescription = section })
        }
        ListItem(
            selected = selected == true,
            // Keep the last visible scope reachable even when its switch cannot be turned off.
            onClick = { if (enabled) activate() },
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis) },
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
            modifier = modifier.fillMaxWidth().padding(bottom = TvSpacing4)
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
