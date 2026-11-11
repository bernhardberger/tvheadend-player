package at.bernhardberger.tvhplayer.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.ui.*
import at.bernhardberger.tvhplayer.ui.common.recordingSpaceText
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseNavigationFocus
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel

internal const val SETTINGS_ROOT = "settings-root"

internal fun isApplianceCompanionInstalled(context: Context): Boolean =
    context.packageManager.checkSignatures(context.packageName, BuildConfig.APPLIANCE_COMPANION_PACKAGE) ==
        PackageManager.SIGNATURE_MATCH

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
    settingsStore: UiSettingsStore = koinInject(),
    profileOwner: AppProfileOwner = koinInject(),
    playbackInterruption: () -> PlaybackInterruption = rememberPlaybackInterruption(),
    stopPlayback: suspend () -> Unit = rememberPlaybackStop(),
) {
    val context = LocalContext.current
    var companionInstalled by remember(context) { mutableStateOf(isApplianceCompanionInstalled(context)) }
    LifecycleResumeEffect(context, isCurrent) {
        if (isCurrent) companionInstalled = isApplianceCompanionInstalled(context)
        onPauseOrDispose { }
    }
    val navigation = rememberDepthNavigationState(SETTINGS_ROOT, section.name)
    val servers = rememberServerSettingsState(navigation, profileOwner, playbackInterruption, stopPlayback)
    val observation by channelsVm.observation.collectAsStateWithLifecycle()
    val profiles = observation.currentDvrConfigurations()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = UiSettings())
    val scope = rememberCoroutineScope()
    val recordingLevels = settingsRecordingLevels(profiles, settings.recordingProfileId,
        recordingSpaceText(observation.dvrDiskSpaceState)) { id ->
        scope.launch { settingsStore.setRecordingProfile(id); navigation.pop() }
    }
    val connectionLevels = settingsConnectionLevels(
        connectionPresentation, onRetry, servers.actions, profileOwner, servers.switchingNow,
    )
    servers.FocusPending(connectionLevels.firstOrNull())
    val profileList by profileOwner.profiles.collectAsStateWithLifecycle()
    val connectionName = connectionScopeName(profileList)
    val levels = settingsGeneralLevels(navigation) + connectionLevels + settingsPlayerLevels(navigation) + listOf(
        settingsRootLevel(recordingLevels.isNotEmpty(),
            connectionRootStatus(profileList, connectionOverviewStatus(connectionPresentation, profileOwner))),
        settingsKeepChannelLevel(navigation),
        settingsWarmPlaybackLevel(navigation),
        settingsStreamProfileLevel(navigation, connectionName = connectionName),
        settingsFrameRateLevel(navigation),
        settingsStartupBufferLevel(navigation),
        settingsChannelTagsLevel(channelsVm, connectionName),
    ) + recordingLevels + settingsAboutLevels(observation.sessionState, companionInstalled,
        onWhatsNewOpened = { scope.launch { settingsStore.markWhatsNewSeen(BuildConfig.VERSION_CODE) } }) +
        listOf(settingsApplianceLevel(companionInstalled))
    SettingsScreenNavigation(navigation, levels, initialFocusEnabled, contentPadding, backEnabled, isCurrent,
        setup = servers.setupContent(),
        onCloseSetup = servers::closeSetup,
        dialog = servers.dialogContent(),
        onCloseDialog = servers::closeDialogs,
        onCancelSwitch = servers::cancelSwitch.takeIf { servers.switchingNow })
}

/** Connection carries its status; About always names the installed version. */
@Composable
internal fun settingsRootLevel(
    showRecordings: Boolean = false,
    connectionStatus: ConnectionOverviewStatus? = null,
): DepthLevel = settingsLevel(SETTINGS_ROOT, stringResource(R.string.nav_settings), buildList {
    add(settingsRow(SettingsSection.GENERAL.name, stringResource(R.string.settings_general), child = SettingsSection.GENERAL.name, icon = R.drawable.ic_tune))
    add(
        settingsRow(
            SettingsSection.CONNECTION.name, stringResource(R.string.settings_connection_nav), connectionStatus?.title,
            child = SettingsSection.CONNECTION.name, leading = { connectionStatusIcon(connectionStatus?.state) },
        ),
    )
    add(settingsRow(SettingsSection.CHANNEL_TAGS.name, stringResource(R.string.settings_channel_tags_nav), child = SettingsSection.CHANNEL_TAGS.name, icon = R.drawable.ic_filter_list))
    add(settingsRow(SettingsSection.PLAYER.name, stringResource(R.string.settings_player), child = SettingsSection.PLAYER.name, icon = R.drawable.ic_play_arrow))
    add(settingsRow(SettingsSection.AUDIO.name, stringResource(R.string.settings_audio_nav), child = SettingsSection.AUDIO.name, icon = R.drawable.ic_subtitles))
    if (showRecordings) add(settingsRow(RECORDINGS_LEVEL, stringResource(R.string.nav_recordings), child = RECORDINGS_LEVEL,
        icon = R.drawable.ic_fiber_manual_record))
    add(settingsRow(SettingsSection.APPLIANCE.name, stringResource(R.string.settings_appliance), child = SettingsSection.APPLIANCE.name, icon = R.drawable.ic_home))
    add(settingsRow(ABOUT_LEVEL, stringResource(R.string.settings_about),
        supporting = stringResource(R.string.settings_about_version_summary, BuildConfig.VERSION_NAME),
        child = ABOUT_LEVEL, icon = R.drawable.ic_info))
})

