@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** [AppPlaybackRuntime]'s recording cutpoints: the marker query for the installed recording and marker seeks. */
internal class RecordingMarkers(
    private val player: ExoPlayer,
    private val session: TvheadendSession,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val targetInstallationInProgress: () -> Boolean,
) {
    private val markerQuery = RecordingMarkerQuery()
    private var markerJob: Job? = null
    val recordingCutpoints = markerQuery.cutpoints
    val recordingMarkerRevision = markerQuery.revision

    fun seekRecordingMarker(positionMs: Long, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (expectedRevision != recordingMarkerRevision.value ||
                !markerQuery.isCurrent() || targetInstallationInProgress()) return@runIfOpen
            if (!player.isCurrentMediaItemSeekable ||
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return@runIfOpen
            if (positionMs in at.bernhardberger.tvhplayer.core.recordingMarkerPositions(
                    recordingCutpoints.value, player.duration,
                )) {
                // seekTo does not change playWhenReady: marker navigation preserves pause.
                player.seekTo(positionMs)
            }
        }
    }

    fun clearRecordingMarkers() {
        markerQuery.use(null)
        markerJob?.cancel()
        markerJob = null
    }

    /** Detach: clears the markers, then waits for the marker query that was running. */
    suspend fun clearRecordingMarkersAndJoin() {
        val pendingMarkers = markerJob
        clearRecordingMarkers()
        pendingMarkers?.join()
    }

    fun observeRecordingMarkersLocked(binding: PlaybackBinding.Recording) {
        clearRecordingMarkers()
        markerQuery.use(binding)
        markerJob = scope.launch {
            // Admission changes, not position samples: at most initial + growing completion.
            var completionFetched = false
            var initialFetched = false
            session.observation.map { binding.admission::class }.distinctUntilChanged().collectLatest {
                when (binding.admission) {
                    is RecordingPlaybackAdmission.GrowingStartOverOnly -> if (!initialFetched) {
                        initialFetched = true
                        markerQuery.refresh(binding)
                    }
                    is RecordingPlaybackAdmission.Completed -> if (!completionFetched) {
                        completionFetched = true
                        markerQuery.refresh(binding)
                    }
                    else -> markerQuery.retire(binding)
                }
            }
        }
    }
}
