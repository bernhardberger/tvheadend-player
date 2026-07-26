package at.bernhardberger.tvhplayer.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import at.bernhardberger.tvhplayer.core.humanTrackLabel

data class UiTrack(
    val group: Tracks.Group,
    val trackIndexInGroup: Int,
    val label: String,
    val secondaryLabel: String? = null,
    val selected: Boolean = false,
)

fun collectTracks(
    tracks: Tracks,
    trackType: Int,
    unknownLanguageLabel: String = "Unknown language",
    monoLabel: String = "Mono",
    stereoLabel: String = "Stereo",
    surround51Label: String = "5.1",
    surround71Label: String = "7.1",
    channelsLabel: (Int) -> String = { "$it channels" },
): List<UiTrack> {
    val out = mutableListOf<UiTrack>()
    for (g in tracks.groups) {
        if (g.type != trackType) continue
        if (!g.isSupported) continue

        for (i in 0 until g.length) {
            if (!g.isTrackSupported(i)) continue
            val f = g.getTrackFormat(i)
            val role = when {
                (f.roleFlags and C.ROLE_FLAG_COMMENTARY) != 0 -> "Commentary"
                (f.roleFlags and C.ROLE_FLAG_ALTERNATE) != 0 -> "Alternate"
                (f.roleFlags and C.ROLE_FLAG_MAIN) != 0 -> null
                else -> null
            }
            val human = humanTrackLabel(
                languageCode = f.language,
                channelCount = f.channelCount.takeIf { it != Format.NO_VALUE },
                sampleRateHz = f.sampleRate.takeIf { it != Format.NO_VALUE },
                sampleMimeType = f.sampleMimeType,
                roleLabel = role,
                unknownLanguageLabel = unknownLanguageLabel,
                monoLabel = monoLabel,
                stereoLabel = stereoLabel,
                surround51Label = surround51Label,
                surround71Label = surround71Label,
                channelsLabel = channelsLabel,
                trackFallbackLabel = "Track ${i + 1}",
            )
            out += UiTrack(
                group = g,
                trackIndexInGroup = i,
                label = human.primary,
                secondaryLabel = human.secondary,
                selected = g.isTrackSelected(i),
            )
        }
    }
    return out
}

fun selectAudioTrack(player: Player, choice: UiTrack) {
    val params = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .addOverride(
            TrackSelectionOverride(
                choice.group.mediaTrackGroup,
                listOf(choice.trackIndexInGroup),
            ),
        )
        .build()

    player.trackSelectionParameters = params
}

fun selectTextTrack(player: Player, choice: UiTrack?) {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)

    if (choice == null) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(
                TrackSelectionOverride(
                    choice.group.mediaTrackGroup,
                    listOf(choice.trackIndexInGroup),
                ),
            )
    }

    player.trackSelectionParameters = builder.build()
}

fun selectedTrackLabel(
    tracks: Tracks,
    trackType: Int,
    noneLabel: String,
): String {
    val selected = collectTracks(tracks, trackType).firstOrNull { it.selected }
    return selected?.label ?: noneLabel
}
