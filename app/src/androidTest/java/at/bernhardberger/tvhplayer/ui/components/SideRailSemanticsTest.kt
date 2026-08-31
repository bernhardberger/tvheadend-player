package at.bernhardberger.tvhplayer.ui.components

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalTestApi::class)
class SideRailSemanticsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun collapsedRailExposesAllDestinationItems() {
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

        composeRule.onNodeWithTag("nav-channels").assertExists()
        composeRule.onNodeWithTag("nav-epg").assertExists()
        composeRule.onNodeWithTag("nav-recordings").assertExists()
        composeRule.onNodeWithTag("nav-settings").assertExists()
        composeRule.onAllNodesWithContentDescription("Channels").assertCountEquals(1)
    }

    @Test
    fun collapsedRailAndExpandedDrawerUseContinuousScrims() {
        val contentFocus = FocusRequester()
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Red)) {
                TVHeadendPlayerTheme {
                    SideRail(
                        currentRoute = Routes.CHANNELS,
                        showEpgMenu = true,
                        onRootBack = {},
                        onNavigate = {},
                        content = { _, drawerActive ->
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    .focusRequester(contentFocus)
                                    .testTag("rail-surface-test-content"),
                            ) {
                                Text("Browse")
                            }
                            LaunchedEffect(drawerActive) {
                                if (!drawerActive) contentFocus.requestFocus()
                            }
                        },
                    )
                }
            }
        }

        val shellPixels = composeRule.onNodeWithTag("global-navigation-shell")
            .captureToImage()
            .toPixelMap()
        val sampleY = shellPixels.height / 2
        fun redAt(x: Int): Float {
            val sampleX = with(composeRule.density) { x.dp.roundToPx() }
            return shellPixels[sampleX, sampleY].red
        }

        assertEquals(0.22f, redAt(1), 0.06f)
        assertEquals(0.28f, redAt(31), 0.06f)
        assertEquals(0.45f, redAt(68), 0.06f)
        assertEquals(0.75f, redAt(97), 0.06f)
        assertEquals(1f, redAt(124), 0.06f)

        composeRule.onNodeWithTag("rail-surface-test-content")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.waitForIdle()

        val expandedPixels = composeRule.onNodeWithTag("global-drawer-surface")
            .captureToImage()
            .toPixelMap()
        val expandedSampleY = expandedPixels.height / 2
        fun expandedRedAt(fraction: Float): Float {
            val sampleX = (expandedPixels.width * fraction).toInt()
                .coerceIn(0, expandedPixels.width - 1)
            return expandedPixels[sampleX, expandedSampleY].red
        }

        assertEquals(0.08f, expandedRedAt(0.01f), 0.06f)
        assertEquals(0.12f, expandedRedAt(0.35f), 0.06f)
        assertEquals(0.28f, expandedRedAt(0.70f), 0.06f)
        assertEquals(0.65f, expandedRedAt(0.90f), 0.06f)
        assertEquals(0.96f, expandedRedAt(0.99f), 0.06f)
    }

    @Test
    fun edgeFadeMaskMakesOpaqueContentTransparentWithoutPaintingTheScrim() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(
                    Modifier
                        .width(160.dp)
                        .height(80.dp)
                        .navigationEdgeFadeMask(width = 32.dp)
                        .background(Color.Red)
                        .testTag("edge-fade-mask"),
                )
            }
        }

        val pixels = composeRule.onNodeWithTag("edge-fade-mask").captureToImage().toPixelMap()
        val sampleY = pixels.height / 2
        fun redAt(offset: Int): Float {
            val x = with(composeRule.density) { offset.dp.roundToPx() }
            return pixels[x, sampleY].red
        }

        assertEquals(0.03f, redAt(1), 0.08f)
        assertEquals(0.50f, redAt(16), 0.08f)
        assertEquals(1f, redAt(31), 0.06f)
    }

    @Test
    fun edgeFadeMaskUsesTheLogicalLeadingEdgeInRtl() {
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(
                        Modifier
                            .width(160.dp)
                            .height(80.dp)
                            .navigationEdgeFadeMask(width = 32.dp)
                            .background(Color.Red)
                            .testTag("rtl-edge-fade-mask"),
                    )
                }
            }
        }

        val pixels = composeRule.onNodeWithTag("rtl-edge-fade-mask").captureToImage().toPixelMap()
        val sampleY = pixels.height / 2
        fun redAtLeadingOffset(offset: Int): Float {
            val offsetPx = with(composeRule.density) { offset.dp.roundToPx() }
            return pixels[pixels.width - 1 - offsetPx, sampleY].red
        }

        assertEquals(0.03f, redAtLeadingOffset(1), 0.08f)
        assertEquals(0.50f, redAtLeadingOffset(16), 0.08f)
        assertEquals(1f, redAtLeadingOffset(31), 0.06f)
    }

    @Test
    fun closedRailAddsSafeInsetInsideBrowseViewport() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = Routes.CHANNELS,
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
                    currentRoute = Routes.CHANNELS,
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
        val route = mutableStateOf(Routes.CHANNELS)
        val contentFocus = FocusRequester()
        var rootBackCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = route.value,
                    rootRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    onRootBack = { rootBackCount += 1 },
                    onNavigate = { route.value = it },
                    content = { _, drawerActive ->
                        Box(Modifier.fillMaxSize().testTag("browse-viewport")) {
                            Button(
                                onClick = {},
                                modifier = Modifier
                                    // Drawer entry must restore the current root destination
                                    // before focus-driven navigation starts.
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
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.waitForIdle()
        val openBounds = composeRule.onNodeWithTag("browse-viewport")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(openBounds.left > closedBounds.left)
        assertEquals(closedBounds.width, openBounds.width, 1f)

        composeRule.onNodeWithTag("nav-channels")
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.runOnIdle { assertEquals(Routes.EPG, route.value) }
        composeRule.onNodeWithTag("nav-epg").assertIsFocused()

        composeRule.onNodeWithTag("nav-epg")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("browse-focus").assertIsFocused()
        dispatchBack()
        composeRule.onNodeWithTag("nav-epg").assertIsFocused()

        dispatchBack()
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        dispatchBack()
        composeRule.runOnIdle { assertEquals(1, rootBackCount) }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            EVIDENCE_STATUS_CODE,
            Bundle().apply { putString("rootBackOwnerTrace", "shell>root") },
        )
    }

    @Test
    fun delayedRouteFeedbackDoesNotPullFocusBackDuringRapidNavigation() {
        val reportedRoute = mutableStateOf(Routes.CHANNELS)
        val requestedRoutes = mutableListOf<String>()
        val contentFocus = FocusRequester()
        var rootBackCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = reportedRoute.value,
                    rootRoute = Routes.CHANNELS,
                    showEpgMenu = true,
                    onRootBack = { rootBackCount += 1 },
                    onNavigate = { requestedRoutes += it },
                    content = { _, drawerActive ->
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
                                .testTag("rapid-browse-focus"),
                        ) {
                            Text("Browse")
                        }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("rapid-browse-focus")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        val guideFocusStartedAt = SystemClock.elapsedRealtimeNanos()
        composeRule.onNodeWithTag("nav-channels")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("nav-epg").assertIsFocused()
        val guideFocusLatencyMillis = (
            SystemClock.elapsedRealtimeNanos() - guideFocusStartedAt + 999_999L
            ) / 1_000_000L
        assertTrue(
            "Guide rail focus took ${guideFocusLatencyMillis}ms",
            guideFocusLatencyMillis <= GUIDE_RAIL_FOCUS_BUDGET_MILLIS,
        )
        composeRule.onNodeWithTag("nav-epg")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.runOnIdle {
            assertEquals(listOf(Routes.EPG), requestedRoutes)
        }
        composeRule.onNodeWithTag("nav-epg")
            .performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag("nav-recordings").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(Routes.EPG, Routes.RECORDINGS), requestedRoutes)
            // Navigation can report an intermediate route after focus has already
            // advanced again during an in-flight destination crossfade.
            reportedRoute.value = Routes.EPG
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav-recordings").assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionUp)
            }
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(
                listOf(Routes.EPG, Routes.RECORDINGS, Routes.EPG, Routes.CHANNELS),
                requestedRoutes,
            )
        }

        composeRule.runOnIdle {
            // A late intermediate completion cannot replace the latest focus intent.
            reportedRoute.value = Routes.RECORDINGS
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()

        composeRule.onNodeWithTag("nav-channels")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("rapid-browse-focus")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(
                listOf(Routes.EPG, Routes.RECORDINGS, Routes.EPG, Routes.CHANNELS),
                requestedRoutes,
            )
        }

        val requestsBeforeSilentBack = requestedRoutes.toList()
        dispatchBack()
        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(requestsBeforeSilentBack, requestedRoutes)
            assertEquals(0, rootBackCount)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            EVIDENCE_STATUS_CODE,
            Bundle().apply {
                putString("guideRailFocusLatencyMs", guideFocusLatencyMillis.toString())
                putString("sideRailRequestTrace", requestedRoutes.joinToString(">"))
                putString("awaitRootDestinationRootBackCount", rootBackCount.toString())
            },
        )
    }

    @Test
    fun removingAPendingClosedDrawerItemDiscardsItBeforeReentry() {
        val reportedRoute = mutableStateOf(Routes.CHANNELS)
        val showEpgMenu = mutableStateOf(true)
        val requestedRoutes = mutableListOf<String>()
        val contentFocus = FocusRequester()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = reportedRoute.value,
                    rootRoute = Routes.CHANNELS,
                    showEpgMenu = showEpgMenu.value,
                    onRootBack = {},
                    onNavigate = { requestedRoutes += it },
                    content = { _, drawerActive ->
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
                                .testTag("closed-item-change-browse-focus"),
                        ) {
                            Text("Browse")
                        }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("closed-item-change-browse-focus")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("nav-channels")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("nav-epg")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("closed-item-change-browse-focus").assertIsFocused()

        composeRule.runOnIdle { showEpgMenu.value = false }
        composeRule.runOnIdle { showEpgMenu.value = true }
        composeRule.onNodeWithTag("closed-item-change-browse-focus")
            .performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.runOnIdle { assertEquals(listOf(Routes.EPG), requestedRoutes) }
    }

    @Test
    fun removingTheFocusedOpenDrawerItemUsesADeterministicFallback() {
        val reportedRoute = mutableStateOf(Routes.EPG)
        val showEpgMenu = mutableStateOf(true)
        val requestedRoutes = mutableListOf<String>()
        val contentFocus = FocusRequester()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = reportedRoute.value,
                    rootRoute = Routes.CHANNELS,
                    showEpgMenu = showEpgMenu.value,
                    onRootBack = {},
                    onNavigate = {
                        requestedRoutes += it
                        reportedRoute.value = it
                    },
                    content = { _, drawerActive ->
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
                                .testTag("item-change-browse-focus"),
                        ) {
                            Text("Browse")
                        }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("item-change-browse-focus")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("nav-epg").assertIsFocused()

        composeRule.runOnIdle { showEpgMenu.value = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav-channels").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(Routes.CHANNELS), requestedRoutes)
        }
    }

    @Test
    fun removingAnotherItemPreservesAStillValidRapidNavigationIntent() {
        val reportedRoute = mutableStateOf(Routes.EPG)
        val showEpgMenu = mutableStateOf(true)
        val requestedRoutes = mutableListOf<String>()
        val contentFocus = FocusRequester()
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SideRail(
                    currentRoute = reportedRoute.value,
                    rootRoute = Routes.CHANNELS,
                    showEpgMenu = showEpgMenu.value,
                    onRootBack = {},
                    onNavigate = { requestedRoutes += it },
                    content = { _, drawerActive ->
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
                                .testTag("preserved-intent-browse-focus"),
                        ) {
                            Text("Browse")
                        }
                        LaunchedEffect(drawerActive) {
                            if (!drawerActive) contentFocus.requestFocus()
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("preserved-intent-browse-focus")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("nav-epg")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag("nav-recordings").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(Routes.RECORDINGS), requestedRoutes)
            // EPG feedback is still reported when that unrelated item disappears.
            showEpgMenu.value = false
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav-recordings").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(listOf(Routes.RECORDINGS), requestedRoutes)
        }
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
                        BackHandler { contentBackCount += 1 }
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(contentFocus)
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
        dispatchBack()

        composeRule.runOnIdle {
            assertEquals(1, contentBackCount)
            assertEquals(0, rootBackCount)
        }
        composeRule.onNodeWithTag("nested-back-owner").assertIsFocused()
    }

    private fun dispatchBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}

private const val EVIDENCE_STATUS_CODE = 2
private const val GUIDE_RAIL_FOCUS_BUDGET_MILLIS = 1_000L
