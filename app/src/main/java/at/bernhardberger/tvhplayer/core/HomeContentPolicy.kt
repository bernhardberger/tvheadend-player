package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.DvrState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry

const val HOME_RECENT_CHANNEL_LIMIT = 8
const val HOME_ON_NOW_LIMIT = 6
const val HOME_RECORDING_LIMIT = 5
const val HOME_HERO_LIMIT = 4

enum class HomeSlideKind { LIVE, CONTINUE, ON_NOW, RECORDING }
enum class HomeRowKind { RECENT, ON_NOW, RECORDINGS, SCHEDULED }

data class HomeHeroSlide(
    val kind: HomeSlideKind,
    val channelId: Int,
    val channelNumber: Int?,
    val channelName: String,
    val piconPath: String?,
    val accentSeed: Int,
    val title: String,
    val subtitle: String?,
    val startSec: Long?,
    val stopSec: Long?,
    val progress: Float?,
    val nextTitle: String?,
    val nextStartSec: Long?,
    val recordingId: Int?,
    val playable: Boolean,
)

data class HomeCardItem(
    val key: String,
    val channelId: Int,
    val channelNumber: Int?,
    val channelName: String,
    val piconPath: String?,
    val accentSeed: Int,
    val title: String,
    val timeLabel: String?,
    val progress: Float?,
    val recordingId: Int?,
    val recordingNow: Boolean,
    val playable: Boolean,
)

data class HomeRow(
    val kind: HomeRowKind,
    val items: List<HomeCardItem>,
)

data class HomeDashboardModel(
    val hero: List<HomeHeroSlide>,
    val rows: List<HomeRow>,
)

enum class HomeFocusTarget {
    HERO,
    STATUS_ACTION,
}

fun homeInitialFocusTarget(model: HomeDashboardModel): HomeFocusTarget =
    if (model.hero.isNotEmpty()) HomeFocusTarget.HERO else HomeFocusTarget.STATUS_ACTION

/** Drop recording hero slides and recording rows when Simple TV disallows recordings. */
fun HomeDashboardModel.withRecordingsAllowed(allowRecordings: Boolean): HomeDashboardModel {
    if (allowRecordings) return this
    return copy(
        hero = hero.filter { it.kind != HomeSlideKind.RECORDING },
        rows = rows.filter {
            it.kind != HomeRowKind.RECORDINGS && it.kind != HomeRowKind.SCHEDULED
        },
    )
}

/**
 * Deterministic hue seed in 0..359 for channel accent washes.
 * Explicit fold — not [String.hashCode] — so unit tests can assert exact values.
 */
fun channelAccentSeed(name: String): Int {
    var hash = 0
    for (ch in name) {
        hash = (hash * 31 + ch.code) and 0x7fff_ffff
    }
    return hash % 360
}

