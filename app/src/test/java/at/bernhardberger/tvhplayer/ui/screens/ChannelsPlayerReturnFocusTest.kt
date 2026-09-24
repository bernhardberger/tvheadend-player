package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.resolveChannelScopeState
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Back from the live player re-enters Channels on the playing row, never the scope tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ChannelsPlayerReturnFocusTest {
    @get:org.junit.Rule val compose = createComposeRule()
    private var selected by mutableStateOf<ChannelId?>(null)
    private var tagSelections = mutableListOf<ChannelTagId?>()

    @Test fun returnFocusesPlayingRowAfterChannelChangeInsteadOfScopeTabs() {
        // Opened from row 3, then zapped to channel 24 (far below the first viewport).
        channels(activeTag = null, playerReturn = PlayerReturnFocus(ChannelId(24), ChannelId(3)))
        assertRowFocused(24)
        assertEquals(ChannelId(24), selected)
        assertEquals(emptyList<ChannelTagId?>(), tagSelections)
    }

    @Test fun returnOutsideCurrentGroupKeepsGroupAndFocusesOriginRow() {
        channels(activeTag = ChannelTagId(1), playerReturn = PlayerReturnFocus(ChannelId(24), ChannelId(4)))
        assertRowFocused(4)
        assertEquals(emptyList<ChannelTagId?>(), tagSelections)
    }

    @Test fun returnWithPlayingAndOriginOutsideGroupFocusesFirstGroupRow() {
        channels(activeTag = ChannelTagId(1), playerReturn = PlayerReturnFocus(ChannelId(24), ChannelId(20)))
        assertRowFocused(1)
        assertEquals(emptyList<ChannelTagId?>(), tagSelections)
    }

    @Test fun returnWaitsForChannelsThatArriveAfterComposition() {
        channels(activeTag = null, playerReturn = PlayerReturnFocus(ChannelId(24), ChannelId(3)), channelsLoaded = false)
        compose.runOnIdle { loaded = true }
        assertRowFocused(24)
    }

    @Test fun secondReturnOnRetainedCompositionIsPositionedAgain() {
        channels(activeTag = null, playerReturn = PlayerReturnFocus(ChannelId(24), ChannelId(3)))
        assertRowFocused(24)
        // Player on top, then Back again with an equal but distinct request after zapping
        // back to the channel that has since scrolled away from the focused row.
        compose.runOnIdle { focusEnabled = false; request = null; selected = ChannelId(2) }
        compose.waitForIdle()
        compose.runOnIdle { focusEnabled = true; request = PlayerReturnFocus(ChannelId(2), ChannelId(3)) }
        assertRowFocused(2)
        compose.runOnIdle { focusEnabled = false; request = null }
        compose.waitForIdle()
        compose.runOnIdle { focusEnabled = true; request = PlayerReturnFocus(ChannelId(24), ChannelId(3)) }
        assertRowFocused(24)
    }

    @Test fun ordinaryEntryStillStartsOnScopeTabs() {
        channels(activeTag = null, playerReturn = null)
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        val focusedTags = compose.onAllNodes(isFocused()).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.TestTag) }
        assertNull(focusedTags.firstOrNull { it?.startsWith("channel-row-") == true })
    }

    private fun assertRowFocused(id: Int) {
        val row = hasTestTag("channel-row-$id")
        compose.waitUntil(10_000) {
            compose.onAllNodes(row and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("channel-row-$id").assertIsFocused().assertIsDisplayed()
    }

    private var loaded by mutableStateOf(true)
    private var request by mutableStateOf<PlayerReturnFocus?>(null)
    private var focusEnabled by mutableStateOf(true)

    private fun channels(activeTag: ChannelTagId?, playerReturn: PlayerReturnFocus?, channelsLoaded: Boolean = true) {
        val channels = (1..30).map { n ->
            Channel.create(
                id = ChannelId(n.toLong()),
                name = "Channel $n",
                number = n.toLong(),
                tagIds = if (n <= 6) listOf(ChannelTagId(1)) else emptyList(),
            )
        }
        val tags = listOf(
            ChannelTag.create(ChannelTagId(1), name = "Favourites", channelIds = channels.take(6).map { it.id }),
        )
        fun observation(loaded: Boolean) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(
                if (loaded) ChannelCatalog.create(channels, tags) else ChannelCatalog.create(),
            ),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )
        // The player's tune updates the shared selection to the playing channel.
        selected = playerReturn?.playingChannelId
        loaded = channelsLoaded
        request = playerReturn
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val observation = observation(loaded)
            TVHeadendPlayerTheme {
                ChannelsScreenContent(
                    initialFocusEnabled = focusEnabled,
                    channelScopeState = resolveChannelScopeState(observation.channelState, activeTag),
                    observation = observation,
                    tagNotice = false,
                    selectedId = { selected },
                    imageLoader = ImageLoader.Builder(context).diskCache(null).build(),
                    playingChannelId = request?.playingChannelId,
                    playerReturn = request,
                    connectionUiState = ConnectionUiState.Ready,
                    onSelectChannel = { selected = it },
                    onSelectTag = { tagSelections += it },
                    onDismissTagNotice = {},
                    onRetryConnection = {},
                    onOpenConnectionSettings = {},
                    onPlay = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
    }
}
