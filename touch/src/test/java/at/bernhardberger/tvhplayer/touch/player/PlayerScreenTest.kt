package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import androidx.annotation.OptIn
import androidx.compose.runtime.SideEffect
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvhplayer.ui.common.AppClock
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.touch.ui.TouchNotice
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import at.bernhardberger.tvhplayer.core.HumanTrackLabel
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Before
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.guide.RIDGE_LIGHT_ID
import at.bernhardberger.tvhplayer.touch.guide.RIDGE_LIGHT_REPEAT_ID
import at.bernhardberger.tvhplayer.touch.guide.byChannelFixture
import at.bernhardberger.tvhplayer.touch.guide.ridgeLightDetails
import at.bernhardberger.tvhplayer.touch.guide.ridgeLightRepeatDetails
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

    private val uses24Hour = AppClock.systemUses24Hour
    private val notice = mutableStateOf<Notice?>(null)
    private val inWindow = mutableStateOf(false)

    /** The drawings show a 12-hour clock. */
    @Before
    fun twelveHourClock() {
        AppClock.systemUses24Hour = false
    }

    @After
    fun restoreClock() {
        AppClock.systemUses24Hour = uses24Hour
    }

    /**
     * Closing the window with its X: the mode change, then the stop and finish, then whatever the
     * main looper still holds. The system forgets the activity's token on dismissal and answers a
     * later parameter update with IllegalStateException; the activity below does the same once it is
     * stopped, which Robolectric's own shadow does not.
     */
    @Test
    @OptIn(UnstableApi::class)
    fun closingTheWindowSendsNoParametersToTheGoneActivity() {
        shadowOf(compose.activity.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val controller = Robolectric.buildActivity(TokenActivity::class.java).setup()
        val activity = controller.get()
        val player = ExoPlayer.Builder(activity).build()
        val window = PlayerWindow(activity, player)
        val view = PlayerView(activity)
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        window.open()
        window.attach(view)
        assertTrue("Parameters reach the activity while it plays", activity.updates > 0)
        window.left()
        controller.pause().stop()
        // The closing window still lays the picture out once more.
        view.layout(0, 0, view.width / 2 + 1, view.height / 2 + 1)
        activity.finish()
        shadowOf(Looper.getMainLooper()).idle()
        // Then onDestroy lets go of the window and the picture's view.
        window.close()
        controller.destroy()
        player.release()
        // Not even an attempt: the narrow catch is only for the gap the system leaves.
        assertEquals(0, activity.late)
    }

    /** Playback starting while the face is composed must not leave the Play glyph behind. */
    @Test
    @OptIn(UnstableApi::class)
    fun playIntentKeepsAChangeMadeBeforeItsListenerIsRegistered() {
        val player = PlayIntentPlayer()
        lateinit var playing: () -> Boolean
        compose.setContent {
            val state = rememberPlayWhenReady(player)
            playing = { state.value }
            SideEffect { player.play() }
        }
        compose.waitForIdle()
        assertTrue(playing())
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletCapturesDragAndSeeksOnlyOnLift() {
        show()
        val canvas = TouchCanvas.TABLET
        capture("tablet-player", canvas)
        noticeOnlyOutsideTheWindow("tablet-player-notice", canvas)

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
        infoSheet("tablet-upright-player", TouchCanvas.TABLET_UPRIGHT, details = false)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun controlsLeaveByThemselvesWhilePlayingAndATouchRestartsTheCount() {
        // Before composing: idling with the clock running would let the count run out unseen.
        compose.mainClock.autoAdvance = false
        show(hideAfterMs = hideMs)
        compose.mainClock.advanceTimeBy(hideMs * 3 / 4)
        // A finger resting on the bar holds them; lifting it starts the count anew.
        timeline().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(hideMs * 2)
        tag("player-transport").assertIsDisplayed()
        timeline().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(hideMs * 3 / 4)
        tag("player-transport").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(hideMs / 2)
        tag("player-transport").assertDoesNotExist()
        // Upright, only the layer over the picture leaves: the list below stays.
        assertInlineChannels()
        tapPicture()
        compose.mainClock.advanceTimeByFrame()
        paused.value = true
        compose.mainClock.advanceTimeBy(hideMs * 2)
        tag("player-transport").assertIsDisplayed()
        // The end of the hold starts the count anew.
        paused.value = false
        compose.mainClock.advanceTimeBy(hideMs * 3 / 4)
        tag("player-transport").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(hideMs / 2)
        tag("player-transport").assertDoesNotExist()
        // A list whose control leaves (audio down to one track) closes and stops holding them.
        tapPicture()
        compose.mainClock.advanceTimeByFrame()
        button(R.string.player_audio).performClick()
        compose.mainClock.advanceTimeBy(hideMs * 2)
        text(SharedR.string.audio_description).assertExists()
        offered.value -= PlayerControl.Audio
        compose.mainClock.advanceTimeBy(hideMs * 3 / 4)
        text(SharedR.string.audio_description).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(hideMs * 2)
        tag("player-transport").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phonePictureTapHidesControlsButKeepsThinProgress() {
        show()
        val canvas = TouchCanvas.PHONE
        assertInlineChannels()
        capture("phone-player", canvas)
        noticeOnlyOutsideTheWindow("phone-player-notice", canvas)
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
        infoSheet("tablet-player", TouchCanvas.TABLET, details = true)
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneMenusAndSheets() {
        show()
        menusAndSheets("phone-player-landscape", TouchCanvas.PHONE_LANDSCAPE, subtitles = false, profile = false)
        infoSheet("phone-player-landscape", TouchCanvas.PHONE_LANDSCAPE, details = false)
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

        infoSheet("phone-player", canvas, details = true)
    }

    private val seeks = mutableListOf<Int>()
    private val actions = mutableListOf<PlayerAction>()
    private val choices = mutableListOf<PlayerChoice>()
    private val played = mutableListOf<String>()
    private val status = mutableStateOf<LivePlayerStatus>(LivePlayerStatus.Playing)

    private val paused = mutableStateOf(false)
    private val offered = mutableStateOf(PlayerControl.entries.toSet())
    private val hideMs = 1_000L

    private fun show(hideAfterMs: Long? = null) {
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
            timeline = mutableStateOf(LiveTimeline(LocalTime.of(20, 0), 3600, 1510, 2568, 2820)),
            tracks = PlayerTracks(
                audio = listOf(
                    HumanTrackLabel("English • Stereo", "English", null, "Stereo • MPEG-1 Layer II"),
                    HumanTrackLabel("English • 5.1", "English", null, "5.1 • Dolby Digital"),
                    HumanTrackLabel(
                        "English • Stereo • Audio description", "Audio description", "English", "Stereo • AAC",
                    ),
                ),
                playingAudio = 0,
                subtitles = listOf(
                    HumanTrackLabel("English", "English", null, "DVB"),
                    HumanTrackLabel("English", "English", null, "Teletext"),
                ),
            ),
            options = PlaybackOptions(
                profiles = listOf(StreamProfile("htsp", direct = true), StreamProfile("h264-720p"), StreamProfile("hevc-1080p")),
            ),
            // The player marks Ridge Light, on now on the channel playing, as the item being watched.
            schedule = byChannelFixture(phone = true).let { day ->
                ChannelSchedule(
                    day.channels.first().programs.map { it.copy(watching = it.id == RIDGE_LIGHT_ID) },
                    day.days, day.selectedDay, day.date,
                    details = mapOf(
                        RIDGE_LIGHT_ID to ridgeLightDetails().copy(watching = true),
                        RIDGE_LIGHT_REPEAT_ID to ridgeLightRepeatDetails(),
                    ),
                )
            },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                PlayerScreen(
                    fixture.copy(status = status.value, paused = paused.value, offered = offered.value),
                    { actions += it }, { seeks += it }, {}, { played += it }, { choices += it },
                    inWindow = inWindow.value, hideAfterMs = hideAfterMs,
                ) { modifier ->
                    val formatter = NoticeFormatter(LocalContext.current)
                    notice.value?.let { formatter.format(it) }?.let { TouchNotice(it.headline, it.detail, modifier) }
                }
            }
        }
    }

    /** A failure posted while playing shows in the face as the frame shows it, never in the window. */
    private fun noticeOnlyOutsideTheWindow(name: String, canvas: TouchCanvas) {
        val failure = Notice.DvrActionFailed(DvrMutationKind.CANCEL, DvrMutationFeedback.CONNECTION_LIMIT)
        val headline = NoticeFormatter(compose.activity).format(failure).headline
        notice.value = failure
        compose.onNodeWithText(headline).assertIsDisplayed()
        capture(name, canvas)
        inWindow.value = true
        compose.onNodeWithText(headline).assertDoesNotExist()
        inWindow.value = false
        notice.value = null
        compose.waitForIdle()
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
        text(SharedR.string.audio_description).assertIsDisplayed()
        captureWindows("$prefix-audio", canvas)
        text(SharedR.string.audio_description).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.Audio(TrackPick.Track(2))), choices) }
        text(SharedR.string.audio_description).assertDoesNotExist()

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

    /**
     * Info opens on the watched program with Record and Details but no Watch; Details replaces the day in
     * the sheet without Watch or Show in Guide, and Back returns to the day, then closes the sheet.
     */
    private fun infoSheet(prefix: String, canvas: TouchCanvas, details: Boolean) {
        val recordOptions = compose.activity.getString(R.string.guide_record_options)
        button(R.string.player_info).performClick()
        // The phone held sideways shows the open program's head; its buttons scroll into view.
        compose.onNodeWithContentDescription(recordOptions).assertExists()
        button(R.string.channels_details).assertExists()
        text(R.string.channels_watch).assertDoesNotExist()
        tag("player-transport").assertDoesNotExist()
        captureWindows("$prefix-info", canvas)
        if (details) {
            button(R.string.channels_details).performClick()
            compose.onNodeWithText("Tomorrow 2:00 AM").assertExists()
            button(R.string.channels_details).assertDoesNotExist()
            text(R.string.channels_watch).assertDoesNotExist()
            text(R.string.channels_show_in_guide).assertDoesNotExist()
            captureWindows("$prefix-info-details", canvas)
            compose.onNodeWithContentDescription(recordOptions).performClick()
            compose.runOnIdle { assertEquals(PlayerChoice.RecordOptions(RIDGE_LIGHT_ID), choices.last()) }
            // A repeat on another channel opens its Details in the sheet, under that channel's name.
            compose.onNodeWithText("Sun 4:00 PM").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(PlayerChoice.Details(RIDGE_LIGHT_REPEAT_ID), choices.last()) }
            compose.onNodeWithText("Today 8:00 PM").assertExists()
            compose.onNodeWithText("Northline World").assertIsDisplayed()
            if (canvas == TouchCanvas.TABLET) captureWindows("$prefix-info-airing", canvas)
            back()
            compose.onNodeWithText("Tomorrow 2:00 AM").assertExists()
            compose.onNodeWithText("Northline World").assertDoesNotExist()
            back()
            button(R.string.channels_details).assertIsDisplayed()
        }
        back()
        compose.onNodeWithContentDescription(recordOptions).assertDoesNotExist()
        tag("player-transport").assertIsDisplayed()
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
        compose.runOnIdle { status.value = LivePlayerStatus.Unavailable() }
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

@OptIn(UnstableApi::class)
/** Like the system after a dismissal: parameters for a stopped activity have no token to land on. */
private class TokenActivity : ComponentActivity() {
    var updates = 0
    var late = 0

    override fun setPictureInPictureParams(params: PictureInPictureParams) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) late++
        check(late == 0) {
            "setPictureInPictureParams: Can't find activity for token"
        }
        updates++
    }
}

private class PlayIntentPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private var playWhenReady = false

    override fun getState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }
}
