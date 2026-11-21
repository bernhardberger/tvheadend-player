package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.touch.R

/** One line of a player list; [selected] is null for a command (Record) rather than a choice. */
internal class PanelEntry(val label: String, val detail: String?, val selected: Boolean?, val onClick: () -> Unit)

/** Builds the lines of one list; every pick reports itself and then runs [done]. */
private class EntryMaker(
    val onAction: (PlayerAction) -> Unit,
    val onChoice: (PlayerChoice) -> Unit,
    val done: () -> Unit,
) {
    fun command(label: String, detail: String?, action: PlayerAction) = PanelEntry(label, detail, null) {
        onAction(action)
        done()
    }

    fun choice(label: String, detail: String?, selected: Boolean, pick: PlayerChoice) =
        PanelEntry(label, detail, selected) {
            onChoice(pick)
            done()
        }
}

/** The lines of a list panel: the three menus and the Playback options pages. [done] runs after a pick. */
@Composable
internal fun panelEntries(
    panel: PlayerPanel,
    state: LivePlayerState,
    onAction: (PlayerAction) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
    done: () -> Unit,
): List<PanelEntry> {
    val make = EntryMaker(onAction, onChoice, done)
    val options = state.options
    return when (panel) {
        PlayerPanel.Record -> make.record(state)
        PlayerPanel.Audio -> make.audio(state.tracks)
        PlayerPanel.Subtitles -> make.subtitles(state.tracks)
        PlayerPanel.DisplayMode -> DisplayMode.entries.map {
            make.choice(stringResource(it.label), null, options.displayMode == it, PlayerChoice.Display(it))
        }
        PlayerPanel.Stats -> StatsLevel.entries.map {
            make.choice(stringResource(it.label), null, options.stats == it, PlayerChoice.Stats(it))
        }
        PlayerPanel.Profile -> make.profiles(options)
        PlayerPanel.Channels, PlayerPanel.Options, PlayerPanel.Info, PlayerPanel.InfoDetails -> emptyList()
    }
}

@Composable
private fun EntryMaker.record(state: LivePlayerState) = listOf(
    command(
        stringResource(R.string.player_record),
        stringResource(R.string.player_record_program_detail, playerClock(state.timeline, state.timeline.duration)),
        PlayerAction.RecordProgram,
    ),
    command(
        stringResource(R.string.player_record_series),
        stringResource(R.string.player_record_series_detail),
        PlayerAction.RecordSeries,
    ),
    command(
        stringResource(R.string.player_record_all),
        state.channel.now?.title?.let { stringResource(R.string.player_record_all_detail, it) },
        PlayerAction.RecordAll,
    ),
)

@Composable
private fun EntryMaker.audio(tracks: PlayerTracks): List<PanelEntry> = buildList {
    val playing = tracks.playingAudio?.let(tracks.audio::getOrNull)
    add(
        choice(
            stringResource(R.string.player_audio_automatic),
            playing?.let { stringResource(R.string.player_audio_playing, details(it.language, it.layout)) },
            tracks.audioPick == TrackPick.Default,
            PlayerChoice.Audio(TrackPick.Default),
        ),
    )
    val description = stringResource(R.string.player_audio_description)
    tracks.audio.forEachIndexed { index, track ->
        val pick = TrackPick.Track(index)
        val selected = tracks.audioPick == pick
        val entry = if (track.audioDescription) {
            choice(description, details(track.language, track.layout, track.format), selected, PlayerChoice.Audio(pick))
        } else {
            choice(track.language, details(track.layout, track.format), selected, PlayerChoice.Audio(pick))
        }
        add(entry)
    }
}

@Composable
private fun EntryMaker.subtitles(tracks: PlayerTracks): List<PanelEntry> = buildList {
    val off = stringResource(R.string.player_off)
    val default = stringResource(R.string.player_option_default, tracks.subtitlesSetting ?: off)
    add(choice(default, null, tracks.subtitlePick == TrackPick.Default, PlayerChoice.Subtitles(TrackPick.Default)))
    add(choice(off, null, tracks.subtitlePick == TrackPick.Off, PlayerChoice.Subtitles(TrackPick.Off)))
    tracks.subtitles.forEachIndexed { index, track ->
        val pick = TrackPick.Track(index)
        add(choice(track.language, track.format, tracks.subtitlePick == pick, PlayerChoice.Subtitles(pick)))
    }
}

@Composable
private fun EntryMaker.profiles(options: PlaybackOptions): List<PanelEntry> = buildList {
    val default = stringResource(
        R.string.player_option_default,
        profileName(options.profiles.getOrNull(options.defaultProfile)),
    )
    add(choice(default, null, options.profilePick == null, PlayerChoice.Profile(null)))
    options.profiles.forEachIndexed { index, profile ->
        val detail = profile.name.takeIf { profile.direct }
        add(choice(profileName(profile), detail, options.profilePick == index, PlayerChoice.Profile(index)))
    }
}

private fun details(vararg parts: String?) = parts.filterNotNull().joinToString(" • ")

@Composable
internal fun profileName(profile: StreamProfile?) = when {
    profile == null -> ""
    profile.direct -> stringResource(R.string.player_direct_streaming)
    else -> profile.name
}

internal val DisplayMode.label: Int
    get() = when (this) {
        DisplayMode.Auto -> R.string.player_display_auto
        DisplayMode.Fill16By9 -> R.string.player_display_16_9
        DisplayMode.Fill4By3 -> R.string.player_display_4_3
    }

internal val StatsLevel.label: Int
    get() = when (this) {
        StatsLevel.Off -> R.string.player_off
        StatsLevel.StreamPath -> R.string.player_stats_path
        StatsLevel.Debug -> R.string.player_stats_debug
    }
