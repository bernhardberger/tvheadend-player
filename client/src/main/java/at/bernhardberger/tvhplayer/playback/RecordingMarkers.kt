@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.extractor.metadata.Chapter
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
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
import at.bernhardberger.tvhplayer.core.recordingProgramMarks
import at.bernhardberger.tvhplayer.core.recordingSkipAction
import at.bernhardberger.tvhplayer.core.recordingSkipLanding
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.settings.SkipLanding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Instant

/** Identity belongs to this action on this installed target, not to the metadata revision. */
class RecordingSkipAction(val mark: RecordingMark, internal val landingMs: Long)

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
    private val foreground = MutableStateFlow(true)
    private var appliedRevision = -1L
    private val boundaryMessages = mutableListOf<PlayerMessage>()
    private var boundaryPositions = emptyList<Long>()
    private var boundaryGeneration = 0L
    private var positionEpoch = 0L
    private var recoveryJob: Job? = null
    private var mediaItem: MediaItem? = null
    private var installation = 0L
    private var skipPolicy = RecordingSkipPolicy()
    private var settings = PlayerSettings()
    private var recordingId: DvrEntryId? = null
    private var fileOrigin: Instant? = null
    private var programMarks = emptyList<RecordingMark>()
    private fun currentProgramMarks(): List<RecordingMark> =
        recordingId?.let { session.observation.value.dvrEntry(it) }
            ?.takeIf { it.startExtraMinutes?.isPositive() == true }
            ?.let { recordingProgramMarks(it, player.duration, fileOrigin) }
            .orEmpty().filter { it.kind == RecordingMarkKind.PROGRAM_START }
    private fun policyMarks(): List<RecordingMark> = marks.value + programMarks
    private var savedVolume: Float? = null
    private val _marks = MutableStateFlow<List<RecordingMark>>(emptyList())
    val marks = _marks.asStateFlow()
    private val _skipPrompt = MutableStateFlow<RecordingSkipAction?>(null)
    val skipPrompt = _skipPrompt.asStateFlow()
    private val _controlsSkip = MutableStateFlow<RecordingSkipAction?>(null)
    val controlsSkip = _controlsSkip.asStateFlow()
    private val _currentAdBreak = MutableStateFlow<RecordingMark?>(null)
    val currentAdBreak = _currentAdBreak.asStateFlow()
    val recordingCutpoints = markerQuery.cutpoints
    val recordingChapters = markerQuery.chapters
    val recordingChapterStartsMs = markerQuery.chapterStartsMs
    val recordingMarkerRevision = markerQuery.revision

    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    fun onTracksChanged() {
        if (targetInstallationInProgress() || mediaItem == null ||
            player.currentMediaItem !== mediaItem || !markerQuery.isCurrent()) return
        val revision = recordingMarkerRevision.value
        markerQuery.updateChapters(recordingChapters(player.currentTracks))
        if (recordingMarkerRevision.value != revision) publishMarks()
    }

    fun applySettings(value: PlayerSettings) {
        settings = value
        val offeredKind = skipPolicy.offeredPrompt?.kind
        val dropProgramStart = offeredKind == RecordingMarkKind.PROGRAM_START && !value.offerProgramStart
        val dropAdBreak = offeredKind == RecordingMarkKind.AD_BREAK && value.adBreakMode != AdBreakMode.ASK
        if (dropAdBreak || dropProgramStart) skipPolicy.dismissPrompt()
        publishSkipState(if (boundaryPositions.isNotEmpty() && isCurrent()) player.currentPosition else null)
        if (dropProgramStart) requestEvaluation(boundaryGeneration)
    }

    private fun isCurrent(): Boolean = !targetInstallationInProgress() && mediaItem != null &&
        player.currentMediaItem === mediaItem && markerQuery.isCurrent()

    private fun publishMarks() {
        _marks.value = recordingMarks(recordingCutpoints.value, recordingChapters.value)
    }

    private fun metadataChanged() {
        publishMarks()
        programMarks = currentProgramMarks()
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
        val positions = (bounds + landings + programMarks.filter {
            it.kind == RecordingMarkKind.PROGRAM_START
        }.map { it.startMs }).distinct().sorted()
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
                skipPolicy.evaluate(policyMarks(), player.currentPosition, false, settings)
                publishSkipState()
            }
            restoreVolume()
        }
        appliedRevision = recordingMarkerRevision.value
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
        if (mediaItem == null) return
        val expectedInstallation = installation
        scope.launch {
            targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                if (expectedInstallation != installation || !isCurrent()) return@serialize
                if (currentProgramMarks() != programMarks) metadataChanged()
                else if (boundaryPositions.isNotEmpty()) evaluatePlayback()
            }
        }
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
        skipPolicy.deliberateSeek(policyMarks(), positionMs, settings.skipLanding)
        publishSkipState(positionMs)
    }

    private fun actionFor(mark: RecordingMark?, previous: RecordingSkipAction?): RecordingSkipAction? =
        mark?.let {
            val landing = if (it.kind == RecordingMarkKind.PROGRAM_START) it.startMs
                else recordingSkipLanding(it, settings.skipLanding)
            previous?.takeIf { action -> action.mark == it && action.landingMs == landing }
                ?: RecordingSkipAction(it, landing)
        }

    private fun publishSkipState(positionMs: Long? = null) {
        _skipPrompt.value = actionFor(skipPolicy.offeredPrompt, _skipPrompt.value)
        _currentAdBreak.value = skipPolicy.currentAdBreak.takeUnless { settings.adBreakMode == AdBreakMode.OFF }
        val mark = positionMs?.takeIf { pendingRecordingRecoveryPosition.value == null && canSeek() }
            ?.let { recordingSkipAction(policyMarks(), it, settings) }
        _controlsSkip.value = actionFor(mark, _controlsSkip.value)
    }

    fun dismissSkipPrompt(expected: RecordingSkipAction) {
        targetCommands.runIfOpen {
            if (!isCurrent() || skipPrompt.value !== expected) return@runIfOpen
            skipPolicy.dismissPrompt()
            _skipPrompt.value = null
            if (expected.mark.kind == RecordingMarkKind.PROGRAM_START) requestEvaluation(boundaryGeneration)
        }
    }

    fun skipAdBreak(expected: RecordingSkipAction, fromControls: Boolean = false): Boolean =
        targetCommands.readIfOpen {
            if (!isCurrent() || pendingRecordingRecoveryPosition.value != null || !canSeek()) return@readIfOpen false
            val activeAction = if (fromControls) controlsSkip.value else skipPrompt.value
            if (activeAction !== expected) return@readIfOpen false
            val actualPosition = player.currentPosition
            val currentMarks = marks.value + currentProgramMarks()
            if (expected.mark !in currentMarks) return@readIfOpen false
            skipPolicy.evaluate(currentMarks, actualPosition, false, settings)
            val target = if (fromControls) {
                skipPolicy.acceptAction(expected.mark, currentMarks, actualPosition, settings)
            } else skipPolicy.acceptPrompt(expected.mark, settings)
            if (target == null) return@readIfOpen false
            // Withdraw the accepted action before the discontinuity reports the new position.
            publishSkipState()
            seekPlayer(target)
            true
        } ?: false

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
        if (pendingRecordingRecoveryPosition.value != null) {
            _controlsSkip.value = null
            return
        }
        val actualPosition = player.currentPosition
        // Only uninterrupted forward playback may use a delivered boundary in place
        // of a later sample. Backward movement never makes a future boundary current.
        val position = positionMs?.takeIf { actualPosition >= it } ?: actualPosition
        // A delivered boundary can arrive late. Once its entry is recorded, the actual position
        // is judged too: every automatic skip whose landing is not ahead is dropped.
        var decision = RecordingSkipDecision()
        var seekTo: Long? = null
        for (candidate in listOf(position, actualPosition).distinct()) {
            decision = skipPolicy.evaluate(policyMarks(), candidate, player.isPlaying && canSeek(), settings)
            seekTo = decision.seekToMs?.takeIf { it > actualPosition }
            if (seekTo != null) break
        }
        seekTo?.let { target ->
            seekPlayer(target)
            decision.automaticAdSkip?.let {
                noticeCenter.post(Notice.AutomaticAdBreakSkip(it.startMs), noticeCenter.context())
            }
        }
        publishSkipState(seekTo ?: actualPosition)
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

    fun seekRecordingMarker(positionMs: Long, expectedRevision: Long, programMarks: List<RecordingMark> = emptyList()) {
        targetCommands.runIfOpen {
            if (expectedRevision != recordingMarkerRevision.value ||
                !markerQuery.isCurrent() || targetInstallationInProgress() ||
                mediaItem == null || player.currentMediaItem !== mediaItem) return@runIfOpen
            if (!player.isCurrentMediaItemSeekable ||
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return@runIfOpen
            val programme = programMarks.filter {
                it.kind == RecordingMarkKind.PROGRAM_START || it.kind == RecordingMarkKind.PROGRAM_END
            }
            if (recordingMarkStops(marks.value + programme, player.duration).any { it.positionMs == positionMs }) {
                // seekTo does not change playWhenReady: marker navigation preserves pause.
                deliberateSeek(positionMs)
                seekPlayer(positionMs)
            }
        }
    }

    fun clearRecordingMarkers(preserveHistory: Boolean = false) {
        installation++
        restoreVolume()
        cancelBoundaries()
        if (!preserveHistory) skipPolicy = RecordingSkipPolicy()
        _marks.value = emptyList()
        _skipPrompt.value = null
        _controlsSkip.value = null
        _currentAdBreak.value = null
        recordingId = null
        fileOrigin = null
        programMarks = emptyList()
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

    /** Returns true only for a terminal result or an exhausted three-completed-attempt budget. */
    private suspend fun refreshMarkers(binding: PlaybackBinding.Recording, attempts: Int, attempted: () -> Unit): Boolean {
        for (attempt in attempts until 3) {
            // Resume an interrupted cycle immediately, retaining its remaining attempt budget.
            delay(if (attempt == attempts) 0L else if (attempt == 1) 3_000L else 7_000L)
            // Installation can invoke this observer inline before its temporary flag clears.
            // That flag and the playhead position must not end an otherwise eligible fetch cycle.
            if (!foreground.value || mediaItem == null || player.currentMediaItem !== mediaItem || !markerQuery.isCurrent(binding)) return false
            val retry = markerQuery.refresh(binding)
            attempted()
            if (!retry) return true
        }
        return true
    }

    @OptIn(ExperimentalTvheadendApi::class)
    fun observeRecordingMarkersLocked(
        binding: PlaybackBinding.Recording,
        recordingId: DvrEntryId,
        continuesTarget: Boolean = false,
        fileOrigin: Instant? = null,
    ) {
        clearRecordingMarkers(preserveHistory = continuesTarget)
        this.recordingId = recordingId
        this.fileOrigin = fileOrigin
        markerQuery.use(binding)
        mediaItem = player.currentMediaItem
        // Tracks may already have arrived synchronously during installation.
        markerQuery.updateChapters(recordingChapters(player.currentTracks))
        publishMarks()
        markerJob = scope.launch {
            launch {
                session.observation.map { observation ->
                    observation.dvrEntry(recordingId)?.let { it.start to it.startExtraMinutes }
                }.distinctUntilChanged().collect { onPlaybackChanged() }
            }
            // Admission changes, not position samples: one bounded fetch cycle per phase.
            var completionFetched = false
            var initialFetched = false
            var initialAttempts = 0
            var completionAttempts = 0
            session.observation.map { binding.admission::class }.distinctUntilChanged()
                .combine(foreground) { admission, active -> admission to active }
                .collectLatest { (_, active) ->
                    when (binding.admission) {
                        is RecordingPlaybackAdmission.Growing -> if (active && !initialFetched) {
                            initialFetched = refreshMarkers(binding, initialAttempts) { initialAttempts++ }
                        }
                        is RecordingPlaybackAdmission.Completed -> if (active && !completionFetched) {
                            completionFetched = refreshMarkers(binding, completionAttempts) { completionAttempts++ }
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
                        if (active && foreground.value && markerQuery.isCurrent(binding) && recordingMarkerRevision.value != appliedRevision) metadataChanged()
                    }
                }
        }
    }
}