/** The Connection row's icon says the state; a connected one is announced since its row drops the word. */
@Composable
private fun connectionStatusIcon(state: ConnectionOverviewState?) {
    val connected = stringResource(R.string.connection_overview_connected)
    val (icon, description) = when (state) {
        ConnectionOverviewState.CONNECTED -> R.drawable.ic_cloud_done to connected
        ConnectionOverviewState.CONNECTING -> R.drawable.ic_cloud_sync to null
        else -> R.drawable.ic_cloud_off_rounded to null
    }
    Icon(painterResource(icon), description, Modifier.size(ListItemDefaults.IconSize))
}

@Composable
internal fun SettingsScreenNavigation(
    navigation: DepthNavigationState,
    levels: List<DepthLevel>,
    initialFocusEnabled: Boolean = true,
    contentPadding: PaddingValues = TvFullScreenPadding,
    backEnabled: Boolean = true,
    isCurrent: Boolean = true,
    /** The connection setup, shown over Settings while it is open; Settings stays where it was. */
    setup: (@Composable () -> Unit)? = null,
    onCloseSetup: () -> Unit = {},
    /** A question over Settings; it takes focus and Back, and Settings refocuses its row after it. */
    dialog: (@Composable () -> Unit)? = null,
    onCloseDialog: () -> Unit = {},
    onCancelSwitch: (() -> Unit)? = null,
) {
    val switchBack = at.bernhardberger.tvhplayer.ui.components.rememberBackKeyCycle()
    val rootFocus = LocalBrowseNavigationFocus.current
    val unavailable = stringResource(R.string.settings_unavailable)
    val layoutDirection = LocalLayoutDirection.current
    // Shell coordinates, in dp: reference x130 is closed drawer80 + safe24 + local26.
    // Drawer expansion pushes this unchanged
    // viewport; it never measures columns narrower. The gap belongs to the depth
    // host so that depth motion stays inside the browse viewport and never paints
    // across the drawer.
    val inset = contentPadding.calculateStartPadding(layoutDirection) + TvBrowseLeadingInset
    // Leaving Settings closes the setup at once, like Back out of it: its disposal cancels a
    // running test, restores the saved connection and clears what was typed; nothing is saved.
    // SideEffect, not LaunchedEffect: it closes in this composition, so a Settings page that leaves
    // composition right after cannot keep its saved setupOpen and reopen the setup on return.
    val shownSetup = setup?.takeIf { isCurrent }
    if (setup != null && !isCurrent) SideEffect(onCloseSetup)
    val setupOpen = shownSetup != null
    // A question closes the same way, so it cannot return with what it said before leaving.
    val shownDialog = dialog?.takeIf { isCurrent }
    if (dialog != null && !isCurrent) SideEffect(onCloseDialog)
    val covered = setupOpen || shownDialog != null
    val layering = LocalBrowseContentLayering.current
    val pageKey = remember { Any() }
    val opacity = remember { mutableFloatStateOf(1f) }
    DisposableEffect(layering, setupOpen, isCurrent) {
        if (setupOpen && isCurrent) layering?.details?.set(pageKey, opacity)
        onDispose { layering?.details?.remove(pageKey) }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        handleSwitchBack(event.nativeKeyEvent, switchBack, onCancelSwitch, covered, isCurrent, backEnabled)
    }) {
        // Settings keeps its columns and focus under the setup; nothing behind it takes focus or
        // Back, and closing the setup returns focus to the row that opened it.
        DepthNavigation(
            state = navigation,
            levels = levels.associateBy { it.id },
            fallbackLevel = { id -> settingsLevel(id, unavailable, listOf(settingsRow("unavailable", unavailable, onClick = navigation::pop))) },
            columnWidth = SettingsDepthColumnWidth,
            columnGap = SettingsDepthColumnGap,
            contentPadding = PaddingValues(start = inset,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize()
                .then(if (setupOpen) Modifier.clearAndSetSemantics { } else Modifier)
                .focusProperties { onEnter = { if (covered) cancelFocusChange() } }
                .focusGroup(),
            initialFocusEnabled = initialFocusEnabled && !covered,
            backEnabled = backEnabled && !covered,
            isCurrent = isCurrent,
            onRootBack = rootFocus?.let { { it.requestFocus() } },
            pageKeyDirection = ChannelNavigation::pageDirectionForKeyCode,
        )
        SwitchBackHandler(backEnabled, isCurrent, covered, onCancelSwitch)
        if (shownSetup != null) {
            // Registered before the setup's own handler, so its steps go back first.
            BackHandler(backEnabled, onCloseSetup)
            Box(Modifier.browseDetailsViewport().background(MaterialTheme.colorScheme.background).fillMaxSize()) {
                shownSetup()
            }
        }
        shownDialog?.invoke()
    }
}

@Composable
private fun SwitchBackHandler(
    backEnabled: Boolean,
    isCurrent: Boolean,
    covered: Boolean,
    onCancelSwitch: (() -> Unit)?,
) {
    BackHandler(enabled = backEnabled && isCurrent && !covered && onCancelSwitch != null) {
        onCancelSwitch?.invoke()
    }
}

private fun handleSwitchBack(
    native: android.view.KeyEvent,
    switchBack: at.bernhardberger.tvhplayer.core.BackKeyCycle,
    onCancelSwitch: (() -> Unit)?,
    covered: Boolean,
    isCurrent: Boolean,
    backEnabled: Boolean,
): Boolean {
    if (onCancelSwitch == null || covered || !isCurrent) {
        switchBack.reset()
        return false
    }
    return if (!backEnabled || native.keyCode != android.view.KeyEvent.KEYCODE_BACK) {
        switchBack.reset()
        false
    } else switchBack.handle(native.action == android.view.KeyEvent.ACTION_DOWN,
        native.repeatCount, native.isCanceled) { onCancelSwitch() }
}
