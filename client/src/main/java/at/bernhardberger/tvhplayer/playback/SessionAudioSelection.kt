package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import at.bernhardberger.tvhplayer.settings.AudioChoice
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice
import at.bernhardberger.tvhplayer.settings.CHANNEL_TRACK_CHOICE_LIMIT
import at.bernhardberger.tvhplayer.settings.RECORDING_TRACK_CHOICE_LIMIT

/** One configured profile, semantic choices only; no retained track groups. */
class SessionAudioSelection {
    private fun choice(format: Format) = AudioTrackChoice(
        format.language, format.sampleMimeType,
        format.roleFlags, format.channelCount, format.sampleRate,
    )

    private var profile: Any? = null
    private var target: AppPlaybackTarget? = null
    private val choices = LinkedHashMap<AppPlaybackTarget, AudioChoice>()
    private var restoring = false
    // Parameter callbacks can arrive after restore returns, including after a looser match.
    private var restoredRecordingChoice: AudioTrackChoice? = null

    fun load(target: AppPlaybackTarget, choice: AudioChoice?) {
        choices.remove(target)
        if (choice != null) choices[target] = choice
        val sameKind = choices.keys.filter { (it is AppPlaybackTarget.Live) == (target is AppPlaybackTarget.Live) }
        val limit = if (target is AppPlaybackTarget.Live) CHANNEL_TRACK_CHOICE_LIMIT else RECORDING_TRACK_CHOICE_LIMIT
        if (sameKind.size > limit) choices.remove(sameKind.first())
    }

    fun useProfile(currentProfile: Any?, player: Player) {
        if (profile === currentProfile) return
        profile = currentProfile
        target = null
        restoredRecordingChoice = null
        choices.clear()
        clearOverride(player)
    }

    fun onMediaItemTransition(player: Player) {
        target = null
        restoredRecordingChoice = null
        clearOverride(player)
    }

    fun activate(target: AppPlaybackTarget, player: Player, muted: Boolean = false) {
        this.target = target
        if (!muted) restore(player)
    }

    fun rememberExplicitChoice(
        player: Player,
        explicitCommand: Boolean = false,
        parameters: TrackSelectionParameters = player.trackSelectionParameters,
    ): Pair<AppPlaybackTarget, AudioTrackChoice>? {
        val currentTarget = target?.takeUnless { profile == null || restoring } ?: return null
        val recording = currentTarget is AppPlaybackTarget.Recording
        return explicitChoice(player, parameters)?.let { selected ->
            val changed = choices[currentTarget] != selected && (!recording || restoredRecordingChoice != selected)
            if (changed || recording && explicitCommand) {
                restoredRecordingChoice = null
                load(currentTarget, selected)
                currentTarget to selected
            } else null
        }
    }

    private fun explicitChoice(player: Player, parameters: TrackSelectionParameters): AudioTrackChoice? {
        val override = parameters.overrides.values.singleOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.trackIndices.size == 1
        } ?: return null
        val index = override.trackIndices.single()
        val group = player.currentTracks.groups.singleOrNull {
            it.mediaTrackGroup === override.mediaTrackGroup
        }
        return group?.takeIf { index in 0 until it.length && it.isTrackSupported(index) }
            ?.let { choice(it.getTrackFormat(index)) }
    }

    fun restore(player: Player) {
        val choice = choices[target] as? AudioTrackChoice ?: return
        val candidates = player.currentTracks.groups.flatMap { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@flatMap emptyList()
            (0 until group.length).mapNotNull { index ->
                if (group.isTrackSupported(index)) {
                    group to index
                } else null
            }
        }
        val exact = candidates.filter { (group, index) -> choice(group.getTrackFormat(index)) == choice }
        val match = if (exact.isNotEmpty()) exact.singleOrNull() else candidates.filter { (group, index) ->
            val candidate = choice(group.getTrackFormat(index))
            candidate.language == choice.language && candidate.mimeType == choice.mimeType &&
                candidate.roleFlags == choice.roleFlags
        }.singleOrNull()
        if (match == null) {
            val current = player.trackSelectionParameters.overrides.values.singleOrNull {
                it.type == C.TRACK_TYPE_AUDIO
            }
            val selected = current?.trackIndices?.singleOrNull()
            val currentMatches = selected != null && candidates.any { (group, index) ->
                    group.mediaTrackGroup === current?.mediaTrackGroup && index == selected &&
                        choice(group.getTrackFormat(index)) == choice
                }
            if (currentMatches) return
            restoredRecordingChoice = null
            clearOverride(player)
            return
        }
        val (group, index) = match
        val current = player.trackSelectionParameters.overrides.values.singleOrNull {
            it.type == C.TRACK_TYPE_AUDIO
        }
        restoredRecordingChoice = choice(group.getTrackFormat(index)).takeIf { target is AppPlaybackTarget.Recording }
        if (current?.mediaTrackGroup === group.mediaTrackGroup && current.trackIndices == listOf(index)) return
        clearOverride(player)
        restoring = true
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                .build()
        } finally {
            restoring = false
        }
    }

    fun useAutomatic(player: Player): AppPlaybackTarget? {
        val current = target
        if (current != null) load(current, AudioChoice.Automatic.takeIf { current is AppPlaybackTarget.Recording })
        restoredRecordingChoice = null
        clearOverride(player)
        return current
    }

    fun clear(player: Player) {
        target = null
        restoredRecordingChoice = null
        choices.clear()
        profile = null
        clearOverride(player)
    }

    private fun clearOverride(player: Player) {
        if (player.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO }) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
    }
}
