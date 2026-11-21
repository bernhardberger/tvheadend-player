package at.bernhardberger.tvhplayer.touch.channels

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ChannelBrowsingScope
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(TvheadendTestingApi::class)
class ChannelsMappingTest {
    private val guide = ServerGuide(Instant.fromEpochSeconds(NOW))
    private val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(guide.snapshot))
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
                it is ChannelsState.Offline && it.list.rows.size == 2
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
            val state = channelsState(scope, case.connection, case.degraded, observation, guide.now)
            assertEquals(case.name, true, case.expected(state))
        }
    }

    @Test
    fun rowsCarryTheGuideAroundNow() {
        val scope = ChannelScopeState(
            ChannelBrowsingScope(
                allChannels = listOf(guide.first, guide.second), visibleChannels = listOf(guide.first, guide.second),
                tags = listOf(guide.news), allChannelsVisible = true, activeTagId = null,
            ),
            channelCatalogCurrent = true,
        )
        val list = (channelsState(scope, ConnectionUiState.Ready, false, observation, guide.now) as ChannelsState.Ready).list

        assertEquals(listOf(ChannelTag(guide.news.id.value.toString(), "News")), list.tags)
        val (first, second) = list.rows
        assertEquals("Channel One", first.name)
        assertEquals(guide.programs[0], first.now?.title)
        assertEquals(guide.programs[1], first.next?.title)
        assertEquals(guide.programs.drop(2), first.details?.following?.map { it.title })
        // A channel without guide data has nothing to open.
        assertEquals(null, second.now)
        assertEquals(null, second.details)
    }

    private companion object {
        const val NOW = 1_800_000_000L
    }
}
