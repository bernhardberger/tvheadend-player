package at.bernhardberger.tvhplayer.touch.channels

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChannelsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletChannels() {
        val list = channelsFixture(playing = true)
        val state = mutableStateOf(ChannelsState.Ready(list))
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                ChannelsScreen(state.value, fixtureActions(optional = true), initiallyOpenChannelId = "amber-stage")
            }
        }
        capture("tablet-channels", TouchCanvas.TABLET)

        val longTitle = "Harbor Kickoff: " + "Harbor FC meet Amber Town in a floodlit stadium. ".repeat(8)
        compose.runOnIdle {
            state.value = ChannelsState.Ready(
                list.copy(rows = list.rows.map {
                    if (it.id == "harbor-sport") it.copy(now = it.now?.copy(title = longTitle)) else it
                }),
            )
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longTitle, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().isLineEllipsized(0))
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording scheduled", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightChannels() {
        show(ChannelsState.Ready(channelsFixture(playing = true)), open = "amber-stage")
        assertCompactFollowing()
        capture("tablet-upright-channels", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneChannels() {
        var played: String? = null
        show(ChannelsState.Ready(channelsFixture()), fixtureActions(onPlay = { played = it }))

        compose.onNodeWithContentDescription("Show programs on Amber Stage").performClick()
        assertNull(played)
        compose.onNodeWithText("S2 E4 • The Long Tide").assertIsDisplayed()
        compose.onNodeWithText("Watch").assertIsDisplayed()
        compose.onNodeWithText("Record").assertDoesNotExist()
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Show in Guide").assertDoesNotExist()
        assertCompactFollowing()
        capture("phone-channels", TouchCanvas.PHONE)

        compose.onNodeWithContentDescription("Hide programs on Amber Stage").performClick()
        compose.onNodeWithText("S2 E4 • The Long Tide").assertDoesNotExist()
        compose.onNodeWithText("Amber Stage").performClick()
        compose.runOnIdle { assertEquals("amber-stage", played) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletLoading() {
        captureLoading("tablet-loading", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneLoading() {
        captureLoading("phone-loading", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletUnreachable() {
        show(ChannelsState.Unreachable)
        capture("tablet-unreachable", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneSignInFailed() {
        show(ChannelsState.SignInFailed)
        capture("phone-sign-in-failed", TouchCanvas.PHONE)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletOffline() {
        show(ChannelsState.Offline(channelsFixture(), retryIn = "12 s"))
        compose.onNodeWithText("Harbor Sport").assertIsDisplayed()
        compose.onNodeWithContentDescription("Recording", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Recording scheduled", useUnmergedTree = true).assertDoesNotExist()
        capture("tablet-offline", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneOffline() {
        show(ChannelsState.Offline(channelsFixture(), retryIn = "12 s"))
        capture("phone-offline", TouchCanvas.PHONE)
    }

    private fun show(state: ChannelsState, actions: ChannelsActions = fixtureActions(), open: String? = null) {
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                ChannelsScreen(state, actions, initiallyOpenChannelId = open)
            }
        }
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice2", name, canvas)

    private fun assertCompactFollowing() {
        compose.onNodeWithText("The Kettle Hour").assertIsDisplayed()
        compose.onNodeWithText("Coastal Miles").assertIsDisplayed()
        compose.onNodeWithText("Amber Late").assertIsDisplayed()
        compose.onNodeWithText("11:40 PM").assertDoesNotExist()
    }

    private fun captureLoading(name: String, canvas: TouchCanvas) {
        compose.mainClock.autoAdvance = false
        show(ChannelsState.Loading)
        compose.mainClock.advanceTimeBy(640)
        capture(name, canvas)
    }

    private fun fixtureActions(onPlay: (String) -> Unit = {}, optional: Boolean = false) = ChannelsActions(
        onPlay = onPlay,
        onTag = {},
        onRetry = {},
        onOpenConnectionSettings = {},
        onRecord = if (optional) ({}) else null,
        onDetails = if (optional) ({}) else null,
        onShowInGuide = if (optional) ({}) else null,
    )
}
