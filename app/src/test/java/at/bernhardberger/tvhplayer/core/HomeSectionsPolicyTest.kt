package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
class HomeSectionsPolicyTest {
    private val nowSec = 1_000_000_000L

    @Test fun hiddenSectionsAreOmittedBeforeInitialAndRestoreFocusSelection() {
        val input = homeInput(playing = HomePlaying.LiveChannel(ChannelId(1)))
        val original = homeSections(input)
        assertEquals(HomeSectionKind.CURRENT_ACTIVITY, homeInitialFocus(original)?.section)
        val hidden = setOf(HomeSectionKind.CURRENT_ACTIVITY)
        val visible = homeSections(input.copy(hiddenSections = hidden))
        assertTrue(visible.none { it.kind in hidden })
        assertEquals(homeInitialFocus(visible), homeRestoreFocus(visible, requireNotNull(homeInitialFocus(original))))
        assertTrue(homeSections(input.copy(hiddenSections = HomeSectionKind.entries.toSet())).isEmpty())
    }

    private fun channel(id: Long, number: Long? = id, name: String = "Ch $id") = Channel.create(
        id = ChannelId(id),
        name = name,
        number = number,
        numberMinor = null,
    )

    private fun event(
        id: Long,
        channelId: Long? = null,
        start: Long,
        stop: Long,
        contentType: Long? = null,
    ) = EpgEvent.create(
        id = EventId(id),
        channelId = channelId?.let(::ChannelId),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = "Event $id",
        contentGenre = ContentGenre.fromContentType(contentType),
    )

    private fun entry(
        id: Long,
        state: DvrEntryState = DvrEntryState.COMPLETED,
        start: Long? = null,
        channelId: Long? = null,
    ) = DvrEntry.create(
        DvrEntryId(id),
        channelId = channelId?.let(::ChannelId),
        state = state,
        start = start?.let(Instant::fromEpochSeconds),
    )

    /** A completed admission with an SDK-verified resume position; null means none is offered. */
    private fun savedPosition(seconds: Long?) = seconds
        ?.let { TvheadendTestResultFactory.completedRecordingAdmission(it.seconds, RecordingProgressCapability.SUPPORTED) }

    private fun admissions(mapping: Map<Long, Long?>) = { entry: DvrEntry -> savedPosition(mapping[entry.id.value]) }

