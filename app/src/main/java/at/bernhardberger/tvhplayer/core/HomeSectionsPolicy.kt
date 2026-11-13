package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.EpgEvent as EpgEventEntry

/**
 * What plays right now: the live channel shown by the player, or the recording being played. Home only
 * merges it with running DVR entries; it never starts playback itself.
 */
sealed interface HomePlaying {
    data class LiveChannel(val channelId: ChannelId) : HomePlaying
    data class Recording(val entryId: DvrEntryId) : HomePlaying
}

enum class HomeSectionKind {
    CURRENT_ACTIVITY,
    RECENT_CHANNELS,
    ON_NOW,
    GENRES,
    CONTINUE_WATCHING,
    EXPIRING_SOON,
    RECENTLY_RECORDED,
    UPCOMING,
}

/**
 * Names a focused Home card across refreshes. [position] is the card's index when focus was saved and is
 * only used to land on the next card when the focused one has vanished.
 */
data class HomeFocusKey(
    val section: HomeSectionKind,
    val item: String,
    val position: Int,
)

sealed interface HomeSection {
    val kind: HomeSectionKind

    data class CurrentActivity(val items: List<CurrentActivityItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.CURRENT_ACTIVITY
    }

    data class RecentChannels(val items: List<RecentChannelItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.RECENT_CHANNELS
    }

    data class OnNow(val items: List<OnNowItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.ON_NOW
    }

    data class Genres(val items: List<GenreItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.GENRES
    }

    data class ContinueWatching(val items: List<RecordingItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.CONTINUE_WATCHING
    }

    data class ExpiringSoon(val items: List<RecordingItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.EXPIRING_SOON
    }

    data class RecentlyRecorded(val items: List<RecordingItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.RECENTLY_RECORDED
    }

    /** The next scheduled recordings, followed by All scheduled in the UI. */
    data class Upcoming(val items: List<UpcomingItem>) : HomeSection {
        override val kind: HomeSectionKind = HomeSectionKind.UPCOMING
    }
}

data class CurrentActivityItem(
    /** The channel or recording framework is playing; null for a recording-only card. */
    val playing: HomePlaying?,
    /** The running DVR entry on the card, when one covers the same programme; null otherwise. */
    val recording: DvrEntry?,
)

data class RecentChannelItem(val channel: Channel)

data class OnNowItem(
    val channel: Channel,
    /** The programme running on [channel] right now, at the time Home looked. */
    val event: EpgEventEntry,
)

data class GenreItem(val category: ProgrammeCategory)

/** One card on the Continue watching or Recently recorded shelf. */
data class RecordingItem(val entry: DvrEntry)

data class UpcomingItem(
    val entry: DvrEntry,
)

data class HomeInput(
    /** The shared selected channel scope, already filtered and ordered by the caller for display. */
    val channels: List<Channel>,
    /** The programme running on each in-scope channel per the caller's cached EPG. */
    val currentEvents: Map<ChannelId, EpgEventEntry>,
    /** Cached EPG ahead of [nowSec], used only for the genre row. */
    val upcomingEvents: List<EpgEventEntry>,
    /** Recently played channels, newest first, from LastPlayedChannelStore. */
    val recentChannelIds: List<ChannelId>,
    val dvrEntries: List<DvrEntry>,

    /**
     * The SDK admission of each DVR entry, or null without a session. The Recordings resume-dialog rule
     * ([offeredResumePosition]) decides which shelf a recording belongs to.
     */
    val admission: (DvrEntry) -> RecordingPlaybackAdmission?,
    val playing: HomePlaying?,
    val nowSec: Long,
    val hiddenSections: Set<HomeSectionKind> = emptySet(),
    /** Locally played recordings, newest first; older entries retain recording-date order. */
    val recentRecordingIds: List<DvrEntryId> = emptyList(),
)

private const val RECENT_CHANNEL_LIMIT = 8
private const val RECORDING_SHELF_LIMIT = 10
private const val GENRE_WINDOW_SECONDS = 24L * 60 * 60
private const val RECENTLY_RECORDED_WINDOW_SECONDS = 7L * 24 * 60 * 60

/**
 * Home's rows in the fixed spec order, each omitted when nothing fills it. Pure classification: playback,
 * Guide and DVR detail cards draw themselves from these items; the All recordings card belongs to the UI.
 */
fun homeSections(input: HomeInput): List<HomeSection> {
    val resumeOffered = input.dvrEntries
        .associate { it.id to (offeredResumePosition(input.admission(it)) != null) }
    return buildList {
        currentActivitySection(input)?.let(::add)
        recentChannelsSection(input)?.let(::add)
        onNowSection(input)?.let(::add)
        genresSection(input)?.let(::add)
        continueWatchingSection(input, resumeOffered)?.let(::add)
        expiringSoonSection(input)?.let(::add)
        recentlyRecordedSection(input, resumeOffered)?.let(::add)
        upcomingSection(input)?.let(::add)
    }.filterNot { it.kind in input.hiddenSections }
}

