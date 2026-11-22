package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.RecordingProgressCapability
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestResultFactory
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.BrowseContextAction.CONTINUE_WATCHING
import at.bernhardberger.tvhplayer.core.BrowseContextAction.DETAILS
import at.bernhardberger.tvhplayer.core.BrowseContextAction.PLAY
import at.bernhardberger.tvhplayer.core.BrowseContextAction.RESUME
import at.bernhardberger.tvhplayer.core.BrowseContextAction.START_OVER
import at.bernhardberger.tvhplayer.core.BrowseContextAction.WATCH
import at.bernhardberger.tvhplayer.core.BrowseContextAction.WATCH_LIVE
import at.bernhardberger.tvhplayer.core.channelContextActions
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatchButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @OptIn(ExperimentalTvheadendApi::class, TvheadendTestingApi::class)
    fun watchWaysKeepOnlyPlaybackInSharedOrder() {
        val guide = ServerGuide(Instant.parse("2027-01-15T12:00:00Z"))
        val airing = guide.snapshot.events.first()
        val admission = TvheadendTestResultFactory.growingRecordingAdmission(null, RecordingProgressCapability.SUPPORTED)
        val now = guide.now.epochSeconds
        val cases = listOf(
            channelContextActions(airing, now, null, null, true) to listOf(WATCH),
            channelContextActions(airing, now, null, null, true, playing = true) to listOf(CONTINUE_WATCHING),
            channelContextActions(airing, now, null, null, true, playing = true, behindLive = true) to twoWays,
            channelContextActions(airing, now, guide.recordingNow, admission, true, playing = true, behindLive = true) to
                (twoWays + START_OVER),
            listOf(RESUME, DETAILS, START_OVER, PLAY) to listOf(RESUME, START_OVER, PLAY),
            listOf(DETAILS) to emptyList(),
        )
        cases.forEach { (actions, expected) -> assertEquals(expected, watchWays(actions)) }
    }

    @Test
    @Config(sdk = [28])
    fun oneWayPlaysImmediately() {
        val played = mutableListOf<BrowseContextAction>()
        show(mutableStateOf(listOf(WATCH)), onWatch = { played += it })

        compose.onNodeWithContentDescription("Watch").performClick()

        assertEquals(listOf(WATCH), played)
        toggle().assertDoesNotExist()
    }

    @Test
    fun multipleWaysCloseAfterSelectionOrBack() {
        val played = mutableListOf<BrowseContextAction>()
        var hostBacks = 0
        show(mutableStateOf(twoWays), onBack = { hostBacks++ }, onWatch = { played += it })

        compose.onNodeWithText("Watch live").assertDoesNotExist()
        toggle().performClick()
        assertEquals(emptyList<BrowseContextAction>(), played)
        compose.onNodeWithText("Continue watching").assertIsDisplayed()
        compose.onNodeWithText("Watch live").performClick()
        assertEquals(listOf(WATCH_LIVE), played)
        compose.onNodeWithText("Watch live").assertDoesNotExist()

        toggle().performClick()
        compose.onNodeWithText("Watch live").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        assertEquals(listOf(WATCH_LIVE), played)
        assertEquals(0, hostBacks)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, hostBacks)
    }

    @Test
    fun subjectChangeAndFewerWaysCloseWithoutConsumingBack() {
        val ways = mutableStateOf(twoWays)
        val subject = mutableStateOf("first")
        var hostBacks = 0
        show(ways, subject = subject, onBack = { hostBacks++ })

        toggle().performClick()
        compose.onNodeWithText("Watch live").assertIsDisplayed()
        compose.runOnIdle { subject.value = "second" }
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, hostBacks)

        listOf(listOf(WATCH), emptyList()).forEachIndexed { index, remaining ->
            toggle().performClick()
            compose.onNodeWithText("Watch live").assertIsDisplayed()
            compose.runOnIdle { ways.value = remaining }
            compose.onNodeWithText("Watch live").assertDoesNotExist()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            assertEquals(index + 2, hostBacks)
            compose.runOnIdle { ways.value = twoWays }
            compose.onNodeWithText("Watch live").assertDoesNotExist()
        }
    }

    @Test
    fun returningToSavedScreenDoesNotReopenMenu() {
        val shown = mutableStateOf(true)
        show(mutableStateOf(twoWays), shown = shown)

        toggle().performClick()
        compose.onNodeWithText("Watch live").assertIsDisplayed()
        compose.runOnIdle { shown.value = false }
        toggle().assertDoesNotExist()
        compose.runOnIdle { shown.value = true }
        toggle().assertIsDisplayed()
        compose.onNodeWithText("Watch live").assertDoesNotExist()
    }

    private fun show(
        ways: MutableState<List<BrowseContextAction>>,
        subject: MutableState<String> = mutableStateOf("first"),
        shown: MutableState<Boolean> = mutableStateOf(true),
        onBack: () -> Unit = {},
        onWatch: (BrowseContextAction) -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = TouchDarkColors) {
                Surface {
                    BackHandler(onBack = onBack)
                    Box(Modifier.fillMaxSize()) {
                        val holder = rememberSaveableStateHolder()
                        if (shown.value) {
                            holder.SaveableStateProvider("watch-screen") {
                                val modifier = Modifier.align(Alignment.BottomEnd)
                                WatchButton(
                                    ways.value,
                                    subject.value,
                                    modifier = if (ways.value.size == 1) modifier.padding(16.dp) else modifier,
                                    onWatch = onWatch,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggle() = compose.onNodeWithContentDescription("Watch options")

    private companion object {
        val twoWays = listOf(CONTINUE_WATCHING, WATCH_LIVE)
    }
}
