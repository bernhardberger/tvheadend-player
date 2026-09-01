package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import at.bernhardberger.tvhplayer.ui.components.SettingsSubRail
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import at.bernhardberger.tvhplayer.ui.TvSpacing32
import at.bernhardberger.tvhplayer.core.SettingsBackAction
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.settingsBackAction
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsAppliance
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsConnection
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsChannelTags
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsGeneral
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsPlayer

@Composable
internal fun SettingsScreen(
    section: SettingsSection = SettingsSection.GENERAL,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    onNavigate: (SettingsSection) -> Unit,
    onStartSimpleTv: (SimpleTvSettings) -> Unit,
) {
    SettingsScreenNavigation(
        currentSection = section,
        initialFocusEnabled = initialFocusEnabled,
        contentPadding = contentPadding,
        backEnabled = backEnabled,
        onNavigate = onNavigate,
    ) { destination, initialFocusRequester ->
        when (destination) {
            SettingsSection.GENERAL -> SettingsGeneral(initialFocusRequester)
            SettingsSection.CONNECTION -> SettingsConnection(initialFocusRequester)
            SettingsSection.CHANNEL_TAGS -> SettingsChannelTags(initialFocusRequester)
            SettingsSection.PLAYER -> SettingsPlayer(initialFocusRequester)
            SettingsSection.APPLIANCE -> SettingsAppliance(
                initialFocusRequester = initialFocusRequester,
                onStartSimpleTv = onStartSimpleTv,
            )
        }
    }
}

@Composable
internal fun SettingsScreenNavigation(
    currentSection: SettingsSection,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    onNavigate: (SettingsSection) -> Unit,
    destinationContent: @Composable (SettingsSection, FocusRequester) -> Unit,
) {
    val settingsSections = remember { SettingsSection.entries }
    val categoryFocus = remember(settingsSections) {
        settingsSections.associateWith { FocusRequester() }
    }
    val contentFocus = remember(settingsSections) {
        settingsSections.associateWith { FocusRequester() }
    }
    var contentPaneFocused by remember { mutableStateOf(false) }
    var initialCategoryFocusHandled by remember { mutableStateOf(false) }

    val backAction = settingsBackAction(
        contentPaneFocused = contentPaneFocused,
    )
    val focusCurrentCategory: () -> Unit = {
        categoryFocus.getValue(currentSection).requestFocus()
    }
    BackHandler(
        enabled = backEnabled && backAction == SettingsBackAction.FOCUS_CURRENT_CATEGORY,
    ) {
        focusCurrentCategory()
    }
    LaunchedEffect(initialFocusEnabled, currentSection) {
        if (!initialFocusEnabled) {
            initialCategoryFocusHandled = false
            return@LaunchedEffect
        }
        if (!initialCategoryFocusHandled) {
            categoryFocus.getValue(currentSection).requestFocus()
            initialCategoryFocusHandled = true
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .focusRestorer(categoryFocus.getValue(currentSection))
                // The shell owns the global rail; Settings owns its inner panel spacing.
                .padding(contentPadding)
        ) {
            SettingsSubRail(
                currentRoute = currentSection,
                categoryFocusRequesters = categoryFocus,
                contentFocusRequesters = contentFocus,
                // Settings owns route-aware entry so a direct Connection start
                // never briefly focuses General while the typed destination initializes.
                initialFocusEnabled = false,
                onNavigate = onNavigate,
            )

            Spacer(Modifier.width(TvSpacing32))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { contentPaneFocused = it.hasFocus }
                    .focusGroup()
            ) {
                destinationContent(
                    currentSection,
                    contentFocus.getValue(currentSection),
                )
            }
        }
    }
}
