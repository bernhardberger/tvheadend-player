@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.AudioChoice
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
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
 * per-target copy, automatic audio, track preferences, frame-rate matching and passthrough.
 * It is the only user of the [SessionAudioSelection]; the runtime and the audio interruption
 * controller change it through the methods here. The runtime owns the lock, the active target
 * and the audio interruption mute.
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
    private val frameRate: PlaybackFrameRate,
) {
    private var audioOutputConfigured = false
    private var audioOutputChanging = false
    private val _audioPassthroughChangeFailed = MutableStateFlow(false)
    val audioPassthroughChangeFailed = _audioPassthroughChangeFailed.asStateFlow()
    private var audioWriteJob: Job? = null
    private var pendingRecordingAudio: (() -> Unit)? = null
    private var storedTunneling: Boolean? = null
    private val _tunnelingFallbackActive = MutableStateFlow(false)
    val tunnelingFallbackActive = _tunnelingFallbackActive.asStateFlow()
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
        rememberAudioChoice()
    }

    fun onTracksChanged() {
        if (targetInstallationInProgress() || audioOutputChanging || interruptionMuted() || !targetCommands.isOpen()) return
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        audioSelection.restore(player)
    }

    /**
     * Loads the stored audio choice for [target] before it is installed. Returns
     * the result that ends the start, or null to go on.
     */
    suspend fun loadStoredAudioLocked(
        target: AppPlaybackTarget,
        recordingChannel: ChannelId? = null,
        canApply: () -> Boolean,
    ): PlaybackTargetResult? {
        pendingRecordingAudio = null
        val audioProfile = profileOwner.serverProfile.value
        val audioProfileId = profileOwner.audioProfileId
        audioWriteJob?.join()
        val storedAudio = try {
            audioProfileId?.let { profileId ->
                when (target) {
                    is AppPlaybackTarget.Live -> settings.audioChoices.read(profileId, target.channelId.value)
                    is AppPlaybackTarget.Recording -> settings.recordingAudioChoices
                        .read(profileId, target.recordingId.value)
                        ?: recordingChannel?.let { settings.audioChoices.read(profileId, it.value) }
                }
            }
        } catch (_: java.io.IOException) {
            null
        }
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (profileOwner.serverProfile.value !== audioProfile || profileOwner.audioProfileId != audioProfileId) {
            return PlaybackTargetResult.NOT_READY
        }
        if (!canApply()) return PlaybackTargetResult.NOT_READY
        audioSelection.useProfile(audioProfile, player)
        if (target is AppPlaybackTarget.Live) audioSelection.load(target, storedAudio)
        else pendingRecordingAudio = pendingRecordingChoice(target, audioProfile, audioProfileId, storedAudio)
        return null
    }

    // A failed restart of the same recording must leave its current choice in place.
    private fun pendingRecordingChoice(
        target: AppPlaybackTarget,
        profile: Any?,
        profileId: String?,
        stored: AudioChoice?,
    ): () -> Unit = {
        if (_activeTarget.value == target && profileOwner.serverProfile.value === profile &&
            profileOwner.audioProfileId == profileId
        ) audioSelection.load(target, stored)
    }

    /** After a target installation, also a failed or cancelled one: the installed target's choice. */
    fun activateAudioAfterInstallLocked(committed: Boolean) {
        val pending = pendingRecordingAudio
        pendingRecordingAudio = null
        if (targetCommands.isOpen()) {
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            _activeTarget.value?.let {
                if (committed) pending?.invoke()
                audioSelection.activate(it, player, muted = interruptionMuted())
            }
        }
    }

    /**
     * The interruption mute ended: restores the session audio choice to the unmuted tracks, unless
     * a target installation runs (it activates the choice itself when it ends).
     *
     * Unlike [onTracksChanged], this needs no [audioOutputChanging] check: an interruption ends
     * only while the command lock is held (a serialized command, or detach's final idle wait), and
     * [applyAudioPassthroughLocked] holds the command lock for the whole output change, so the two
     * never overlap.
     */
    fun restoreAudioAfterInterruptionLocked() {
        if (!targetInstallationInProgress()) audioSelection.restore(player)
    }

    /** Detach, inside the runtime's final idle wait: forgets the session audio choices. */
    fun clearSessionAudioLocked() {
        audioSelection.clear(player)
    }

    /** Detach: waits for the last stored-choice write. */
    suspend fun joinAudioWrites() {
        audioWriteJob?.join()
    }

    fun setFrameRateSwitching(switching: FrameRateSwitching) {
        targetCommands.runIfOpen { frameRate.setSwitching(switching) }
    }

    fun configureAudioOutputBeforeFirstTargetLocked(enabled: Boolean) {
        targetCommands.runIfOpen {
            if (!audioOutputConfigured) {
                audioOutput.configurePassthrough(enabled)
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

    fun selectAudioLocked(override: androidx.media3.common.TrackSelectionOverride?, enableAudio: Boolean) {
        if (override == null) {
            applyAutomaticAudioLocked()
        } else {
            // Keep the exact group identity for SessionAudioSelection's explicit-choice listener.
            val parameters = player.trackSelectionParameters.buildUpon()
                .apply { if (enableAudio && !interruptionMuted()) setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false) }
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
            val changed = parameters != player.trackSelectionParameters
            if (changed) player.trackSelectionParameters = parameters
            if (_activeTarget.value is AppPlaybackTarget.Recording) {
                // The mute owner keeps audio disabled; the command still owns the choice.
                rememberAudioChoice(explicitCommand = true, parameters = parameters)
            } else if (!changed && !interruptionMuted()) {
                rememberAudioChoice()
            }
        }
    }

    fun applyAutomaticAudioLocked() {
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        val target = audioSelection.useAutomatic(player)
        val profileId = profileOwner.audioProfileId
        if (target != null && profileId != null) write(profileId, target, AudioChoice.Automatic)
    }

    private fun rememberAudioChoice(
        explicitCommand: Boolean = false,
        parameters: TrackSelectionParameters = player.trackSelectionParameters,
    ) {
        if (targetInstallationInProgress() || audioOutputChanging || _activeTarget.value == null) return
        audioSelection.useProfile(profileOwner.serverProfile.value, player)
        val profileId = profileOwner.audioProfileId ?: return
        audioSelection.rememberExplicitChoice(player, explicitCommand, parameters)?.let { (target, choice) ->
            write(profileId, target, choice)
        }
    }

    private fun write(profileId: String, target: AppPlaybackTarget, choice: AudioChoice) {
        val previous = audioWriteJob
        audioWriteJob = scope.launch {
            previous?.join()
            try {
                when (target) {
                    is AppPlaybackTarget.Live ->
                        settings.audioChoices.set(profileId, target.channelId.value, choice as? AudioTrackChoice)
                    is AppPlaybackTarget.Recording ->
                        settings.recordingAudioChoices.set(profileId, target.recordingId.value, choice)
                }
            } catch (_: java.io.IOException) {
                // A storage failure must not interrupt otherwise valid playback.
            }
        }
    }

    /** Observe switches even while a serialized installation or settings application is suspended. */
    fun observeTunnelingSetting(enabled: Boolean) {
        targetCommands.runIfOpen {
            if (storedTunneling != null && storedTunneling != enabled) _tunnelingFallbackActive.value = false
            storedTunneling = enabled
        }
    }

    fun tunnelingState(): AppTunnelingState = when {
        _tunnelingFallbackActive.value -> AppTunnelingState.OFF_AFTER_AUDIO_ERROR
        storedTunneling != true -> AppTunnelingState.OFF
        player.isTunnelingEnabled -> AppTunnelingState.ON
        else -> AppTunnelingState.REQUESTED_NOT_ACTIVE
    }

    fun applyPlayerSettingsLocked(value: PlayerSettings) {
        targetCommands.runIfOpen {
            player.trackSelectionParameters = trackPreferences(
                player.trackSelectionParameters,
                value.copy(tunnelingEnabled = value.tunnelingEnabled && !_tunnelingFallbackActive.value),
            )
            frameRate.setSwitching(value.frameRateSwitching)
        }
    }

    /** Device output rejection is run-local; only changing the stored switch opts back in. */
    fun noteTunnelingRefused() {
        player.trackSelectionParameters = player.trackSelectionParameters.withTunneling(false)
        _tunnelingFallbackActive.value = true
    }
}
