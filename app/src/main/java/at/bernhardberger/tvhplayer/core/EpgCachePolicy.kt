package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

const val EPG_KEEP_PAST_SEC = 6 * 3600L
const val EPG_KEEP_FUTURE_SEC = 24 * 3600L

data class EpgRetentionWindow(
    val fromSec: Long,
    val toSec: Long,
)

fun epgRetentionWindow(anchorSec: Long): EpgRetentionWindow = EpgRetentionWindow(
    fromSec = anchorSec - EPG_KEEP_PAST_SEC,
    toSec = anchorSec + EPG_KEEP_FUTURE_SEC,
)

fun evictEpgOutsideWindow(
    events: List<EpgEventEntry>,
    window: EpgRetentionWindow,
): List<EpgEventEntry> = events
    .asSequence()
    .filter { it.stop >= window.fromSec && it.start <= window.toSec }
    .sortedBy { it.start }
    .toList()
