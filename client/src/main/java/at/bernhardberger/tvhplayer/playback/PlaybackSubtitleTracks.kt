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
    private var pendingRecordingSubtitles: (() -> Unit)? = null
    private val _subtitlesDefault = MutableStateFlow(usesDefaultSubtitles(player.trackSelectionParameters))
    val subtitlesDefault = _subtitlesDefault.asStateFlow()

    fun onMediaItemTransition() {
        if (!targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        selection.onMediaItemTransition(player)
    }

    fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters, explicitCommand: Boolean = false) {
        if (!targetCommands.isOpen()) return
        _subtitlesDefault.value = usesDefaultSubtitles(parameters)
        if (targetInstallationInProgress() || activeTarget.value == null) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        profileOwner.audioProfileId?.let { profileId ->
            selection.rememberExplicitChoice(player, explicitCommand)?.let { (target, choice) ->
                write(profileId, target, choice)
            }
        }
    }

    fun onTracksChanged() {
        if (targetInstallationInProgress() || !targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        selection.restore(player)
    }

    suspend fun loadStoredSubtitlesLocked(
        target: AppPlaybackTarget,
        recordingChannel: ChannelId? = null,
        canApply: () -> Boolean,
    ): PlaybackTargetResult? {
        pendingRecordingSubtitles = null
        val profile = profileOwner.serverProfile.value
        val profileId = profileOwner.audioProfileId
        writeJob?.join()
        val stored = try {
            profileId?.let {
                when (target) {
                    is AppPlaybackTarget.Live -> settings.subtitleChoices.read(it, target.channelId.value)
                    is AppPlaybackTarget.Recording -> settings.recordingSubtitleChoices
                        .read(it, target.recordingId.value)
                        ?: recordingChannel?.let { channel -> settings.subtitleChoices.read(it, channel.value) }
                }
            }
        } catch (_: IOException) {
            null
        }
        return when {
            !targetCommands.isOpen() -> PlaybackTargetResult.SHUT_DOWN
            profileOwner.serverProfile.value !== profile || profileOwner.audioProfileId != profileId || !canApply() ->
                PlaybackTargetResult.NOT_READY
            else -> {
                selection.useProfile(profile, player)
                if (target is AppPlaybackTarget.Live) selection.load(target, stored)
                else pendingRecordingSubtitles = pendingRecordingChoice(target, profile, profileId, stored)
                null
            }
        }
    }

    private fun pendingRecordingChoice(
        target: AppPlaybackTarget,
        profile: Any?,
        profileId: String?,
        stored: SubtitleChoice?,
    ): () -> Unit = {
        if (activeTarget.value == target && profileOwner.serverProfile.value === profile &&
            profileOwner.audioProfileId == profileId
        ) selection.load(target, stored)
    }

    fun activateAfterInstallLocked(committed: Boolean) {
        val pending = pendingRecordingSubtitles
        pendingRecordingSubtitles = null
        if (!targetCommands.isOpen()) return
        selection.useProfile(profileOwner.serverProfile.value, player)
        activeTarget.value?.let {
            if (committed) pending?.invoke()
            selection.activate(it, player)
        }
    }

    fun applyDefaultLocked() {
        selection.useProfile(profileOwner.serverProfile.value, player)
        val target = selection.useDefault(player)
        val profileId = profileOwner.audioProfileId
        if (target != null && profileId != null) write(profileId, target, SubtitleChoice.Default)
    }

    fun selectLocked(override: androidx.media3.common.TrackSelectionOverride?, off: Boolean) {
        if (override == null && !off) {
            applyDefaultLocked()
        } else {
            val parameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, off)
                .apply { override?.let(::addOverride) }
                .build()
            val changed = parameters != player.trackSelectionParameters
            if (changed) player.trackSelectionParameters = parameters
            if (!changed || activeTarget.value is AppPlaybackTarget.Recording) {
                onTrackSelectionParametersChanged(parameters, explicitCommand = true)
            }
        }
    }

    private fun write(profileId: String, target: AppPlaybackTarget, choice: SubtitleChoice) {
        val previous = writeJob
        writeJob = scope.launch {
            previous?.join()
            try {
                when (target) {
                    is AppPlaybackTarget.Live -> settings.subtitleChoices.set(
                        profileId, target.channelId.value, choice.takeUnless { it == SubtitleChoice.Default },
                    )
                    is AppPlaybackTarget.Recording ->
                        settings.recordingSubtitleChoices.set(profileId, target.recordingId.value, choice)
                }
            } catch (_: IOException) {
                // Storage cannot interrupt playback, including when forgetting a choice.
            }
        }
    }

    fun clearSessionLocked() = selection.clear(player)
    suspend fun joinWrites() { writeJob?.join() }
}

private fun usesDefaultSubtitles(parameters: TrackSelectionParameters) =
    C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes &&
        parameters.overrides.values.none { it.type == C.TRACK_TYPE_TEXT }
