package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.ui.*
import at.bernhardberger.tvhplayer.ui.common.recordingSpaceText
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel

internal const val SETTINGS_ROOT = "settings-root"

@Composable
internal fun SettingsScreen(
    channelsVm: ChannelsViewModel,
    connectionPresentation: at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation,
    onRetry: () -> Unit,
    section: SettingsSection = SettingsSection.GENERAL,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    isCurrent: Boolean = true,
    applianceBuild: Boolean = BuildConfig.APPLIANCE_BUILD,
    settingsStore: UiSettingsStore = koinInject(),
) {
    val navigation = rememberDepthNavigationState(SETTINGS_ROOT, section.name)
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val profiles = observation.currentDvrConfigurations()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()
    val recordingLevels = settingsRecordingLevels(profiles, settings.recordingProfileId,
        recordingSpaceText(observation.dvrDiskSpaceState)) { id ->
        scope.launch { settingsStore.setRecordingProfile(id); navigation.pop() }
    }
    val levels = settingsGeneralLevels(navigation) + settingsConnectionLevels(navigation, connectionPresentation, onRetry) + settingsPlayerLevels(navigation) + listOf(
        settingsRootLevel(applianceBuild, recordingLevels.isNotEmpty()),
        settingsKeepChannelLevel(navigation),
        settingsFrameRateLevel(navigation),
        settingsStartupBufferLevel(navigation),
        settingsChannelTagsLevel(channelsVm),
    ) + recordingLevels + settingsAboutLevels(observation.sessionState, applianceBuild) +
        if (applianceBuild) listOf(settingsApplianceLevel()) else emptyList()
    SettingsScreenNavigation(navigation, levels, initialFocusEnabled, contentPadding, backEnabled, isCurrent)
}

@Composable
internal fun settingsRootLevel(applianceBuild: Boolean = BuildConfig.APPLIANCE_BUILD, showRecordings: Boolean = false): DepthLevel = settingsLevel(SETTINGS_ROOT, stringResource(R.string.nav_settings), listOf(
    settingsRow(SettingsSection.GENERAL.name, stringResource(R.string.settings_general_nav), child = SettingsSection.GENERAL.name, icon = R.drawable.ic_tune),
    settingsRow(SettingsSection.CHANNEL_TAGS.name, stringResource(R.string.settings_channel_tags_nav), child = SettingsSection.CHANNEL_TAGS.name, icon = R.drawable.ic_filter_list),
    settingsRow(SettingsSection.CONNECTION.name, stringResource(R.string.settings_connection_nav), child = SettingsSection.CONNECTION.name, icon = R.drawable.ic_cloud),
    settingsRow(SettingsSection.PLAYER.name, stringResource(R.string.settings_player_nav), child = SettingsSection.PLAYER.name, icon = R.drawable.ic_play_arrow),
) + (if (showRecordings) listOf(settingsRow(RECORDINGS_LEVEL, stringResource(R.string.nav_recordings), child = RECORDINGS_LEVEL,
    icon = R.drawable.ic_fiber_manual_record)) else emptyList()) + (if (applianceBuild) listOf(
    settingsRow(SettingsSection.APPLIANCE.name, stringResource(R.string.settings_appliance_nav), child = SettingsSection.APPLIANCE.name, icon = R.drawable.ic_home),
) else emptyList()) + listOf(
    settingsRow(ABOUT_LEVEL, stringResource(R.string.settings_about), child = ABOUT_LEVEL, icon = R.drawable.ic_info),
))

@Composable
internal fun SettingsScreenNavigation(
    navigation: DepthNavigationState,
    levels: List<DepthLevel>,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    isCurrent: Boolean = true,
) {
    val rootFocus = LocalBrowseNavigationFocus.current
    val unavailable = stringResource(R.string.settings_unavailable)
    val layoutDirection = LocalLayoutDirection.current
    // Shell coordinates, in dp: reference x130 is closed drawer80 + safe24 + local26.
    // Drawer expansion pushes this unchanged
    // viewport; it never measures columns narrower. The gap belongs to the depth
    // host so that depth motion stays inside the browse viewport and never paints
    // across the drawer.
    val inset = contentPadding.calculateStartPadding(layoutDirection) + TvBrowseLeadingInset
    DepthNavigation(
        state = navigation,
        levels = levels.associateBy { it.id },
        fallbackLevel = { id -> settingsLevel(id, unavailable, listOf(settingsRow("unavailable", unavailable, onClick = navigation::pop))) },
        columnWidth = SettingsDepthColumnWidth,
        columnGap = SettingsDepthColumnGap,
        contentPadding = PaddingValues(start = inset,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()),
        modifier = Modifier.fillMaxSize(),
        initialFocusEnabled = initialFocusEnabled,
        backEnabled = backEnabled,
        isCurrent = isCurrent,
        onRootBack = rootFocus?.let { { it.requestFocus() } },
    )
}
