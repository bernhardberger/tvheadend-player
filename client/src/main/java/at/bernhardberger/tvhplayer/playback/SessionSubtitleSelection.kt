package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.SubtitleChoice

/** Explicit live-channel choices; recordings keep only their current player parameters. */
internal class SessionSubtitleSelection {
    private fun choice(format: Format) =
        SubtitleChoice.Track(format.language, format.sampleMimeType, format.roleFlags)

    private var profile: Any? = null
    private var channel: ChannelId? = null
    private val choices = LinkedHashMap<ChannelId, SubtitleChoice>()
    private var restoring = false

    fun load(channelId: ChannelId, choice: SubtitleChoice?) {
        choices.remove(channelId)
        if (choice != null) choices[channelId] = choice
        if (choices.size > 64) choices.remove(choices.keys.first())
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
        val currentChannel = channel ?: return null
        if (profile == null || restoring) return null
        val parameters = player.trackSelectionParameters
        val selected = if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) {
            SubtitleChoice.Off
        } else {
            val override = parameters.overrides.values.singleOrNull {
                it.type == C.TRACK_TYPE_TEXT && it.trackIndices.size == 1
            } ?: return null
            val group = player.currentTracks.groups.singleOrNull {
                it.mediaTrackGroup === override.mediaTrackGroup
            } ?: return null
            val index = override.trackIndices.single()
            if (index !in 0 until group.length || !group.isTrackSupported(index)) return null
            choice(group.getTrackFormat(index))
        }
        if (choices[currentChannel] == selected) return null
        load(currentChannel, selected)
        return currentChannel to selected
    }

    fun restore(player: Player) {
        val saved = choices[channel] ?: return
        val parameters = player.trackSelectionParameters
        val override = when (saved) {
            SubtitleChoice.Off -> {
                if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) return
                null
            }
            is SubtitleChoice.Track -> {
                val current = parameters.overrides.values.singleOrNull { it.type == C.TRACK_TYPE_TEXT }
                val selected = current?.trackIndices?.singleOrNull()
                if (C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes && selected != null &&
                    player.currentTracks.groups.any { group ->
                        group.type == C.TRACK_TYPE_TEXT && group.mediaTrackGroup === current?.mediaTrackGroup &&
                            selected in 0 until group.length && group.isTrackSupported(selected) &&
                            choice(group.getTrackFormat(selected)) == saved
                    }
                ) return
                val matches = player.currentTracks.groups.flatMap { group ->
                    if (group.type != C.TRACK_TYPE_TEXT) return@flatMap emptyList()
                    (0 until group.length).mapNotNull { index ->
                        if (group.isTrackSupported(index) && choice(group.getTrackFormat(index)) == saved) {
                            group to index
                        } else null
                    }
                }
                val (group, index) = matches.singleOrNull() ?: run {
                    reset(player)
                    return
                }
                TrackSelectionOverride(group.mediaTrackGroup, listOf(index))
            }
        }
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
