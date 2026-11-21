package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import java.time.LocalTime
import org.junit.Assert.assertEquals
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
class PlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletCapturesDragAndSeeksOnlyOnLift() {
        show()
        val canvas = TouchCanvas.TABLET
        capture("tablet-player", canvas)

        timeline().performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        chromeIsVisible()
        dragTo(.525f)
        tag("player-top").assertDoesNotExist()
        tag("player-transport").assertDoesNotExist()
        tag("player-brightness").assertDoesNotExist()
        controls(R.string.player_record).assertIsNotEnabled()
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        capture("tablet-player-drag", canvas)
        moveTo(.785f)
        capture("tablet-player-drag-edge", canvas)
        timeline().performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2820), seeks) }
        chromeIsVisible()

        tapPicture()
        capture("tablet-player-hidden", canvas)
        tapPicture()
        captureStatuses("tablet", canvas, reconnecting = true)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightTabletShowsChannelListInPlaceOfButton() {
        show()
        assertInlineChannels()
        capture("tablet-upright-player", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phonePictureTapHidesControlsButKeepsThinProgress() {
        show()
        val canvas = TouchCanvas.PHONE
        assertInlineChannels()
        capture("phone-player", canvas)
        val open = orangeProgressRows()
        tapPicture()
        tag("player-top").assertDoesNotExist()
        tag("player-transport").assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_full_screen)).assertDoesNotExist()
        assertInlineChannels()
        val hidden = orangeProgressRows()
        assertTrue("The elapsed segment remains, but thinner than the control track", hidden in 1 until open)
        capture("phone-player-hidden", canvas)
        tapPicture()
        tag("player-transport").assertIsDisplayed()
        captureStatuses("phone", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneKeepsTimelineDuringDrag() {
        show()
        val next = checkNotNull(channelsFixture(playing = true).rows.first().next)
        val nextLine = compose.activity.getString(R.string.player_next_program, next.start, next.title)
        compose.onNodeWithText(nextLine).assertDoesNotExist()
        val canvas = TouchCanvas.PHONE_LANDSCAPE
        capture("phone-player-landscape", canvas)
        dragTo(.525f)
        timeline().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_record)).assertIsNotEnabled()
        capture("phone-player-landscape-drag", canvas)
        timeline().performTouchInput { cancel() }
        chromeIsVisible()
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
    }

    private val seeks = mutableListOf<Int>()
    private val status = mutableStateOf<LivePlayerStatus>(LivePlayerStatus.Playing)

    private fun show() {
        val channels = channelsFixture(playing = true)
        val channel = channels.rows.first().copy(
            details = ChannelDetails(
                artwork = BitmapPainter(TouchFixtureArt.art("ridge-light").asImageBitmap()),
                episode = "S2 E3 • Winter on the ridge",
                metadata = "Documentary",
            ),
        )
        val fixture = LivePlayerState(
            channel = channel, channels = channels,
            frame = BitmapPainter(TouchFixtureArt.art("still-ridge-light").asImageBitmap()),
            timeline = LiveTimeline(LocalTime.of(20, 0), 3600, 1510, 2568, 2820),
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                PlayerScreen(fixture.copy(status = status.value), {}, { seeks += it }, {}, {})
            }
        }
    }

    private fun captureStatuses(prefix: String, canvas: TouchCanvas, reconnecting: Boolean = false) {
        compose.runOnIdle { status.value = LivePlayerStatus.Tuning }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_tuning)).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        capture("$prefix-player-tuning", canvas)
        compose.mainClock.autoAdvance = true
        if (reconnecting) {
            compose.runOnIdle { status.value = LivePlayerStatus.Reconnecting(12) }
            capture("$prefix-player-reconnecting", canvas)
        }
        compose.runOnIdle { status.value = LivePlayerStatus.PauseEnded }
        capture("$prefix-player-pause-ended", canvas)
        compose.runOnIdle { status.value = LivePlayerStatus.Unavailable }
        capture("$prefix-player-unavailable", canvas)
    }

    private fun assertInlineChannels() {
        tag("player-channel-list").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText(compose.activity.getString(R.string.player_channels))).assertDoesNotExist()
    }

    private fun chromeIsVisible() {
        tag("player-top").assertIsDisplayed()
        tag("player-transport").assertIsDisplayed()
        tag("player-brightness").assertIsDisplayed()
    }

    private fun orangeProgressRows(): Int {
        val image = timeline().captureToImage().toPixelMap()
        return (0 until image.height).count { y ->
            val color = image[image.width / 2, y]
            color.red > .85f && color.green in .4f..0.65f && color.blue < .35f
        }
    }

    private fun tapPicture() = tag("player-picture").performTouchInput { click(Offset(width * .25f, height * .28f)) }

    private fun dragTo(fraction: Float) = timeline().performTouchInput {
        down(Offset(width * .713f, center.y))
        moveTo(Offset(width * fraction, center.y))
    }

    private fun moveTo(fraction: Float) = timeline().performTouchInput { moveTo(Offset(width * fraction, center.y)) }

    private fun tag(name: String) = compose.onNodeWithTag(name)
    private fun timeline() = tag("player-timeline")
    private fun controls(id: Int) = compose.onNode(
        hasText(compose.activity.getString(id)), useUnmergedTree = false,
    )

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice2-player", name, canvas)
}
