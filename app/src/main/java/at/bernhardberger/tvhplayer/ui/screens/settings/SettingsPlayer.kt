package at.bernhardberger.tvhplayer.ui.screens.settings

import android.hardware.display.DisplayManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.presentation.R
import at.bernhardberger.tvhplayer.ui.common.formatDurationSeconds
import at.bernhardberger.tvhplayer.core.streamProfilePresentation
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.keepChannelMinuteChoices
import at.bernhardberger.tvhplayer.ui.player.displayRefreshRateChoices
import at.bernhardberger.tvhplayer.ui.player.formatRefreshRate
import at.bernhardberger.tvhplayer.ui.player.matchesVideoRate
import at.bernhardberger.tvhplayer.ui.player.MatchFrameRatePreference
import at.bernhardberger.tvhplayer.ui.player.readMatchFrameRatePreference
import at.bernhardberger.tvhplayer.ui.player.toSpec
import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_FIXED_MILLIS
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

internal const val KEEP_CHANNEL_LEVEL = "keep-channel"

internal const val WARM_PLAYBACK_LEVEL = "warm-playback"

@Composable
internal fun settingsWarmPlaybackLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsWarmPlaybackLevel(ui.warmPlaybackMode) { mode ->
        vm.onWarmPlaybackModeChanged(mode)
        navigation.pop()
    }
}

@Composable
internal fun settingsWarmPlaybackLevel(mode: WarmPlaybackMode, onSelect: (WarmPlaybackMode) -> Unit): DepthLevel =
    settingsLevel(WARM_PLAYBACK_LEVEL, stringResource(R.string.warm_playback_setting), WarmPlaybackMode.entries.map { value ->
        settingsRow(value.name, warmPlaybackLabel(value), supporting = stringResource(when (value) {
            WarmPlaybackMode.ON -> R.string.warm_playback_on_help
            WarmPlaybackMode.AUDIO_ONLY -> R.string.warm_playback_audio_help
            WarmPlaybackMode.OFF -> R.string.warm_playback_off_help
        }), selected = mode == value, onClick = { onSelect(value) })
    }, initialItemId = mode.name)

@Composable
private fun warmPlaybackLabel(mode: WarmPlaybackMode): String = stringResource(when (mode) {
    WarmPlaybackMode.ON -> R.string.warm_playback_on
    WarmPlaybackMode.AUDIO_ONLY -> R.string.warm_playback_audio
    WarmPlaybackMode.OFF -> R.string.warm_playback_off
})

@Composable
internal fun settingsKeepChannelLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsKeepChannelLevel(ui.keepChannelMinutes) { minutes ->
        vm.onKeepChannelMinutesChanged(minutes)
        navigation.pop()
    }
}

@Composable
internal fun settingsKeepChannelLevel(minutes: Int, onSelect: (Int) -> Unit): DepthLevel =
    settingsLevel(KEEP_CHANNEL_LEVEL, stringResource(R.string.keep_channel_setting), keepChannelMinuteChoices.map { value ->
        settingsRow("keep-$value", keepChannelLabel(value), selected = minutes == value, onClick = { onSelect(value) })
    }, initialItemId = "keep-$minutes",
        description = stringResource(R.string.keep_channel_description))

/** Written out ("20 minutes"): the value reads as a sentence on the row and in its choices. */
@Composable
private fun keepChannelLabel(minutes: Int): String = if (minutes == 0) stringResource(R.string.keep_channel_off)
    else at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(minutes.toLong(),
        androidx.compose.ui.platform.LocalResources.current, spoken = true)

internal const val DISPLAY_RATE_LEVEL = "display-rate"

/** The display's refresh rates at its current resolution; empty before the window has a display. */
@Composable
private fun rememberDisplayRefreshRates(): List<Float> {
    val view = androidx.compose.ui.platform.LocalView.current
    return remember(view) {
        val display = view.display ?: return@remember emptyList()
        displayRefreshRateChoices(display.mode.toSpec(), display.supportedModes.map { it.toSpec() })
    }
}

@Composable
internal fun settingsDisplayRateLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsDisplayRateLevel(ui.displayRefreshRate, rememberDisplayRefreshRates()) { rate ->
        vm.onDisplayRefreshRateChanged(rate)
        navigation.pop()
    }
}