fun buildHomeDashboard(
    channelsById: Map<Int, ChannelUi>,
    activeServiceId: Int?,
    activeRecordingId: Int?,
    activeProgrammeTitle: String?,
    lastWatchedChannelId: Int?,
    recentChannelIds: List<Int>,
    onNowEvents: List<Pair<ChannelUi, EpgEventEntry>>,
    nextEvents: Map<Int, EpgEventEntry>,
    recordings: List<DvrEntry>,
    nowSec: Long,
    allowRecordings: Boolean = true,
): HomeDashboardModel {
    val onNowByChannel = onNowEvents
        .asSequence()
        .filter { (_, event) -> event.start <= nowSec && nowSec < event.stop }
        .associate { (channel, event) -> channel.id to (channel to event) }

    val hero = mutableListOf<HomeHeroSlide>()
    val heroChannelIds = linkedSetOf<Int>()
    val heroRecordingIds = linkedSetOf<Int>()

    fun addSlide(slide: HomeHeroSlide) {
        if (hero.size >= HOME_HERO_LIMIT) return
        if (slide.recordingId != null) {
            if (slide.recordingId in heroRecordingIds) return
            heroRecordingIds += slide.recordingId
        } else if (slide.channelId >= 0) {
            if (slide.channelId in heroChannelIds) return
            heroChannelIds += slide.channelId
        }
        hero += slide
    }

    // 1. Active live session
    if (activeServiceId != null) {
        val channel = channelsById[activeServiceId]
        val now = onNowByChannel[activeServiceId]?.second
        val next = nextEvents[activeServiceId]
        val title = activeProgrammeTitle?.takeIf { it.isNotBlank() }
            ?: now?.title
            ?: channel?.name.orEmpty()
        addSlide(
            liveOrContinueSlide(
                kind = HomeSlideKind.LIVE,
                channel = channel,
                channelId = activeServiceId,
                title = title,
                event = now,
                next = next,
                nowSec = nowSec,
            ),
        )
    } else if (activeRecordingId != null && allowRecordings) {
        // 1b. Active recording session
        recordings.firstOrNull { it.id == activeRecordingId }?.let { entry ->
            addSlide(recordingSlide(entry, channelsById, nowSec))
        }
    }

    // 2. Last-watched when nothing is playing (CONTINUE)
    if (activeServiceId == null && activeRecordingId == null) {
        val lastId = lastWatchedChannelId
        if (lastId != null && lastId !in heroChannelIds) {
            val channel = channelsById[lastId]
            val now = onNowByChannel[lastId]?.second
            val next = nextEvents[lastId]
            addSlide(
                liveOrContinueSlide(
                    kind = HomeSlideKind.CONTINUE,
                    channel = channel,
                    channelId = lastId,
                    title = now?.title ?: channel?.name.orEmpty(),
                    event = now,
                    next = next,
                    nowSec = nowSec,
                ),
            )
        }
    }

    // 3. On-now on recent channels
    for (channelId in recentChannelIds) {
        if (hero.size >= HOME_HERO_LIMIT) break
        if (channelId in heroChannelIds) continue
        val pair = onNowByChannel[channelId] ?: continue
        val (channel, event) = pair
        addSlide(
            liveOrContinueSlide(
                kind = HomeSlideKind.ON_NOW,
                channel = channel,
                channelId = channelId,
                title = event.title,
                event = event,
                next = nextEvents[channelId],
                nowSec = nowSec,
            ),
        )
    }

    // 4. Recording now
    if (allowRecordings) {
        recordings
            .asSequence()
            .filter { it.state == DvrState.RECORDING }
            .sortedByDescending { it.start }
            .forEach { entry ->
                if (hero.size >= HOME_HERO_LIMIT) return@forEach
                addSlide(recordingSlide(entry, channelsById, nowSec))
            }
    }

    // Fallback so the hero exists whenever there is browsable content.
    if (hero.isEmpty()) {
        onNowByChannel.values
            .asSequence()
            .take(HOME_HERO_LIMIT)
            .forEach { (channel, event) ->
                addSlide(
                    liveOrContinueSlide(
                        kind = HomeSlideKind.ON_NOW,
                        channel = channel,
                        channelId = channel.id,
                        title = event.title,
                        event = event,
                        next = nextEvents[channel.id],
                        nowSec = nowSec,
                    ),
                )
            }
    }
    if (hero.isEmpty()) {
        for (channelId in recentChannelIds) {
            if (hero.size >= HOME_HERO_LIMIT) break
            val channel = channelsById[channelId] ?: continue
            addSlide(
                liveOrContinueSlide(
                    kind = HomeSlideKind.CONTINUE,
                    channel = channel,
                    channelId = channelId,
                    title = channel.name,
                    event = null,
                    next = nextEvents[channelId],
                    nowSec = nowSec,
                ),
            )
        }
    }
    if (hero.isEmpty() && allowRecordings) {
        recordings
            .asSequence()
            .filter {
                it.state == DvrState.COMPLETED &&
                    recordingPlaybackAvailability(it) is RecordingPlaybackAvailability.Ready
            }
            .sortedByDescending { it.start }
            .take(HOME_HERO_LIMIT)
            .forEach { entry ->
                addSlide(recordingSlide(entry, channelsById, nowSec))
            }
    }

    val recentIdSet = recentChannelIds.toSet()
    val rows = buildList {
        val recentItems = recentChannelIds
            .asSequence()
            .filter { it !in heroChannelIds }
            .mapNotNull { channelId ->
                val channel = channelsById[channelId] ?: return@mapNotNull null
                val event = onNowByChannel[channelId]?.second
                channelCard(
                    key = "recent-$channelId",
                    channel = channel,
                    title = event?.title ?: channel.name,
                    event = event,
                    nowSec = nowSec,
                )
            }
            .take(HOME_RECENT_CHANNEL_LIMIT)
            .toList()
        if (recentItems.isNotEmpty()) {
            add(HomeRow(HomeRowKind.RECENT, recentItems))
        }

        val onNowItems = onNowByChannel.values
            .asSequence()
            .filter { (channel, _) ->
                channel.id !in heroChannelIds && channel.id !in recentIdSet
            }
            .map { (channel, event) ->
                channelCard(
                    key = "onnow-${channel.id}",
                    channel = channel,
                    title = event.title,
                    event = event,
                    nowSec = nowSec,
                )
            }
            .take(HOME_ON_NOW_LIMIT)
            .toList()
        if (onNowItems.isNotEmpty()) {
            add(HomeRow(HomeRowKind.ON_NOW, onNowItems))
        }

        if (allowRecordings) {
            val recordingItems = buildList {
                recordings
                    .asSequence()
                    .filter { it.state == DvrState.RECORDING }
                    .filter { it.id !in heroRecordingIds }
                    .sortedByDescending { it.start }
                    .map { recordingCard(it, channelsById, recordingNow = true, playable = true) }
                    .forEach { add(it) }
                recordings
                    .asSequence()
                    .filter { it.state == DvrState.COMPLETED }
                    .filter { recordingPlaybackAvailability(it) is RecordingPlaybackAvailability.Ready }
                    .filter { it.id !in heroRecordingIds }
                    .sortedByDescending { it.start }
                    .map { recordingCard(it, channelsById, recordingNow = false, playable = true) }
                    .forEach { add(it) }
            }.take(HOME_RECORDING_LIMIT)
            if (recordingItems.isNotEmpty()) {
                add(HomeRow(HomeRowKind.RECORDINGS, recordingItems))
            }

            val scheduledItems = recordings
                .asSequence()
                .filter { it.state == DvrState.SCHEDULED && it.start >= nowSec }
                .sortedBy { it.start }
                .map { recordingCard(it, channelsById, recordingNow = false, playable = false) }
                .take(HOME_RECORDING_LIMIT)
                .toList()
            if (scheduledItems.isNotEmpty()) {
                add(HomeRow(HomeRowKind.SCHEDULED, scheduledItems))
            }
        }
    }

    return HomeDashboardModel(hero = hero, rows = rows)
}

