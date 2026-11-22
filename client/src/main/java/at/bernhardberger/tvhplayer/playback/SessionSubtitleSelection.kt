package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvhplayer.settings.CHANNEL_TRACK_CHOICE_LIMIT
import at.bernhardberger.tvhplayer.settings.RECORDING_TRACK_CHOICE_LIMIT
import at.bernhardberger.tvhplayer.settings.SubtitleChoice

private fun choice(format: Format) =
    SubtitleChoice.Track(format.language, format.sampleMimeType, format.roleFlags)

/** One configured profile, semantic choices only; no retained track groups. */
internal class SessionSubtitleSelection {
    private var profile: Any? = null
    private var target: AppPlaybackTarget? = null
    private val choices = LinkedHashMap<AppPlaybackTarget, SubtitleChoice>()
    private var restoring = false

    fun load(target: AppPlaybackTarget, choice: SubtitleChoice?) {
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
        choices.clear()
        reset(player)
    }

    fun onMediaItemTransition(player: Player) {
        target = null
        reset(player)
    }

    fun activate(target: AppPlaybackTarget, player: Player) {
        this.target = target
        restore(player)
    }

    fun rememberExplicitChoice(
        player: Player,
        explicitCommand: Boolean = false,
    ): Pair<AppPlaybackTarget, SubtitleChoice>? {
        val current = target?.takeUnless { profile == null || restoring } ?: return null
        val selected = explicitChoice(player)
        val pinRecording = explicitCommand && current is AppPlaybackTarget.Recording
        return if (selected != null && (choices[current] != selected || pinRecording)) {
            load(current, selected)
            current to selected
        } else null
    }

    private fun explicitChoice(player: Player): SubtitleChoice? {
        val parameters = player.trackSelectionParameters
        if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) return SubtitleChoice.Off
        return parameters.overrides.values.singleOrNull {
            it.type == C.TRACK_TYPE_TEXT && it.trackIndices.size == 1
        }?.let { override ->
            player.currentTracks.groups.singleOrNull {
                it.mediaTrackGroup === override.mediaTrackGroup
            }?.let { group ->
                val index = override.trackIndices.single()
                if (index in 0 until group.length && group.isTrackSupported(index)) {
                    choice(group.getTrackFormat(index))
                } else null
            }
        }
    }

    fun restore(player: Player) {
        val saved = choices[target] ?: return
        val parameters = player.trackSelectionParameters
        if (saved == SubtitleChoice.Off) {
            if (C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes) applySaved(player, saved, null)
        } else if (saved is SubtitleChoice.Track && !currentMatches(player, saved)) {
            val override = matchingOverride(player, saved)
            if (override == null) reset(player) else applySaved(player, saved, override)
        }
    }

    private fun applySaved(player: Player, saved: SubtitleChoice, override: TrackSelectionOverride?) {
        restoring = true
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, saved == SubtitleChoice.Off)
                .apply { override?.let(::addOverride) }
                .build()
        } finally {
            restoring = false
        }
    }

    fun useDefault(player: Player): AppPlaybackTarget? {
        val current = target
        if (current != null) load(current, SubtitleChoice.Default.takeIf { current is AppPlaybackTarget.Recording })
        reset(player)
        return current
    }

    fun clear(player: Player) {
        target = null
        choices.clear()
        profile = null
        reset(player)
    }

    private fun reset(player: Player) {
        // Enabling text or clearing an override is not an instruction to forget a saved choice.
        restoring = true
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        } finally {
            restoring = false
        }
    }
}

private fun currentMatches(player: Player, saved: SubtitleChoice.Track): Boolean {
    val parameters = player.trackSelectionParameters
    val current = parameters.overrides.values.singleOrNull { it.type == C.TRACK_TYPE_TEXT }
    val selected = current?.trackIndices?.singleOrNull()
    if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes || selected == null) return false
    return player.currentTracks.groups.any { group ->
        val supported = selected in 0 until group.length && group.isTrackSupported(selected)
        val sameGroup = group.type == C.TRACK_TYPE_TEXT && group.mediaTrackGroup === current.mediaTrackGroup
        sameGroup && supported && choice(group.getTrackFormat(selected)) == saved
    }
}

private fun matchingOverride(player: Player, saved: SubtitleChoice.Track): TrackSelectionOverride? {
    val matches = player.currentTracks.groups.flatMap { group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@flatMap emptyList()
        (0 until group.length).mapNotNull { index ->
            if (group.isTrackSupported(index) && choice(group.getTrackFormat(index)) == saved) {
                group to index
            } else null
        }
    }
    return matches.singleOrNull()?.let { (group, index) ->
        TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
    }
}