@Composable
internal fun settingsDisplayRateLevel(selected: Float?, rates: List<Float>, onSelect: (Float?) -> Unit): DepthLevel {
    val selectedRate = selected?.let { stored -> rates.firstOrNull { matchesVideoRate(it, stored) } }
    return settingsLevel(DISPLAY_RATE_LEVEL, stringResource(R.string.display_refresh_rate_setting), listOf(
        settingsRow(DISPLAY_RATE_SYSTEM, stringResource(R.string.display_refresh_rate_system),
            supporting = stringResource(R.string.display_refresh_rate_system_help),
            selected = selected == null, onClick = { onSelect(null) }),
    ) + rates.map { rate ->
        settingsRow(rate.toString(), displayRefreshRateLabel(rate),
            selected = rate == selectedRate, onClick = { onSelect(rate) })
    }, initialItemId = selectedRate?.toString() ?: DISPLAY_RATE_SYSTEM,
        description = stringResource(R.string.display_refresh_rate_description))
}

private const val DISPLAY_RATE_SYSTEM = "system"

@Composable
private fun displayRefreshRateLabel(rate: Float?): String =
    if (rate == null) stringResource(R.string.display_refresh_rate_system)
    else stringResource(R.string.display_refresh_rate_value, formatRefreshRate(rate))

internal const val FRAME_RATE_LEVEL = "frame-rate"

@Composable
internal fun settingsFrameRateLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsFrameRateLevel(ui.frameRateSwitching) { switching ->
        vm.onFrameRateSwitchingChanged(switching)
        navigation.pop()
    }
}

@Composable
internal fun settingsFrameRateLevel(switching: FrameRateSwitching, onSelect: (FrameRateSwitching) -> Unit): DepthLevel {
    val sdkInt = Build.VERSION.SDK_INT
    val supportsFollowSystem = FrameRateSwitching.supportsFollowSystem(sdkInt)
    val title = stringResource(R.string.refresh_rate_matching_setting)
    return settingsLevel(FRAME_RATE_LEVEL, title, FrameRateSwitching.choices(sdkInt).map { value ->
        val help = when (value) {
            FrameRateSwitching.FOLLOW_SYSTEM -> rememberFrameRateSystemChoice()?.let {
                stringResource(R.string.frame_rate_switching_follow_system_choice, it)
            } ?: stringResource(R.string.frame_rate_switching_follow_system_help)
            FrameRateSwitching.ALWAYS -> stringResource(
                if (supportsFollowSystem) R.string.frame_rate_switching_force_help
                else R.string.frame_rate_switching_always_help,
            )
            FrameRateSwitching.SEAMLESS_ONLY -> stringResource(R.string.frame_rate_switching_seamless_help)
            FrameRateSwitching.OFF -> stringResource(R.string.frame_rate_switching_off_help)
        }
        settingsRow(value.name, frameRateSwitchingLabel(value), supporting = help,
            selected = switching == value, onClick = { onSelect(value) })
    }, initialItemId = switching.name)
}

@Composable
private fun frameRateSwitchingLabel(switching: FrameRateSwitching): String =
    stringResource(when (switching) {
        FrameRateSwitching.FOLLOW_SYSTEM -> R.string.frame_rate_switching_follow_system
        FrameRateSwitching.ALWAYS -> R.string.frame_rate_switching_always
        FrameRateSwitching.SEAMLESS_ONLY -> R.string.frame_rate_switching_seamless_only
        FrameRateSwitching.OFF -> R.string.frame_rate_switching_off
    })

@Composable
private fun frameRateSwitchingSummary(switching: FrameRateSwitching): String {
    val label = frameRateSwitchingLabel(switching)
    if (switching != FrameRateSwitching.FOLLOW_SYSTEM) return label
    val choice = rememberFrameRateSystemChoice()
    return if (choice == null) label
    else stringResource(R.string.frame_rate_switching_follow_system_summary, label, choice)
}

@Composable
private fun rememberFrameRateSystemChoice(): String? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    var preference by remember(manager) { mutableStateOf(readMatchFrameRatePreference(manager)) }
    LifecycleResumeEffect(manager) {
        preference = readMatchFrameRatePreference(manager)
        onPauseOrDispose { }
    }
    val choice = when (preference) {
        MatchFrameRatePreference.NEVER -> R.string.frame_rate_switching_never
        MatchFrameRatePreference.SEAMLESS_ONLY -> R.string.frame_rate_switching_seamless_only
        MatchFrameRatePreference.ALWAYS -> R.string.frame_rate_switching_always
        null -> return null
    }
    return stringResource(choice)
}

internal const val STARTUP_BUFFER_LEVEL = "startup-buffer"

@Composable
internal fun settingsStartupBufferLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsStartupBufferLevel(ui.startupBufferMillis, ui.startupBufferLearnedMillis) { millis ->
        vm.onStartupBufferMillisChanged(millis)
        navigation.pop()
    }
}

