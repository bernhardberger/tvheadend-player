package at.bernhardberger.tvhplayer.touch

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi
import at.bernhardberger.tvhplayer.touch.testutil.FakeConnection
import at.bernhardberger.tvhplayer.touch.testutil.TouchCanvas
import at.bernhardberger.tvhplayer.touch.testutil.captureTouch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(TvheadendTestingApi::class)
class TouchFrameTest {
    // MainActivity takes its session from Koin; a saved connection opens the frame directly.
    @get:Rule(order = 0) val graph = object : ExternalResource() {
        private val connection = FakeConnection()

        override fun before() {
            runBlocking { connection.store.addAnonymous("192.168.1.20", 9982, "Home server", true) }
            connection.start()
            startKoin {
                modules(
                    module {
                        single<TvheadendSession> { connection.session }
                        single { connection.owner }
                        viewModel { connection.presentation() }
                    },
                )
            }
        }

        override fun after() {
            stopKoin()
            connection.close()
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private val destination = hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    @Config(qualifiers = "en-rUS-w1280dp-h800dp-land-xhdpi")
    fun expandedFrame() {
        assertChannelsFrame(vertical = true)
        compose.captureTouch("slice0", "tablet-landscape", TouchCanvas.TABLET)
    }

    @Test
    @Config(qualifiers = "en-rUS-w800dp-h1280dp-port-xhdpi")
    fun mediumFrame() {
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "tablet-upright", TouchCanvas.TABLET_UPRIGHT)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h892dp-port-xhdpi")
    fun compactFrameNavigatesAndRestoresSelection() {
        assertChannelsFrame(vertical = false)
        compose.captureTouch("slice0", "phone", TouchCanvas.PHONE)

        compose.onNode(destination and hasText("Guide")).performClick()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNode(destination and hasText("Guide")).assertIsSelected()
        compose.onNode(heading and hasText("Guide")).assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNode(destination and hasText("Channels")).assertIsSelected()
        compose.onNode(heading and hasText("Channels")).assertIsDisplayed()
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
