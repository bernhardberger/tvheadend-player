package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage

/** Focus must rest this long on a quick-list row before that row is applied. */
internal const val PLAYBACK_QUICK_LIST_SETTLE_MS = 300L

/** A quick list closes this long after the last key press and keeps what is playing. */
internal const val PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS = 5_000L

/**
 * What the player screen tells an open Audio or Subtitles quick list: every key
 * down (restarting the auto-close), a repeated list key (focus one row down) and
 * Back (put back what was selected when the list opened).
 */
@Stable
internal class PlaybackQuickListSignals {
    var keyPresses by mutableIntStateOf(0)
        private set

    var moveDownRequests by mutableIntStateOf(0)
        private set

    private var restoreHandler: (() -> Unit)? = null

    fun onKeyDown() {
        keyPresses++
    }

    fun moveDown() {
        moveDownRequests++
    }

    /** Back: undo every row the open list applied. The caller closes the list. */
    fun restoreStart() {
        restoreHandler?.invoke()
    }

    internal fun bindRestore(handler: () -> Unit) {
        restoreHandler = handler
    }

    internal fun unbindRestore(handler: () -> Unit) {
        if (restoreHandler === handler) restoreHandler = null
    }
}

/**
 * Restore the selection, not the runtime's audio-interruption mute. Text's disabled
 * flag is the user's Off choice; audio's disabled flag belongs to the runtime.
 */
internal fun restoredTrackSelection(
    current: TrackSelectionParameters,
    start: TrackSelectionParameters,
    trackType: Int,
): TrackSelectionParameters = current.buildUpon()
    .clearOverridesOfType(trackType)
    .apply {
        if (trackType != C.TRACK_TYPE_AUDIO) {
            setTrackTypeDisabled(trackType, trackType in start.disabledTrackTypes)
        }
        start.overrides.values.filter { it.type == trackType }.forEach(::addOverride)
    }
    .build()

/**
 * Back on a quick list: put back the selection of [page] as it was in [start].
 * An automatic audio start goes back through [onAutomaticAudio] so the runtime also
 * forgets the explicit channel choice a previewed row made; a manual start gets its
 * exact override back, which the runtime remembers as it does for a picked row.
 */
internal fun restoreQuickListStart(
    player: Player,
    page: PlaybackOptionsPage,
    start: TrackSelectionParameters,
    onAutomaticAudio: () -> Unit,
) {
    val trackType = when (page) {
        PlaybackOptionsPage.AUDIO -> C.TRACK_TYPE_AUDIO
        PlaybackOptionsPage.SUBTITLES -> C.TRACK_TYPE_TEXT
        else -> return
    }
    val current = player.trackSelectionParameters
    if (
        trackType == C.TRACK_TYPE_AUDIO &&
        start.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO }
    ) {
        if (current.overrides.values.any { it.type == C.TRACK_TYPE_AUDIO }) onAutomaticAudio()
        return
    }
    val restored = restoredTrackSelection(current, start, trackType)
    if (restored != current) player.trackSelectionParameters = restored
}
