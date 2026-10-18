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
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_FIXED_MILLIS
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import org.koin.androidx.compose.koinViewModel

internal const val KEEP_CHANNEL_LEVEL = "keep-channel"

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
    }, initialItemId = "keep-$minutes")

@Composable
private fun keepChannelLabel(minutes: Int): String = if (minutes == 0) stringResource(R.string.keep_channel_off)
    else at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes(minutes.toLong(), androidx.compose.ui.platform.LocalResources.current)

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
        settingsRow(value.name, frameRateSwitchingLabel(value), selected = switching == value, onClick = { onSelect(value) })
    }, initialItemId = switching.name,
        description = stringResource(R.string.frame_rate_switching_description))

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

/** "Automatic · 1 s" with the learned level, or a fixed level in locale decimals ("0,5 s"). */
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

@Composable
internal fun settingsPlayerLevels(navigation: DepthNavigationState, vm: SettingsPlayerViewModel = koinViewModel()): List<DepthLevel> {
    val ui by vm.ui.collectAsStateWithLifecycle()
    return listOf(settingsPlayerLevel(ui, vm::onTimeshiftEnabledChanged,
        vm::onProfileSelected, vm::onAudioPassthroughEnabledChanged, vm::onAudioDescriptionChanged,
        vm::onAutoStartPlaybackChanged)) +
        settingsAudioPreferenceLevels(ui,
            onAudioLanguageSelected = { slot, language -> vm.onAudioLanguageSelected(slot, language); navigation.pop() },
            onAudioFormatSelected = { vm.onAudioFormatSelected(it); navigation.pop() },
            onSubtitleLanguageSelected = { vm.onSubtitleLanguageSelected(it); navigation.pop() })
}

@Composable
internal fun settingsPlayerLevel(
    ui: SettingsPlayerUiState,
    onTimeshiftEnabledChanged: (Boolean) -> Unit,
    onProfileSelected: (StreamProfileId?) -> Unit,
    onAudioPassthroughEnabledChanged: (Boolean) -> Unit,
    onAudioDescriptionChanged: (Boolean) -> Unit = {},
    onAutoStartPlaybackChanged: (Boolean) -> Unit = {},
): DepthLevel {
    val direct = stringResource(R.string.profile_direct_streaming)
    val profileSection = stringResource(R.string.profile)
    return settingsLevel(SettingsSection.PLAYER.name, stringResource(R.string.settings_player), buildList {
        add(settingsRow("autoplay", stringResource(R.string.auto_start_playback), checked = ui.autoStartPlayback,
            onClick = { onAutoStartPlaybackChanged(!ui.autoStartPlayback) }))
        add(settingsRow("timeshift", stringResource(R.string.timeshift_setting), checked = ui.timeshiftEnabled,
            onClick = { onTimeshiftEnabledChanged(!ui.timeshiftEnabled) }))
        add(settingsRow(FRAME_RATE_LEVEL, stringResource(R.string.refresh_rate_matching_setting),
            frameRateSwitchingLabel(ui.frameRateSwitching), child = FRAME_RATE_LEVEL))
        add(settingsRow("audio-passthrough", stringResource(R.string.audio_passthrough_setting),
            supporting = if (ui.audioPassthroughChangeFailed) stringResource(R.string.audio_passthrough_failed)
                else stringResource(R.string.audio_passthrough_description), checked = ui.audioPassthroughEnabled,
            onClick = { onAudioPassthroughEnabledChanged(!ui.audioPassthroughEnabled) }))
        addAll(settingsAudioPreferenceRows(ui, onAudioDescriptionChanged))
        add(settingsRow(KEEP_CHANNEL_LEVEL, stringResource(R.string.keep_channel_setting),
            keepChannelLabel(ui.keepChannelMinutes), child = KEEP_CHANNEL_LEVEL, titleMaxLines = 2))
        add(settingsRow(STARTUP_BUFFER_LEVEL, stringResource(R.string.startup_buffer_setting),
            startupBufferLabel(ui.startupBufferMillis, ui.startupBufferLearnedMillis), child = STARTUP_BUFFER_LEVEL))
        when (val profiles = ui.profiles) {
            StreamProfilesResult.Failed(TvheadendFailure.NOT_READY) -> add(settingsRow("profiles-status",
                stringResource(if (ui.connected) R.string.loading_wait else R.string.not_connected), section = profileSection))
            is StreamProfilesResult.Available -> {
                add(settingsRow("profile-default", stringResource(R.string.profile_server_default), section = profileSection,
                    selected = ui.selectedProfileId == null, onClick = { onProfileSelected(null) }))
                profiles.profiles.forEach { profile ->
                    val presentation = streamProfilePresentation(profile.name, direct)
                    add(settingsRow("profile-${profile.id.value}", presentation.primaryLabel, presentation.secondaryLabel,
                        selected = profile.id == ui.selectedProfileId, onClick = { onProfileSelected(profile.id) }))
                }
            }
            else -> add(settingsRow("profiles-status", stringResource(R.string.stream_profiles_unavailable), section = profileSection))
        }
    })
}
