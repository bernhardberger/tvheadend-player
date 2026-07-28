package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.simpleTvProfile
import at.bernhardberger.tvhplayer.ui.Routes
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalTestApi::class)
class SideRailSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsedRailIconsExposeDestinationNames() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    simpleTvProfile = simpleTvProfile(
                        SimpleTvSettings(),
                        active = false,
                    ),
                    onRootBack = {},
                    onNavigate = {},
                    content = { _, _ -> },
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Home").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Channels").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Guide").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Recordings").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Settings").assertCountEquals(1)
    }

    @Test
    fun drawerUsesAnOpaqueNearBlackSurface() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                TVHeadendPlayerTheme {
                    SideRail(
                        currentRoute = Routes.HOME,
                        showEpgMenu = true,
                        onRootBack = {},
                        onNavigate = {},
                        content = { _, _ -> },
                    )
                }
            }
        }

        val surfacePixels = composeRule.onNodeWithTag("global-drawer-surface")
            .captureToImage()
            .toPixelMap()
        val surfacePixel = surfacePixels[
            surfacePixels.width / 2,
            surfacePixels.height / 2,
        ]

        assertTrue(surfacePixel.red < 0.1f)
        assertTrue(surfacePixel.green < 0.1f)
        assertTrue(surfacePixel.blue < 0.1f)
    }

    @Test
    fun closedRailAddsSafeInsetInsideBrowseViewport() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.HOME,
                    showEpgMenu = true,
                    onRootBack = {},
                    onNavigate = {},
                    content = { contentPadding, _ ->
                        Box {
                            Text("Viewport", Modifier.testTag("viewport-start"))
                            Text(
                                "Browse content",
                                Modifier.padding(contentPadding).testTag("content"),
                            )
                        }
                    },
                )
            }
        }

        val viewportStart = composeRule.onNodeWithTag("viewport-start")
            .fetchSemanticsNode().boundsInRoot.left
        val contentStart = composeRule.onNodeWithTag("content")
            .fetchSemanticsNode().boundsInRoot.left
        val expectedInset = with(composeRule.density) { 24.dp.toPx() }
        assertEquals(expectedInset, contentStart - viewportStart, 1f)
    }

    @Test
    fun shellProvidesBrowseSafeAreaToContent() {
        var startPadding = 0.dp
        var topPadding = 0.dp
        var endPadding = 0.dp
        var bottomPadding = 0.dp
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.HOME,
                    showEpgMenu = true,
                    onRootBack = {},
                    onNavigate = {},
                    content = { contentPadding, _ ->
                        startPadding = contentPadding.calculateStartPadding(LayoutDirection.Ltr)
                        topPadding = contentPadding.calculateTopPadding()
                        endPadding = contentPadding.calculateEndPadding(LayoutDirection.Ltr)
                        bottomPadding = contentPadding.calculateBottomPadding()
                    },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(24.dp, startPadding)
            assertEquals(32.dp, topPadding)
            assertEquals(48.dp, endPadding)
            assertEquals(32.dp, bottomPadding)
        }
    }

    @Test
    fun openDrawerPreservesBrowseWidthAndFocusedDestinationNavigates() {
        val route = mutableStateOf(Routes.HOME)
        val contentFocus = FocusRequester()
        var rootBackCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = route.value,
                    showEpgMenu = true,
                    onRootBack = { rootBackCount += 1 },
                    onNavigate = { route.value = it },
                    content = { _, drawerActive ->
                        Box(Modifier.fillMaxSize().testTag("browse-viewport")) {
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    // Geometrically aligns with Channels, so drawer entry must
                                    // still restore Home before focus-driven navigation starts.
                                    .padding(top = 60.dp)
                                    .focusRequester(contentFocus)
                                    .testTag("browse-focus"),
                            ) {
                                Text("Browse")
                            }
                        }
                        LaunchedEffect(route.value, drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    },
                )
            }
        }

        val closedBounds = composeRule.onNodeWithTag("browse-viewport")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("browse-focus").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("nav-home").assertIsFocused()
        composeRule.waitForIdle()
        val openBounds = composeRule.onNodeWithTag("browse-viewport")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(openBounds.left > closedBounds.left)
        assertEquals(closedBounds.width, openBounds.width, 1f)

        composeRule.onNodeWithTag("nav-home")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.runOnIdle { assertEquals(Routes.CHANNELS, route.value) }
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()

        composeRule.onNodeWithTag("nav-channels")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("browse-focus").assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()

        composeRule.onNodeWithTag("nav-channels")
            .performKeyInput { pressKey(Key.Back) }
        composeRule.runOnIdle { assertEquals(Routes.HOME, route.value) }
        composeRule.onNodeWithTag("nav-home").assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }
        composeRule.runOnIdle { assertEquals(1, rootBackCount) }
    }

    @Test
    fun focusedContentCanConsumeBackBeforeTheBrowseShell() {
        val contentFocus = FocusRequester()
        var contentBackCount = 0
        var rootBackCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    onRootBack = { rootBackCount += 1 },
                    onNavigate = {},
                    content = { _, _ ->
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
                                .onKeyEvent { event ->
                                    if (
                                        event.key == Key.Back &&
                                        event.type == KeyEventType.KeyUp
                                    ) {
                                        contentBackCount += 1
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .testTag("nested-back-owner"),
                        ) {
                            Text("Nested content")
                        }
                        LaunchedEffect(Unit) { contentFocus.requestFocus() }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("nested-back-owner")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Back) }

        composeRule.runOnIdle {
            assertEquals(1, contentBackCount)
            assertEquals(0, rootBackCount)
        }
        composeRule.onNodeWithTag("nested-back-owner").assertIsFocused()
    }
}
