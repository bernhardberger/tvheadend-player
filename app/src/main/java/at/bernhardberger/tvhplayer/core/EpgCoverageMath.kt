package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

/**
 * EPG coverage window of a channel: the earliest event start and latest event stop
 * currently retained. An empty channel is represented by an "inverted" window
 * ([from] == [Long.MAX_VALUE], [to] == 0) so it is always treated as needing a fetch.
 */
data class Coverage(val from: Long, val to: Long) {
    val isEmpty: Boolean get() = from == Long.MAX_VALUE && to == 0L
}

/**
 * Derives coverage authoritatively from the events we still hold. This must NOT be
 * a monotonic maximum: when the retained list drains (e.g. after a long uptime), the
 * coverage has to drop accordingly so the EPG worker knows it must re-fetch instead
 * of believing it is still up to date (which surfaced as "No EPG" everywhere).
 */
fun coverageForEvents(events: List<EpgEventEntry>): Coverage =
    if (events.isEmpty()) {
        Coverage(Long.MAX_VALUE, 0L)
    } else {
        Coverage(events.minOf { it.start }, events.maxOf { it.stop })
    }
