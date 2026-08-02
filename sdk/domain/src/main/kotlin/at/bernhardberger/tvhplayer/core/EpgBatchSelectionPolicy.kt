package at.bernhardberger.tvhplayer.core

data class EpgWarmupProgress(
    val completed: Int,
    val total: Int,
)

fun selectEpgTopUpChannelIds(
    orderedChannelIds: List<Int>,
    coverageByChannelId: Map<Int, EpgCoverage>,
    inFlightChannelIds: Set<Int>,
    wantedTo: Long,
    nowSec: Long,
    cooldownSec: Long,
    limit: Int,
): List<Int> {
    require(limit >= 0) { "limit must not be negative" }

    return orderedChannelIds.asSequence()
        .filterNot(inFlightChannelIds::contains)
        .filter { channelId ->
            coverageByChannelId[channelId]
                .orEmpty()
                .needsTopUp(wantedTo, nowSec, cooldownSec)
        }
        .sortedBy { channelId -> coverageByChannelId[channelId]?.coveredTo ?: 0L }
        .take(limit)
        .toList()
}

fun epgWarmupProgress(
    orderedChannelIds: List<Int>,
    coverageByChannelId: Map<Int, EpgCoverage>,
    wantedTo: Long,
    nowSec: Long,
): EpgWarmupProgress = EpgWarmupProgress(
    completed = orderedChannelIds.count { channelId ->
        coverageByChannelId[channelId]?.let { coverage ->
            !coverage.needsTopUp(wantedTo, nowSec, cooldownSec = 0L)
        } == true
    },
    total = orderedChannelIds.size,
)

private fun EpgCoverage?.orEmpty(): EpgCoverage = this ?: EpgCoverage()
