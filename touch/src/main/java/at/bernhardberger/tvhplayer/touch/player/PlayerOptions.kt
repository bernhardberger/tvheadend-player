@file:OptIn(UnstableApi::class)

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.StreamProfileId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.core.HumanTrackLabel
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.ui.player.UiTrack
import at.bernhardberger.tvhplayer.ui.player.collectTracks
import at.bernhardberger.tvhplayer.ui.player.rememberTargetAspect
import java.util.Locale
import at.bernhardberger.tvheadend.sdk.core.StreamProfile as ServerProfile
import at.bernhardberger.tvhplayer.presentation.R as SharedR

/**
 * The Audio and Subtitles menus as the TV's lists: Automatic ahead of the audio tracks, Default and
 * Off ahead of the subtitle tracks. Automatic marks only itself; a track marks itself only when
 * the viewer picked it (an override), as on the TV.
 */
internal fun playerTracks(
    audio: List<HumanTrackLabel>,
    playingAudio: Int?,
    subtitles: List<HumanTrackLabel>,
    pickedSubtitle: Int?,
    pick: TrackSettings,
    subtitlesSetting: String?,
): PlayerTracks = PlayerTracks(
    audio = audio,
    audioPick = if (pick.audioAutomatic) TrackPick.Default else playingAudio?.let(TrackPick::Track),
    playingAudio = playingAudio,
    subtitles = subtitles,
    subtitlePick = when {
        pick.subtitlesDefault -> TrackPick.Default
        pick.subtitlesOff -> TrackPick.Off
        else -> pickedSubtitle?.let(TrackPick::Track)
    },
    subtitlesSetting = subtitlesSetting,
)

/** The runtime's audio and subtitle settings in effect for the playing picture. */
internal data class TrackSettings(
    val audioAutomatic: Boolean,
    val subtitlesDefault: Boolean,
    val subtitlesOff: Boolean,
)

/**
 * Playback options always open (display mode, and a channel's profile is the way out of a bad
 * one). Audio opens with another track to choose (the TV's quick-list rule), Subtitles with a
 * track; both only while the shown target's own picture plays ([targetCurrent]).
 */
internal fun optionControls(audioTracks: Int, subtitleTracks: Int, targetCurrent: Boolean): Set<PlayerControl> =
    buildSet {
        add(PlayerControl.Settings)
        if (targetCurrent && audioTracks > 1) add(PlayerControl.Audio)
        if (targetCurrent && subtitleTracks > 0) add(PlayerControl.Subtitles)
    }

/** Display mode for either target; only live has stream profiles. */
internal fun playbackOptions(
    target: AppPlaybackTarget,
    listed: List<ServerProfile>,
    setting: StreamProfileId?,
    choice: String?,
    mode: AspectRatioMode,
): PlaybackOptions {
    val profiles = listed.takeIf { target is AppPlaybackTarget.Live }.orEmpty()
    return PlaybackOptions(
        displayMode = mode.displayMode,
        profiles = profiles.map { StreamProfile(it.name, direct = it.name.trim().equals(DIRECT_PROFILE, true)) },
        defaultProfile = profiles.indexOfFirst { it.id == setting }.takeIf { it >= 0 },
        profilePick = profiles.indexOfFirst { it.id.value == choice }.takeIf { it >= 0 },
        // The TV's stream statistics overlay is its own; touch has none to switch.
        stats = null,
    )
}

internal val AspectRatioMode.displayMode: DisplayMode
    get() = when (this) {
        AspectRatioMode.FIT -> DisplayMode.Auto
        AspectRatioMode.FORCE_16_9 -> DisplayMode.Fill16By9
        AspectRatioMode.FORCE_4_3 -> DisplayMode.Fill4By3
    }

internal val DisplayMode.aspectMode: AspectRatioMode
    get() = when (this) {
        DisplayMode.Auto -> AspectRatioMode.FIT
        DisplayMode.Fill16By9 -> AspectRatioMode.FORCE_16_9
        DisplayMode.Fill4By3 -> AspectRatioMode.FORCE_4_3
    }

private fun UiTrack.lines() = HumanTrackLabel(label, headline, overline, secondaryLabel)

private fun UiTrack.picked(override: TrackSelectionOverride?) =
    override != null && override.mediaTrackGroup == group.mediaTrackGroup && trackIndexInGroup in override.trackIndices

private fun UiTrack.override() = TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndexInGroup))

/** The menus' state and what a pick in them does; [choose] answers false for a choice it does not own. */
internal class PlayerOptions(
    val tracks: PlayerTracks,
    val options: PlaybackOptions,
    val offered: Set<PlayerControl>,
    /** The display mode the picture and its window are drawn in. */
    val aspect: AspectRatioMode,
    val choose: (PlayerChoice) -> Boolean,
)

/**
 * The runtime's tracks and options for [shown]. Picks go through the runtime as the TV's quick
 * lists send them: tied to the picture's epoch, so a pick can't land on a different target.
 */
