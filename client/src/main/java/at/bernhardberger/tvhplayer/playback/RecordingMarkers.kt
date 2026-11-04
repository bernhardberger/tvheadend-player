@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.extractor.metadata.Chapter
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.DvrCutpointAction
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.RecordingChapter
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingSkipDecision
import at.bernhardberger.tvhplayer.core.RecordingSkipPolicy
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import at.bernhardberger.tvhplayer.core.recordingMarks
import at.bernhardberger.tvhplayer.core.recordingMuted
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.SkipLanding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration

internal fun recordingChapters(tracks: Tracks): List<RecordingChapter> = buildList {
    for (group in tracks.groups) {
        for (index in 0 until group.length) {
            val metadata = group.getTrackFormat(index).metadata ?: continue
            for (entryIndex in 0 until metadata.length()) {
                val chapter = metadata[entryIndex] as? Chapter ?: continue
                if (!chapter.isHidden) add(RecordingChapter(chapter.startTimeMs, chapter.title?.value?.trim()?.takeIf { it.isNotEmpty() }))
            }
        }
    }
}.distinct().sortedBy { it.startMs }

/** Optional navigation metadata for the installed recording, never live tracks. */
internal class RecordingMarkers(
    private val player: ExoPlayer,
    private val session: TvheadendSession,
    private val scope: CoroutineScope,
    private val targetCommands: PlaybackTargetCommandSerialization,
    private val noticeCenter: NoticeCenter,
    private val pendingRecordingRecoveryPosition: StateFlow<Duration?>,
    private val targetInstallationInProgress: () -> Boolean,
) {
    private val markerQuery = RecordingMarkerQuery()
    private var markerJob: Job? = null
    private val boundaryMessages = mutableListOf<PlayerMessage>()
    private var boundaryPositions = emptyList<Long>()
    private var boundaryGeneration = 0L
    private var positionEpoch = 0L
    private var recoveryJob: Job? = null
    private var mediaItem: MediaItem? = null
    private var skipPolicy = RecordingSkipPolicy()
    private var settings = PlayerSettings()
    private var savedVolume: Float? = null
    private val _marks = MutableStateFlow<List<RecordingMark>>(emptyList())
    val marks = _marks.asStateFlow()
    private val _skipPrompt = MutableStateFlow<RecordingMark?>(null)
    val skipPrompt = _skipPrompt.asStateFlow()
    private val _currentAdBreak = MutableStateFlow<RecordingMark?>(null)
    val currentAdBreak = _currentAdBreak.asStateFlow()
    val recordingCutpoints = markerQuery.cutpoints
    val recordingChapters = markerQuery.chapters
    val recordingChapterStartsMs = markerQuery.chapterStartsMs
    val recordingMarkerRevision = markerQuery.revision

    fun onTracksChanged() {
        if (targetInstallationInProgress() || mediaItem == null ||
            player.currentMediaItem !== mediaItem || !markerQuery.isCurrent()) return
        val revision = recordingMarkerRevision.value
        markerQuery.updateChapters(recordingChapters(player.currentTracks))
        if (recordingMarkerRevision.value != revision) publishMarks()
    }

    fun applySettings(value: PlayerSettings) {
        settings = value
        if (value.adBreakMode != AdBreakMode.ASK) skipPolicy.dismissPrompt()
        publishSkipState()
    }

    private fun isCurrent(): Boolean = !targetInstallationInProgress() && mediaItem != null &&
        player.currentMediaItem === mediaItem && markerQuery.isCurrent()

    private fun publishMarks() {
        _marks.value = recordingMarks(recordingCutpoints.value, recordingChapters.value)
    }

    private fun metadataChanged() {
        publishMarks()
        val hadBoundaries = boundaryPositions.isNotEmpty()
        val bounds = recordingCutpoints.value.filter {
            it.action == DvrCutpointAction.CUT || it.action == DvrCutpointAction.COMMERCIAL_BREAK ||
                it.action == DvrCutpointAction.MUTE
        }.filter { it.end > it.start && it.end.inWholeMilliseconds > 0 }.flatMap {
            listOf(it.start.inWholeMilliseconds.coerceAtLeast(0), it.end.inWholeMilliseconds)
        }
        // An ad break stops being current at its landing point, which an early landing puts inside it.
        // The policy judges the merged marks, so the landing points come from those, not raw cutpoints.
        val landings = marks.value.filter { it.kind == RecordingMarkKind.AD_BREAK }.flatMap { mark ->
            SkipLanding.entries.map { landing -> mark.endMs - landing.beforeEndMs }.filter { it > mark.startMs }
        }
        val positions = (bounds + landings).distinct().sorted()
        if (positions != boundaryPositions) {
            cancelBoundaries()
            boundaryPositions = positions
            val generation = boundaryGeneration
            armBoundaries()
            if (positions.isNotEmpty()) recoveryJob = scope.launch {
                pendingRecordingRecoveryPosition.collect { pending ->
                    if (pending == null) {
                        // The SDK clears pending just before issuing its recovery seek.
                        // Let that synchronous issuance finish before reading the position.
                        yield()
                        requestEvaluation(generation)
                    }
                }
            }
        }
        if (positions.isNotEmpty()) evaluatePlayback()
        else if (hadBoundaries) {
            if (pendingRecordingRecoveryPosition.value == null) {
                skipPolicy.evaluate(marks.value, player.currentPosition, false, settings)
                publishSkipState()
            }
            restoreVolume()
        }
    }

    private fun cancelBoundaries() {
        boundaryGeneration++
        recoveryJob?.cancel()
        recoveryJob = null
        boundaryMessages.forEach { it.cancel() }
        boundaryMessages.clear()
        boundaryPositions = emptyList()
    }

    private fun armBoundaries() {
        boundaryMessages.forEach { it.cancel() }
        boundaryMessages.clear()
        val generation = boundaryGeneration
        val armedEpoch = positionEpoch
        for (position in boundaryPositions) {
            boundaryMessages += player.createMessage { _, _ ->
                if (armedEpoch == positionEpoch && generation == boundaryGeneration) {
                    requestEvaluation(generation, position, armedEpoch)
                }
            }.setPosition(position)
                .setLooper(player.applicationLooper)
                .setDeleteAfterDelivery(false)
                .send()
        }
    }

    private fun invalidatePosition() {
        positionEpoch++
        armBoundaries()
    }

    /** Player callbacks and message deliveries run on the application looper, then take the command lock. */
    fun onPlaybackChanged() {
        if (boundaryMessages.isNotEmpty()) requestEvaluation(boundaryGeneration)
    }

    fun onPositionDiscontinuity() {
        invalidatePosition()
        onPlaybackChanged()
    }

    private fun requestEvaluation(generation: Long, positionMs: Long? = null, epoch: Long = positionEpoch) {
        scope.launch {
            targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                if (generation == boundaryGeneration && epoch == positionEpoch) {
                    evaluatePlayback(positionMs)
                }
            }
        }
    }

    fun deliberateSeek(positionMs: Long) {
        invalidatePosition()
        if (!isCurrent()) return
        skipPolicy.deliberateSeek(marks.value, positionMs, settings.skipLanding)
        publishSkipState()
    }

    private fun publishSkipState() {
        _skipPrompt.value = skipPolicy.offeredAdBreak
        _currentAdBreak.value = skipPolicy.currentAdBreak.takeUnless { settings.adBreakMode == AdBreakMode.OFF }
    }

    fun dismissSkipPrompt(expected: RecordingMark, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (!isCurrent() || recordingMarkerRevision.value != expectedRevision || skipPrompt.value != expected) return@runIfOpen
            skipPolicy.dismissPrompt()
            publishSkipState()
        }
    }

    fun skipAdBreak(expected: RecordingMark, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (!isCurrent() || pendingRecordingRecoveryPosition.value != null ||
                expectedRevision != recordingMarkerRevision.value || !canSeek()) return@runIfOpen
            skipPolicy.evaluate(marks.value, player.currentPosition, false, settings)
            skipPolicy.skipAdBreak(expected, settings)?.let { target ->
                seekPlayer(target)
            }
            publishSkipState()
        }
    }

    private fun canSeek(): Boolean = player.isCurrentMediaItemSeekable &&
        player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)

    private fun evaluatePlayback(positionMs: Long? = null) {
        if (!isCurrent()) {
            restoreVolume()
            return
        }
        // Recovery can temporarily play from zero before its owed seek. That transport
        // position must not erase the continuing target's deliberate-entry history.
        // The SDK does not report ordinary saved-position resumes as recovery pending.
        if (pendingRecordingRecoveryPosition.value != null) return
        val actualPosition = player.currentPosition
        // Only uninterrupted forward playback may use a delivered boundary in place
        // of a later sample. Backward movement never makes a future boundary current.
        val position = positionMs?.takeIf { actualPosition >= it } ?: actualPosition
        // A delivered boundary can arrive late. Once its entry is recorded, the actual position
        // is judged too: an automatic ad skip whose landing is behind it is dropped, which would
        // only seek backwards and post a notice for a break that was not skipped.
        var decision = RecordingSkipDecision()
        var seekTo: Long? = null
        for (candidate in listOf(position, actualPosition).distinct()) {
            decision = skipPolicy.evaluate(marks.value, candidate, player.isPlaying && canSeek(), settings)
            seekTo = decision.seekToMs?.takeIf { decision.automaticAdSkip == null || it > actualPosition }
            if (seekTo != null) break
        }
        seekTo?.let { target ->
            seekPlayer(target)
            decision.automaticAdSkip?.let {
                noticeCenter.post(Notice.AutomaticAdBreakSkip(it.startMs), noticeCenter.context())
            }
        }
        publishSkipState()
        val muted = recordingMuted(recordingCutpoints.value,
            if (seekTo != null) player.currentPosition else actualPosition)
        if (muted) {
            if (savedVolume == null) savedVolume = player.volume
            player.volume = 0f
        } else restoreVolume()
    }

    private fun seekPlayer(positionMs: Long) {
        invalidatePosition()
        player.seekTo(positionMs)
    }

    private fun restoreVolume() {
        savedVolume?.let { player.volume = it }
        savedVolume = null
    }

    fun seekRecordingMarker(positionMs: Long, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (expectedRevision != recordingMarkerRevision.value ||
                !markerQuery.isCurrent() || targetInstallationInProgress() ||
                mediaItem == null || player.currentMediaItem !== mediaItem) return@runIfOpen
            if (!player.isCurrentMediaItemSeekable ||
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return@runIfOpen
            if (recordingMarkStops(marks.value, player.duration).any { it.positionMs == positionMs }) {
                // seekTo does not change playWhenReady: marker navigation preserves pause.
                deliberateSeek(positionMs)
                seekPlayer(positionMs)
            }
        }
    }

    fun clearRecordingMarkers(preserveHistory: Boolean = false) {
        restoreVolume()
        cancelBoundaries()
        if (!preserveHistory) skipPolicy = RecordingSkipPolicy()
        _marks.value = emptyList()
        _skipPrompt.value = null
        _currentAdBreak.value = null
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
    fun observeRecordingMarkersLocked(binding: PlaybackBinding.Recording, continuesTarget: Boolean = false) {
        clearRecordingMarkers(preserveHistory = continuesTarget)
        markerQuery.use(binding)
        mediaItem = player.currentMediaItem
        // Tracks may already have arrived synchronously during installation.
        markerQuery.updateChapters(recordingChapters(player.currentTracks))
        publishMarks()
        markerJob = scope.launch {
            // Admission changes, not position samples: at most initial + growing completion.
            var completionFetched = false
            var initialFetched = false
            session.observation.map { binding.admission::class }.distinctUntilChanged().collectLatest {
                val revision = recordingMarkerRevision.value
                when (binding.admission) {
                    is RecordingPlaybackAdmission.Growing -> if (!initialFetched) {
                        initialFetched = true
                        markerQuery.refresh(binding)
                    }
                    is RecordingPlaybackAdmission.Completed -> if (!completionFetched) {
                        completionFetched = true
                        markerQuery.refresh(binding)
                    }
                    else -> {
                        targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                            markerQuery.retire(binding)
                            cancelBoundaries()
                            restoreVolume()
                            skipPolicy = RecordingSkipPolicy()
                            publishMarks()
                            publishSkipState()
                        }
                    }
                }
                targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                    if (markerQuery.isCurrent(binding) && recordingMarkerRevision.value != revision) metadataChanged()
                }
            }
        }
    }
}
