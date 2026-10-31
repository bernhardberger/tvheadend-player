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

    @Test fun appRootBackFromGuideRestoresTheHomeGenreCard() {
        fixture.app()
        fixture.openDestination("nav-channels", Key.DirectionUp, "nav-home")
        fixture.focus("home-genre-0")
        fixture.key(Key.DirectionRight)
        compose.onNodeWithTag("home-genre-1").assertIsFocused()
        fixture.key(Key.Enter)
        compose.onNodeWithTag("guide-filter-button").assertExists()
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("home-genre-1")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { fixture.back.onBackPressed() }
                fixture.settle()
            }
        }
        // Back from Guide may return via the drawer; entering content restores its last card.
        if (compose.onAllNodes(hasTestTag("nav-home") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            fixture.key(Key.DirectionRight)
        }
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
        org.junit.Assert.assertEquals("return focus: ${compose.onNode(isFocused()).printToString()}",
            1, compose.onAllNodes(hasTestTag("home-onnow-1") and isFocused()).fetchSemanticsNodes().size)
        val after = compose.onNodeWithTag("home-onnow-1").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(before.left.value, after.left.value, 2f)
        org.junit.Assert.assertEquals(before.top.value, after.top.value, 2f)
    }
}
