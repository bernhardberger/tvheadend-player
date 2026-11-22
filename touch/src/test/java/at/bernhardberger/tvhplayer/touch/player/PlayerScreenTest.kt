package at.bernhardberger.tvhplayer.touch.player

import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.touch.testutil.ServerGuide
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import kotlin.time.Instant

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
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.After
import org.junit.Before
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.ChannelPlayback
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.channelsFixture
import at.bernhardberger.tvhplayer.touch.channels.withoutLogos
import at.bernhardberger.tvhplayer.touch.guide.RIDGE_LIGHT_ID
import at.bernhardberger.tvhplayer.touch.guide.GuideAvailability
import at.bernhardberger.tvhplayer.touch.guide.RIDGE_LIGHT_REPEAT_ID
import at.bernhardberger.tvhplayer.touch.guide.byChannelFixture
import at.bernhardberger.tvhplayer.touch.guide.ridgeLightDetails
import at.bernhardberger.tvhplayer.touch.guide.ridgeLightRepeatDetails
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.TouchFixtureArt
import at.bernhardberger.tvhplayer.touch.testutil.capturePausedPlayer
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
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenTest {
    private val motionScale = object : MotionDurationScale { override var scaleFactor = 1f }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)

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
        compose.capturePausedPlayer("tablet-live", canvas, ::button) { paused.value = it }

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
        timeline().assertDoesNotExist()
        capture("tablet-player-hidden", canvas)
        tapPicture()
        noticeOnlyOutsideTheWindow("tablet-player-notice", canvas)
        dragToLiveWithNotice()
        val old = liveTimeline.value
        dragTo(.525f)
        compose.runOnIdle { liveTimeline.value = old.copy(programStart = old.programStart.plusHours(1)) }
        timeline().performTouchInput { up() }
        compose.runOnIdle {
            assertEquals("Lifting on a new program does not seek", listOf(2820), seeks)
            liveTimeline.value = old
        }
        captureStatuses("tablet", canvas, reconnecting = true)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun brightnessDragFollowsTheFingerWhenThePlayerWritesBackEachStep() {
        show()
        val slider = tag("player-brightness")
        // Allow a move to cross touch slop. Send the moves separately so the player can
        // recompose with the reported values while the finger is still down.
        slider.performTouchInput { down(Offset(center.x, height * .38f)) }
        repeat(5) { slider.performTouchInput { moveBy(Offset(0f, height * .064f)) } }
        slider.performTouchInput { up() }
        compose.runOnIdle {
            assertTrue(
                "Brightness should keep reporting downward moves after touch slop: $brightnessChanges",
                brightnessChanges.size >= 4 && brightnessChanges.zipWithNext().all { (a, b) -> b < a },
            )
            assertEquals("The first drag ends at 30% brightness", .30f, brightness.value, .07f)
            brightnessChanges.clear()
        }

        slider.performTouchInput { down(Offset(center.x, height * .70f)) }
        repeat(5) { slider.performTouchInput { moveBy(Offset(0f, -height * .096f)) } }
        slider.performTouchInput { up() }
        compose.runOnIdle {
            assertTrue(
                "Brightness should keep reporting upward moves after touch slop: $brightnessChanges",
                brightnessChanges.size >= 4 && brightnessChanges.zipWithNext().all { (a, b) -> b > a },
            )
            assertEquals("The second drag ends at 78% brightness", .78f, brightness.value, .07f)
            brightness.value = .4f
        }
        assertEquals(.4f, slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
    }

    private fun dragToLiveWithNotice() {
        val behind = liveTimeline.value
        fun distance() = compose.onNodeWithText(compose.activity.getString(R.string.player_behind_live, "4:12"))
        distance().assertIsDisplayed()
        button(R.string.player_go_live).assertIsDisplayed()
        val forward = button(R.string.player_forward_thirty)
        forward.assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(PlayerAction.ForwardThirty, actions.last()) }
        notice.value = Notice.CacheClear(success = true)
        val snackbar = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        snackbar.assertIsDisplayed()
        val liveEdge = 2820f / 3600
        dragTo(.525f)
        moveTo(liveEdge)
        val track = timeline().fetchSemanticsNode().boundsInRoot
        val label = text(R.string.player_live).fetchSemanticsNode().boundsInRoot
        val thumbX = track.left + track.width * liveEdge
        snackbar.assertIsDisplayed()
        assertEquals("Live drag label follows the thumb while the notice shows", thumbX, label.center.x, 1f)
        timeline().performTouchInput { cancel() }
        notice.value = null

        compose.runOnIdle { liveTimeline.value = behind.copy(position = behind.live, behind = 0) }
        text(R.string.player_live).assertDoesNotExist()
        distance().assertDoesNotExist()
        button(R.string.player_go_live).assertDoesNotExist()
        val actionsBeforeTap = compose.runOnIdle { actions.toList() }
        forward.assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(actionsBeforeTap, actions) }
        button(R.string.player_back_ten).assertIsEnabled()
        val boundary = compose.activity.getString(
            SharedR.string.timeshift_buffer_start_description,
            formatPlaybackDuration((behind.live - checkNotNull(behind.bufferStart)) * 1_000L),
        )
        val liveDescription = compose.activity.getString(SharedR.string.player_seekbar_timeshift_live_description, boundary)
        timeline().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, liveDescription))
        compose.runOnIdle { liveTimeline.value = behind }
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun uprightTabletShowsChannelListInPlaceOfButton() {
        show()
        assertInlineChannels()
        channelHandle(SemanticsActions.Expand).assertDoesNotExist()
        channelHandle(SemanticsActions.Collapse).assertDoesNotExist()
        capture("tablet-upright-player", TouchCanvas.TABLET_UPRIGHT)
        infoSheet("tablet-upright-player", TouchCanvas.TABLET_UPRIGHT, details = false)
        assertOfflineChannels(R.string.channels_offline)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun controlsLeaveByThemselvesWhilePlayingAndATouchRestartsTheCount() {
        // Before composing: idling with the clock running would let the count run out unseen.
        compose.mainClock.autoAdvance = false
        show(hideAfterMs = hideMs)
        pressPlayPause(R.string.player_pause)
        compose.mainClock.advanceTimeBy(hideMs * 2)
        tag("player-transport").assertIsDisplayed()
        // The end of the pause starts the count anew.
        pressPlayPause(R.string.player_play)
        compose.mainClock.advanceTimeBy(hideMs * 3 / 4)
        tag("player-transport").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(hideMs / 2)
        tag("player-transport").assertDoesNotExist()
        tapPicture()
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
        assertPictureChromeMotion()
        compose.mainClock.autoAdvance = true
    }

    private fun assertPictureChromeMotion() {
        val picture = tag("player-picture")
        val hidden = picture.redAt(Offset(40f, 50f))
        tapPicture()
        compose.mainClock.advanceTimeBy(80)
        val top = tag("player-top").fetchSemanticsNode().boundsInRoot
        val partial = picture.redAt(Offset(40f, 50f))
        compose.mainClock.advanceTimeBy(350)
        val shown = picture.redAt(Offset(40f, 50f))
        assertTrue("The controls are partly drawn during entry: $hidden, $partial, $shown", partial in shown..hidden &&
            partial != hidden && partial != shown)
        assertEquals(top, tag("player-top").fetchSemanticsNode().boundsInRoot)

        tapPicture()
        compose.mainClock.advanceTimeBy(80)
        tag("player-top").assertDoesNotExist()
        val leaving = picture.redAt(Offset(40f, 50f))
        tapPicture()
        compose.mainClock.advanceTimeByFrame()
        assertEquals("A touch reverses the fade without jumping", leaving, picture.redAt(Offset(40f, 50f)), .08f)

        compose.runOnUiThread { motionScale.scaleFactor = 0f }
        togglePicture()
        compose.mainClock.advanceTimeByFrame()
        tag("player-top").assertDoesNotExist()
        assertEquals(hidden, picture.redAt(Offset(40f, 50f)), .01f)
        togglePicture()
        compose.mainClock.advanceTimeByFrame()
        tag("player-top").assertIsDisplayed()
        assertEquals(shown, picture.redAt(Offset(40f, 50f)), .01f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phonePictureTapHidesControlsButKeepsThinProgress() {
        show()
        val canvas = TouchCanvas.PHONE
        assertInlineChannels()
        capture("phone-player", canvas)
        compose.capturePausedPlayer("phone-live", canvas, ::button) { paused.value = it }
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
        status.value = LivePlayerStatus.Playing
        checkPhoneChannelSheet(canvas)
    }

    private fun checkPhoneChannelSheet(canvas: TouchCanvas) {
        val expand = channelHandle(SemanticsActions.Expand)
        val collapse = channelHandle(SemanticsActions.Collapse)
        val picture = tag("player-picture").getBoundsInRoot()
        val actions = tag("player-actions").assertIsDisplayed().getBoundsInRoot()
        val restHandle = expand.getBoundsInRoot()
        val restTop = restHandle.top
        val stockHeight = restHandle.bottom - restTop
        assertTrue("The channel sheet rests below the action row", restTop >= actions.bottom)
        val scrollableProgram = hasAnyDescendant(hasTestTag("player-actions")) and
            SemanticsMatcher("has vertical scroll range") {
                val range = SemanticsProperties.VerticalScrollAxisRange
                range in it.config && it.config[range].maxValue() > 0f
            }
        compose.onAllNodes(scrollableProgram, useUnmergedTree = true).assertCountEquals(0)

        expand.performTouchInput { click() }
        fun assertRaised() {
            val sheetTop = collapse.getBoundsInRoot().top
            assertEquals("Raised sheet stops at the picture", picture.bottom.value, sheetTop.value, 1f)
            assertEquals("The sheet does not move the picture", picture, tag("player-picture").getBoundsInRoot())
        }
        assertRaised()
        capture("phone-player-list-raised", canvas)
        collapse.performTouchInput { click() }
        assertEquals(restTop.value, expand.getBoundsInRoot().top.value, 1f)
        tag("player-actions").assertIsDisplayed()

        expand.performTouchInput {
            swipe(center, center - Offset(0f, (restTop - picture.bottom).toPx()), durationMillis = 240)
        }
        assertRaised()
        val sport = compose.onNode(hasText("Harbor Sport") and hasAnyAncestor(hasTestTag("player-channel-list")))
        sport.performClick()
        compose.runOnIdle {
            assertEquals(listOf("harbor-sport"), played)
            channelId.value = "harbor-sport"
        }
        assertRaised()
        val feedback = Notice.CacheClear(success = true)
        notice.value = feedback
        compose.onNodeWithText(NoticeFormatter(compose.activity).format(feedback).headline).assertIsDisplayed()
        notice.value = null

        collapse.performTouchInput { click() }
        sport.performTouchInput {
            swipe(center, center - Offset(0f, (restTop - picture.bottom).toPx()), durationMillis = 240)
        }
        assertRaised()

        collapse.performSemanticsAction(SemanticsActions.Collapse)
        compose.runOnIdle {
            channelId.value = null
            val height = (420 * compose.activity.resources.displayMetrics.density).roundToInt()
            compose.activity.window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, height)
        }
        val shortHandle = expand.getBoundsInRoot()
        val shortHeight = shortHandle.bottom - shortHandle.top
        assertTrue("Short window handle $shortHeight is shorter than the stock $stockHeight", shortHeight >= stockHeight)
        val row = tag("player-actions")
        val fullRowHeight = actions.bottom - actions.top
        val clippedRow = row.getBoundsInRoot()
        assertTrue("The action row does not fully fit at rest", clippedRow.bottom - clippedRow.top < fullRowHeight)
        row.performScrollTo().assertIsDisplayed()
        val scrolledRow = row.getBoundsInRoot()
        assertEquals(fullRowHeight, scrolledRow.bottom - scrolledRow.top)
        assertTrue("The full action row is above the resting sheet", scrolledRow.bottom <= expand.getBoundsInRoot().top)
        assertEquals(picture, tag("player-picture").getBoundsInRoot())
        compose.onNode(scrollableProgram, useUnmergedTree = true).performTouchInput {
            swipe(Offset(center.x, height * .75f), Offset(center.x, height * .25f), durationMillis = 240)
        }
        assertEquals(shortHandle, expand.getBoundsInRoot())
        expand.assertIsDisplayed().performSemanticsAction(SemanticsActions.Expand)
        assertRaised()
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
        noticeOnlyOutsideTheWindow("phone-player-landscape-notice", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletMenusOpenAtTheirButtonAndSheetsLeaveThePictureClear() {
        show()
        menusAndSheets("tablet-player", TouchCanvas.TABLET, subtitles = true, profile = true)
        infoSheet("tablet-player", TouchCanvas.TABLET, details = true)
        val top = hasAnyAncestor(hasTestTag("player-top"))
        val tile = compose.onNode(hasTestTag("channel-logo-tile") and top, useUnmergedTree = true)
        val keyline = tile.getBoundsInRoot().left
        compose.runOnIdle {
            channelId.value = "harbor-sport"
            channelState.value = ChannelsState.Ready(channelsFixture().withoutLogos("harbor-sport"))
        }
        tile.assertDoesNotExist()
        val name = compose.onNode(hasText("Harbor Sport") and top, useUnmergedTree = true)
        assertEquals(
            "A channel without a logo starts next to Back, with no logo padding",
            keyline, name.getBoundsInRoot().left,
        )
        button(R.string.player_info).performClick()
        tag("player-info-logo").assertDoesNotExist()
        compose.onNodeWithText("Harbor Sport").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneMenusAndSheets() {
        show()
        menusAndSheets("phone-player-landscape", TouchCanvas.PHONE_LANDSCAPE, subtitles = false, profile = false)
        infoSheet("phone-player-landscape", TouchCanvas.PHONE_LANDSCAPE, details = false)
        button(R.string.player_channels).performClick()
        assertOfflineChannels(R.string.channels_saved)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun channelsSheetSurvivesZapAndHoldsTheControls() {
        show(hideAfterMs = hideMs)
        button(R.string.player_channels).performClick()
        val close = button(R.string.side_sheet_close)
        close.assertIsDisplayed()
        val switched = channelsFixture().let { list ->
            list.copy(rows = list.rows.map { row ->
                if (row.id == "harbor-sport") row.copy(playback = ChannelPlayback(null)) else row
            })
        }
        compose.onNodeWithText("Harbor Sport").performClick()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            assertEquals(listOf("harbor-sport"), played)
            channelId.value = "harbor-sport"
            channelState.value = ChannelsState.Ready(switched)
        }
        compose.mainClock.advanceTimeBy(hideMs + 32)
        val playing = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, compose.activity.getString(R.string.channels_playing),
        )
        compose.onNode(playing and hasText("Harbor Sport")).assertIsDisplayed()
        tag("player-picture").assertIsDisplayed()
        close.assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        back()
        close.assertDoesNotExist()
        chromeIsVisible()

        button(R.string.player_channels).performClick()
        close.assertIsDisplayed()
        offered.value -= PlayerControl.Channels
        close.assertDoesNotExist()
        button(R.string.player_channels).assertDoesNotExist()
        chromeIsVisible()
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun channelsSheetUsesTheBrowseFormsWithoutSettings() {
        channelState.value = ChannelsState.Loading
        show()
        button(R.string.player_channels).performClick()
        text(R.string.channels_loading).assertIsDisplayed()

        val channels = channelsFixture()
        channelState.value = ChannelsState.Ready(channels.copy(rows = emptyList(), selectedTagId = "news"))
        button(R.string.channels_all).performClick()
        compose.runOnIdle { assertEquals(PlayerChoice.ChannelTag(null), choices.last()) }

        channelState.value = ChannelsState.SignInNeeded
        text(SharedR.string.error_106_title).assertIsDisplayed()
        button(R.string.channels_connection_settings).assertDoesNotExist()

        channelState.value = ChannelsState.Failed(
            AppError(AppErrorCondition.AUTHENTICATION_REJECTED, listOf(AppErrorAction.CONNECTION_SETTINGS)),
        )
        text(R.string.channels_sign_in_failed).assertIsDisplayed()
        button(R.string.channels_connection_settings).assertDoesNotExist()

        channelState.value = ChannelsState.Unreachable
        button(R.string.channels_retry).performClick()
        compose.runOnIdle { assertEquals(PlayerAction.RetryChannels, actions.last()) }
        button(R.string.channels_connection_settings).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun phoneListsOpenAsBottomSheetsOverThinProgress() {
        show()
        val canvas = TouchCanvas.PHONE
        button(R.string.player_record).performClick()
        compose.runOnIdle { assertEquals(PlayerChoice.LiveRecord(fixtureRecord), choices.removeLast()) }
        text(R.string.player_record_series).assertDoesNotExist()
        timeline().assertExists()
        capture("phone-player-record", canvas)
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

        button(R.string.player_info).performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performTouchInput {
            swipe(center, center + Offset(0f, 450f), durationMillis = 240)
        }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    private val seeks = mutableListOf<Int>()
    private val actions = mutableListOf<PlayerAction>()
    private val choices = mutableListOf<PlayerChoice>()
    private val played = mutableListOf<String>()
    private val status = mutableStateOf<LivePlayerStatus>(LivePlayerStatus.Playing)
    private val liveTimeline = mutableStateOf(LiveTimeline(LocalTime.of(20, 0), 3600, 1510, 2568, 2820))
    private val channelId = mutableStateOf<String?>(null)
    private val channelState = mutableStateOf<ChannelsState?>(null)
    private val scheduleDay = mutableStateOf(0)
    private val scheduleHasRows = mutableStateOf(true)
    private val schedulePending = mutableStateOf(false)
    private val scheduleAvailability = mutableStateOf<GuideAvailability>(GuideAvailability.Current)

    private val paused = mutableStateOf(false)
    private val brightness = mutableStateOf(.62f)
    private val brightnessChanges = mutableListOf<Float>()
    private val offered = mutableStateOf(PlayerControl.entries.toSet())
    private val fixtureRecord = LiveRecordOffer(
        EventId(1), ProgramRecordControl.Record,
        requireNotNull(ServerGuide(Instant.fromEpochSeconds(1_800_000_000)).connected().currentSession)
            .generationIdentity,
    )
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
            channel = channel, channels = ChannelsState.Ready(channels),
            liveRecord = fixtureRecord,
            frame = BitmapPainter(TouchFixtureArt.art("still-ridge-light").asImageBitmap()),
            timeline = liveTimeline,
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
                    day.channels.first().programs.map {
                        if (it.id == RIDGE_LIGHT_ID) it.copy(watching = true, record = ProgramRecordControl.Record) else it
                    },
                    day.days, day.selectedDay, day.date,
                    details = mapOf(
                        RIDGE_LIGHT_ID to ridgeLightDetails().copy(watching = true, record = ProgramRecordControl.Record),
                        RIDGE_LIGHT_REPEAT_ID to ridgeLightRepeatDetails(),
                    )::get,
                )
            },
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                PlayerScreen(
                    fixture.copy(
                        channel = ((channelState.value as? ChannelsState.Ready)?.list ?: channels)
                            .rows.firstOrNull { it.id == channelId.value } ?: fixture.channel,
                        channels = channelState.value ?: fixture.channels,
                        status = status.value, paused = paused.value, offered = offered.value,
                        brightness = brightness.value,
                        schedule = fixture.schedule?.let { schedule ->
                            schedule.copy(
                                programs = schedule.programs.takeIf { channelId.value == null && scheduleHasRows.value }
                                    .orEmpty(),
                                selectedDay = scheduleDay.value,
                                dayLoading = schedulePending.value && scheduleAvailability.value == GuideAvailability.Current,
                                availability = scheduleAvailability.value,
                            )
                        },
                    ),
                    {
                        actions += it
                        if (it == PlayerAction.TogglePlay) paused.value = !paused.value
                    }, { seeks += it },
                    { brightness.value = it; brightnessChanges += it }, { played += it }, { choices += it },
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
        val landscape = canvas == TouchCanvas.TABLET || canvas == TouchCanvas.PHONE_LANDSCAPE
        val program = if (landscape) tag("player-program").fetchSemanticsNode().boundsInRoot else null
        notice.value = failure
        compose.onNodeWithText(headline).assertIsDisplayed()
        capture(name, canvas)
        if (program != null) {
            landscapeNoticePlacement(program, replacesProgram = canvas == TouchCanvas.PHONE_LANDSCAPE)
            notice.value = failure
            compose.onNodeWithText(headline).assertIsDisplayed()
        }
        inWindow.value = true
        compose.onNodeWithText(headline).assertDoesNotExist()
        inWindow.value = false
        notice.value = null
        compose.waitForIdle()
        if (landscape) compose.onNodeWithText("Ridge Light").assertIsDisplayed()
    }

    private fun landscapeNoticePlacement(program: Rect, replacesProgram: Boolean) {
        val snackbar = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        val picture = tag("player-picture").fetchSemanticsNode().boundsInRoot
        val top = tag("player-top").fetchSemanticsNode().boundsInRoot
        val twoLine = snackbar.fetchSemanticsNode().boundsInRoot
        val transport = tag("player-transport").fetchSemanticsNode().boundsInRoot
        assertTrue("The two-line notice must clear transport", !twoLine.overlaps(transport))
        assertEquals("Notice starts at the program keyline", program.left, twoLine.left, 1f)
        if (replacesProgram) {
            compose.onNodeWithText("Ridge Light").assertIsNotDisplayed()
            assertEquals("Notice stands at the foot of the slot", program.bottom, twoLine.bottom, 1f)
        } else {
            assertTrue("Notice must be above the program", twoLine.bottom < program.top)
        }
        val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.config.getOrElse(SemanticsProperties.TestTag) { "" } != "player-picture" }
            .map { it.boundsInRoot }
        notice.value = Notice.CacheClear(success = true)
        val oneLine = snackbar.fetchSemanticsNode().boundsInRoot
        val obstacles = buttons + timeline().fetchSemanticsNode().boundsInRoot + top + transport
        assertTrue("Notice overlaps the top row, a timeline or a button", obstacles.none {
            oneLine.overlaps(it) || twoLine.overlaps(it)
        })
        compose.mainClock.autoAdvance = false
        togglePicture()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        val moving = snackbar.fetchSemanticsNode().boundsInRoot.bottom
        compose.mainClock.autoAdvance = true
        timeline().assertDoesNotExist()
        val hidden = snackbar.fetchSemanticsNode().boundsInRoot
        assertTrue("Notice travels between ${oneLine.bottom} and ${hidden.bottom}: $moving",
            moving > oneLine.bottom && moving < hidden.bottom)
        assertEquals("Hidden notice uses the bottom margin", twoLine.left - picture.left, picture.bottom - hidden.bottom, 1f)
        tapPicture()
        val restored = snackbar.fetchSemanticsNode().boundsInRoot
        if (replacesProgram) {
            compose.onNodeWithText("Ridge Light").assertIsNotDisplayed()
            assertEquals("Notice returns to the program slot", program.bottom, restored.bottom, 1f)
        } else {
            assertTrue("Notice returns above the program", restored.bottom < program.top)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { motionScale.scaleFactor = 0f }
        togglePicture()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(hidden.bottom, snackbar.fetchSemanticsNode().boundsInRoot.bottom, 1f)
        togglePicture()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(restored.bottom, snackbar.fetchSemanticsNode().boundsInRoot.bottom, 1f)
        compose.runOnUiThread { motionScale.scaleFactor = 1f }
        compose.mainClock.autoAdvance = true
    }

    private fun togglePicture() = tag("player-picture").performSemanticsAction(SemanticsActions.OnClick) {
        it()
        Snapshot.sendApplyNotifications()
    }

        /** Each menu opens at its button with the controls kept; each sheet clears the picture and closes on it. */
    private fun menusAndSheets(prefix: String, canvas: TouchCanvas, subtitles: Boolean, profile: Boolean) {
        button(R.string.player_record).performClick()
        compose.runOnIdle { assertEquals(PlayerChoice.LiveRecord(fixtureRecord), choices.removeLast()) }
        text(R.string.player_record_all).assertDoesNotExist()
        chromeIsVisible()
        capture("$prefix-record", canvas)

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
        compose.onNode(
            hasClickAction() and hasText(compose.activity.getString(R.string.channels_record)) and
                !hasAnyAncestor(hasTestTag("player-actions")),
        ).assertExists()
        compose.onNodeWithContentDescription(recordOptions).assertDoesNotExist()
        button(R.string.channels_details).assertExists()
        text(R.string.channels_watch).assertDoesNotExist()
        tag("player-transport").assertDoesNotExist()
        if (canvas == TouchCanvas.TABLET) {
            val facts = compose.onNodeWithText("60m • 2024 • Documentary").assertIsDisplayed()
            compose.onAllNodes(hasText("8:00–9:00 PM", substring = true)).assertCountEquals(1)
            val range = compose.onNodeWithText("8:00–9:00 PM").assertIsDisplayed()
            val title = compose.onNode(hasText("Ridge Light") and !hasClickAction()).getBoundsInRoot()
            assertTrue(range.getBoundsInRoot().top < title.top && title.top < facts.getBoundsInRoot().top)
            compose.onNodeWithText("13m left").assertIsDisplayed()
        }
        captureWindows("$prefix-info", canvas)
        if (details) {
            button(R.string.channels_details).performClick()
            compose.onNodeWithText("Tomorrow 2:00 AM").assertExists()
            button(R.string.channels_details).assertDoesNotExist()
            text(R.string.channels_watch).assertDoesNotExist()
            text(R.string.channels_show_in_guide).assertDoesNotExist()
            captureWindows("$prefix-info-details", canvas)
            compose.onNodeWithContentDescription(recordOptions).assertDoesNotExist()
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

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun infoReturnsToTheNewChannelsDayAfterZapAndClosesWhenWithdrawn() {
        show()
        button(R.string.player_info).performClick()
        button(R.string.channels_details).performClick()
        compose.onNodeWithText("Tomorrow 2:00 AM").assertExists()
        compose.runOnIdle { channelId.value = "harbor-sport" }
        compose.onNodeWithText("Tomorrow 2:00 AM").assertDoesNotExist()
        compose.onNodeWithText("Harbor Sport").assertIsDisplayed()
        text(R.string.guide_no_schedule).assertIsDisplayed()
        compose.runOnIdle { offered.value = offered.value - PlayerControl.Info }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.side_sheet_close)).assertDoesNotExist()
        tag("player-transport").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun infoKeepsSavedRowsAndOnlyShowsPendingIndicatorForAnEmptyDay() {
        show()
        button(R.string.player_info).performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Tomorrow").performClick()
        compose.runOnIdle {
            assertEquals(PlayerChoice.Day(1), choices.last())
            scheduleDay.value = 1
            schedulePending.value = true
        }
        val loading = compose.onNodeWithContentDescription(compose.activity.getString(SharedR.string.epg_loading))
        button(R.string.channels_details).assertIsDisplayed()
        loading.assertDoesNotExist()
        compose.runOnIdle { scheduleHasRows.value = false }
        loading.assertIsDisplayed()
        compose.runOnIdle {
            scheduleHasRows.value = true
            scheduleAvailability.value = GuideAvailability.Offline
        }
        text(R.string.guide_saved).assertIsDisplayed()
        button(R.string.channels_details).assertIsDisplayed()
        loading.assertDoesNotExist()
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

    private fun channelHandle(action: SemanticsPropertyKey<*>) =
        compose.onNode(SemanticsMatcher.keyIsDefined(action))

    private fun assertOfflineChannels(message: Int) {
        channelState.value = ChannelsState.Offline(channelsFixture())
        text(message).assertIsDisplayed()
        compose.onNodeWithText("Harbor Sport").assertIsDisplayed()
        button(R.string.channels_connection_settings).assertDoesNotExist()
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

    private fun pressPlayPause(label: Int) {
        button(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .assert(hasContentDescription(compose.activity.getString(label)))
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(label == R.string.player_pause, paused.value) }
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

internal fun androidx.compose.ui.test.SemanticsNodeInteraction.redAt(point: Offset): Float =
    captureToImage().toPixelMap()[point.x.roundToInt(), point.y.roundToInt()].red

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
