package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import kotlin.time.Duration
import kotlin.time.Instant

const val RECORDING_REMOVAL_WHEN_SPACE_NEEDED = 2147483646L

/** A finished recording under this share of its planned length shows as stopped early... */
const val RECORDING_STOPPED_EARLY_FRACTION = 0.9

/** ...provided it also misses at least this much, so a short programme isn't flagged for seconds. */
const val RECORDING_STOPPED_EARLY_MIN_SHORTFALL_SECONDS = 120L

fun recordingIsRunning(recording: DvrEntry): Boolean =
    recording.state == DvrEntryState.RECORDING || recording.state == DvrEntryState.RECORDING_ERROR

/** The scheduled start less the effective pre-padding Tvheadend reports for the entry. */
fun recordingPaddedStart(recording: DvrEntry): Instant? =
    recording.start?.let { it - (recording.startExtraMinutes ?: Duration.ZERO) }

/** The scheduled stop plus the effective post-padding, which is when Tvheadend actually stops. */
fun recordingPaddedStop(recording: DvrEntry): Instant? =
    recording.stop?.let { it + (recording.stopExtraMinutes ?: Duration.ZERO) }

/** The scheduled programme length, without padding. */
fun recordingPlannedSeconds(recording: DvrEntry): Long? =
    recording.start?.let { start -> recording.stop?.let { (it - start).inWholeSeconds.coerceAtLeast(0L) } }

/**
 * What was actually recorded: the union of file intervals, extending open running files to [nowSec].
 * Only a finished recording without any file times falls back to the planned length; a running one
 * has recorded only part of it, so that and incomplete evidence stay unknown. A missed or invalid
 * entry never recorded anything, so it has no length at all.
 */
fun recordingLengthSeconds(recording: DvrEntry, nowSec: Long): Long? = when {
    recording.files.orEmpty().any { it.start != null || it.stop != null } -> recordedFileSeconds(recording, nowSec)
    recordingIsRunning(recording) -> null
    recording.state == DvrEntryState.MISSED || recording.state == DvrEntryState.INVALID -> null
    else -> recordingPlannedSeconds(recording)
}

/** From the first file's start to the last file's stop, which is when the recording actually ran. */
fun recordingRecordedSpan(recording: DvrEntry, nowSec: Long): Pair<Instant, Instant>? =
    recordedFileIntervals(recording, nowSec)?.let { it.first().first to it.maxOf { (_, stop) -> stop } }

private fun recordedFileIntervals(recording: DvrEntry, nowSec: Long): List<Pair<Instant, Instant>>? {
    val files = recording.files.orEmpty()
    if (files.isEmpty()) return null
    return files.map { file ->
        val start = file.start ?: return null
        val stop = file.stop ?: if (recordingIsRunning(recording)) {
            maxOf(start, Instant.fromEpochSeconds(nowSec))
        } else return null
        if (stop < start) return null
        start to stop
    }.sortedBy { it.first }
}

private fun recordedFileSeconds(recording: DvrEntry, nowSec: Long): Long? {
    val intervals = recordedFileIntervals(recording, nowSec) ?: return null
    var start = intervals.first().first
    var stop = intervals.first().second
    var total = Duration.ZERO
    for ((nextStart, nextStop) in intervals.drop(1)) {
        if (nextStart <= stop) {
            stop = maxOf(stop, nextStop)
        } else {
            total += stop - start
            start = nextStart
            stop = nextStop
        }
    }
    return (total + (stop - start)).inWholeSeconds
}

data class RecordingShortfall(val recordedSeconds: Long, val plannedSeconds: Long)

/**
 * A completed or failed recording whose files hold under [RECORDING_STOPPED_EARLY_FRACTION] of the plan
 * and miss it by at least [RECORDING_STOPPED_EARLY_MIN_SHORTFALL_SECONDS], ending at least that early.
 */
