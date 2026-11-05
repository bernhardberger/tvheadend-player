package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.WarmPlaybackMode
import at.bernhardberger.tvhplayer.testutil.assertLooksAsWhenRefocused
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.ui.components.ChannelSchedule
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparation
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.player.ChannelDrawer
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionTrack
import at.bernhardberger.tvhplayer.ui.player.PlaybackOptionsSheetContent
import at.bernhardberger.tvhplayer.ui.player.PlayerChannelBrowser
import at.bernhardberger.tvhplayer.ui.player.PlayerChannelBrowserState
import at.bernhardberger.tvhplayer.ui.player.PlayerChannelRow
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Focus that reaches a tv-material control before its highlight listens is lost: the control holds
 * focus but draws unfocused until focus leaves and returns. Only a view outside touch mode, as on a
 * TV, hands focus over early enough for that, so these run outside the test default.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvFocusArrivalLookTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var drawnView: View

    @Test fun scheduleCloseLooksFocusedWhenLoadingEndsEmpty() {
        leaveTouchModeBeforeComposing()
        var loading by mutableStateOf(true)
        schedule { ChannelSchedule(null, emptyList(), NOW, NOW, loading, false, { null }, {}, {}, {}) }
        compose.onNodeWithContentDescription("Loading schedule").assertIsFocused()
        compose.runOnIdle { loading = false }
        settle()
        assertLightFill(compose.onNodeWithText("Close"), "Close")
    }

    @Test fun scheduleRowLooksFocusedWhenLoadingEndsWithProgrammes() {
        leaveTouchModeBeforeComposing()
        var data by mutableStateOf(emptyList<EpgEvent>())
        schedule { ChannelSchedule(null, data, NOW, NOW, data.isEmpty(), false, { null }, {}, {}, {}) }
        compose.runOnIdle { data = PROGRAMMES }
        settle()
        assertLooksAsWhenRefocused("schedule-row-2", Key.DirectionDown, Key.DirectionUp)
    }

    @Test fun shelfCloseLooksFocusedWhenTheChannelsGoAway() {
        leaveTouchModeBeforeComposing()
        var channels by mutableStateOf(CHANNELS)
        shelf { channels }
        compose.onNodeWithTag("player-channel-card-1").assertIsFocused()
        compose.runOnIdle { channels = emptyList() }
        settle()
        assertLightFill(compose.onNodeWithTag("player-shelf-close"), "Close")
    }

    @Test fun shelfCardLooksFocusedWhenChannelsReplaceTheClose() {
        leaveTouchModeBeforeComposing()
        var channels by mutableStateOf(emptyList<Channel>())
        shelf { channels }
        compose.onNodeWithTag("player-shelf-close").assertIsFocused()
        compose.runOnIdle { channels = CHANNELS }
        settle()
        assertLooksAsWhenRefocused("player-channel-card-1", Key.DirectionRight, Key.DirectionLeft)
    }

    @Test fun onNowCardLooksFocusedWhenTheFocusedRecentRowGoesAway() {
        leaveTouchModeBeforeComposing()
        val state = PlayerChannelBrowserState()
        var recent by mutableStateOf(CHANNELS.reversed())
        browser(state) { recent }
        key(Key.DirectionDown)
        assertEquals(PlayerChannelRow.RECENT, state.row)
        compose.runOnIdle { recent = emptyList() }
        settle()
        assertEquals(PlayerChannelRow.ON_NOW, state.row)
        assertLooksAsWhenRefocused("player-channel-card-1", Key.DirectionRight, Key.DirectionLeft)
    }

    @Test fun recentCardLooksFocusedAfterMovingDownARow() {
        leaveTouchModeBeforeComposing()
        val state = PlayerChannelBrowserState()
        browser(state) { CHANNELS.reversed() }
        key(Key.DirectionDown)
        assertLooksAsWhenRefocused("player-channel-card-6", Key.DirectionRight, Key.DirectionLeft)
    }

    @Test fun panelRowLooksFocusedWhenItIsScrolledIntoViewOnOpening() {
        leaveTouchModeBeforeComposing()
        val entries = (1..40).map { BrowsePanelEntry.Choice("panel-row-$it", "Row $it", it == 30, {}) }
        schedule { BrowsePanelList(entries, radio = false) }
        assertLooksAsWhenRefocused("panel-row-30", Key.DirectionDown, Key.DirectionUp)
    }

    @Test fun settingsRowsLookFocusedOpeningAndEnteringLevels() {
        leaveTouchModeBeforeComposing()
        host {
            val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.PLAYER.name)
            SideRail(currentRoute = AppDestination.SETTINGS, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                    settingsPlayerLevel(SettingsPlayerUiState(), {}), settingsWarmPlaybackLevel(WarmPlaybackMode.ON) {}),
                    initialFocusEnabled = !drawerActive, contentPadding = padding)
            }
        }
        assertLightFill(compose.onNodeWithTag("settings-row-PLAYER"), "root row")
        key(Key.DirectionCenter)
        assertLooksAsWhenRefocused("settings-row-autoplay", Key.DirectionDown, Key.DirectionUp)
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        assertLooksAsWhenRefocused("settings-row-ON", Key.DirectionDown, Key.DirectionUp)
    }

    @Test fun depthRowLooksFocusedWhenItIsComposedBelowTheViewport() {
        leaveTouchModeBeforeComposing()
        depth(DepthStack(listOf(DepthFrame("root", "root-30"))))
        assertLooksAsWhenRefocused("root-30", Key.DirectionDown, Key.DirectionUp)
        key(Key.DirectionRight)
        assertLooksAsWhenRefocused("child-30", Key.DirectionDown, Key.DirectionUp)
    }

    @Test fun depthRowLooksFocusedAfterAPageKey() {
        leaveTouchModeBeforeComposing()
        depth(DepthStack(listOf(DepthFrame("root", "root-1"))))
        compose.onNodeWithTag("root-1").assertIsFocused()
        key(Key.PageDown)
        val paged = compose.onNode(hasTestTagPrefix("root-") and isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]
        assertTrue("page key moved on, to $paged", paged != "root-1")
        assertLooksAsWhenRefocused(paged, Key.DirectionDown, Key.DirectionUp)
    }

    @Test fun recordingTabLooksFocusedWhenAnEmptyLibraryIsPrepared() {
        leaveTouchModeBeforeComposing()
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList(), emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ))
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) { NoticeContext(0L, null) }
        val state = RecordingsScreenState()
        val observation = session.observation.value
        host {
            SideRail(rootRoute = AppDestination.HOME, availableDestinations = setOf(AppDestination.HOME, AppDestination.RECORDINGS),
                currentRoute = AppDestination.RECORDINGS, onRootBack = {}, onNavigate = {}) { padding, drawer ->
                RecordingsScreenContent(observation = observation, currentObservation = { observation },
                    state = state, contentPadding = padding, initialFocusEnabled = !drawer, backEnabled = !drawer,
                    connectionUiState = ConnectionUiState.Ready, dvrMutationActions = DvrMutationActions(session.dvrRepository),
                    notices = notices, onRetry = {}, onPlayRecording = { _, _ -> })
            }
        }
        compose.awaitBrowsePreparation()
        settle()
        assertLooksAsWhenRefocused("recordings-mode-archive", Key.DirectionRight, Key.DirectionLeft)
    }

    @Test fun selectedAudioRowLooksFocusedWhenItsPageOpensBelowTheViewport() {
        leaveTouchModeBeforeComposing()
        var page by mutableStateOf(PlaybackOptionsPage.ROOT)
        val tracks = (1..40).map { PlaybackOptionTrack("a$it", "Track $it", selected = it == 30) }
        options({ page }, audio = tracks)
        compose.runOnIdle { page = PlaybackOptionsPage.AUDIO }
        settle()
        assertDrawnLightUntilLeft("playback-options-track-a30")
    }

    @Test fun subtitlesOffLooksFocusedWhenItsPageOpens() {
        leaveTouchModeBeforeComposing()
        var page by mutableStateOf(PlaybackOptionsPage.ROOT)
        options({ page }, subtitles = listOf(PlaybackOptionTrack("s1", "German")))
        compose.runOnIdle { page = PlaybackOptionsPage.SUBTITLES }
        settle()
        assertDrawnLightUntilLeft("playback-options-subtitles-off")
    }

    private fun depth(stack: DepthStack) {
        fun rows(prefix: String) = (1..40).map { index ->
            DepthRow(DepthItem("$prefix-$index", "child".takeIf { prefix == "root" })) { modifier, _ ->
                Button(onClick = {}, modifier = modifier.testTag("$prefix-$index")) { Text("Row $index") }
            }
        }
        val levels = mapOf(
            "root" to DepthLevel("root", rows("root"), heading = {}),
            "child" to DepthLevel("child", rows("child"), heading = {}, initialItemId = "child-30"),
        )
        val navigation = DepthNavigationState(stack)
        host {
            DepthNavigation(navigation, levels, { levels.getValue("root") }, columnWidth = 340.dp, columnGap = 92.dp,
                contentPadding = PaddingValues(), modifier = Modifier.fillMaxSize(),
                pageKeyDirection = { if (it == KeyEvent.KEYCODE_PAGE_DOWN) 1 else null })
        }
    }

    private fun options(page: () -> PlaybackOptionsPage, audio: List<PlaybackOptionTrack> = emptyList(),
        subtitles: List<PlaybackOptionTrack> = emptyList()) {
        host {
            drawnView = LocalView.current
            PlaybackOptionsSheetContent(page(), audio, subtitles, tracksResolving = false, AspectRatioMode.FIT,
                statsVisible = false, onPageChange = {}, onAudioTrackSelected = {}, onSubtitleTrackSelected = {},
                onAspectRatioChange = {}, onStatsVisibleChange = {})
        }
    }

    /** The options frame's dialog semantics stop captureToImage, so this draws the view; moving on must darken the row. */
    private fun assertDrawnLightUntilLeft(tag: String) {
        val bounds = compose.onNodeWithTag(tag).assertIsFocused().fetchSemanticsNode().boundsInRoot
        fun fill(): Float = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(drawnView.width, drawnView.height, Bitmap.Config.ARGB_8888)
            drawnView.draw(Canvas(bitmap))
            val row = (bounds.left.toInt() until bounds.right.toInt()).map { Color(bitmap.getPixel(it, bounds.center.y.toInt())).luminance() }.sorted()
            row[row.size / 2]
        }
        val arrival = fill()
        assertTrue("the focused $tag has a light fill, its middle row is at $arrival", arrival > 0.5f)
        key(Key.DirectionDown)
        val left = fill()
        assertTrue("$tag darkens once left, its middle row is at $left", left < 0.5f)
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    @Test fun railItemLooksFocusedWhenTheItemSetReplacesItsTarget() {
        leaveTouchModeBeforeComposing()
        var destinations by mutableStateOf(setOf(AppDestination.CHANNELS, AppDestination.GUIDE))
        host {
            SideRail(currentRoute = AppDestination.GUIDE, onRootBack = {}, onNavigate = {}, rootRoute = AppDestination.HOME,
                availableDestinations = destinations) { _, _ -> Button(onClick = {}, Modifier.testTag("rail-content")) { Text("Content") } }
        }
        compose.onNodeWithTag("nav-epg").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        settle()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        compose.runOnIdle { destinations = setOf(AppDestination.HOME, AppDestination.CHANNELS) }
        settle()
        assertLooksAsWhenRefocused("nav-home", Key.DirectionDown, Key.DirectionUp)
    }

    /** Dark as the app is: an unfocused row is see-through, and the bare test window behind it is light. */
    private fun host(content: @Composable () -> Unit) {
        compose.setContent { TVHeadendPlayerTheme { Box(Modifier.fillMaxSize().background(Color.Black)) { content() } } }
        settle()
    }

    /** Without its overlay frame, whose dialog semantics the capture can't follow; the frame holds no focus. */
    private fun schedule(content: @Composable () -> Unit) = host { Box(Modifier.fillMaxHeight().width(480.dp)) { content() } }

    private fun shelf(channels: () -> List<Channel>) = host {
        Column(Modifier.fillMaxSize()) {
            ChannelDrawer(channels(), null, null, recordingChannelIds = emptySet(), nowEvent = { null }, nowSec = NOW,
                onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {})
        }
    }

    private fun browser(state: PlayerChannelBrowserState, recent: () -> List<Channel>) = host {
        Column(Modifier.fillMaxSize()) {
            PlayerChannelBrowser(state, CHANNELS, recent(), null, emptySet(), { null }, NOW, true, {}, {}, { _, _, _ -> }, {},
                channelsTitle = "Sport")
        }
    }

    private fun settle() { compose.mainClock.advanceTimeBy(1_000); compose.waitForIdle() }
    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; settle() }

    private fun leaveTouchModeBeforeComposing() = compose.leaveTouchModeBeforeComposing()

    /** Focused buttons and list rows invert to a light fill, unfocused ones stay dark. */
    private fun assertLightFill(node: SemanticsNodeInteraction, name: String) {
        val image = node.assertIsFocused().captureToImage().toPixelMap()
        val row = (0 until image.width).map { image[it, image.height / 2].luminance() }.sorted()
        assertTrue("the focused $name has a light fill, its middle row is at ${row[row.size / 2]}", row[row.size / 2] > 0.5f)
    }

    private fun assertLooksAsWhenRefocused(tag: String, away: Key, back: Key) =
        compose.onNodeWithTag(tag).assertLooksAsWhenRefocused(tag, { key(away) }, { key(back) })

    private companion object {
        const val NOW = 1789246200L
        val CHANNELS = (1L..6L).map { Channel.create(ChannelId(it), name = "Channel $it", number = it) }
        val PROGRAMMES = listOf(programme(1, NOW - 5400, NOW - 1800), programme(2, NOW - 1800, NOW + 1800), programme(3, NOW + 1800, NOW + 5400))
        fun programme(id: Long, start: Long, stop: Long) = EpgEvent.create(EventId(id), ChannelId(1),
            Instant.fromEpochSeconds(start), Instant.fromEpochSeconds(stop), title = "Programme $id")
    }
}
