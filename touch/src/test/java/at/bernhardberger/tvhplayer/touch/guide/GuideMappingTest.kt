package at.bernhardberger.tvhplayer.touch.guide

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class GuideMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val clock = GuideClock(NOW, ZoneOffset.UTC)
    private val texts = GuideTexts(
        clock = { LocalTime.ofSecondOfDay(it % DAY_SEC).toString() },
        timeRange = { start, stop -> "${LocalTime.ofSecondOfDay(start % DAY_SEC)}–${LocalTime.ofSecondOfDay(stop % DAY_SEC)}" },
        duration = { "${it / 60}m" },
        day = { date, today -> if (date == today) "Today" else date.toString() },
        date = { it.toString() },
        airing = { start, _ -> start.toString() },
        number = { null },
        episode = { null },
        genre = { it.genre },
        firstAired = { it.toString() },
    )
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

        val next = state(GuideSelection(day = 1), events)
        assertEquals(listOf("Tomorrow show"), next.channels[1].day?.map { it.title })
        assertEquals(listOf("Tomorrow show"), next.channels[1].programs.map { it.title })
    }

    @Test
    fun aJumpMovesTheOverviewAndDetailsCarryTheProgram() {
        val evening = state(GuideSelection(jump = 1))
        assertEquals(emptyList<GuideProgram>(), evening.channels[0].programs)
        assertEquals(1, evening.selectedJump)

        val details = state(GuideSelection(detailsId = "1")).details.getValue("1")
        assertEquals("Channel One", details.channel)
        assertEquals("About ${guide.programs[0]}", details.synopsis)
        assertEquals("07:30–08:30", details.timeRange)
        assertEquals(true, details.recording)
    }

    private fun state(selection: GuideSelection, events: List<EpgEvent> = guide.snapshot.events): GuideState {
        val all = listOf(guide.first, guide.second)
        return guideState(
            scope(all, all, tagged = false, loaded = true), ConnectionUiState.Ready, false,
            observation(EpgSnapshot.create(events)), selection, false, clock, texts,
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
