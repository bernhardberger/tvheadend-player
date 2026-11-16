@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.SubtitleChoice
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Runtime-owned subtitle state. The runtime serializes target changes and Default commands. */
internal class PlaybackSubtitleTracks(
    private val player: ExoPlayer,
    private val settings: PlayerSettingsStore,
    private val profileOwner: AppProfileOwner,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val activeTarget: StateFlow<AppPlaybackTarget?>,
    private val targetInstallationInProgress: () -> Boolean,
) {
    private val selection = SessionSubtitleSelection()
    private var writeJob: Job? = null
    private fun isDefault(parameters: TrackSelectionParameters) =
        C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes &&
            parameters.overrides.values.none { it.type == C.TRACK_TYPE_TEXT }
    private val _subtitlesDefault = MutableStateFlow(isDefault(player.trackSelectionParameters))
    val subtitlesDefault = _subtitlesDefault.asStateFlow()

    fun onMediaItemTransition() {
        if (!targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        selection.onMediaItemTransition(player)
    }

    fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
        if (!targetCommands.isOpen()) return
        _subtitlesDefault.value = isDefault(parameters)
        selection.useProfile(profileOwner.serverProfile.value, player)
        val profileId = profileOwner.audioProfileId
        if (profileId != null) {
            selection.rememberExplicitChoice(player)?.let { (channel, choice) -> write(profileId, channel, choice) }
        }
    }

    fun onTracksChanged() {
        if (targetInstallationInProgress() || !targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        selection.restore(player)
    }

    suspend fun loadStoredSubtitlesLocked(channelId: ChannelId, canApply: () -> Boolean): PlaybackTargetResult? {
        val profile = profileOwner.serverProfile.value
        val profileId = profileOwner.audioProfileId
        writeJob?.join()
        val stored = try {
            profileId?.let { settings.subtitleChoices.read(it, channelId.value) }
        } catch (_: IOException) {
            null
        }
        return when {
            !targetCommands.isOpen() -> PlaybackTargetResult.SHUT_DOWN
            profileOwner.serverProfile.value !== profile || profileOwner.audioProfileId != profileId || !canApply() ->
                PlaybackTargetResult.NOT_READY
            else -> {
                selection.useProfile(profile, player)
                selection.load(channelId, stored)
                null
            }
        }
    }

    fun activateAfterInstallLocked() {
        if (!targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        (activeTarget.value as? AppPlaybackTarget.Live)?.let { selection.activate(it.channelId, player) }
    }

    fun applyDefaultLocked() {
        selection.useProfile(profileOwner.serverProfile.value, player)
        val channel = selection.useDefault(player)
        val profileId = profileOwner.audioProfileId
        if (channel != null && profileId != null) write(profileId, channel, null)
    }

    fun selectLocked(override: androidx.media3.common.TrackSelectionOverride?, off: Boolean) {
        if (override == null && !off) {
            applyDefaultLocked()
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, off)
                .apply { override?.let(::addOverride) }
                .build()
        }
    }

    private fun write(profileId: String, channel: ChannelId, choice: SubtitleChoice?) {
        val previous = writeJob
        writeJob = scope.launch {
            previous?.join()
            try {
                settings.subtitleChoices.set(profileId, channel.value, choice)
            } catch (_: IOException) {
                // Storage cannot interrupt playback, including when forgetting a choice.
            }
        }
    }

    fun clearSessionLocked() = selection.clear(player)
    suspend fun joinWrites() { writeJob?.join() }
}