@Composable
internal fun settingsStartupBufferLevel(millis: Int, learnedMillis: Int, onSelect: (Int) -> Unit): DepthLevel =
    settingsLevel(STARTUP_BUFFER_LEVEL, stringResource(R.string.startup_buffer_setting),
        (listOf(STARTUP_BUFFER_AUTOMATIC) + STARTUP_BUFFER_FIXED_MILLIS).map { value ->
            settingsRow("startup-buffer-$value", startupBufferLabel(value, learnedMillis),
                selected = millis == value, onClick = { onSelect(value) })
        }, initialItemId = "startup-buffer-$millis",
        description = stringResource(R.string.startup_buffer_description))

/** "Automatic • 1s" with the learned level, or a fixed level in locale decimals ("0,5s"), to a tenth of a second. */
@Composable
internal fun startupBufferLabel(millis: Int, learnedMillis: Int): String =
    if (millis == STARTUP_BUFFER_AUTOMATIC) {
        stringResource(R.string.startup_buffer_automatic, startupBufferSeconds(learnedMillis))
    } else startupBufferSeconds(millis)

@Composable
private fun startupBufferSeconds(millis: Int): String =
    formatDurationSeconds(Math.round(millis / 100.0) / 10.0, LocalResources.current)

internal const val STREAM_PROFILE_LEVEL = "stream-profile"
internal const val STREAM_PROFILE_DEFAULT = "profile-default"

@Composable
internal fun settingsStreamProfileLevel(
    navigation: DepthNavigationState,
    vm: SettingsPlayerViewModel = koinViewModel(),
    connectionName: String? = null,
): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsStreamProfileLevel(ui, connectionName = connectionName) { profileId ->
        vm.onProfileSelected(profileId)
        navigation.pop()
    }
}

/**
 * The server's stream profiles, with Server default first. While the profiles aren't available
 * the page shows only why, so a page left open across a disconnect offers nothing stale.
 */
@Composable
internal fun settingsStreamProfileLevel(
    ui: SettingsPlayerUiState,
    connectionName: String? = null,
    onSelect: (StreamProfileId?) -> Unit,
): DepthLevel {
    val direct = stringResource(R.string.profile_direct_streaming)
    val profiles = ui.profiles as? StreamProfilesResult.Available
    val rows = if (profiles == null) {
        listOf(settingsRow(
            "profiles-status", streamProfilesStatus(ui.profiles, ui.connected).orEmpty(), enabled = false,
        ))
    } else buildList {
        add(settingsRow(STREAM_PROFILE_DEFAULT, stringResource(R.string.profile_server_default),
            selected = ui.selectedProfileId == null, onClick = { onSelect(null) }))
        profiles.profiles.forEach { profile ->
            val presentation = streamProfilePresentation(profile.name, direct)
            add(settingsRow(streamProfileRowId(profile.id), presentation.primaryLabel, presentation.secondaryLabel,
                selected = profile.id == ui.selectedProfileId, onClick = { onSelect(profile.id) }))
        }
    }
    return settingsLevel(STREAM_PROFILE_LEVEL, stringResource(R.string.profile), rows,
        initialItemId = rows.firstOrNull { it.item.id == ui.selectedProfileId?.let(::streamProfileRowId) }?.item?.id
            ?: rows.first().item.id,
        description = stringResource(R.string.stream_profile_description), connectionName = connectionName)
}

private fun streamProfileRowId(id: StreamProfileId) = "profile-${id.value}"

/** Why the profiles can't be chosen yet, or null once the server has listed them. */
@Composable
internal fun streamProfilesStatus(profiles: StreamProfilesResult, connected: Boolean): String? = when (profiles) {
    StreamProfilesResult.Failed(TvheadendFailure.NOT_READY) ->
        stringResource(if (connected) R.string.loading_wait else R.string.not_connected)
    is StreamProfilesResult.Available -> null
    else -> stringResource(R.string.stream_profiles_unavailable)
}

/** The chosen profile's name; a saved profile the server no longer lists reads as Server default. */
@Composable
internal fun streamProfileLabel(profiles: StreamProfilesResult, selectedProfileId: StreamProfileId?): String {
    val selected = (profiles as? StreamProfilesResult.Available)?.profiles?.firstOrNull { it.id == selectedProfileId }
        ?: return stringResource(R.string.profile_server_default)
    return streamProfilePresentation(selected.name, stringResource(R.string.profile_direct_streaming)).primaryLabel
}

