package at.bernhardberger.tvhplayer.touch.guide

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val details = mutableListOf<String>()
    private val watched = mutableListOf<String>()

    @Test
    @Config(qualifiers = TABLET)
    fun tabletOverviewOpensAProgramInPlaceAndAChannelHeadOpensItsDay() {
        show(overviewFixture())
        capture("tablet-guide-overview", TouchCanvas.TABLET)

        // A tap opens the program where it is and plays nothing; only on-air programs offer Watch.
        compose.onAllNodesWithText("Coastal Miles")[0].performClick()
        compose.onNodeWithText(COASTAL_MILES_SYNOPSIS_START, substring = true).assertIsDisplayed()
        compose.onAllNodes(hasText("Watch")).assertCountEquals(0)
        assertEquals(emptyList<String>(), watched)
        capture("tablet-guide-overview-open", TouchCanvas.TABLET)

        // Back folds it again.
        back()
        compose.onAllNodes(hasText(COASTAL_MILES_SYNOPSIS_START, substring = true)).assertCountEquals(0)

        compose.onNodeWithContentDescription("Ridge Earth, by channel").performClick()
        compose.onNodeWithText("Sunday, November 15").assertIsDisplayed()
        compose.onNodeWithText("Six Bridges and a River That Changed Its Mind").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletChannelDay() {
        show(byChannelFixture(), GuideView.ByChannel, open = RIDGE_LIGHT_ID)
        compose.onNodeWithText("Monday, November 16").assertIsDisplayed()
        capture("tablet-guide-channel", TouchCanvas.TABLET)

        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("ridge-earth"), watched)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletChannelWithoutGuideDataOffersWatch() {
        show(byChannelFixture(), GuideView.ByChannel, channel = "glass-drama")
        compose.onNodeWithText("No schedule").assertIsDisplayed()
        capture("tablet-guide-channel-empty", TouchCanvas.TABLET)

        compose.onNodeWithText("Watch").performClick()
        assertEquals(listOf("glass-drama"), watched)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletDetailsOpenFromTheOpenedProgramAndBackReturns() {
        show(overviewFixture())
        compose.onAllNodesWithText("Glass Harbor")[0].performClick()
        compose.onNodeWithText("Details").performClick()
        assertEquals(listOf(GLASS_HARBOR_ID), details)
        compose.onNodeWithText("Other airings of Glass Harbor").assertIsDisplayed()
        capture("tablet-details", TouchCanvas.TABLET)

        back()
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onAllNodes(hasText("Other airings of Glass Harbor")).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightOverview() {
        show(overviewFixture(upright = true))
        capture("tablet-upright-guide-overview", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightChannelDay() {
        show(byChannelFixture(upright = true), GuideView.ByChannel, open = RIDGE_LIGHT_ID)
        capture("tablet-upright-guide-channel", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = UPRIGHT)
    fun uprightDetails() {
        show(overviewFixture(upright = true), detailsId = GLASS_HARBOR_ID)
        capture("tablet-upright-details", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneOverviewOpensInPlaceAndTabsSwitchTheView() {
        show(phoneOverviewFixture())
        capture("phone-guide-overview", TouchCanvas.PHONE)

        compose.onNodeWithText("Glass Harbor").performClick()
        compose.onNodeWithText("S2 E4 • The Long Tide").assertIsDisplayed()
        capture("phone-guide-overview-open", TouchCanvas.PHONE)

        compose.onNodeWithText("By channel").performClick()
        compose.onNodeWithText("Sunday, November 15").assertIsDisplayed()
        compose.onNodeWithText("Overview").performClick()
        compose.onNodeWithText("Ridge Earth • 8:00 PM").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneChannelDay() {
        show(byChannelFixture(phone = true), GuideView.ByChannel, open = RIDGE_LIGHT_ID)
        capture("phone-guide-channel", TouchCanvas.PHONE)

        // A tap on another program opens it instead.
        compose.onNodeWithText("Snowline").performClick()
        compose.onAllNodes(hasText("Winter on the ridge", substring = true)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phoneDetails() {
        show(overviewFixture(), detailsId = GLASS_HARBOR_ID)
        capture("phone-details", TouchCanvas.PHONE)

        back()
        compose.onNodeWithText("Overview").assertIsDisplayed()
    }

    private fun show(
        state: GuideState,
        view: GuideView = GuideView.Overview,
        channel: String? = null,
        open: String? = null,
        detailsId: String? = null,
    ) {
        val actions = GuideActions(
            onWatch = { watched += it },
            onRecord = {},
            onRecordOptions = {},
            onDetails = { details += it },
            onDay = {},
            onJump = {},
            onTag = {},
        )
        compose.setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                GuideScreen(
                    state, actions, initialView = view, initialChannelId = channel, initiallyOpen = open,
                    initialDetails = detailsId,
                )
            }
        }
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun capture(name: String, canvas: TouchCanvas) = compose.captureTouch(GROUP, name, canvas)

    private companion object {
        const val GROUP = "slice4"
        const val TABLET = "en-rUS-w1280dp-h800dp-land-xhdpi"
        const val UPRIGHT = "en-rUS-w800dp-h1280dp-port-xhdpi"
        const val PHONE = "en-rUS-w412dp-h892dp-port-xhdpi"
        const val COASTAL_MILES_SYNOPSIS_START = "Along the northern coast road"
    }
}