/**
 * The first card of the topmost visible section. Spec order puts playback ahead of running recordings ahead
 * of recents, so picking the first card follows the whole §5 chain; null leaves focus to recovery UI.
 */
fun homeInitialFocus(sections: List<HomeSection>): HomeFocusKey? {
    val section = sections.firstOrNull() ?: return null
    val keys = section.itemKeys()
    if (keys.isEmpty()) return null
    return HomeFocusKey(section.kind, keys.first(), 0)
}

/**
 * Recovers the focused card after a refresh. Same card first; else the card where it stood, or the first of
 * its section once it was last; else the initial-focus rule for the sections at hand.
 */
fun homeRestoreFocus(sections: List<HomeSection>, previous: HomeFocusKey): HomeFocusKey? {
    val section = sections.firstOrNull { it.kind == previous.section } ?: return homeInitialFocus(sections)
    val keys = section.itemKeys()
    return when {
        previous.item in keys -> HomeFocusKey(section.kind, previous.item, keys.indexOf(previous.item))
        previous.position < keys.size -> HomeFocusKey(section.kind, keys[previous.position], previous.position)
        keys.isNotEmpty() -> HomeFocusKey(section.kind, keys.first(), 0)
        else -> homeInitialFocus(sections)
    }
}

/**
 * Playback first, then everything else recording right now. A playing live channel that is itself being
 * recorded stays one card marked as recording; a running recording of a playing channel is that channel,
 * so any further entries for it drop rather than repeat it.
 */
private fun currentActivitySection(input: HomeInput): HomeSection.CurrentActivity? {
    val running = input.dvrEntries
        .filter(::recordingIsRunning)
        .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }
    val playing = input.playing
    val items = buildList {
        when (playing) {
            is HomePlaying.LiveChannel -> add(
                CurrentActivityItem(
                    playing = playing,
                    recording = running.firstOrNull { it.channelId == playing.channelId },
                ),
            )
            is HomePlaying.Recording -> add(
                CurrentActivityItem(
                    playing = playing,
                    recording = running.firstOrNull { it.id == playing.entryId },
                ),
            )
            null -> Unit
        }
        val playingChannelId = (playing as? HomePlaying.LiveChannel)?.channelId
        val playingEntryId = (playing as? HomePlaying.Recording)?.entryId
        running.forEach { entry ->
            if (playingChannelId != null && entry.channelId == playingChannelId) return@forEach
            if (entry.id == playingEntryId) return@forEach
            add(CurrentActivityItem(playing = null, recording = entry))
        }
    }
    if (items.isEmpty()) return null
    return HomeSection.CurrentActivity(items)
}

/** Up to [RECENT_CHANNEL_LIMIT] recently played channels, in store order, still in the shared scope. */
private fun recentChannelsSection(input: HomeInput): HomeSection.RecentChannels? {
    val channelsById = input.channels.associateBy { it.id }
    val playingChannelId = (input.playing as? HomePlaying.LiveChannel)?.channelId
    val items = input.recentChannelIds
        .asSequence()
        .filter { it != playingChannelId }
        .mapNotNull(channelsById::get)
        .take(RECENT_CHANNEL_LIMIT)
        .map(::RecentChannelItem)
        .toList()
    if (items.isEmpty()) return null
    return HomeSection.RecentChannels(items)
}

/**
 * Current broadcasts in ascending channel order, so ended programmes never stay actionable: an event whose
 * window no longer covers nowSec is not current even if the caller's snapshot still lists it as such.
 */
private fun onNowSection(input: HomeInput): HomeSection.OnNow? {
    val items = orderBrowseChannels(input.channels)
        .asSequence()
        .mapNotNull { channel ->
            input.currentEvents[channel.id]
                ?.takeIf { isRunningAt(it, input.nowSec) }
                ?.let { OnNowItem(channel, it) }
        }
        .toList()
    if (items.isEmpty()) return null
    return HomeSection.OnNow(items)
}

/**
 * The Guide's genre families in its fixed order, each kept while at least one genre-coded programme is on
 * in the shared scope or starts within the next 24 hours. No genre codes mean no row.
 */
private fun genresSection(input: HomeInput): HomeSection.Genres? {
    val channelIds = input.channels.map { it.id }.toSet()
    val categories = buildSet {
        input.currentEvents.forEach { (channelId, event) ->
            if (channelId in channelIds && isRunningAt(event, input.nowSec)) programmeCategory(event)?.let(::add)
        }
        input.upcomingEvents.forEach { event ->
            if (event.channelId in channelIds && event.startsInGenreWindow(input.nowSec)) {
                programmeCategory(event)?.let(::add)
            }
        }
    }
    val items = ProgrammeCategory.entries
        .filter { it in categories }
        .map(::GenreItem)
    if (items.isEmpty()) return null
    return HomeSection.Genres(items)
}

