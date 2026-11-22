package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class ChannelsMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val observation = SessionObservation.create(
        epgState = EpgRepositoryState.Current(guide.snapshot), dvrState = DvrRepositoryState.Current(guide.dvr),
    )
    private val texts = ChannelsTexts(
        clock = { "at $it" },
        timeRange = { start, stop -> "$start-$stop" },
        duration = { "${it}s" },
        episode = { it.subtitle },
        genre = { it.genre },
    )
    private val unreachable = ConnectionUiState.Error(
        ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY,
    )

    @Test
    fun connectionAndScopeChooseTheState() {
        val all = listOf(guide.first, guide.second)
        data class Case(
            val name: String,
            val connection: ConnectionUiState,
            val channels: List<Channel>,
            val visible: List<Channel> = channels,
            val current: Boolean = true,
            val loaded: Boolean = true,
            val tagged: Boolean = false,
            val degraded: Boolean = false,
            val expected: (ChannelsState) -> Boolean,
        )
        val cases = listOf(
            Case("password unreadable", ConnectionUiState.CredentialUnavailable, all) { it == ChannelsState.SignInNeeded },
            Case("settings not read yet", ConnectionUiState.Ready, all, loaded = false) { it == ChannelsState.Loading },
            Case("connecting, nothing known", ConnectionUiState.Connecting, emptyList()) { it == ChannelsState.Loading },
            Case("catalog still arriving", ConnectionUiState.Ready, emptyList(), current = false) {
                it == ChannelsState.Loading
            },
            Case("server has no channels", ConnectionUiState.Ready, emptyList()) {
                it is ChannelsState.Ready && it.list.rows.isEmpty() && it.list.selectedTagId == null
            },
            Case("unreachable, nothing saved", unreachable, emptyList()) {
                it is ChannelsState.Failed && it.error.condition == AppErrorCondition.UNREACHABLE
            },
            Case("tag without channels", ConnectionUiState.Ready, all, visible = emptyList(), tagged = true) {
                it is ChannelsState.Ready && it.list.rows.isEmpty() && it.list.selectedTagId == guide.news.id.value.toString()
            },
            Case("offline with saved channels", unreachable, all, degraded = true) {
                it is ChannelsState.Offline && it.list.rows.size == 2 && !it.list.hasLogos
            },
            Case("only visible channel has no logo", ConnectionUiState.Ready, all, visible = listOf(guide.second)) {
                it is ChannelsState.Ready && it.list.rows.size == 1 && !it.list.hasLogos
            },
            Case("connected", ConnectionUiState.Ready, all) { it is ChannelsState.Ready && it.list.rows.size == 2 },
        )
        cases.forEach { case ->
            val scope = ChannelScopeState(
                ChannelBrowsingScope(
                    allChannels = case.channels, visibleChannels = case.visible, tags = listOf(guide.news),
                    allChannelsVisible = !case.tagged, activeTagId = guide.news.id.takeIf { case.tagged },
                ),
                channelCatalogCurrent = case.current,
                settingsLoaded = case.loaded,
            )
            val state = channelsState(scope, case.connection, case.degraded, observation, guide.now, texts)
            assertEquals(case.name, true, case.expected(state))
        }
    }

    @Test
    fun rowsCarryTheGuideAroundNowAndThePlayingChannel() {
        val scope = ChannelScopeState(
            ChannelBrowsingScope(
                allChannels = listOf(guide.first, guide.second), visibleChannels = listOf(guide.first, guide.second),
                tags = listOf(guide.news), allChannelsVisible = true, activeTagId = null,
            ),
            channelCatalogCurrent = true,
        )
        val playing = guide.first.id.value.toString()
        val state = channelsState(scope, ConnectionUiState.Ready, false, observation, guide.now, texts, playing)
        val list = (state as ChannelsState.Ready).list

        assertEquals(listOf(ChannelTag(guide.news.id.value.toString(), "News")), list.tags)
        val (first, second) = list.rows
        assertEquals("Channel One", first.name)
        assertEquals(guide.programs[0], first.now?.title)
        assertEquals(guide.programs[1], first.next?.title)
        assertEquals(guide.programs.drop(2), first.details?.following?.map { it.title })
        // The playing row shows how far its running program is: half of the hour.
        assertEquals(0.5f, first.playback?.progress)
        assertEquals(null, second.playback)
        // The program on now: its range, what is left and how far it is on air; the ones ahead start.
        val start = guide.now.epochSeconds - HALF_HOUR
        assertEquals("$start-${start + 2 * HALF_HOUR}", first.now?.timeRange)
        assertEquals("${HALF_HOUR}s", first.now?.timeLeft)
        assertEquals(0.5f, first.now?.progress)
        assertEquals("at ${start + 2 * HALF_HOUR}", first.next?.start)
        assertEquals(null, first.next?.progress)
        assertEquals(listOf(4, 6).map { "at ${start + it * HALF_HOUR}" }, first.details?.following?.map { it.start })
        assertEquals("About Evening News", first.details?.synopsis)
        assertEquals("${2 * HALF_HOUR}s", first.details?.metadata)
        // A channel without guide data has nothing to open.
        assertEquals(null, second.now)
        assertEquals(null, second.details)
    }

    @Test
    fun aGuideUpdateReusesUnchangedRows() {
        val channels = listOf(guide.first, guide.second)
        val scope = ChannelScopeState(ChannelBrowsingScope(channels, channels, emptyList(), true, null), true)
        val session = FakeTvheadendSession()
        val otherShow = EpgEvent.create(
            EventId(100), guide.second.id, guide.now - 10.minutes, guide.now + 10.minutes, title = "Other show",
        )
        fun publish(epg: EpgSnapshot): SessionObservation {
            session.publish(guide.connected(epg = EpgSnapshot.create(epg.events + otherShow)))
            return session.observation.value
        }
        val previous = mutableMapOf<ChannelId, MappedChannelRow>()
        val before = channelsState(
            scope, ConnectionUiState.Ready, false, publish(guide.snapshot), guide.now, texts, mappedRows = previous,
        ) as ChannelsState.Ready
        val after = channelsState(
            scope, ConnectionUiState.Ready, false, publish(guide.refreshedEpg("Updated News")),
            guide.now, texts, previousRows = previous,
        ) as ChannelsState.Ready

        assertEquals("Updated News", after.list.rows.first().now?.title)
        assertSame(before.list.rows.last(), after.list.rows.last())
    }

    @Test
    fun cachedRowsRefreshClockRecordingControlsAndPlayingMark() {
        val channels = listOf(guide.first, guide.second)
        val scope = ChannelScopeState(ChannelBrowsingScope(channels, channels, emptyList(), true, null), true)
        var previous = emptyMap<ChannelId, MappedChannelRow>()
        fun map(at: Instant, entries: List<DvrEntry>, playing: Channel): List<ChannelRow> {
            val next = mutableMapOf<ChannelId, MappedChannelRow>()
            val result = channelsState(
                scope, ConnectionUiState.Ready, false, guide.connected(entries), at, texts, playing.id.value.toString(),
                previousRows = previous, mappedRows = next,
            ) as ChannelsState.Ready
            previous = next
            return result.list.rows
        }
        val before = map(guide.now, emptyList(), guide.first).first()
        val minute = guide.now + 1.minutes
        val afterMinute = map(minute, emptyList(), guide.first).first()
        assertEquals("${HALF_HOUR - 60}s", afterMinute.now?.timeLeft)
        check(requireNotNull(afterMinute.now?.progress) > requireNotNull(before.now?.progress))

        val scheduled = map(minute, listOf(guide.scheduling(99, 1)), guide.first).first()
        assertEquals(ProgramRecordControl.Cancel, scheduled.now?.record)
        val recording = map(minute, listOf(guide.recordingNow), guide.first).first()
        assertEquals(ChannelRecording.Program, recording.recording)
        assertEquals(ProgramRecordControl.Stop, recording.now?.record)

        val (notPlaying, playing) = map(minute, emptyList(), guide.second)
        assertEquals(null, notPlaying.playback)
        assertEquals(null, notPlaying.recording)
        assertEquals(ProgramRecordControl.Record, notPlaying.now?.record)
        assertEquals(ChannelPlayback(null), playing.playback)
        val boundary = map(guide.now + 30.minutes, emptyList(), guide.second).first()
        assertEquals(guide.programs[1], boundary.now?.title)
        assertEquals(0f, boundary.now?.progress)
    }

    @Test
    fun marksFollowTheLinkedRecordingThenTheChannelsRecording() {
        data class Case(
            val name: String,
            val entries: List<DvrEntry>,
            val first: ChannelRecording?,
            val second: ChannelRecording?,
        )
        val elsewhere = DvrEntry.create(
            id = DvrEntryId(99), channelId = guide.second.id, start = guide.now - 10.minutes,
            stop = guide.now + 10.minutes, title = "Unlisted", state = DvrEntryState.RECORDING,
        )
        val cases = listOf(
            Case("the program on now is recorded", listOf(guide.recordingNow), ChannelRecording.Program, null),
            Case("a recording without a program", listOf(elsewhere), null, ChannelRecording.Channel),
            Case("finished and scheduled ones mark no row", listOf(guide.film, guide.scheduled), null, null),
            Case("a removed recording marks nothing", listOf(elsewhere.copyRemoved()), null, null),
        )
        cases.forEach { case ->
            val observed = SessionObservation.create(
                epgState = EpgRepositoryState.Current(guide.snapshot),
                dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = case.entries)),
            )
            val first = channelRow(guide.first, observed, guide.now, texts = texts)
            val second = channelRow(guide.second, observed, guide.now, texts = texts)
            assertEquals(case.name, case.first, first.recording)
            assertEquals(case.name, case.second, second.recording)
        }
        // The scheduled recording marks its program among the following ones only.
        val row = channelRow(guide.first, observation, guide.now, texts = texts)
        assertEquals(listOf(true, false), row.details?.following?.map { it.scheduled })
        assertEquals(false, row.next?.scheduled)
    }

    @Test
    fun rowsCarryTheServersImagesByTheSession() {
        val artwork = ArtworkModels(tvheadend = null)
        val connected = guide.connected()
        val session = connected.currentSession
        val logo = AppArtworkSource(session, (guide.logo as ChannelIcon.ImageCache).artworkId)
        val row = channelRow(guide.first, connected, guide.now, texts = texts, artwork = artwork)
        assertEquals(logo, row.logoModel)
        assertEquals(true, ChannelList(listOf(row)).hasLogos)
        assertEquals(AppArtworkSource(session, guide.picture), row.details?.artworkModel)
        assertEquals(null, channelRow(guide.second, connected, guide.now, artwork = artwork).logoModel)
        // Without a current session the logo falls back to its saved copy and pictures stay out.
        val offline = channelRow(guide.first, observation, guide.now, texts = texts, artwork = artwork)
        assertEquals(AppArtworkSource(null, logo.id), offline.logoModel)
        assertEquals(null, offline.details?.artworkModel)
    }

    private fun DvrEntry.copyRemoved() = DvrEntry.create(
        id = id, channelId = channelId, start = start, stop = stop, title = title, state = DvrEntryState.REMOVED,
    )

    private companion object {
        const val NOW = 1_800_000_000L
        const val HALF_HOUR = 1_800L
    }
}
