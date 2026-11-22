package at.bernhardberger.tvhplayer.touch.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Assert.assertTrue
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
    fun oneWayPlaysImmediately() {
        val played = mutableListOf<BrowseContextAction>()
        val active = mutableStateOf(false)
        show(mutableStateOf(listOf(WATCH)), active = active, onWatch = { played += it })

        val watch = compose.onNodeWithContentDescription("Watch")
        watch.performClick()
        assertEquals(emptyList<BrowseContextAction>(), played)

        compose.runOnIdle { active.value = true }
        watch.performClick()
        assertEquals(listOf(WATCH), played)
        toggle().assertDoesNotExist()
    }

    @Test
    fun multipleWaysCloseAfterSelectionOrBack() {
        val played = mutableListOf<BrowseContextAction>()
        var hostBacks = 0
        show(mutableStateOf(twoWays), keyboard = true, onBack = { hostBacks++ }, onWatch = { played += it })

        compose.onNodeWithText("Watch live").assertDoesNotExist()
        toggle().performClick()
        assertEquals(emptyList<BrowseContextAction>(), played)
        compose.onNodeWithText("Continue watching").assertIsDisplayed()
        compose.onNodeWithText("Watch live").performClick()
        assertEquals(listOf(WATCH_LIVE), played)
        compose.onNodeWithText("Watch live").assertDoesNotExist()

        toggle().performClick()
        val live = compose.onNodeWithText("Watch live")
        live.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue("Menu item accepts keyboard focus", it()) }
        live.assertIsFocused()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        live.assertDoesNotExist()
        toggle().assertIsFocused()

        toggle().performClick()
        live.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        toggle().performClick()
        live.assertDoesNotExist()
        toggle().assertIsFocused()

        toggle().performClick()
        live.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
        val closeAction = live.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        compose.runOnIdle { closeAction.action() }
        live.assertDoesNotExist()
        toggle().assertIsFocused()

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
        val active = mutableStateOf(true)
        show(mutableStateOf(twoWays), shown = shown, active = active)

        toggle().performClick()
        compose.onNodeWithText("Watch live").assertIsDisplayed()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        toggle().performClick()
        compose.onNodeWithText("Watch live").assertDoesNotExist()
        compose.runOnIdle { active.value = true }
        compose.onNodeWithText("Watch live").assertDoesNotExist()

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
        active: MutableState<Boolean> = mutableStateOf(true),
        keyboard: Boolean = false,
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
                                // Robolectric key injection does not switch Compose out of touch input mode.
                                val inputMode = if (keyboard) KeyboardInputMode else LocalInputModeManager.current
                                CompositionLocalProvider(LocalInputModeManager provides inputMode) {
                                    WatchButton(
                                        ways.value,
                                        subject.value,
                                        modifier = if (ways.value.size == 1) modifier.padding(16.dp) else modifier,
                                        active = active.value,
                                        onWatch = onWatch,
                                    )
                                }
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

    private object KeyboardInputMode : InputModeManager {
        override val inputMode = InputMode.Keyboard
        override fun requestInputMode(inputMode: InputMode): Boolean = inputMode == this.inputMode
    }
}
