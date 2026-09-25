package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice

/** One configured profile, at most 64 explicit channel choices, and no retained track groups. */
class SessionAudioSelection {
    private fun choice(format: Format) = AudioTrackChoice(
        format.language, format.sampleMimeType,
        format.roleFlags, format.channelCount, format.sampleRate,
    )

    private var profile: Any? = null
    private var channel: ChannelId? = null
    private val choices = LinkedHashMap<ChannelId, AudioTrackChoice>()
    private var restoring = false

    fun load(channelId: ChannelId, choice: AudioTrackChoice?) {
        choices.remove(channelId)
        if (choice != null) choices[channelId] = choice
        if (choices.size > 64) choices.remove(choices.keys.first())
    }

    fun useProfile(currentProfile: Any?, player: Player) {
        if (profile === currentProfile) return
        profile = currentProfile
        channel = null
        choices.clear()
        clearOverride(player)
    }

    fun onMediaItemTransition(player: Player) {
        channel = null
        clearOverride(player)
    }

    fun activate(channelId: ChannelId, player: Player) {
        channel = channelId
        restore(player)
    }

    fun rememberExplicitChoice(player: Player): Pair<ChannelId, AudioTrackChoice>? {
        val currentChannel = channel ?: return null
        if (profile == null || restoring) return null
        val override = player.trackSelectionParameters.overrides.values.singleOrNull {
            it.type == C.TRACK_TYPE_AUDIO && it.trackIndices.size == 1
        } ?: return null
        val group = player.currentTracks.groups.singleOrNull {
            it.mediaTrackGroup === override.mediaTrackGroup
        } ?: return null
        val index = override.trackIndices.single()
        if (index !in 0 until group.length || !group.isTrackSupported(index)) return null
        val selected = choice(group.getTrackFormat(index))
        if (choices[currentChannel] == selected) return null
        load(currentChannel, selected)
        return currentChannel to selected
    }

    fun restore(player: Player) {
        val choice = choices[channel] ?: return
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
            clearOverride(player)
            return
        }
        val (group, index) = match
        val current = player.trackSelectionParameters.overrides.values.singleOrNull {
            it.type == C.TRACK_TYPE_AUDIO
        }
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

    fun useAutomatic(player: Player): ChannelId? {
        val forgotten = channel
        if (forgotten != null) choices.remove(forgotten)
        clearOverride(player)
        return forgotten
    }

    fun clear(player: Player) {
        channel = null
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
