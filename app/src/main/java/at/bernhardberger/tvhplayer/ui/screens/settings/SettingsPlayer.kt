package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.streamProfilePresentation
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
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
    settingsLevel(KEEP_CHANNEL_LEVEL, stringResource(R.string.keep_channel_setting), listOf(0, 10, 20, 30).map { value ->
        settingsRow("keep-$value", keepChannelLabel(value), selected = minutes == value, onClick = { onSelect(value) })
    }, initialItemId = "keep-$minutes",
        description = stringResource(R.string.keep_channel_description))

/** Written out ("20 minutes"): the value reads as a sentence on the row and in its choices. */
@Composable
private fun keepChannelLabel(minutes: Int): String = if (minutes == 0) stringResource(R.string.keep_channel_off)
    else at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(minutes.toLong(),
        androidx.compose.ui.platform.LocalResources.current, spoken = true)

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
internal fun settingsFrameRateLevel(switching: FrameRateSwitching, onSelect: (FrameRateSwitching) -> Unit): DepthLevel =
    settingsLevel(FRAME_RATE_LEVEL, stringResource(R.string.refresh_rate_matching_setting), FrameRateSwitching.entries.map { value ->
        settingsRow(value.name, frameRateSwitchingLabel(value), supporting = stringResource(when (value) {
            FrameRateSwitching.ALWAYS -> R.string.frame_rate_switching_always_help
            FrameRateSwitching.SEAMLESS_ONLY -> R.string.frame_rate_switching_seamless_help
            FrameRateSwitching.OFF -> R.string.frame_rate_switching_off_help
        }), selected = switching == value, onClick = { onSelect(value) })
    }, initialItemId = switching.name)

@Composable
private fun frameRateSwitchingLabel(switching: FrameRateSwitching): String = stringResource(when (switching) {
    FrameRateSwitching.ALWAYS -> R.string.frame_rate_switching_always
    FrameRateSwitching.SEAMLESS_ONLY -> R.string.frame_rate_switching_seamless_only
    FrameRateSwitching.OFF -> R.string.frame_rate_switching_off
})

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

/** "Automatic • 1 s" with the learned level, or a fixed level in locale decimals ("0,5 s"). */
@Composable
internal fun startupBufferLabel(millis: Int, learnedMillis: Int): String =
    if (millis == STARTUP_BUFFER_AUTOMATIC) {
        stringResource(R.string.startup_buffer_automatic, startupBufferSeconds(learnedMillis))
    } else startupBufferSeconds(millis)

@Composable
private fun startupBufferSeconds(millis: Int): String {
    return stringResource(R.string.startup_buffer_seconds,
        at.bernhardberger.tvhplayer.ui.common.formatDecimal(millis / 1000.0, LocalConfiguration.current.locales[0], 0, 1))
}

internal const val STREAM_PROFILE_LEVEL = "stream-profile"
internal const val STREAM_PROFILE_DEFAULT = "profile-default"

@Composable
internal fun settingsStreamProfileLevel(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): DepthLevel {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return settingsStreamProfileLevel(ui) { profileId ->
        vm.onProfileSelected(profileId)
        navigation.pop()
    }
}

/**
 * The server's stream profiles, with Server default first. While the profiles aren't available
 * the page shows only why, so a page left open across a disconnect offers nothing stale.
 */
@Composable
internal fun settingsStreamProfileLevel(ui: SettingsPlayerUiState, onSelect: (StreamProfileId?) -> Unit): DepthLevel {
    val direct = stringResource(R.string.profile_direct_streaming)
    val profiles = ui.profiles as? StreamProfilesResult.Available
    val rows = if (profiles == null) {
        listOf(settingsRow("profiles-status", streamProfilesStatus(ui).orEmpty(), enabled = false))
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
        description = stringResource(R.string.stream_profile_description))
}

private fun streamProfileRowId(id: StreamProfileId) = "profile-${id.value}"

/** Why the profiles can't be chosen yet, or null once the server has listed them. */
@Composable
private fun streamProfilesStatus(ui: SettingsPlayerUiState): String? = when (ui.profiles) {
    StreamProfilesResult.Failed(TvheadendFailure.NOT_READY) ->
        stringResource(if (ui.connected) R.string.loading_wait else R.string.not_connected)
    is StreamProfilesResult.Available -> null
    else -> stringResource(R.string.stream_profiles_unavailable)
}

/** The chosen profile's name; a saved profile the server no longer lists reads as Server default. */
@Composable
private fun streamProfileLabel(ui: SettingsPlayerUiState): String {
    val selected = (ui.profiles as? StreamProfilesResult.Available)?.profiles?.firstOrNull { it.id == ui.selectedProfileId }
        ?: return stringResource(R.string.profile_server_default)
    return streamProfilePresentation(selected.name, stringResource(R.string.profile_direct_streaming)).primaryLabel
}

@Composable
internal fun settingsPlayerLevels(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): List<DepthLevel> {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return listOf(
        settingsPlayerLevel(ui, vm::onTimeshiftEnabledChanged, vm::onAutoStartPlaybackChanged, vm::onTunnelingEnabledChanged),
        settingsAudioLevel(ui, vm::onAudioPassthroughEnabledChanged, vm::onAudioDescriptionChanged),
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
): DepthLevel {
    val profilesStatus = streamProfilesStatus(ui)
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
        settingsRow(STREAM_PROFILE_LEVEL, stringResource(R.string.profile), profilesStatus ?: streamProfileLabel(ui),
            child = STREAM_PROFILE_LEVEL.takeIf { profilesStatus == null }, enabled = profilesStatus == null,
            section = stringResource(R.string.settings_section_streaming)),
        settingsRow(STARTUP_BUFFER_LEVEL, stringResource(R.string.startup_buffer_setting),
            startupBufferLabel(ui.startupBufferMillis, ui.startupBufferLearnedMillis), child = STARTUP_BUFFER_LEVEL),
        settingsRow(FRAME_RATE_LEVEL, stringResource(R.string.refresh_rate_matching_setting),
            frameRateSwitchingLabel(ui.frameRateSwitching), child = FRAME_RATE_LEVEL,
            section = stringResource(R.string.settings_section_picture)),
        settingsRow("tunneling", stringResource(R.string.tunneling_setting),
            supporting = stringResource(R.string.tunneling_setting_description), checked = ui.tunnelingEnabled,
            section = stringResource(R.string.settings_section_experimental),
            onClick = { onTunnelingEnabledChanged(!ui.tunnelingEnabled) }),
    ))
}