@Composable
internal fun settingsPlayerLevels(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): List<DepthLevel> {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return listOf(
        settingsPlayerLevel(ui, vm::onTimeshiftEnabledChanged, vm::onAutoStartPlaybackChanged, vm::onTunnelingEnabledChanged,
            vm::onSkipCutsChanged, vm::onOfferProgramStartChanged, vm::onContinueAutomaticallyChanged),
        settingsAudioLevel(ui, vm::onAudioPassthroughEnabledChanged, vm::onAudioDescriptionChanged),
        settingsAdBreaksLevel(ui.adBreakMode) { vm.onAdBreakModeChanged(it); navigation.pop() },
        settingsSkipLandingLevel(ui.skipLanding) { vm.onSkipLandingChanged(it); navigation.pop() },
    ) + settingsAudioPreferenceLevels(ui,
        onAudioLanguageSelected = { slot, language -> vm.onAudioLanguageSelected(slot, language); navigation.pop() },
        onAudioFormatSelected = { vm.onAudioFormatSelected(it); navigation.pop() },
        onSubtitleLanguageSelected = { vm.onSubtitleLanguageSelected(it); navigation.pop() })
}

@Composable
internal fun settingsPlayerLevel(
    ui: SettingsPlayerUiState,
    onTimeshiftEnabledChanged: (Boolean) -> Unit,
    onAutoStartPlaybackChanged: (Boolean) -> Unit = {},
    onTunnelingEnabledChanged: (Boolean) -> Unit = {},
    onSkipCutsChanged: (Boolean) -> Unit = {},
    onOfferProgramStartChanged: (Boolean) -> Unit = {},
    onContinueAutomaticallyChanged: (Boolean) -> Unit = {},
): DepthLevel {
    val profilesStatus = streamProfilesStatus(ui.profiles, ui.connected)
    return settingsLevel(SettingsSection.PLAYER.name, stringResource(R.string.settings_player), listOf(
        settingsRow("autoplay", stringResource(R.string.auto_start_playback), checked = ui.autoStartPlayback,
            onClick = { onAutoStartPlaybackChanged(!ui.autoStartPlayback) }),
        settingsRow(WARM_PLAYBACK_LEVEL, stringResource(R.string.warm_playback_setting),
            warmPlaybackLabel(ui.warmPlaybackMode), child = WARM_PLAYBACK_LEVEL),
        settingsRow("timeshift", stringResource(R.string.timeshift_setting),
            supporting = stringResource(R.string.timeshift_setting_description), checked = ui.timeshiftEnabled,
            onClick = { onTimeshiftEnabledChanged(!ui.timeshiftEnabled) }),
        // Only a paused timeshift channel is kept tuned, so without timeshift the row has no effect.
        settingsRow(KEEP_CHANNEL_LEVEL, stringResource(R.string.keep_channel_setting),
            if (ui.timeshiftEnabled) keepChannelLabel(ui.keepChannelMinutes)
                else stringResource(R.string.keep_channel_needs_timeshift, stringResource(R.string.timeshift_setting)),
            child = KEEP_CHANNEL_LEVEL.takeIf { ui.timeshiftEnabled }, enabled = ui.timeshiftEnabled),
        settingsRow("continue-automatically", stringResource(R.string.continue_automatically),
            supporting = stringResource(R.string.continue_automatically_description), checked = ui.continueAutomatically,
            onClick = { onContinueAutomaticallyChanged(!ui.continueAutomatically) }),
    ) + settingsRecordingMarksRows(ui, onSkipCutsChanged, onOfferProgramStartChanged) + listOf(
        settingsRow(STREAM_PROFILE_LEVEL, stringResource(R.string.profile),
            profilesStatus ?: streamProfileLabel(ui.profiles, ui.selectedProfileId),
            child = STREAM_PROFILE_LEVEL.takeIf { profilesStatus == null }, enabled = profilesStatus == null,
            section = stringResource(R.string.settings_section_streaming)),
        settingsRow(STARTUP_BUFFER_LEVEL, stringResource(R.string.startup_buffer_setting),
            startupBufferLabel(ui.startupBufferMillis, ui.startupBufferLearnedMillis), child = STARTUP_BUFFER_LEVEL),
        settingsRow(DISPLAY_RATE_LEVEL, stringResource(R.string.display_refresh_rate_setting),
            displayRefreshRateLabel(ui.displayRefreshRate), child = DISPLAY_RATE_LEVEL,
            section = stringResource(R.string.settings_section_picture)),
        settingsRow(FRAME_RATE_LEVEL, stringResource(R.string.refresh_rate_matching_setting),
            frameRateSwitchingSummary(ui.frameRateSwitching), child = FRAME_RATE_LEVEL),
        settingsRow("tunneling", stringResource(R.string.tunneling_setting),
            supporting = stringResource(
                if (ui.tunnelingEnabled && ui.tunnelingFallbackActive) R.string.tunneling_setting_fallback
                else R.string.tunneling_setting_description), checked = ui.tunnelingEnabled,
            section = stringResource(R.string.settings_section_experimental),
            onClick = { onTunnelingEnabledChanged(!ui.tunnelingEnabled) }),
    ))
}
