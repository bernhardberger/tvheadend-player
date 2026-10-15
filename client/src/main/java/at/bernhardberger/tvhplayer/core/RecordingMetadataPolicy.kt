package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType

const val RECORDING_REMOVAL_WHEN_SPACE_NEEDED = 2147483646L

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
