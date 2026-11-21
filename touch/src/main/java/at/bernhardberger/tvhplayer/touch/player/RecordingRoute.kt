package at.bernhardberger.tvhplayer.touch.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.RecordingFinishedAction
import at.bernhardberger.tvhplayer.core.recordingFinishedAction
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal sealed interface PlayerRoute

internal data class RecordingRoute(val id: DvrEntryId, val start: RecordingPlaybackStart) : PlayerRoute {
    fun intent(context: Context): Intent = Intent(context, PlayerActivity::class.java)
        .putExtra(EXTRA_RECORDING, id.value)
        .putExtra(EXTRA_START_OVER, start == RecordingPlaybackStart.START_OVER)
}

internal fun Intent.recordingRoute(): RecordingRoute? =
    getLongExtra(EXTRA_RECORDING, -1L).takeIf { it >= 0 }?.let {
        RecordingRoute(DvrEntryId(it), recordingStart(getBooleanExtra(EXTRA_START_OVER, false)))
    }

internal fun recordingStart(startOver: Boolean): RecordingPlaybackStart =
    if (startOver) RecordingPlaybackStart.START_OVER else RecordingPlaybackStart.RESUME

internal fun startsRecording(route: RecordingRoute, active: AppPlaybackTarget?, playback: AppPlaybackState): Boolean =
    route.start == RecordingPlaybackStart.START_OVER || playingRecordingId(active, playback) != route.id

@OptIn(UnstableApi::class)
internal suspend fun restoreRecording(
    runtime: AppPlaybackRuntime,
    session: TvheadendSession,
    route: RecordingRoute,
    inWindow: StateFlow<Boolean>,
) = combine(session.observation, inWindow) { observation, window ->
    currentRecordingPlaybackSelection(observation, route.id) to window
}.distinctUntilChanged().collect { (selection, window) ->
    if (!window && selection != null) {
        val failure = runtime.state.value as? AppPlaybackState.Failed
        // The initial viewer start already published this result; don't retry a rejected click.
        if (failure?.target != AppPlaybackTarget.Recording(route.id) ||
            runtime.recordingSelection.value?.currentSession !== selection.currentSession
        ) runtime.restoreRecordingRoute(selection, route.start)
    }
}

@OptIn(UnstableApi::class)
internal fun finishRecordingAtEof(
    scope: CoroutineScope,
    runtime: AppPlaybackRuntime,
    route: StateFlow<PlayerRoute>,
) = scope.launch {
    combine(runtime.state, runtime.activeTarget) { playback, target -> playback to target }
        .collect { (playback, target) ->
            if (runtime.state.value !== playback || runtime.activeTarget.value != target) return@collect
            val id = (target as? AppPlaybackTarget.Recording)?.recordingId
            if (recordingFinishedAction(playback == AppPlaybackState.Finished, id, false) ==
                RecordingFinishedAction.NONE
            ) return@collect
            val epoch = runtime.videoPresentation.value.epoch
            val endedRoute = route.value
            // A replacement can own the serializer while this EOF is delivered. Recheck ownership
            // there, before stopping playback or letting sessionStops close the player.
            runtime.stopFromSession {
                runtime.activeTarget.value == target &&
                    runtime.state.value == AppPlaybackState.Finished &&
                    runtime.videoPresentation.value.epoch == epoch &&
                    route.value == endedRoute
            }
        }
}

private const val EXTRA_RECORDING = "recording"
private const val EXTRA_START_OVER = "start_over"