    @Test fun playingCardLeadsAndRunningRecordingsFollow() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(11, DvrEntryState.RECORDING, start = nowSec - 600, channelId = 1),
                entry(12, DvrEntryState.RECORDING, start = nowSec - 300, channelId = 2),
            ),
            playing = HomePlaying.LiveChannel(ChannelId(1)),
        )
        val records = homeSections(input).filterIsInstance<HomeSection.CurrentActivity>().single().items
        assertEquals(2, records.size)
        assertEquals(HomePlaying.LiveChannel(ChannelId(1)), records[0].playing)
        assertEquals(11L, records[0].recording?.id?.value)
        assertNull(records[1].playing)
        assertEquals(12L, records[1].recording?.id?.value)
    }

    @Test fun playingChannelBeingRecordedStaysOneCard() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(11, DvrEntryState.RECORDING, start = nowSec - 300, channelId = 1),
                entry(13, DvrEntryState.RECORDING, start = nowSec - 200, channelId = 1),
                entry(12, DvrEntryState.RECORDING, start = nowSec - 100, channelId = 2),
            ),
            playing = HomePlaying.LiveChannel(ChannelId(1)),
        )
        val records = homeSections(input).filterIsInstance<HomeSection.CurrentActivity>().single().items
        assertEquals(2, records.size)
        assertEquals(HomePlaying.LiveChannel(ChannelId(1)), records[0].playing)
        assertEquals(11L, records[0].recording?.id?.value)
        assertEquals(12L, records[1].recording?.id?.value)
        assertTrue(records.none { it.recording?.id?.value == 13L })
    }

    @Test fun playingRecordingKeepsOneMarkedCard() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(11, DvrEntryState.RECORDING, start = nowSec - 300, channelId = 1),
                entry(12, DvrEntryState.RECORDING, start = nowSec - 100, channelId = 2),
            ),
            playing = HomePlaying.Recording(DvrEntryId(11)),
        )
        val records = homeSections(input).filterIsInstance<HomeSection.CurrentActivity>().single().items
        assertEquals(2, records.size)
        assertEquals(HomePlaying.Recording(DvrEntryId(11)), records[0].playing)
        assertEquals(11L, records[0].recording?.id?.value)
        assertNull(records[1].playing)
        assertEquals(12L, records[1].recording?.id?.value)
    }

    @Test fun playingRecordingNotRunningYetStillGetsAPlaybackCard() {
        val input = homeInput(
            dvrEntries = listOf(entry(20, start = nowSec - 100)),
            playing = HomePlaying.Recording(DvrEntryId(20)),
        )
        val records = homeSections(input).filterIsInstance<HomeSection.CurrentActivity>().single().items
        assertEquals(1, records.size)
        assertEquals(HomePlaying.Recording(DvrEntryId(20)), records[0].playing)
        assertNull(records[0].recording)
    }

    @Test fun recentsFollowStoreOrderInScopeWithoutPlayingChannel() {
        val input = homeInput(
            channels = listOf(channel(1), channel(2), channel(4)),
            recentChannelIds = listOf(ChannelId(4), ChannelId(9), ChannelId(2), ChannelId(1), ChannelId(3)),
            playing = HomePlaying.LiveChannel(ChannelId(3)),
        )
        val recents = homeSections(input).filterIsInstance<HomeSection.RecentChannels>().single().items
        assertEquals(listOf(4L, 2L, 1L), recents.map { it.channel.id.value })
        assertTrue(recents.none { it.channel.id.value == 9L })
        assertTrue(recents.none { it.channel.id.value == 3L })
    }

    @Test fun recentsCapAtEight() {
        val input = homeInput(
            channels = (1L..10L).map(::channel),
            recentChannelIds = (10L downTo 1L).map(::ChannelId),
        )
        val recents = homeSections(input).filterIsInstance<HomeSection.RecentChannels>().single().items
        assertEquals(listOf(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L), recents.map { it.channel.id.value })
    }

    @Test fun onNowRunsInChannelNumberOrderPastEndedEvents() {
        val input = homeInput(
            channels = listOf(channel(100, number = 30), channel(101, number = 10), channel(102, number = 20)),
            currentEvents = mapOf(
                ChannelId(100) to event(1, 100, nowSec - 100, nowSec + 100),
                ChannelId(101) to event(2, 101, nowSec - 100, nowSec + 100),
                ChannelId(102) to event(3, 102, nowSec - 100, nowSec - 1),
            ),
        )
        val onNow = homeSections(input).filterIsInstance<HomeSection.OnNow>().single().items
        assertEquals(listOf(101L, 100L), onNow.map { it.channel.id.value })
        assertTrue(onNow.none { it.channel.id.value == 102L })
    }

    @Test fun onNowHasNoCap() {
        val channels = (1L..12L).map(::channel)
        val input = homeInput(
            channels = channels,
            currentEvents = channels.associate { it.id to event(it.id.value, it.id.value, nowSec - 10, nowSec + 10) },
        )
        assertEquals(12, homeSections(input).filterIsInstance<HomeSection.OnNow>().single().items.size)
    }

    @Test fun genreRowUsesGuideOrderWithinScope() {
        val input = homeInput(
            channels = listOf(channel(1), channel(2), channel(3)),
            currentEvents = mapOf(
                ChannelId(1) to event(1, 1, nowSec - 10, nowSec + 10, contentType = 0x10L), // FILM_DRAMA
            ),
            upcomingEvents = listOf(
                event(2, 2, nowSec + 3600, nowSec + 4000, contentType = 0x20L), // NEWS, starts last
                event(3, 3, nowSec + 100, nowSec + 200, contentType = 0x50L), // CHILDREN
                event(4, 2, nowSec + 50, nowSec + 80, contentType = 0x30L), // ENTERTAINMENT
                event(5, 9, nowSec + 10, nowSec + 20, contentType = 0x40L), // SPORT, out of scope
            ),
        )
        val genres = homeSections(input).filterIsInstance<HomeSection.Genres>().single().items
        assertEquals(
            listOf(ProgrammeCategory.FILM_DRAMA, ProgrammeCategory.NEWS, ProgrammeCategory.ENTERTAINMENT, ProgrammeCategory.CHILDREN),
            genres.map { it.category },
        )
    }

    @Test fun genreRowNeedsGenreCodes() {
        val input = homeInput(
            channels = listOf(channel(1)),
            currentEvents = mapOf(
                ChannelId(1) to event(1, 1, nowSec - 10, nowSec + 10, contentType = 0xb0L), // special, uncategorised
            ),
            upcomingEvents = listOf(event(2, 1, nowSec + 10, nowSec + 20)),
        )
        assertTrue(homeSections(input).none { it is HomeSection.Genres })
    }

    @Test fun genreRowKeepsProgrammesWithin24Hours() {
        val inside = homeInput(
            channels = listOf(channel(1)),
            upcomingEvents = listOf(
                event(1, 1, nowSec + 24 * 3600, nowSec + 24 * 3600 + 100, contentType = 0x10L),
            ),
        )
        assertEquals(
            listOf(ProgrammeCategory.FILM_DRAMA),
            homeSections(inside).filterIsInstance<HomeSection.Genres>().single().items.map { it.category },
        )
        val outside = inside.copy(
            upcomingEvents = listOf(
                event(1, 1, nowSec + 24 * 3600 + 1, nowSec + 24 * 3600 + 100, contentType = 0x10L),
            ),
        )
        assertTrue(homeSections(outside).none { it is HomeSection.Genres })
    }

    @Test fun continueWatchingKeepsOfferedResumePositionsNewestFirst() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(30, start = nowSec - 3000),
                entry(20, DvrEntryState.RECORDING, start = nowSec - 1000),
                entry(10, start = nowSec - 100),
                entry(40, start = nowSec - 50),
            ),
            admission = { entry ->
                when (entry.id.value) {
                    30L -> savedPosition(300L)
                    20L -> TvheadendTestResultFactory.growingRecordingAdmission(0.seconds, RecordingProgressCapability.SUPPORTED)
                    else -> null
                }
            },
        )
        val watching = homeSections(input).filterIsInstance<HomeSection.ContinueWatching>().single().items
        assertEquals(listOf(20L, 30L), watching.map { it.entry.id.value })
    }

    @Test fun continueWatchingCapAtTen() {
        val input = homeInput(
            dvrEntries = (1L..11L).map { entry(it, start = nowSec - it) },
            admission = {
                TvheadendTestResultFactory.completedRecordingAdmission(30.seconds, RecordingProgressCapability.SUPPORTED)
            },
        )
        val watching = homeSections(input).filterIsInstance<HomeSection.ContinueWatching>().single().items
        assertEquals((1L..10L).toList(), watching.map { it.entry.id.value })
    }

    @Test fun recentlyRecordedKeepsUnstartedFinishedOfTheLastSevenDays() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(50, start = nowSec - 7 * 86400),
                entry(51, start = nowSec - 7 * 86400 - 1),
                entry(52, DvrEntryState.RECORDING_ERROR, start = nowSec - 100),
                entry(53, DvrEntryState.RECORDING, start = nowSec - 200),
                entry(54, start = nowSec - 100),
            ),
            admission = { entry ->
                when (entry.id.value) {
                    54L -> savedPosition(10L)
                    53L -> TvheadendTestResultFactory.growingRecordingAdmission(10.seconds, RecordingProgressCapability.SUPPORTED)
                    else -> null
                }
            },
        )
        val recorded = homeSections(input).filterIsInstance<HomeSection.RecentlyRecorded>().single().items
        assertEquals(listOf(50L), recorded.map { it.entry.id.value })
    }

    @Test fun aRecordingWithAResumePositionNeverRepeatsAsRecentlyRecorded() {
        val input = homeInput(
            dvrEntries = listOf(
                entry(70, start = nowSec - 300),
                entry(71, start = nowSec - 200),
            ),
            admission = admissions(mapOf(70L to 30L, 71L to null)),
        )
        val sections = homeSections(input)
        assertEquals(listOf(70L), sections.filterIsInstance<HomeSection.ContinueWatching>().single().items.map { it.entry.id.value })
        assertEquals(listOf(71L), sections.filterIsInstance<HomeSection.RecentlyRecorded>().single().items.map { it.entry.id.value })
    }

    @Test fun sectionsFollowSpecOrderSkippingOmitted() {
        val input = homeInput(
            channels = listOf(channel(1), channel(2)),
            currentEvents = mapOf(
                ChannelId(2) to event(1, 2, nowSec - 10, nowSec + 10, contentType = 0x10L),
            ),
            recentChannelIds = listOf(ChannelId(2), ChannelId(1)),
            dvrEntries = listOf(
                entry(11, DvrEntryState.RECORDING, start = nowSec - 600, channelId = 2),
                entry(21, start = nowSec - 2000),
                entry(22, start = nowSec - 1000),
                entry(31, DvrEntryState.SCHEDULED, start = nowSec + 3600),
                entry(32, DvrEntryState.SCHEDULED, start = nowSec + 7200),
            ),
            admission = admissions(mapOf(21L to 30L, 22L to null)),
            playing = HomePlaying.LiveChannel(ChannelId(1)),
        )
        val sections = homeSections(input)
        assertEquals(
            listOf(
                HomeSectionKind.CURRENT_ACTIVITY,
                HomeSectionKind.RECENT_CHANNELS,
                HomeSectionKind.ON_NOW,
                HomeSectionKind.GENRES,
                HomeSectionKind.CONTINUE_WATCHING,
                HomeSectionKind.RECENTLY_RECORDED,
                HomeSectionKind.UPCOMING,
            ),
            sections.map { it.kind },
        )
        val upcoming = sections.filterIsInstance<HomeSection.Upcoming>().single().items
        assertEquals(listOf(31L, 32L), upcoming.map { it.entry.id.value })
    }

    @Test fun upcomingIsNextFirstCappedAtTenAndSkipsOtherStates() {
        val scheduled = (1L..12L).map { entry(it, DvrEntryState.SCHEDULED, start = nowSec + it * 60) }
        val sections = homeSections(homeInput(dvrEntries = scheduled.reversed() + entry(20, DvrEntryState.COMPLETED)))
        assertEquals((1L..10L).toList(), sections.filterIsInstance<HomeSection.Upcoming>().single().items.map { it.entry.id.value })
    }

    @Test fun emptyInputYieldsNoSections() {
        assertTrue(homeSections(homeInput()).isEmpty())
        assertNull(homeInitialFocus(emptyList()))
        assertNull(homeRestoreFocus(emptyList(), HomeFocusKey(HomeSectionKind.ON_NOW, "x", 0)))
    }

    @Test fun initialFocusIsTheFirstCardOfTheTopmostSection() {
        val input = homeInput(
            dvrEntries = listOf(entry(11, DvrEntryState.RECORDING, start = nowSec - 600, channelId = 1)),
            playing = HomePlaying.LiveChannel(ChannelId(1)),
        )
        assertEquals(
            HomeFocusKey(HomeSectionKind.CURRENT_ACTIVITY, "live:1", 0),
            homeInitialFocus(homeSections(input)),
        )
    }

    @Test fun initialFocusLandsOnTheRecordingCardWithoutPlayback() {
        val input = homeInput(dvrEntries = listOf(entry(11, DvrEntryState.RECORDING, start = nowSec - 600)))
        assertEquals(
            HomeFocusKey(HomeSectionKind.CURRENT_ACTIVITY, "recording:11", 0),
            homeInitialFocus(homeSections(input)),
        )
    }

    @Test fun initialFocusLandsOnOnNowWithoutHistory() {
        val input = homeInput(
            channels = listOf(channel(5)),
            currentEvents = mapOf(ChannelId(5) to event(1, 5, nowSec - 10, nowSec + 10)),
        )
        assertEquals(
            HomeFocusKey(HomeSectionKind.ON_NOW, "onnow:5", 0),
            homeInitialFocus(homeSections(input)),
        )
    }

    @Test fun restoreKeepsTheFocusedCardAtItsNewPosition() {
        val sections = watchingSections(70, 71, 72)
        assertEquals(
            HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:71", 1),
            homeRestoreFocus(sections, HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:71", 2)),
        )
    }

    @Test fun restoreAdvancesToTheNextCardInTheSameSection() {
        val sections = watchingSections(70, 71, 72)
        assertEquals(
            HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:71", 1),
            homeRestoreFocus(sections, HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:gone", 1)),
        )
    }

    @Test fun restoreFallsToTheSectionsFirstCardPastItsEnd() {
        val sections = watchingSections(70)
        assertEquals(
            HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:70", 0),
            homeRestoreFocus(sections, HomeFocusKey(HomeSectionKind.CONTINUE_WATCHING, "recording:70", 3)),
        )
    }

    @Test fun restoreRestartsAtInitialFocusWhenTheSectionIsGone() {
        val input = homeInput(
            channels = listOf(channel(5)),
            currentEvents = mapOf(ChannelId(5) to event(1, 5, nowSec - 10, nowSec + 10)),
        )
        val sections = homeSections(input)
        assertEquals(
            homeInitialFocus(sections),
            homeRestoreFocus(sections, HomeFocusKey(HomeSectionKind.RECENT_CHANNELS, "channel:5", 0)),
        )
    }

    private fun homeInput(
        channels: List<Channel> = emptyList(),
        currentEvents: Map<ChannelId, EpgEvent> = emptyMap(),
        upcomingEvents: List<EpgEvent> = emptyList(),
        recentChannelIds: List<ChannelId> = emptyList(),
        dvrEntries: List<DvrEntry> = emptyList(),
        admission: (DvrEntry) -> RecordingPlaybackAdmission? = { null },
        playing: HomePlaying? = null,
    ) = HomeInput(
        channels = channels,
        currentEvents = currentEvents,
        upcomingEvents = upcomingEvents,
        recentChannelIds = recentChannelIds,
        dvrEntries = dvrEntries,
        admission = admission,
        playing = playing,
        nowSec = nowSec,
    )

    private fun watchingSections(vararg ids: Long): List<HomeSection> = homeSections(
        homeInput(
            dvrEntries = ids.map { entry(it, start = nowSec - it) },
            admission = { savedPosition(10L) },
        ),
    )
}
