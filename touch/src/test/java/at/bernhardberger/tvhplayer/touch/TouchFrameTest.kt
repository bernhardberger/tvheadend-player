package at.bernhardberger.tvhplayer.touch

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsActions
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsScreen
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsState
import at.bernhardberger.tvhplayer.touch.recordings.flatFixture
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameNotices
import at.bernhardberger.tvhplayer.touch.ui.TouchNotice
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.channels.ChannelsActions
import at.bernhardberger.tvhplayer.touch.channels.ChannelsScreen
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.guide.GuideActions
import at.bernhardberger.tvhplayer.touch.guide.GuideScreen
import at.bernhardberger.tvhplayer.touch.guide.overviewFixture
import at.bernhardberger.tvhplayer.touch.guide.phoneOverviewFixture
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.reserveSystemBars
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.DestinationFrame
import at.bernhardberger.tvhplayer.touch.ui.LocalDestinationReselections
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.touch.ui.TouchDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The frame with all three destinations, as drawn; the real app offers only those it has wired. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchFrameTest {
    private val motionScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)

    private val destination = hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedFrame() {
        show()
        assertChannelsFrame(vertical = true)
        compose.captureTouch("slice0", "tablet-landscape", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun mediumFrame() {
        show()
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "tablet-upright", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun destinationFadesOutBeforeTheNextFadesInWhileTheRailStaysPut() {
        var outgoingClicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                DestinationFrame { current ->
                    TouchFrame(initialDestination = current) {
                        val outgoing = current == Destination.Channels
                        val action = if (outgoing) Modifier.clickable { outgoingClicks++ } else Modifier
                        Box(Modifier.fillMaxSize().background(if (outgoing) Color.Red else Color.Green).then(action))
                    }
                }
            }
        }
        val stable = listOf(
            compose.onNode(destination and hasText("Recordings")),
            compose.onNodeWithTag("browse-app-mark"),
        )
        val before = stable.map { it.idAndPixels() }
        compose.mainClock.autoAdvance = false
        val guide = compose.onNode(destination and hasText("Guide"))
        selectOnTheNextFrame("Guide")
        guide.assertIsSelected()
        var fading = contentPixel()
        compose.mainClock.advanceTimeUntil {
            fading = contentPixel()
            fading.red < 0.75f
        }
        assertTrue("The outgoing red page should be partly faded: $fading", fading.red in 0.1f..<0.99f)
        assertTrue("The incoming green page must still be transparent: $fading", fading.green < 0.1f)
        assertEquals("Rail item and mark retain their identity and pixels", before, stable.map { it.idAndPixels() })
        compose.onRoot().performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, outgoingClicks) }
        compose.mainClock.autoAdvance = true
        assertEquals(Color.Green, contentPixel())

        motionScale.scaleFactor = 0f
        selectOnTheNextFrame("Channels")
        assertEquals(Color.Red, contentPixel())
        compose.onNode(heading and hasText("Guide")).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun destinationRestoresItsListAndOpenedRowAfterLeaving() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                DestinationFrame { current ->
                    if (current == Destination.Channels) {
                        TouchFrame { SavedList() }
                    }
                }
            }
        }
        compose.onNodeWithText("Opened row: 0").performClick()
        compose.onNodeWithTag("saved-list").performScrollToIndex(8)
        assertSavedList()
        selectOnTheNextFrame("Channels")
        assertSavedList()
        selectOnTheNextFrame("Guide")
        compose.onNodeWithTag("saved-list").assertDoesNotExist()
        selectOnTheNextFrame("Channels")
        assertSavedList()
        selectOnTheNextFrame("Guide")
        compose.onNodeWithTag("saved-list").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        selectOnTheNextFrame("Channels")
        assertSavedList()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun backDuringTheFadeDoesNotCloseTheOutgoingChannel() {
        val channels = ChannelsState.Ready(channelsFixture())
        val actions = ChannelsActions(onPlay = null, onTag = {}, onRetry = {}, onOpenConnectionSettings = {})
        compose.setContent {
            MaterialTheme {
                DestinationFrame { current ->
                    if (current == Destination.Channels) {
                        ChannelsScreen(channels, actions, initiallyOpenChannelId = "amber-stage")
                    } else {
                        TouchFrame(initialDestination = current)
                    }
                }
            }
        }
        val opened = compose.onNodeWithText("S2 E4 • The Long Tide")
        opened.assertExists()
        compose.mainClock.autoAdvance = false
        selectOnTheNextFrame("Guide")
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        opened.assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        opened.assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun restoringFullWindowDetailsDoesNotLayOutNavigation() {
        val restoration = StateRestorationTester(compose)
        val heights = mutableListOf<Int>()
        restoration.setContent {
            MaterialTheme {
                DestinationFrame(initialDestination = Destination.Recordings) { current ->
                    TouchFrame(
                        initialDestination = current,
                        showTopBar = { false },
                        showNavigation = { false },
                    ) {
                        Box(Modifier.fillMaxSize().onSizeChanged { heights += it.height })
                    }
                }
            }
        }
        val fullHeight = compose.runOnIdle { heights.last().also { heights.clear() } }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals("The first restored layout must not reserve navigation space", fullHeight, heights.first())
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneNavigationFadesInAndOut() {
        val inset = showNavigationWithNotice()
        val shownBottom = bottomOf("frame-content")
        val windowBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        // Sample an unoccupied part of the bar, above the system gesture inset.
        val y = ((shownBottom + windowBottom - inset) / 2).toInt()
        fun barPixel() = compose.onRoot().captureToImage().toPixelMap()[2, y]
        val shown = barPixel()
        setBrowsing(false)
        val hidden = barPixel()
        assertTrue(shown != hidden)

        for (visible in listOf(true, false)) {
            compose.mainClock.autoAdvance = false
            setBrowsing(visible)
            compose.mainClock.advanceTimeBy(64)
            val moving = barPixel()
            assertTrue(
                "Navigation should be partially transparent (showing=$visible)",
                moving != shown && moving != hidden,
            )
            assertEquals(shownBottom, bottomOf("frame-content"))
            assertEquals(shownBottom, bottomOf("frame-notice"))
            compose.mainClock.autoAdvance = true
            assertEquals(if (visible) shown else hidden, barPixel())
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneNavigationWithoutMotionKeepsTheSettledBoundsAndSystemInset() {
        motionScale.scaleFactor = 0f
        val navigationInset = showNavigationWithNotice()
        val windowBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val shownBottom = bottomOf("frame-content")
        assertEquals(shownBottom, bottomOf("frame-notice"))
        assertTrue(shownBottom < windowBottom - navigationInset)

        compose.mainClock.autoAdvance = false
        setBrowsing(false)
        // The page reports its bars after composition; allow composition and the transition's first frame.
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(destination).assertDoesNotExist()
        assertEquals(windowBottom, bottomOf("frame-content"))
        assertEquals(windowBottom - navigationInset, bottomOf("frame-notice"))

        setBrowsing(true)
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.onNode(destination and hasText("Guide")).assertIsDisplayed()
        assertEquals(shownBottom, bottomOf("frame-content"))
        assertEquals(shownBottom, bottomOf("frame-notice"))
    }

    private val browsing = mutableStateOf(true)

    private fun setBrowsing(value: Boolean) = compose.runOnUiThread {
        browsing.value = value
        Snapshot.sendApplyNotifications()
    }

    private fun showNavigationWithNotice(): Float {
        compose.setContent {
            MaterialTheme {
                val host: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.height(40.dp).testTag("frame-notice"))
                }
                CompositionLocalProvider(LocalFrameNotices provides host) {
                    DestinationFrame { current ->
                        TouchFrame(
                            initialDestination = current,
                            showTopBar = { browsing.value },
                            showNavigation = { browsing.value },
                        ) {
                            Box(Modifier.fillMaxSize().testTag("frame-content"))
                        }
                    }
                }
            }
        }
        val inset = compose.reserveSystemBars(TouchCanvas.PHONE)
        return with(compose.density) { inset.toPx() }
    }

    private fun bottomOf(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.bottom

    @Composable
    private fun SavedList() {
        var opened by rememberSaveable { mutableIntStateOf(0) }
        val list = rememberLazyListState()
        LazyColumn(state = list, modifier = Modifier.testTag("saved-list")) {
            items(40) { Text("Row $it") }
        }
        Button(onClick = { opened++ }) { Text("Opened row: $opened") }
        Text("First row: ${list.firstVisibleItemIndex}")
    }

    private fun assertSavedList() {
        compose.onNodeWithText("Opened row: 1").assertExists()
        compose.onNodeWithText("First row: 8").assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun onlyTappingTheSelectedDestinationEmitsAReselection() {
        val received = mutableListOf<Destination>()
        compose.setContent {
            MaterialTheme {
                DestinationFrame { current ->
                    val events = LocalDestinationReselections.current
                    LaunchedEffect(events, current) { events.collect { if (it == current) received += it } }
                    TouchFrame(initialDestination = current)
                }
            }
        }
        selectOnTheNextFrame("Channels")
        compose.runOnIdle { assertEquals(listOf(Destination.Channels), received) }
        selectOnTheNextFrame("Guide")
        compose.runOnIdle { assertEquals(listOf(Destination.Channels), received) }
        selectOnTheNextFrame("Guide")
        compose.runOnIdle { assertEquals(listOf(Destination.Channels, Destination.Guide), received) }
    }

    private fun SemanticsNodeInteraction.idAndPixels(): Pair<Int, List<Color>> {
        val image = captureToImage().toPixelMap()
        return fetchSemanticsNode().id to List(image.width * image.height) { image[it % image.width, it / image.width] }
    }

    private fun contentPixel(): Color {
        val image = compose.onRoot().captureToImage().toPixelMap()
        return image[image.width / 2, image.height / 2]
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactBrowseActionsShareAnEndKeyline() = assertBrowseActionEnds(compact = true)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedBrowseActionsEndAtTheContentKeyline() = assertBrowseActionEnds(compact = false)

    private fun assertBrowseActionEnds(compact: Boolean) {
        val channels = ChannelsState.Ready(channelsFixture())
        val guide = if (compact) phoneOverviewFixture() else overviewFixture()
        val recordings = flatFixture()
        val channelsActions = ChannelsActions(onPlay = null, onTag = {}, onRetry = {}, onOpenConnectionSettings = {})
        val guideActions = GuideActions(
            onWatch = null, onRecord = null, onRecordOptions = null, onDay = {}, onJump = {}, onTag = {},
        )
        val recordingsActions = RecordingsActions(onView = {}, onPlay = null, onDelete = null, onMore = null)
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                DestinationFrame { current ->
                    when (current) {
                        Destination.Channels -> ChannelsScreen(channels, channelsActions)
                        Destination.Guide -> GuideScreen(guide, guideActions)
                        Destination.Recordings -> RecordingsScreen(recordings, recordingsActions)
                    }
                }
            }
        }
        val settingsEnd = symbolEnd(R.string.channels_connection_settings)
        val channelsMatch = !compact || rowSharesKeyline("Channels", "Ridge Earth", settingsEnd)
        compose.onNode(destination and hasText("Guide")).performClick()
        val guideEnd = buttonEnd(R.string.channels_all)
        val overviewMatch = !compact || rowSharesKeyline("Guide", "Ridge Light", guideEnd)
        compose.onNode(destination and hasText("Recordings")).performClick()
        val recordingsEnd = if (compact) symbolEnd(R.string.recordings_view_options)
        else buttonEnd(R.string.recordings_view_options)
        val keyline = if (compact) guideEnd else compose.onNodeWithTag("recordings-pane")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right
        assertEquals(
            "Action end: Channels / Guide / Recordings",
            List(3) { keyline },
            listOf(settingsEnd, guideEnd, recordingsEnd),
        )
        if (compact) {
            val recordingsMatch = rowSharesKeyline("Recordings", "Field Desk", recordingsEnd)
            assertEquals(
                "Row edges share the title/action keyline: Channels / Guide / Recordings",
                List(3) { true },
                listOf(channelsMatch, overviewMatch, recordingsMatch),
            )
        }
    }

    private fun rowSharesKeyline(page: String, title: String, actionEnd: Float): Boolean {
        val pageStart = compose.onNode(heading and hasText(page)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        val row = compose.onNode(hasText(title) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        return row.left == pageStart && row.right == actionEnd
    }

    private fun symbolEnd(label: Int) = compose.onNode(
        hasContentDescription(compose.activity.getString(label)) and !hasClickAction(),
        useUnmergedTree = true,
    ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right

    private fun buttonEnd(label: Int) = compose.onNode(
        hasText(compose.activity.getString(label)) and hasClickAction(),
    ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.right

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactFrameNavigatesAndRestoresSelection() {
        motionScale.scaleFactor = 0f
        val restoration = StateRestorationTester(compose)
        show(restoration)
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "phone", TouchCanvas.PHONE)

        selectOnTheNextFrame("Guide")
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()
        compose.onNode(heading and hasText("Channels")).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun singleDestinationLeavesThePhoneBarOut() {
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) { TouchFrame(destinations = listOf(Destination.Channels)) {} }
        }
        compose.onNode(destination).assertDoesNotExist()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletNoticeBesideTheRail() {
        showRecordingsWith(Notice.DvrActionFailed(DvrMutationKind.CANCEL, DvrMutationFeedback.CONNECTION_LIMIT))
        compose.captureTouch("slice0", "tablet-notice", TouchCanvas.TABLET)

        notice.value = Notice.Connection(ConnectionNoticeKind.RESTORED)
        compose.captureTouch("slice0", "tablet-notice-one-line", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneNoticeAboveTheBar() {
        showRecordingsWith(
            Notice.DvrActionFailed(DvrMutationKind.CANCEL, DvrMutationFeedback.CONNECTION_LIMIT),
            flatFixture(harborLights = true),
        )
        compose.captureTouch("slice0", "phone-notice", TouchCanvas.PHONE)
    }

    private val notice = mutableStateOf<Notice?>(null)

    /** The Recordings fixture with [shown] in the frame's notice place, as the host draws it. */
    private fun showRecordingsWith(shown: Notice, recordings: RecordingsState = flatFixture()) {
        notice.value = shown
        val actions = RecordingsActions(onView = {}, onPlay = { _, _ -> }, onDelete = {}, onMore = {})
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                val formatter = NoticeFormatter(LocalContext.current)
                val host: @Composable (Modifier) -> Unit = { modifier ->
                    notice.value?.let { formatter.format(it) }?.let { TouchNotice(it.headline, it.detail, modifier) }
                }
                CompositionLocalProvider(LocalFrameNotices provides host) {
                    RecordingsScreen(recordings, actions)
                }
            }
        }
        val headline = NoticeFormatter(compose.activity).format(shown).headline
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion) and hasText(headline))
            .assertIsDisplayed()
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @Composable () -> Unit = {
            MaterialTheme(colorScheme = TouchDarkColors) {
                DestinationFrame { current ->
                    TouchFrame(
                        initialDestination = current,
                        lastActionIsIconOnly = true,
                        actions = { destination ->
                            if (destination == Destination.Channels) {
                                IconButton(onClick = {}) {
                                    Icon(
                                        painterResource(R.drawable.channels_settings),
                                        stringResource(R.string.channels_connection_settings),
                                    )
                                }
                            }
                        },
                    ) {}
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    private fun assertChannelsFrame(vertical: Boolean) {
        val labels = compose.onAllNodes(destination).fetchSemanticsNodes()
            .sortedBy { if (vertical) it.boundsInRoot.top else it.boundsInRoot.left }
            .map { it.config.getOrNull(SemanticsProperties.Text)?.single()?.text }
        assertEquals(listOf("Channels", "Guide", "Recordings"), labels)
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
    }

    private fun selectOnTheNextFrame(label: String) {
        compose.onNode(destination and hasText(label)).performSemanticsAction(SemanticsActions.OnClick)
        // Deliver the click's snapshot write before asking the paused clock for its next frame.
        compose.runOnUiThread { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
    }
}