private fun EpgEventEntry.startsInGenreWindow(nowSec: Long): Boolean =
    isRunningAt(this, nowSec) ||
        start.epochSeconds in nowSec + 1..nowSec + GENRE_WINDOW_SECONDS

/** Recordings offering resume, most recently played first, then newest recording first. */
private fun continueWatchingSection(
    input: HomeInput,
    resumeOffered: Map<DvrEntryId, Boolean>,
): HomeSection.ContinueWatching? {
    val recentOrder = input.recentRecordingIds.withIndex().associate { it.value to it.index }
    val items = input.dvrEntries
        .asSequence()
        .filter { resumeOffered.getValue(it.id) }
        .sortedWith(compareBy<DvrEntry> { recentOrder[it.id] ?: Int.MAX_VALUE }
            .thenByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE })
        .take(RECORDING_SHELF_LIMIT)
        .map(::RecordingItem)
        .toList()
    if (items.isEmpty()) return null
    return HomeSection.ContinueWatching(items)
}

/**
 * Finished recordings of the last seven days that offer no resume position, i.e. were never started; a
 * recording classified for Continue watching never repeats here.
 */
private fun recentlyRecordedSection(
    input: HomeInput,
    resumeOffered: Map<DvrEntryId, Boolean>,
): HomeSection.RecentlyRecorded? {
    val earliestStart = input.nowSec - RECENTLY_RECORDED_WINDOW_SECONDS
    val items = input.dvrEntries
        .asSequence()
        .filter { it.state == DvrEntryState.COMPLETED }
        .filter { !resumeOffered.getValue(it.id) }
        .filter { (it.start?.epochSeconds ?: Long.MIN_VALUE) >= earliestStart }
        .sortedByDescending { it.start?.epochSeconds ?: Long.MIN_VALUE }
        .take(RECORDING_SHELF_LIMIT)
        .map(::RecordingItem)
        .toList()
    if (items.isEmpty()) return null
    return HomeSection.RecentlyRecorded(items)
}

/** Unwatched or partly watched files, soonest expiry first, using the shared seven-day window. */
private fun expiringSoonSection(input: HomeInput): HomeSection.ExpiringSoon? {
    val items = input.dvrEntries.asSequence()
        .filter { it.state == DvrEntryState.COMPLETED || it.state == DvrEntryState.COMPLETED_ERROR }
        .filter {
            recordingWatchState(it, offeredResumePosition(input.admission(it)), input.nowSec) !=
                RecordingWatchState.Watched
        }
        .mapNotNull { entry ->
            recordingExpiryTime(entry, input.nowSec)
                ?.takeIf { recordingExpiryTimeLeft(it, input.nowSec) != null }
                ?.let { entry to it }
        }
        .sortedBy { it.second }
        .take(RECORDING_SHELF_LIMIT)
        .map { RecordingItem(it.first) }
        .toList()
    return items.takeIf { it.isNotEmpty() }?.let(HomeSection::ExpiringSoon)
}

/** Next-first scheduled recordings; nothing scheduled means no section at all. */
private fun upcomingSection(input: HomeInput): HomeSection.Upcoming? {
    val scheduled = input.dvrEntries
        .filter { it.state == DvrEntryState.SCHEDULED }
        .sortedBy { it.start?.epochSeconds ?: Long.MAX_VALUE }
    if (scheduled.isEmpty()) return null
    return HomeSection.Upcoming(scheduled.take(10).map { UpcomingItem(it) })
}

private fun isRunningAt(event: EpgEventEntry, nowSec: Long): Boolean =
    event.start.epochSeconds <= nowSec && nowSec < event.stop.epochSeconds

private fun HomeSection.itemKeys(): List<String> = when (this) {
    is HomeSection.CurrentActivity -> items.map { it.itemKey() }
    is HomeSection.RecentChannels -> items.map { "channel:${it.channel.id.value}" }
    is HomeSection.OnNow -> items.map { "onnow:${it.channel.id.value}" }
    is HomeSection.Genres -> items.map { "genre:${it.category.name}" }
    is HomeSection.ContinueWatching -> items.map { "recording:${it.entry.id.value}" }
    is HomeSection.ExpiringSoon -> items.map { "recording:${it.entry.id.value}" }
    is HomeSection.RecentlyRecorded -> items.map { "recording:${it.entry.id.value}" }
    is HomeSection.Upcoming -> items.map { "recording:${it.entry.id.value}" }
}

/** The playing side names the card when one plays; otherwise the running recording does. */
private fun CurrentActivityItem.itemKey(): String = when (val playing = playing) {
    is HomePlaying.LiveChannel -> "live:${playing.channelId.value}"
    is HomePlaying.Recording -> "recording:${playing.entryId.value}"
    null -> "recording:${recording?.id?.value}"
}
