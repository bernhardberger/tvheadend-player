package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

/**
 * EPG coverage window of a channel: the earliest event start and latest event stop
 * currently retained. An empty channel is represented by an "inverted" window
 * ([from] == [Long.MAX_VALUE], [to] == 0) so retained-cache emptiness remains explicit.
 */
data class Coverage(val from: Long, val to: Long) {
    val isEmpty: Boolean get() = from == Long.MAX_VALUE && to == 0L

    companion object {
        val Empty = Coverage(from = Long.MAX_VALUE, to = 0L)
    }
}

/**
 * Retained event bounds plus server-query history for one channel.
 *
 * Event bounds can be replaced authoritatively after cache trimming, while successful
 * query horizons remain monotonic so an empty response still covers its requested range.
 */
data class EpgCoverage(
    val eventCoverage: Coverage = Coverage.Empty,
    val queriedTo: Long = 0L,
    val lastAttemptSec: Long = 0L,
) {
    val coveredFrom: Long get() = eventCoverage.from
    val coveredTo: Long get() = eventCoverage.to
    val knownTo: Long get() = maxOf(coveredTo, queriedTo)

    fun afterAttempt(attemptedAtSec: Long): EpgCoverage =
        copy(lastAttemptSec = attemptedAtSec)

    fun afterSuccessfulFetch(targetTo: Long, attemptedAtSec: Long): EpgCoverage = copy(
        queriedTo = maxOf(queriedTo, targetTo),
        lastAttemptSec = attemptedAtSec,
    )

    fun includingObservedCoverage(observed: Coverage): EpgCoverage = copy(
        eventCoverage = Coverage(
            from = minOf(coveredFrom, observed.from),
            to = maxOf(coveredTo, observed.to),
        )
    )

    fun withRetainedCoverage(retained: Coverage): EpgCoverage =
        copy(eventCoverage = retained)

    fun needsTopUp(wantedTo: Long, nowSec: Long, cooldownSec: Long): Boolean =
        knownTo < wantedTo && nowSec - lastAttemptSec >= cooldownSec

    fun nextTargetTo(
        desiredWarmTo: Long,
        desiredMinTo: Long,
        desiredMaxTo: Long,
        chunkSec: Long,
    ): Long? = when {
        knownTo < desiredWarmTo -> minOf(desiredWarmTo, desiredMaxTo)
        knownTo < desiredMinTo -> minOf(knownTo + chunkSec, desiredMaxTo)
        else -> null
    }
}

/**
 * Derives coverage authoritatively from the events we still hold. This must NOT be
 * a monotonic maximum: when the retained list drains (e.g. after a long uptime), the
 * coverage has to drop accordingly so the EPG worker can re-fetch once any confirmed
 * query horizon is exhausted instead of believing retained events are still present.
 */
fun coverageForEvents(events: List<EpgEventEntry>): Coverage =
    if (events.isEmpty()) {
        Coverage.Empty
    } else {
        Coverage(events.minOf { it.start }, events.maxOf { it.stop })
    }
