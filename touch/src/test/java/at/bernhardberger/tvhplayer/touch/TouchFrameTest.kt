package at.bernhardberger.tvhplayer.touch

import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
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
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameSelection
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import org.junit.Assert.assertEquals
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactBrowseActionsEndAtTheContentKeyline() = assertBrowseActionEnds(16.dp, compact = true)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedBrowseActionsEndAtTheContentKeyline() = assertBrowseActionEnds(24.dp, compact = false)

    private fun assertBrowseActionEnds(inset: Dp, compact: Boolean) {
        val selection = mutableStateOf(Destination.Channels)
        val channels = ChannelsState.Ready(channelsFixture())
        val guide = (if (compact) phoneOverviewFixture() else overviewFixture()).copy(details = null)
        val recordings = flatFixture()
        val channelsActions = ChannelsActions(onPlay = null, onTag = {}, onRetry = {}, onOpenConnectionSettings = {})
        val guideActions = GuideActions(
            onWatch = null, onRecord = null, onRecordOptions = null, onDay = {}, onJump = {}, onTag = {},
        )
        val recordingsActions = RecordingsActions(onView = {}, onPlay = null, onDelete = null, onMore = null)
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                CompositionLocalProvider(LocalFrameSelection provides selection) {
                    when (selection.value) {
                        Destination.Channels -> ChannelsScreen(channels, channelsActions)
                        Destination.Guide -> GuideScreen(guide, guideActions)
                        Destination.Recordings -> RecordingsScreen(recordings, recordingsActions)
                    }
                }
            }
        }
        val keyline = compose.onRoot().fetchSemanticsNode().boundsInRoot.right -
            inset.value * compose.activity.resources.displayMetrics.density
        val settingsEnd = symbolEnd(R.string.channels_connection_settings)
        compose.onNode(destination and hasText("Guide")).performClick()
        val guideEnd = buttonEnd(R.string.channels_all)
        compose.onNode(destination and hasText("Recordings")).performClick()
        val recordingsEnd = if (compact) symbolEnd(R.string.recordings_view_options)
        else buttonEnd(R.string.recordings_view_options)
        assertEquals("Channels / Guide / Recordings", List(3) { keyline }, listOf(settingsEnd, guideEnd, recordingsEnd))
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
        val restoration = StateRestorationTester(compose)
        show(restoration)
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "phone", TouchCanvas.PHONE)

        compose.onNode(destination and hasText("Guide")).performClick()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()
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
            MaterialTheme(colorScheme = MobileDarkColors) { TouchFrame(destinations = listOf(Destination.Channels)) {} }
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
            MaterialTheme(colorScheme = MobileDarkColors) {
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
            MaterialTheme(colorScheme = MobileDarkColors) {
                TouchFrame(
                    lastActionIsIconOnly = true,
                    actions = { current ->
                        if (current == Destination.Channels) {
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
}
