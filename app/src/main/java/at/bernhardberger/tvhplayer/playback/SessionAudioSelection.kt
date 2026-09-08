package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.settings.AudioTrackChoice

/** One configured profile, at most 64 explicit channel choices, and no retained track groups. */
internal class SessionAudioSelection {
    private fun choice(format: Format) = AudioTrackChoice(
        format.id, format.language, format.sampleMimeType,
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
        val matches = player.currentTracks.groups.flatMap { group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@flatMap emptyList()
            (0 until group.length).mapNotNull { index ->
                if (group.isTrackSupported(index) && choice(group.getTrackFormat(index)) == choice) {
                    group to index
                } else null
            }
        }
        val match = matches.singleOrNull()
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
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(index)))
                .build()
        } finally {
            restoring = false
        }
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
