@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.Chapter
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun recordingChapterStarts(tracks: Tracks): List<Long> = buildList {
    for (group in tracks.groups) {
        for (index in 0 until group.length) {
            val metadata = group.getTrackFormat(index).metadata ?: continue
            for (entryIndex in 0 until metadata.length()) {
                val chapter = metadata[entryIndex] as? Chapter ?: continue
                if (!chapter.isHidden) add(chapter.startTimeMs)
            }
        }
    }
}.distinct().sorted()

/** Optional navigation metadata for the installed recording, never live tracks. */
internal class RecordingMarkers(
    private val player: ExoPlayer,
    private val session: TvheadendSession,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val targetInstallationInProgress: () -> Boolean,
) {
    private val markerQuery = RecordingMarkerQuery()
    private var markerJob: Job? = null
    private var mediaItem: MediaItem? = null
    val recordingCutpoints = markerQuery.cutpoints
    val recordingChapterStartsMs = markerQuery.chapterStartsMs
    val recordingMarkerRevision = markerQuery.revision

    fun onTracksChanged() {
        if (targetInstallationInProgress() || mediaItem == null ||
            player.currentMediaItem !== mediaItem || !markerQuery.isCurrent()) return
        markerQuery.updateChapters(recordingChapterStarts(player.currentTracks))
    }

    fun seekRecordingMarker(positionMs: Long, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (expectedRevision != recordingMarkerRevision.value ||
                !markerQuery.isCurrent() || targetInstallationInProgress() ||
                mediaItem == null || player.currentMediaItem !== mediaItem) return@runIfOpen
            if (!player.isCurrentMediaItemSeekable ||
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return@runIfOpen
            if (positionMs in at.bernhardberger.tvhplayer.core.recordingMarkerPositions(
                    recordingCutpoints.value, player.duration, recordingChapterStartsMs.value,
                )) {
                // seekTo does not change playWhenReady: marker navigation preserves pause.
                player.seekTo(positionMs)
            }
        }
    }

    fun clearRecordingMarkers() {
        mediaItem = null
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

    @OptIn(ExperimentalTvheadendApi::class)
    fun observeRecordingMarkersLocked(binding: PlaybackBinding.Recording) {
        clearRecordingMarkers()
        markerQuery.use(binding)
        mediaItem = player.currentMediaItem
        // Tracks may already have arrived synchronously during installation.
        markerQuery.updateChapters(recordingChapterStarts(player.currentTracks))
        markerJob = scope.launch {
            // Admission changes, not position samples: at most initial + growing completion.
            var completionFetched = false
            var initialFetched = false
            session.observation.map { binding.admission::class }.distinctUntilChanged().collectLatest {
                when (binding.admission) {
                    is RecordingPlaybackAdmission.Growing -> if (!initialFetched) {
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
