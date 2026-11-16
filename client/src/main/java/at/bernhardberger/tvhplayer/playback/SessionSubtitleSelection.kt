package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.SubtitleChoice

private const val MAX_SUBTITLE_CHOICES = 64

private fun choice(format: Format) =
    SubtitleChoice.Track(format.language, format.sampleMimeType, format.roleFlags)

/** Explicit live-channel choices; recordings keep only their current player parameters. */
internal class SessionSubtitleSelection {
    private var profile: Any? = null
    private var channel: ChannelId? = null
    private val choices = LinkedHashMap<ChannelId, SubtitleChoice>()
    private var restoring = false

    fun load(channelId: ChannelId, choice: SubtitleChoice?) {
        choices.remove(channelId)
        if (choice != null) choices[channelId] = choice
        if (choices.size > MAX_SUBTITLE_CHOICES) choices.remove(choices.keys.first())
    }

    fun useProfile(currentProfile: Any?, player: Player) {
        if (profile === currentProfile) return
        profile = currentProfile
        channel = null
        choices.clear()
        reset(player)
    }

    fun onMediaItemTransition(player: Player) {
        channel = null
        reset(player)
    }

    fun activate(channelId: ChannelId, player: Player) {
        channel = channelId
        restore(player)
    }

    fun rememberExplicitChoice(player: Player): Pair<ChannelId, SubtitleChoice>? {
        val currentChannel = channel
        if (currentChannel == null || profile == null || restoring) return null
        val selected = explicitChoice(player)
        return if (selected != null && choices[currentChannel] != selected) {
            load(currentChannel, selected)
            currentChannel to selected
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
        val saved = choices[channel] ?: return
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

    fun useDefault(player: Player): ChannelId? {
        val forgotten = channel
        if (forgotten != null) choices.remove(forgotten)
        reset(player)
        return forgotten
    }

    fun clear(player: Player) {
        channel = null
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
