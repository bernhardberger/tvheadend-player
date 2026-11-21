package at.bernhardberger.tvhplayer.touch.player

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.touch.testutil.captureTouchWindows
import at.bernhardberger.tvhplayer.touch.ui.TouchNotice
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordingPlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val position = mutableLongStateOf(RECORDING_BEFORE_AD)
    private val hasMarks = mutableStateOf(true)
    private val choice = mutableStateOf<AdBreakMode?>(null)
    private val notice = mutableStateOf<Notice?>(null)
    private val seeks = mutableListOf<Long>()
    private val choices = mutableListOf<PlayerChoice>()

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletMarkerSeekKeepsSheetAndDragSeeksOnlyOnLift() {
        show()
        val canvas = TouchCanvas.TABLET
        capture("tablet-recording", canvas)
        button(R.string.player_markers).performClick()
        captureWindows("tablet-recording-markers", canvas)
        val mark = 1_440_000L
        tag("recording-marker-$mark").performClick()
        compose.runOnIdle { assertEquals(listOf(mark), seeks); seeks.clear() }
        tag("recording-markers").assertIsDisplayed()
        back()
        val bar = tag("player-timeline")
        bar.performTouchInput {
            down(Offset(width / 2f, center.y))
            moveTo(Offset(width * (3_432_000f / RECORDING_LENGTH), center.y))
        }
        capture("tablet-recording-drag", canvas)
        bar.performTouchInput { moveTo(Offset(width * (3_744_000f / RECORDING_LENGTH), center.y)) }
        capture("tablet-recording-drag-snap", canvas)
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
        bar.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(fixtureAd.endMs), seeks) }

        compose.runOnIdle { hasMarks.value = false }
        button(R.string.player_markers).assertDoesNotExist()
        button(R.string.player_channels).assertDoesNotExist()
        button(R.string.player_record).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletAskOfferStaysThroughoutBreakAndDismissesOnlyBarePrompt() {
        show()
        val canvas = TouchCanvas.TABLET
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        capture("tablet-recording-ad", canvas)
        tapPicture()
        capture("tablet-recording-skip", canvas)
        compose.runOnIdle { position.longValue = fixtureAd.endMs - 1L }
        tag("recording-ad-prompt").assertIsDisplayed()
        button(SharedR.string.server_keep_watching).performClick()
        tag("recording-ad-prompt").assertDoesNotExist()
        tapPicture()
        button(SharedR.string.skip_ad_break).performClick()
        compose.runOnIdle {
            assertEquals(listOf(PlayerChoice.KeepWatching(fixtureAd), PlayerChoice.SkipAdBreak(fixtureAd)), choices)
            position.longValue = fixtureAd.endMs
        }
        button(SharedR.string.skip_ad_break).assertDoesNotExist()

        tapPicture()
        compose.runOnIdle {
            choice.value = AdBreakMode.AUTOMATIC
            notice.value = Notice.AutomaticAdBreakSkip(fixtureAd.startMs)
        }
        capture("tablet-recording-skipped", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun tabletRecordingOptionsChooseAnExplicitOverride() {
        show()
        val canvas = TouchCanvas.TABLET
        button(R.string.player_settings).performClick()
        captureWindows("tablet-recording-options", canvas)
        compose.onNodeWithText(compose.activity.getString(R.string.player_stream_profile)).assertDoesNotExist()
        button(SharedR.string.ad_breaks_setting).performClick()
        captureWindows("tablet-recording-options-adbreaks", canvas)
        button(SharedR.string.ad_breaks_automatic).performClick()
        compose.runOnIdle { assertEquals(listOf(PlayerChoice.AdBreaks(AdBreakMode.AUTOMATIC)), choices) }
    }

    @Test
    @Config(qualifiers = "en-rUS-w892dp-h412dp-land-xhdpi")
    fun landscapePhoneKeepsMarkersInASideSheet() {
        show()
        val canvas = TouchCanvas.PHONE_LANDSCAPE
        capture("phone-recording-landscape", canvas)
        button(R.string.player_markers).performClick()
        captureWindows("phone-recording-landscape-markers", canvas)
        back()
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        capture("phone-recording-landscape-ad", canvas)
        tapPicture()
        capture("phone-recording-landscape-skip", canvas)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun portraitPhoneHasInlineMarkersAndBottomSheetOptions() {
        show()
        val canvas = TouchCanvas.PHONE
        button(R.string.player_markers).assertDoesNotExist()
        tag("recording-markers").assertIsDisplayed()
        capture("phone-recording", canvas)
        compose.runOnIdle { position.longValue = RECORDING_INSIDE_AD }
        capture("phone-recording-ad", canvas)
        compose.runOnIdle { position.longValue = RECORDING_BEFORE_AD }
        button(R.string.player_settings).performClick()
        captureWindows("phone-recording-options", canvas)
    }

    private fun show() {
        val fixture = recordingPlayerFixture(position)
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                PlayerScreen(
                    fixture.copy(marks = fixture.marks.takeIf { hasMarks.value }.orEmpty(), adBreakPick = choice.value),
                    onAction = {}, onSeek = { seeks += it }, onBrightness = {}, onChoice = { choices += it },
                ) { modifier ->
                    val formatter = NoticeFormatter(LocalContext.current)
                    notice.value?.let(formatter::format)?.let { TouchNotice(it.headline, it.detail, modifier) }
                }
            }
        }
    }

    private fun tapPicture() = tag("player-picture").performTouchInput { click(Offset(width * .25f, height * .28f)) }
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun tag(name: String) = compose.onNodeWithTag(name)
    private fun button(id: Int) = compose.activity.getString(id).let {
        compose.onNode(hasClickAction() and (hasText(it) or hasContentDescription(it)))
    }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch("slice3-player", name, canvas)
    private fun captureWindows(name: String, canvas: TouchCanvas) =
        compose.captureTouchWindows("slice3-player", name, canvas)
}