@Composable
internal fun rememberPlayerOptions(
    runtime: AppPlaybackRuntime,
    settings: PlayerSettingsStore,
    shown: AppPlaybackTarget,
    playing: Boolean,
    recordingChannel: (DvrEntryId) -> ChannelId? = { null },
): PlayerOptions {
    val (tracks, parameters) = rememberTrackState(runtime.player).value
    val audioAutomatic by runtime.audioAutomatic.collectAsStateWithLifecycle()
    val subtitlesDefault by runtime.subtitlesDefault.collectAsStateWithLifecycle()
    val language by runtime.subtitleLanguage.collectAsStateWithLifecycle()
    val profiles by runtime.streamProfiles.collectAsStateWithLifecycle()
    val setting by runtime.selectedStreamProfileId.collectAsStateWithLifecycle()
    val choices by runtime.channelStreamProfiles.collectAsStateWithLifecycle()
    val target by runtime.activeTarget.collectAsStateWithLifecycle()
    val aspect = rememberTargetAspect(target, settings, recordingChannel)
    val epoch = runtime.videoPresentation.collectAsStateWithLifecycle().value.epoch
    val (audio, subtitles) = trackLists(tracks)
    val locale = LocalConfiguration.current.locales[0]
    val subtitlesSetting = language?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }
        ?: stringResource(SharedR.string.subtitle_follow_system_short)
    val current = playing && target == shown && runtime.isQuickListTargetCurrent(epoch)
    val live = shown as? AppPlaybackTarget.Live
    val listed = (profiles as? StreamProfilesResult.Available)?.profiles.orEmpty()
    val subtitleOverride = parameters.overrides.values.singleOrNull { it.type == C.TRACK_TYPE_TEXT }
    return PlayerOptions(
        tracks = playerTracks(
            audio.map(UiTrack::lines), audio.indexOfFirst(UiTrack::selected).takeIf { it >= 0 },
            subtitles.map(UiTrack::lines), subtitles.indexOfFirst { it.picked(subtitleOverride) }.takeIf { it >= 0 },
            TrackSettings(audioAutomatic, subtitlesDefault, C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes),
            subtitlesSetting,
        ),
        options = playbackOptions(shown, listed, setting, choices?.get(live?.channelId?.value), aspect.mode),
        offered = optionControls(audio.size, subtitles.size, current),
        aspect = aspect.mode,
    ) { choice ->
        when (choice) {
            is PlayerChoice.Audio -> runtime.selectQuickListAudio(epoch, audio.pickOverride(choice.pick))
            is PlayerChoice.Subtitles ->
                runtime.selectSubtitles(subtitles.pickOverride(choice.pick), choice.pick == TrackPick.Off, epoch)
            is PlayerChoice.Profile -> if (live != null) {
                runtime.selectChannelStreamProfile(live.channelId, choice.index?.let { listed[it].id })
            } else {
                return@PlayerOptions false
            }
            is PlayerChoice.Display -> aspect.select(choice.mode.aspectMode)
            else -> return@PlayerOptions false
        }
        true
    }
}

private fun List<UiTrack>.pickOverride(pick: TrackPick) =
    (pick as? TrackPick.Track)?.let { getOrNull(it.index) }?.override()

/** The player's tracks and selection parameters, re-read once the listener is on so no change slips past. */
@Composable
private fun rememberTrackState(player: Player): State<Pair<Tracks, TrackSelectionParameters>> =
    produceState(player.currentTracks to player.trackSelectionParameters, player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                value = tracks to player.trackSelectionParameters
            }

            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
                value = player.currentTracks to parameters
            }
        }
        player.addListener(listener)
        value = player.currentTracks to player.trackSelectionParameters
        awaitDispose { player.removeListener(listener) }
    }

/** Audio and subtitle tracks labelled by the shared rule with the shared strings, as the TV labels them. */
@Composable
private fun trackLists(tracks: Tracks): Pair<List<UiTrack>, List<UiTrack>> {
    val unknown = stringResource(SharedR.string.track_unknown_language)
    val multiple = stringResource(SharedR.string.track_multiple_languages)
    val mono = stringResource(SharedR.string.track_mono)
    val stereo = stringResource(SharedR.string.track_stereo)
    val surround51 = stringResource(SharedR.string.track_surround_5_1)
    val surround71 = stringResource(SharedR.string.track_surround_7_1)
    val channels = stringResource(SharedR.string.track_channels_count)
    val description = stringResource(SharedR.string.audio_description)
    val clearDialogue = stringResource(SharedR.string.track_clear_dialogue)
    val locale = LocalConfiguration.current.locales[0]
    return remember(tracks, unknown, locale) {
        fun collect(type: Int) = collectTracks(
            tracks, type, unknown, multiple, mono, stereo, surround51, surround71,
            channelsLabel = { channels.format(locale, it) },
            audioDescriptionLabel = description, clearDialogueLabel = clearDialogue,
        )
        collect(C.TRACK_TYPE_AUDIO) to collect(C.TRACK_TYPE_TEXT)
    }
}

private const val DIRECT_PROFILE = "htsp"
