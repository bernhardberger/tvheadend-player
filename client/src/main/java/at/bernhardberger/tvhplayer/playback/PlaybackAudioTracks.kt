@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [AppPlaybackRuntime]'s audio tracks and output: the session audio choice and its stored
 * per-channel copy, automatic audio, track preferences, frame-rate matching and passthrough.
 * The runtime owns the lock, the active target and the audio interruption mute.
 */
internal class PlaybackAudioTracks(
    private val player: ExoPlayer,
    private val settings: PlayerSettingsStore,
    private val profileOwner: AppProfileOwner,
    private val scope: CoroutineScope,
    private val audioOutput: TvheadendAudioOutputProvider,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val audioSelection: SessionAudioSelection,
    private val _activeTarget: StateFlow<AppPlaybackTarget?>,
    private val targetInstallationInProgress: () -> Boolean,
    private val interruptionMuted: () -> Boolean,
    private val preserveInterruptionMute: () -> Unit,
) {
    private var audioOutputConfigured = false
    private var audioOutputChanging = false
    private val _audioPassthroughChangeFailed = MutableStateFlow(false)
    val audioPassthroughChangeFailed = _audioPassthroughChangeFailed.asStateFlow()
    private var audioWriteJob: Job? = null
    private val _audioAutomatic = MutableStateFlow(player.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO })
    val audioAutomatic = _audioAutomatic.asStateFlow()

    fun onMediaItemTransition() {
        if (!targetCommands.isOpen()) return
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        audioSelection.onMediaItemTransition(player)
    }

    fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
        if (!targetCommands.isOpen()) return
        _audioAutomatic.value = parameters.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO }
        if (interruptionMuted()) {
            preserveInterruptionMute()
            return
        }
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        val profileId = profileOwner.audioProfileId ?: return
        val selected = audioSelection.rememberExplicitChoice(player) ?: return
        val previous = audioWriteJob
        audioWriteJob = scope.launch {
            previous?.join()
            try {
                settings.audioChoices.write(profileId, selected.first, selected.second)
            } catch (_: java.io.IOException) {
                // A storage failure must not interrupt otherwise valid playback.
            }
        }
    }

    fun onTracksChanged() {
        if (targetInstallationInProgress() || audioOutputChanging || interruptionMuted() || !targetCommands.isOpen()) return
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        audioSelection.restore(player)
    }

    /**
     * Loads the stored audio choice for [channelId] before a live target is installed. Returns
     * the result that ends the start, or null to go on.
     */
    suspend fun loadStoredAudioLocked(channelId: ChannelId): PlaybackTargetResult? {
        val audioProfile = profileOwner.serverProfile.value
        val audioProfileId = profileOwner.audioProfileId
        audioWriteJob?.join()
        val storedAudio = try {
            audioProfileId?.let { settings.audioChoices.read(it, channelId) }
        } catch (_: java.io.IOException) {
            null
        }
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (profileOwner.serverProfile.value !== audioProfile || profileOwner.audioProfileId != audioProfileId) {
            return PlaybackTargetResult.NOT_READY
        }
        audioSelection.useProfile(audioProfile, player)
        audioSelection.load(channelId, storedAudio)
        return null
    }

    /** After a target installation, also a failed or cancelled one: the installed live channel's choice. */
    fun activateAudioAfterInstallLocked() {
        if (targetCommands.isOpen()) {
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            (_activeTarget.value as? AppPlaybackTarget.Live)?.takeUnless { interruptionMuted() }?.let {
                audioSelection.activate(it.channelId, player)
            }
        }
    }

    /** Detach: waits for the last stored-choice write. */
    suspend fun joinAudioWrites() {
        audioWriteJob?.join()
    }

    fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        targetCommands.runIfOpen {
            player.setVideoChangeFrameRateStrategy(
                if (enabled) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            )
        }
    }

    fun configureAudioOutputBeforeFirstTargetLocked(value: PlayerSettings) {
        targetCommands.runIfOpen {
            if (!audioOutputConfigured) {
                audioOutput.configurePassthrough(value.audioPassthroughEnabled)
                audioOutputConfigured = true
            }
        }
    }

    suspend fun applyAudioPassthroughLocked(enabled: Boolean) {
        if (!targetCommands.isOpen() || !audioOutputConfigured) return
        if (audioOutput.isPassthroughEnabled == enabled) {
            _audioPassthroughChangeFailed.value = false
            return
        }
        audioOutputChanging = true
        try {
            val applied = audioOutput.setPassthroughEnabled(player, enabled)
            if (targetCommands.isOpen()) _audioPassthroughChangeFailed.value = !applied
        } finally {
            audioOutputChanging = false
            // onTracksChanged restores the remembered choice using NEW mode capabilities.
            // currentTracks here can still describe the old mode and must not reapply an override.
        }
    }

    fun selectQuickListAudioLocked(override: androidx.media3.common.TrackSelectionOverride?) {
        if (override == null) {
            applyAutomaticAudioLocked()
        } else {
            // Keep the exact group identity for SessionAudioSelection's explicit-choice listener.
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
        }
    }

    fun applyAutomaticAudioLocked() {
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        val channel = audioSelection.useAutomatic(player)
        val profileId = profileOwner.audioProfileId
        if (channel != null && profileId != null) {
            // A pending explicit-choice write must finish before its removal is persisted.
            val previous = audioWriteJob
            audioWriteJob = scope.launch {
                previous?.join()
                try {
                    settings.audioChoices.remove(profileId, channel)
                } catch (_: java.io.IOException) {
                    // Storage failure must not interrupt playback; the session choice is still forgotten.
                }
            }
        }
    }

    fun applyPlayerSettingsLocked(value: PlayerSettings) {
        targetCommands.runIfOpen {
            player.trackSelectionParameters = trackPreferences(
                player.trackSelectionParameters, value,
            )
            player.setVideoChangeFrameRateStrategy(
                if (value.refreshRateMatchingEnabled) {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                } else {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                },
            )
        }
    }
}
