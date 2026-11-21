package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgCredit
import at.bernhardberger.tvheadend.sdk.core.EpgCreditRole
import at.bernhardberger.tvheadend.sdk.core.EpgEpisode
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRating
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class GuideMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val clock = GuideClock(NOW, ZoneOffset.UTC)
    private val texts = guide.texts
    private val unreachable = ConnectionUiState.Error(
        ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY,
    )

    @Test
    fun connectionScopeAndGuideChooseTheState() {
        val all = listOf(guide.first, guide.second)
        data class Case(
            val name: String,
            val connection: ConnectionUiState,
            val channels: List<Channel>,
            val visible: List<Channel> = channels,
            val loaded: Boolean = true,
            val tagged: Boolean = false,
            val withGuide: Boolean = true,
            val degraded: Boolean = false,
            val expected: (GuideState) -> Boolean,
        )
        val cases = listOf(
            Case("password unreadable", ConnectionUiState.CredentialUnavailable, all) {
                it.availability == GuideAvailability.SignInNeeded
            },
            Case("settings not read yet", ConnectionUiState.Ready, all, loaded = false) {
                it.availability == GuideAvailability.Loading
            },
            Case("server has no channels", ConnectionUiState.Ready, emptyList()) {
                it.availability == GuideAvailability.Current && it.channels.isEmpty() && it.selectedTagId == null
            },
            Case("unreachable, nothing saved", unreachable, emptyList()) {
                val availability = it.availability
                availability is GuideAvailability.Failed && availability.error.condition == AppErrorCondition.UNREACHABLE
            },
            Case("tag without channels", ConnectionUiState.Ready, all, visible = emptyList(), tagged = true) {
                it.listed && it.channels.isEmpty() && it.selectedTagId == guide.news.id.value.toString()
            },
            Case("channels, guide still arriving", ConnectionUiState.Ready, all, withGuide = false) {
                it.availability == GuideAvailability.Loading
            },
            Case("offline with the saved guide", unreachable, all, degraded = true) {
                it.availability == GuideAvailability.Offline && it.channels.size == 2
            },
            Case("connected", ConnectionUiState.Ready, all) {
                it.availability == GuideAvailability.Current && it.channels.size == 2
            },
        )
        cases.forEach { case ->
            val scope = scope(case.channels, case.visible, case.tagged, case.loaded)
            val observation = if (case.withGuide) observation() else SessionObservation.create()
            val state = guideState(scope, case.connection, case.degraded, observation, GuideSelection(), false, clock, texts)
            assertEquals(case.name, true, case.expected(state))
        }
    }

    @Test
    fun overviewRunsFromNowWithMarksAndProgress() {
        val state = state(GuideSelection())
        val (first, second) = state.channels

        assertEquals(guide.programs, first.programs.map { it.title })
        val onNow = first.programs[0]
        assertSame("overview and the channel day reuse the same mapped program", onNow, first.day?.first())
        assertEquals(0.5f, onNow.progress)
        assertEquals("30m", onNow.timeLeft)
        assertEquals("07:30", onNow.start)
        assertEquals(true, onNow.recording)
        assertEquals(listOf(false, false, true, false), first.programs.map { it.scheduled })
        assertEquals(null, first.programs[1].progress)
        assertEquals(emptyList<GuideProgram>(), second.programs)
        // Later today and today's jump times; nothing on the second channel after today.
        assertEquals(listOf("Today"), state.days)
        assertEquals(listOf("20:15", "22:00"), state.jumps)
    }

    @Test
    fun aGapEndsTheOverviewAndALaterDayGetsItsTab() {
        val late = event(EVENT_LATE, guide.first, Instant.fromEpochSeconds(NOW) + 5.hours)
        val tomorrow = event(EVENT_TOMORROW, guide.second, Instant.fromEpochSeconds(NOW) + 1.days)
        val events = guide.snapshot.events + late + tomorrow

        val today = state(GuideSelection(), events)
        assertEquals(guide.programs, today.channels[0].programs.map { it.title })
        assertEquals(GuideGap("11:30", "13:00"), today.channels[0].gap)
        assertEquals(listOf("Today", "2027-01-16"), today.days)

        val next = state(GuideSelection(day = LocalDate.parse("2027-01-16")), events)
        assertEquals(listOf("Tomorrow show"), next.channels[1].day?.map { it.title })
        assertEquals(GuideGap("00:00", "08:00"), next.channels[1].gap)
    }

    @Test
    fun aJumpMovesTheOverviewAndDetailsCarryTheProgram() {
        val selection = GuideSelection(jump = NOW + 12 * 3600 + 15 * 60)
        val evening = state(selection)
        assertEquals(emptyList<GuideProgram>(), evening.channels[0].programs)
        assertEquals(1, evening.selectedJump)
        val pastFirstJump = clock.copy(nowSec = NOW + 13 * 3600)
        assertEquals("A passed jump returns to Now", 0, state(selection, at = pastFirstJump).selectedJump)
        assertEquals("Yesterday's jump stays expired after midnight", 0, state(selection, at = clock.copy(nowSec = NOW + DAY_SEC)).selectedJump)

        val details = checkNotNull(state(GuideSelection(detailsId = "1")).details)
        assertEquals("Channel One", details.channel)
        assertEquals("About ${guide.programs[0]}", details.synopsis)
        assertEquals("07:30–08:30", details.timeRange)
        assertEquals(true, details.recording)
    }

    @Test
    fun aSelectedDateDoesNotMoveWhenAnEarlierDayAppearsOrTheDateDisappears() {
        val later = event(EVENT_TOMORROW, guide.second, guide.now + 2.days)
        val earlier = event(EVENT_LATE, guide.first, guide.now + 1.days)
        val selectedDate = LocalDate.parse("2027-01-17")
        val selection = GuideSelection(day = selectedDate)

        assertEquals("An inserted tab does not change the chosen date", selectedDate.toString(), state(selection, listOf(later, earlier)).date)
        val tomorrow = clock.copy(nowSec = NOW + DAY_SEC)
        assertEquals("Midnight does not change the chosen date", selectedDate.toString(), state(selection, listOf(later, earlier), tomorrow).date)
        assertEquals("A removed date returns to today", state(GuideSelection()).date, state(selection, listOf(earlier)).date)
    }

    @Test
    fun anEventCrossingMidnightAddsItsOverlappingDayButNotTheDayItEndsAtMidnight() {
        val late = event(EVENT_LATE, guide.first, guide.now + 15.hours + 30.minutes)
        val endingAtMidnight = event(EVENT_TOMORROW, guide.first, guide.now + 39.hours)
        assertEquals(listOf("Today", "2027-01-16"), state(GuideSelection(), listOf(late, endingAtMidnight)).days)
        assertEquals(listOf("Today", "2027-01-16"), state(GuideSelection(), listOf(late)).days)
        val next = state(GuideSelection(day = LocalDate.parse("2027-01-16")), listOf(late))
        assertEquals(listOf("Late show"), next.channels[0].day?.map { it.title })
        assertEquals(listOf("Late show"), next.channels[0].programs.map { it.title })
    }

    @Test
    fun aLateJumpDoesNotLeadWithATomorrowProgram() {
        val afterMidnight = event(EVENT_TOMORROW, guide.second, guide.now + 16.hours)
        val second = state(GuideSelection(jump = NOW + 14 * 3600), listOf(afterMidnight)).channels[1]
        assertEquals("Nothing overlaps the selected time on this channel", emptyList<GuideProgram>(), second.programs)
        assertEquals(GuideGap("22:00", "00:00"), second.gap)
    }

    @Test
    fun theServersImagesReachChannelsProgramsAndDetails() {
        val all = listOf(guide.first, guide.second)
        val connected = guide.connected()
        val state = guideState(
            scope(all, all, tagged = false, loaded = true), ConnectionUiState.Ready, false, connected,
            GuideSelection(detailsId = "1"), false, clock, texts, ArtworkModels(tvheadend = null),
        )
        val logo = AppArtworkSource(connected.currentSession, (guide.logo as ChannelIcon.ImageCache).artworkId)
        val picture = AppArtworkSource(connected.currentSession, guide.picture)
        val (first, second) = state.channels
        assertEquals(logo, first.logoModel)
        assertEquals(null, second.logoModel)
        assertEquals(listOf(picture, null, null, null), first.programs.map { it.stillModel })
        val details = checkNotNull(state.details)
        assertEquals(logo, details.channelLogoModel)
        assertEquals(picture, details.pictureModel)
    }

    @OptIn(ExperimentalTvheadendApi::class)
    @Test
    fun detailsCarryTheTvsFactsAndCreditsAndTheWatchedProgram() {
        val running = guide.snapshot.events.first()
        val full = EpgEvent.create(
            running.id, running.channelId, running.start, running.stop, title = running.title,
            genre = "News", categories = listOf(" Talk ", "Talk", ""), keywords = listOf("Politics"),
            rating = EpgRating(age = null, label = "12", icon = null, authority = null, country = null, stars = 4),
            copyrightYear = 2026, isNew = false,
            episode = EpgEpisode(
                id = null, seriesLinkId = null, seasonNumber = 2, seasonCount = 3, episodeNumber = 7,
                episodeCount = 5, partNumber = null, partCount = null, onscreen = "Episode seven",
            ),
            credits = listOf(
                EpgCredit("Ada", EpgCreditRole.PRESENTER), EpgCredit(" Ada ", EpgCreditRole.PRESENTER),
                EpgCredit("Ben", EpgCreditRole.GUEST), EpgCredit("Cleo", EpgCreditRole.ACTOR),
            ),
        )
        val events = listOf(full) + guide.snapshot.events.drop(1)
        val all = listOf(guide.first, guide.second)
        fun shown(playing: String?) = guideState(
            scope(all, all, tagged = false, loaded = true), ConnectionUiState.Ready, false,
            observation(EpgSnapshot.create(events)), GuideSelection(detailsId = "1"), false, clock, texts,
            playing = playing,
        )
        val details = checkNotNull(shown(playing = guide.first.id.value.toString()).details)
        assertEquals(
            listOf(
                FactKey.Genre to "News", FactKey.Season to "2 of 3", FactKey.Episode to "7",
                FactKey.AgeRating to "12+", FactKey.Stars to "4", FactKey.Broadcast to "Repeat",
                FactKey.Categories to "Talk", FactKey.Keywords to "Politics",
            ),
            details.facts.filter { it.key != FactKey.Year }.map { it.key to it.value },
        )
        assertEquals(
            listOf(
                CreditRole.Cast to listOf("Cleo"), CreditRole.Guests to listOf("Ben"),
                CreditRole.Presenters to listOf("Ada"),
            ),
            details.credits.map { it.role to it.names },
        )
        assertEquals(true, details.watching)
        val overview = shown(playing = guide.first.id.value.toString()).channels.first().programs
        assertEquals(listOf(true, false, false, false), overview.map { it.watching })
        assertEquals(false, shown(playing = guide.second.id.value.toString()).details?.watching)
    }

    private fun state(
        selection: GuideSelection,
        events: List<EpgEvent> = guide.snapshot.events,
        at: GuideClock = clock,
    ): GuideState {
        val all = listOf(guide.first, guide.second)
        return guideState(
            scope(all, all, tagged = false, loaded = true), ConnectionUiState.Ready, false,
            observation(EpgSnapshot.create(events)), selection, false, at, texts,
        )
    }

    private fun scope(channels: List<Channel>, visible: List<Channel>, tagged: Boolean, loaded: Boolean) =
        ChannelScopeState(
            ChannelBrowsingScope(
                allChannels = channels, visibleChannels = visible, tags = listOf(guide.news),
                allChannelsVisible = !tagged, activeTagId = guide.news.id.takeIf { tagged },
            ),
            channelCatalogCurrent = true,
            settingsLoaded = loaded,
        )

    private fun observation(snapshot: EpgSnapshot = guide.snapshot) = SessionObservation.create(
        epgState = EpgRepositoryState.Current(snapshot),
        dvrState = DvrRepositoryState.Current(guide.dvr),
    )

    private fun event(id: Long, channel: Channel, start: Instant) = EpgEvent.create(
        EventId(id), channel.id, start, start + 1.hours,
        title = if (id == EVENT_TOMORROW) "Tomorrow show" else "Late show",
    )

    private companion object {
        // 2027-01-15 08:00 UTC.
        const val NOW = 1_800_000_000L
        const val DAY_SEC = 86_400L
        const val EVENT_LATE = 20L
        const val EVENT_TOMORROW = 21L
    }
}