fun pushRecentChannelId(
    current: List<Int>,
    channelId: Int,
    limit: Int = HOME_RECENT_CHANNEL_LIMIT,
): List<Int> = (listOf(channelId) + current.filterNot { it == channelId }).take(limit)

private fun liveOrContinueSlide(
    kind: HomeSlideKind,
    channel: ChannelUi?,
    channelId: Int,
    title: String,
    event: EpgEventEntry?,
    next: EpgEventEntry?,
    nowSec: Long,
): HomeHeroSlide {
    val name = channel?.name.orEmpty()
    return HomeHeroSlide(
        kind = kind,
        channelId = channelId,
        channelNumber = channel?.number,
        channelName = name,
        piconPath = channel?.icon,
        accentSeed = channelAccentSeed(name),
        title = title.ifBlank { name },
        subtitle = null,
        startSec = event?.start,
        stopSec = event?.stop,
        progress = event?.let { eventProgress(it, nowSec) },
        nextTitle = next?.title,
        nextStartSec = next?.start,
        recordingId = null,
        playable = true,
    )
}

private fun recordingSlide(
    entry: DvrEntry,
    channelsById: Map<Int, ChannelUi>,
    nowSec: Long,
): HomeHeroSlide {
    val channel = channelsById[entry.channelId]
    val name = entry.channelName ?: channel?.name.orEmpty()
    val playable = recordingPlaybackAvailability(entry) is RecordingPlaybackAvailability.Ready
    val progress = if (entry.state == DvrState.RECORDING && entry.stop > entry.start) {
        eventProgress(entry.start, entry.stop, nowSec)
    } else {
        null
    }
    return HomeHeroSlide(
        kind = HomeSlideKind.RECORDING,
        channelId = entry.channelId,
        channelNumber = channel?.number,
        channelName = name,
        piconPath = channel?.icon,
        accentSeed = channelAccentSeed(name),
        title = entry.title,
        subtitle = name.takeIf { it.isNotBlank() },
        startSec = entry.start,
        stopSec = entry.stop,
        progress = progress,
        nextTitle = null,
        nextStartSec = null,
        recordingId = entry.id,
        playable = playable,
    )
}

private fun channelCard(
    key: String,
    channel: ChannelUi,
    title: String,
    event: EpgEventEntry?,
    nowSec: Long,
): HomeCardItem = HomeCardItem(
    key = key,
    channelId = channel.id,
    channelNumber = channel.number,
    channelName = channel.name,
    piconPath = channel.icon,
    accentSeed = channelAccentSeed(channel.name),
    title = title,
    timeLabel = event?.let { remainingTimeLabel(it.stop, nowSec) },
    progress = event?.let { eventProgress(it, nowSec) },
    recordingId = null,
    recordingNow = false,
    playable = true,
)

private fun recordingCard(
    entry: DvrEntry,
    channelsById: Map<Int, ChannelUi>,
    recordingNow: Boolean,
    playable: Boolean,
): HomeCardItem {
    val channel = channelsById[entry.channelId]
    val name = entry.channelName ?: channel?.name.orEmpty()
    return HomeCardItem(
        key = "rec-${entry.id}",
        channelId = entry.channelId,
        channelNumber = channel?.number,
        channelName = name,
        piconPath = channel?.icon,
        accentSeed = channelAccentSeed(name),
        title = entry.title,
        timeLabel = null,
        progress = null,
        recordingId = entry.id,
        recordingNow = recordingNow,
        playable = playable,
    )
}

private fun eventProgress(event: EpgEventEntry, nowSec: Long): Float =
    eventProgress(event.start, event.stop, nowSec)

private fun eventProgress(start: Long, stop: Long, nowSec: Long): Float {
    val dur = (stop - start).coerceAtLeast(1L)
    val pos = (nowSec - start).coerceIn(0L, dur)
    return (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
}

/** Locale-neutral remaining minutes for policy-level cards; UI formats in presentation. */
private fun remainingTimeLabel(stopSec: Long, nowSec: Long): String? {
    val remaining = stopSec - nowSec
    if (remaining <= 0L) return null
    val minutes = ((remaining + 59) / 60).toInt().coerceAtLeast(1)
    return minutes.toString()
}