fun recordingStoppedEarly(recording: DvrEntry): RecordingShortfall? {
    if (recording.state != DvrEntryState.COMPLETED && recording.state != DvrEntryState.COMPLETED_ERROR &&
        recording.state != DvrEntryState.FILE_MISSING) return null
    val recorded = recordedFileSeconds(recording, nowSec = 0L)?.takeIf { it > 0L } ?: return null
    val planned = recordingPlannedSeconds(recording)?.takeIf { it > 0L } ?: return null
    val plannedStop = recording.stop ?: return null
    val latestStop = recording.files.orEmpty().mapNotNull { it.stop }.maxOrNull() ?: return null
    return RecordingShortfall(recorded, planned).takeIf {
        recorded < planned * RECORDING_STOPPED_EARLY_FRACTION &&
            planned - recorded >= RECORDING_STOPPED_EARLY_MIN_SHORTFALL_SECONDS &&
            (plannedStop - latestStop).inWholeSeconds >= RECORDING_STOPPED_EARLY_MIN_SHORTFALL_SECONDS
    }
}

/** File removal, not DVR log retention. Unknown and server-managed policies have no date. */
fun recordingRemovalTime(recording: DvrEntry, nowSec: Long): Long? {
    val days = recording.removalDays?.takeIf { it in 1 until RECORDING_REMOVAL_WHEN_SPACE_NEEDED } ?: return null
    val stop = recording.stop?.epochSeconds ?: return null
    return (stop + days * 86400L).takeIf { it > nowSec }
}

fun recordingSizeBytes(recording: DvrEntry): Long? = recording.dataSizeBytes?.takeIf { it >= 0 }
    ?: recording.files?.mapNotNull { it.sizeBytes?.takeIf { bytes -> bytes >= 0 } }
        ?.takeIf { it.isNotEmpty() }?.sum()

fun recordingErrorCount(recording: DvrEntry): Long? =
    listOfNotNull(recording.streamErrors, recording.dataErrors).takeIf { it.isNotEmpty() }?.sum()

/** Highest known video height across recording segments; unknown codecs are not guessed. */
@OptIn(ExperimentalTvheadendApi::class)
fun recordingPicture(recording: DvrEntry): String? = recording.files.orEmpty().flatMap { it.streams.orEmpty() }
    .filter { it.type in listOf(SubscriptionStreamType.MPEG2_VIDEO, SubscriptionStreamType.H264, SubscriptionStreamType.H265) }
    .mapNotNull { it.height?.takeIf { height -> height > 0 } }.maxOrNull()?.let { height ->
        val quality = when {
            height >= 2160 -> "UHD"
            height >= 720 -> "HD"
            else -> "SD"
        }
        "$quality $height"
    }

/**
 * What the viewer has seen of a recording. Tvheadend's play count counts file opens, not viewings,
 * so it only separates watched from unwatched; a saved position makes it partly watched.
 */
sealed interface RecordingWatchState {
    data object Unwatched : RecordingWatchState
    data object Watched : RecordingWatchState
    /** How far in, as a share of what was recorded; null when the length is unknown. */
    data class PartlyWatched(val fraction: Double?) : RecordingWatchState
}

/**
 * [resumePosition] is the position the recording offers to resume from, if any. The SDK clears it when
 * it marks the recording watched, so a saved position always means partly watched.
 */
fun recordingWatchState(recording: DvrEntry, resumePosition: Duration?, nowSec: Long): RecordingWatchState = when {
    resumePosition != null && resumePosition.isPositive() -> RecordingWatchState.PartlyWatched(
        (recordingLengthSeconds(recording, nowSec) ?: recordingPlannedSeconds(recording))
            ?.takeIf { it > 0 }
            ?.let { (resumePosition.inWholeSeconds.toDouble() / it).coerceIn(0.0, 1.0) }
    )
    // Tvheadend ignores progress writes for a missed entry, so it never shows as watched.
    (recording.playCount ?: 0L) > 0L && recording.state != DvrEntryState.MISSED -> RecordingWatchState.Watched
    else -> RecordingWatchState.Unwatched
}
