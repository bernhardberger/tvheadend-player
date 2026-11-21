package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
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
import org.robolectric.shadows.ShadowDialog

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

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletMenusOpenAtTheirButtonAndSheetsLeaveThePictureClear() {
        show()
        menusAndSheets("tablet-player", TouchCanvas.TABLET, subtitles = true, profile = true)
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneMenusAndSheets() {
        show()
        menusAndSheets("phone-player-landscape", TouchCanvas.PHONE_LANDSCAPE, subtitles = false, profile = false)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneListsOpenAsBottomSheetsOverThinProgress() {
        show()
        val canvas = TouchCanvas.PHONE
        button(R.string.player_record).performClick()
        text(R.string.player_record_series).assertIsDisplayed()
        tag("player-transport").assertDoesNotExist()
        timeline().assertExists()
        captureWindows("phone-player-record", canvas)
        text(R.string.player_record_series).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerAction.RecordSeries), actions) }
        text(R.string.player_record_series).assertDoesNotExist()
        tag("player-transport").assertIsDisplayed()

        button(R.string.player_audio).performClick()
        captureWindows("phone-player-audio", canvas)
        compose.onNodeWithText("5.1 • Dolby Digital").performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.Audio(TrackPick.Track(1))), choices) }
        text(R.string.player_audio_automatic).assertDoesNotExist()

        button(R.string.player_subtitles).performClick()
        captureWindows("phone-player-subtitles", canvas)
        back()
        compose.onNodeWithText("Teletext").assertDoesNotExist()

        optionsPages("phone-player-options", canvas, profile = true)
        back()
        text(R.string.player_display_mode).assertDoesNotExist()
        tag("player-transport").assertIsDisplayed()
    }

    private val seeks = mutableListOf<Int>()
    private val actions = mutableListOf<PlayerAction>()
    private val choices = mutableListOf<PlayerChoice>()
    private val played = mutableListOf<String>()
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
            tracks = PlayerTracks(
                audio = listOf(
                    PlayerTrack("English", "MPEG-1 Layer II", "Stereo"),
                    PlayerTrack("English", "Dolby Digital", "5.1"),
                    PlayerTrack("English", "AAC", "Stereo", audioDescription = true),
                ),
                playingAudio = 0,
                subtitles = listOf(PlayerTrack("English", "DVB"), PlayerTrack("English", "Teletext")),
            ),
            options = PlaybackOptions(
                profiles = listOf(StreamProfile("htsp", direct = true), StreamProfile("h264-720p"), StreamProfile("hevc-1080p")),
            ),
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                PlayerScreen(
                    fixture.copy(status = status.value), { actions += it }, { seeks += it }, {}, { played += it },
                    { choices += it },
                )
            }
        }
    }

    /** Each menu opens at its button with the controls kept; each sheet clears the picture and closes on it. */
    private fun menusAndSheets(prefix: String, canvas: TouchCanvas, subtitles: Boolean, profile: Boolean) {
        button(R.string.player_record).performClick()
        text(R.string.player_record_all).assertIsDisplayed()
        chromeIsVisible()
        captureWindows("$prefix-record", canvas)
        text(R.string.player_record_all).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerAction.RecordAll), actions) }
        text(R.string.player_record_all).assertDoesNotExist()

        button(R.string.player_audio).performClick()
        text(R.string.player_audio_description).assertIsDisplayed()
        captureWindows("$prefix-audio", canvas)
        text(R.string.player_audio_description).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.Audio(TrackPick.Track(2))), choices) }
        text(R.string.player_audio_description).assertDoesNotExist()

        if (subtitles) {
            button(R.string.player_subtitles).performClick()
            compose.onNodeWithText("Teletext").assertIsDisplayed()
            captureWindows("$prefix-subtitles", canvas)
            button(R.string.player_subtitles).performClick()
            compose.onNodeWithText("Teletext").assertDoesNotExist()
        }

        button(R.string.player_channels).performClick()
        tag("player-top").assertDoesNotExist()
        tag("player-actions").assertDoesNotExist()
        timeline().assertDoesNotExist()
        captureWindows("$prefix-channels", canvas)
        compose.onNodeWithText("Harbor Sport").performClick()
        compose.runOnIdle { assertEquals(listOf("harbor-sport"), played) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.side_sheet_close)).assertIsDisplayed()
        tapPicture()
        chromeIsVisible()

        optionsPages("$prefix-options", canvas, profile)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.side_sheet_close)).performClick()
        chromeIsVisible()
    }

    /** Playback options: a row opens its list inside the sheet, and Back returns to the rows. */
    private fun optionsPages(name: String, canvas: TouchCanvas, profile: Boolean) {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_settings)).performClick()
        text(R.string.player_display_mode).assertIsDisplayed()
        tag("player-transport").assertDoesNotExist()
        captureWindows(name, canvas)
        text(R.string.player_stream_profile).performClick()
        compose.onNodeWithText("hevc-1080p").assertIsDisplayed()
        text(R.string.player_display_mode).assertDoesNotExist()
        if (profile) captureWindows("$name-profile", canvas)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.side_sheet_back)).performClick()
        text(R.string.player_display_mode).assertIsDisplayed()
        text(R.string.player_stream_profile).performClick()
        back()
        text(R.string.player_display_mode).assertIsDisplayed()
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
    private fun text(id: Int) = compose.onNodeWithText(compose.activity.getString(id))
    private fun button(id: Int) = compose.activity.getString(id).let {
        compose.onNode(hasClickAction() and (hasText(it) or hasContentDescription(it)))
    }
    /** System Back to the window on top: a bottom sheet's dialog, else the activity. */
    private fun back() {
        compose.waitForIdle()
        compose.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog() as? ComponentDialog
            val owner = dialog?.takeIf { it.isShowing } ?: compose.activity
            owner.onBackPressedDispatcher.onBackPressed()
        }
    }
    private fun captureWindows(name: String, canvas: TouchCanvas) = compose.captureTouchWindows("slice2-player", name, canvas)
    private fun timeline() = tag("player-timeline")
    private fun controls(id: Int) = compose.onNode(
        hasText(compose.activity.getString(id)), useUnmergedTree = false,
    )

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice2-player", name, canvas)
}
