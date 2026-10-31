@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.settings.ServerSettings
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeNavigationJourneyTest {
    @get:Rule val compose = createComposeRule()
    private val fixture = NavigationJourneyFixture(compose, ServerSettings(host = javaClass.simpleName))
    @After fun after() = fixture.close()

    @Test fun emptyHomeBrowseChannelsUsesTheChannelsDestination() {
        fixture.app(emptyEpg = true)
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.DirectionUp)
        compose.onNodeWithTag("home-view-button").assertIsFocused()
        fixture.key(Key.DirectionDown)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.DirectionLeft)
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithText("Browse channels").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("channel-row-1").assertIsFocused()
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
    }

    @Test fun appRootTwoBackPressesFromGuideReachHomeRailThenRightRestoresTheGenreCard() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-genre-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("guide-filter-button").assertExists()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-epg").assertIsFocused()
        compose.onNodeWithTag("home-genre-1").assertDoesNotExist()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
    }

    @Test fun appRootPlayerBackRestoresTheHomeCardAndViewport() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-onnow-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        val before = compose.onNodeWithTag("home-onnow-1").getUnclippedBoundsInRoot()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("home-view-button").assertDoesNotExist()
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("home-view-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { fixture.back.onBackPressed() }
                fixture.settle()
            }
        }
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        val after = compose.onNodeWithTag("home-onnow-1").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(before.left.value, after.left.value, 2f)
        org.junit.Assert.assertEquals(before.top.value, after.top.value, 2f)
    }

    @Test fun realRailRightRestoresTheCardLeftBehindInsteadOfUpcoming() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-0").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
        compose.runOnIdle { fixture.back.onBackPressed() }
        fixture.settle()
        compose.onNodeWithTag("nav-home").assertIsFocused()
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-onnow-1").assertIsFocused()
    }
}
