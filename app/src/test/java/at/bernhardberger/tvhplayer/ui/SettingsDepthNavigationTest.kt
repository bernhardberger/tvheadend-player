package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.ui.components.depth.*
import at.bernhardberger.tvhplayer.ui.components.SideRail
import at.bernhardberger.tvhplayer.ui.player.DebugVideoBackdrop
import at.bernhardberger.tvhplayer.core.ChannelScopeVisibility
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerUiState
import at.bernhardberger.tvheadend.sdk.core.ChannelTag
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.StreamProfilesResult
import at.bernhardberger.tvhplayer.ui.screens.*
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.CacheClearState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Host-only native-graphics production Compose tests; no device, server or app graph. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class SettingsDepthNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var captureView: View

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    /**
     * OK reaches the focused library row itself: the depth host must not consume
     * DPAD_CENTER in preview, so the row's press state and onClick run. Leaf
     * activation comes from that onClick, exactly once per press.
     */
    @Test fun okIsDeliveredToTheFocusedRowNotConsumedByTheHost() {
        var leafCalls = 0
        var hostSawCenterUp = false
        compose.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.onKeyEvent { event ->
                    if (event.key == Key.DirectionCenter && event.type == KeyEventType.KeyUp) hostSawCenterUp = true
                    false
                }) {
                    SettingsScreenNavigation(rememberDepthNavigationState("root"), listOf(
                        settingsLevel("root", "Settings", listOf(settingsRow("leaf", "Leaf", onClick = { leafCalls++ }))),
                    ))
                }
            }
        }
        compose.onNodeWithText("Leaf").assertIsFocused()
        compose.onNodeWithText("Leaf").performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, leafCalls)
        // Row consumed the click on release; the outer host never had to.
        assertFalse(hostSawCenterUp)
    }

    /**
     * Enter/Back before the fade or before the slide settles must keep focus and
     * activation on the latest visit. Outgoing copies cannot commit.
     */
    @Test fun interruptedPushAndPopFocusLatestAndDropStaleActivation() {
        var activations = 0
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(navigation, listOf(
                    settingsLevel("root", "Root", listOf(
                        settingsRow("child", "Open child", child = "child"),
                        settingsRow("leaf", "Leaf", onClick = { activations++ }),
                    )),
                    settingsLevel("child", "Child", listOf(
                        settingsRow("inner", "Inner", onClick = { activations++ }),
                    )),
                ))
            }
        }
        compose.onNodeWithText("Open child").assertIsFocused()
        for (delay in listOf(100L, 400L)) {
            compose.mainClock.autoAdvance = false
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.advanceTimeBy(delay)
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            compose.mainClock.advanceTimeBy(delay)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("stale activation after interrupt $delay", 0, activations)
            assertEquals(2, navigation.stack.frames.size)
            compose.onNodeWithText("Inner").assertIsFocused()
            compose.mainClock.autoAdvance = false
            compose.onRoot().performKeyInput { pressKey(Key.Back) }
            compose.mainClock.advanceTimeBy(delay)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals("stale inner activation after pop-push $delay", 0, activations)
            assertEquals(2, navigation.stack.frames.size)
            compose.onNodeWithText("Inner").assertIsFocused()
            press(Key.Back)
            compose.onNodeWithText("Open child").assertIsFocused()
        }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(1, activations)
    }

    /**
     * Path reuse updates the live row callback on recomposition. The activate
     * lambda from the first visit must stay inert after leave + re-enter of the
     * same path — invoking that retained original, not a later outgoing copy.
     */
    @Test fun retainedActivateFromEarlierVisitDoesNotCommitAfterReentry() {
        var activations = 0
        var retainedActivate: (() -> Unit)? = null
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(navigation, listOf(
                    settingsLevel("root", "Root", listOf(
                        settingsRow("child", "Open child", child = "child"),
                        DepthRow(DepthItem("leaf"), { activations++ }) { modifier, activate ->
                            SideEffect { if (retainedActivate == null) retainedActivate = activate }
                            Button(onClick = activate, modifier = modifier) { Text("Leaf") }
                        },
                    )),
                    settingsLevel("child", "Child", listOf(
                        settingsRow("inner", "Inner"),
                    )),
                ))
            }
        }
        compose.waitForIdle()
        val original = retainedActivate
        assertNotNull("original active callback was never captured", original)
        val firstVisit = navigation.stack.visit
        press(Key.DirectionCenter)
        compose.onNodeWithText("Inner").assertIsFocused()
        assertTrue(navigation.stack.visit > firstVisit)
        press(Key.Back)
        compose.onNodeWithText("Open child").assertIsFocused()
        val returnedVisit = navigation.stack.visit
        assertTrue("re-entry must be a new visit", returnedVisit > firstVisit)
        assertEquals(1, navigation.stack.frames.size)
        original!!.invoke()
        compose.waitForIdle()
        assertEquals("retained first-visit activate committed after re-entry", 0, activations)
        assertEquals(returnedVisit, navigation.stack.visit)
        assertEquals(1, navigation.stack.frames.size)
        compose.onNodeWithText("Open child").assertIsFocused()
    }

    /**
     * isCurrent can drop without a new visit. The first-visit activate lambda must
     * read latest ownership, not the captured active flag.
     */
    @Test fun retainedActivateDoesNotCommitAfterOwnerLosesCurrentWithoutNewVisit() {
        var activations = 0
        var retainedActivate: (() -> Unit)? = null
        var current by mutableStateOf(true)
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(
                    navigation,
                    listOf(
                        settingsLevel("root", "Root", listOf(
                            settingsRow("child", "Open child", child = "child"),
                            DepthRow(DepthItem("leaf"), { activations++ }) { modifier, activate ->
                                SideEffect { if (retainedActivate == null) retainedActivate = activate }
                                Button(onClick = activate, modifier = modifier) { Text("Leaf") }
                            },
                        )),
                        settingsLevel("child", "Child", listOf(settingsRow("inner", "Inner"))),
                    ),
                    isCurrent = current,
                )
            }
        }
        compose.waitForIdle()
        val original = retainedActivate
        assertNotNull(original)
        compose.onNodeWithText("Open child").assertIsFocused()
        val visit = navigation.stack.visit
        val frames = navigation.stack.frames.size
        compose.runOnIdle { current = false }
        compose.waitForIdle()
        assertEquals(visit, navigation.stack.visit)
        original!!.invoke()
        compose.waitForIdle()
        assertEquals(0, activations)
        assertEquals(visit, navigation.stack.visit)
        assertEquals(frames, navigation.stack.frames.size)
        assertEquals("child", navigation.stack.active.focusedItemId)
    }

    /**
     * Off-screen restore must not request the captured item after a newer focus
     * lands before the suspended restore finishes.
     */
    @Test fun offscreenRestoreDoesNotStealNewerFocus() {
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(navigation, listOf(
                    settingsLevel("root", "Root", (0..18).map { index ->
                        settingsRow("row-$index", "Item $index", child = "child")
                    }),
                    settingsLevel("child", "Child", listOf(settingsRow("inner", "Inner"))),
                ))
            }
        }
        repeat(12) { press(Key.DirectionDown) }
        compose.onNodeWithText("Item 12").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Inner").assertIsFocused()
        val items = (0..18).map { DepthItem("row-$it", "child") }
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { navigation.update(navigation.stack.focus("row-11", items)) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("row-11", navigation.stack.active.focusedItemId)
        assertEquals(1, navigation.stack.frames.size)
    }

    @Test fun dedicatedLeafWithoutPreviewRowsStillReceivesInitialFocus() {
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState("editor"), listOf(
                    settingsLevel("editor", "Editor", emptyList(), activeContent = { requester, _ ->
                        Button(onClick = {}, modifier = Modifier.focusRequester(requester)) { Text("Editor action") }
                    }),
                ))
            }
        }
        compose.onNodeWithText("Editor action").assertIsFocused()
    }

    @Test fun rootGeneralLanguageChoiceAndSwitchAreExplicitAndRestoreParent() {
        var language by mutableStateOf(AppLanguage.SYSTEM)
        var showGuide by mutableStateOf(true)
        var languageCalls = 0
        var clearCalls = 0
        lateinit var navigation: DepthNavigationState
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TVHeadendPlayerTheme {
                captureView = LocalView.current
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.GENERAL.name)
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DebugVideoBackdrop(true, Modifier.fillMaxSize())
                    WarmPlaybackScrim() // This fixture explicitly models warm playback.
                    SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                        onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                    SettingsScreenNavigation(navigation,
                        listOf(settingsRootLevel()) + settingsGeneralLevels(
                            UiSettings(showEpgMenu = showGuide), language, CacheStatistics.EMPTY, CacheClearState.IDLE,
                            { languageCalls++; navigation.pop(); language = it }, { showGuide = !showGuide }, { clearCalls++ }),
                        initialFocusEnabled = !drawerActive, contentPadding = padding)
                    }
                }
            }
        }
        compose.onNodeWithText("General").assertIsFocused()
        capture("root-general")
        press(Key.DirectionCenter)
        compose.onNodeWithText("App language").assertIsFocused()
        assertEquals(0, languageCalls)
        capture("general-language")
        press(Key.DirectionRight)
        compose.onNodeWithText("Follow system").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("German").assertIsFocused()
        assertEquals(0, languageCalls)
        capture("language-choices")
        press(Key.DirectionCenter)
        compose.onNodeWithText("App language").assertIsFocused()
        compose.onNodeWithText("German").assertExists()
        assertEquals(1, languageCalls)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("App language").assertIsFocused()
        assertEquals(SettingsSection.GENERAL.name, navigation.stack.active.levelId)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        assertTrue(showGuide)
        press(Key.DirectionCenter)
        assertFalse(showGuide)
        press(Key.DirectionDown)
        compose.onNodeWithText("Clear cache").assertIsFocused()
        compose.onNodeWithText("0.0 MB").assertExists()
        press(Key.DirectionCenter)
        assertEquals(1, clearCalls)
        press(Key.DirectionLeft)
        compose.onNodeWithText("General").assertIsFocused()
        val width = compose.onNodeWithText("General").fetchSemanticsNode().boundsInRoot.width
        press(Key.DirectionLeft)
        compose.onNodeWithTag("nav-settings").assertIsFocused()
        assertEquals(width, compose.onNodeWithText("General").fetchSemanticsNode().boundsInRoot.width)
        capture("global-drawer")
        press(Key.DirectionRight)
        compose.onNodeWithText("General").assertIsFocused()
        assertEquals(1, navigation.stack.frames.size)
    }

    @Test fun fourNestedLevelsConsumeHeldEntryAndPopRestoreScrolledParent() {
        lateinit var navigation: DepthNavigationState
        var leafCalls = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("level-0")
                val levels = (0..3).map { depth -> settingsLevel("level-$depth", "Depth $depth",
                    (0..18).map { index -> settingsRow("row-$index", "Level $depth item $index",
                        child = if (depth < 3) "level-${depth + 1}" else null,
                        onClick = { leafCalls++ }) }) }
                SettingsScreenNavigation(navigation, levels)
            }
        }
        repeat(12) { press(Key.DirectionDown) }
        val parentBounds = compose.onNodeWithText("Level 0 item 12").fetchSemanticsNode().boundsInRoot
        // The focused row's own onClick commits on key release, so the library
        // press state can run; the press itself must not enter.
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(1, navigation.stack.frames.size)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.waitForIdle()
        compose.onNodeWithText("Level 1 item 0").assertIsFocused()
        assertEquals(2, navigation.stack.frames.size)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(4, navigation.stack.frames.size)
        assertEquals(0, leafCalls)
        press(Key.DirectionRight)
        assertEquals(0, leafCalls)
        press(Key.DirectionCenter)
        assertEquals(1, leafCalls)
        repeat(3) { press(Key.DirectionLeft) }
        compose.onNodeWithText("Level 0 item 12").assertIsFocused()
        assertEquals(parentBounds, compose.onNodeWithText("Level 0 item 12").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun dedicatedLeafIsNeverMountedAsPreviewOrOutgoingAndBackRestoresInvoker() {
        var mounted = 0
        var activations = 0
        var shellBack = 0
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                BackHandler { shellBack++ }
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(navigation, listOf(
                    settingsLevel("root", "Settings", listOf(settingsRow("connection", "Connection", child = "editor"))),
                    settingsLevel("editor", "Connection", listOf(settingsRow("safe", "Open connection settings")), activeContent = { requester, _ ->
                        DisposableEffect(Unit) { mounted++; onDispose { mounted-- } }
                        Button(onClick = { activations++ }, modifier = Modifier.focusRequester(requester)) { Text("Editor fixture") }
                    }),
                ))
            }
        }
        assertEquals(0, mounted)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Editor fixture").assertIsFocused()
        assertEquals(1, mounted)
        assertEquals(0, activations)
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
        assertEquals(0, mounted)
        assertEquals(0, shellBack)
    }

    @Test fun missingAndEmptyRowsRecoverWithoutStaleFocusOrPreview() {
        var rows by mutableStateOf(listOf("one", "two", "three"))
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                navigation = rememberDepthNavigationState("root")
                SettingsScreenNavigation(navigation, listOf(settingsLevel("root", "Root",
                    rows.map { settingsRow(it, it, child = "child-$it") })))
            }
        }
        press(Key.DirectionDown)
        compose.onNodeWithText("two").assertIsFocused()
        compose.runOnIdle { rows = listOf("one", "three") }
        compose.onNodeWithText("three").assertIsFocused()
        assertEquals("child-three", navigation.stack.previewKey(rows.map { DepthItem(it, "child-$it") })?.childLevelId)
        compose.runOnIdle { rows = emptyList() }
        compose.onNode(hasText("Settings unavailable") and hasClickAction()).assertIsFocused()
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanEnlargedStorageGuardsAndStateCaptures() {
        var clearState by mutableStateOf(CacheClearState.IDLE)
        var calls = 0
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.3f),
            ) {
                TVHeadendPlayerTheme {
                    captureView = LocalView.current
                    val navigation = remember { DepthNavigationState(DepthStack(listOf(
                        DepthFrame(SETTINGS_ROOT, SettingsSection.GENERAL.name),
                        DepthFrame(SettingsSection.GENERAL.name, "clear-cache"),
                    ))) }
                    Box(Modifier.fillMaxSize()) {
                        DebugVideoBackdrop(true, Modifier.fillMaxSize())
                        WarmPlaybackScrim() // This fixture explicitly models warm playback.
                        SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                            onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                            SettingsScreenNavigation(navigation, listOf(settingsRootLevel()) + settingsGeneralLevels(
                                UiSettings(), AppLanguage.GERMAN, CacheStatistics.EMPTY, clearState, {}, {},
                                { calls++; clearState = CacheClearState.CLEARING }),
                                initialFocusEnabled = !drawerActive, contentPadding = padding)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Cache leeren").assertIsFocused()
        capture("de-font1.3-storage-idle")
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        assertEquals(1, calls)
        capture("de-font1.3-storage-clearing")
        compose.runOnIdle { clearState = CacheClearState.FAILED }
        compose.onNodeWithText("Cache leeren").assertIsFocused()
        capture("de-font1.3-storage-failed")
        press(Key.DirectionCenter)
        assertEquals(2, calls)
        compose.runOnIdle { clearState = CacheClearState.CLEARED }
        capture("de-font1.3-storage-cleared")
    }

    @Test fun deferredEntryDynamicChannelRecoveryAndOtherSettingsStayActionOnly() {
        var ready by mutableStateOf(false)
        var loaded by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var retries = 0
        var toggles = 0
        var settingsActions = 0
        lateinit var navigation: DepthNavigationState
        compose.setContent {
            TVHeadendPlayerTheme {
                captureView = LocalView.current
                navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CHANNEL_TAGS.name)
                Box(Modifier.fillMaxSize()) {
                    DebugVideoBackdrop(true, Modifier.fillMaxSize())
                    WarmPlaybackScrim() // This fixture explicitly models warm playback.
                    SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                        onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                        SettingsScreenNavigation(navigation, listOf(settingsRootLevel(),
                            settingsChannelTagsLevel(emptyList(), ChannelScopeVisibility(), loaded, failed,
                                { retries++ }, { toggles++ }),
                            settingsPlayerLevel(SettingsPlayerUiState(profiles = StreamProfilesResult.AccessDenied),
                                { settingsActions++ }, { settingsActions++ }, { settingsActions++ }),
                            settingsApplianceLevel(false, false, { settingsActions++ }, { settingsActions++ }),
                        ), initialFocusEnabled = ready && !drawerActive, contentPadding = padding)
                    }
                }
            }
        }
        compose.onNodeWithText("Channel groups").assertIsNotFocused()
        compose.runOnIdle { ready = true }
        compose.onNodeWithText("Channel groups").assertIsFocused()
        press(Key.DirectionCenter)
        capture("channel-groups-loading")
        compose.runOnIdle { failed = true }
        compose.onNodeWithText("Retry").assertIsFocused()
        capture("channel-groups-error")
        press(Key.DirectionCenter)
        assertEquals(1, retries)
        compose.runOnIdle { loaded = true; failed = false }
        compose.onNodeWithText("All channels").assertIsFocused()
        capture("channel-groups-last-visible")
        press(Key.DirectionCenter)
        assertEquals(0, toggles)
        press(Key.Back)
        press(Key.DirectionDown) // Connection; never activates an editor from focus.
        press(Key.DirectionDown) // Player.
        press(Key.DirectionCenter)
        capture("player-unavailable")
        press(Key.DirectionDown)
        assertEquals(0, settingsActions)
        press(Key.DirectionCenter)
        assertEquals(1, settingsActions)
        press(Key.Back)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        capture("appliance-autoplay")
        press(Key.DirectionDown)
        capture("appliance-accessibility")
        assertEquals(1, settingsActions)
        press(Key.DirectionCenter)
        assertEquals(2, settingsActions)
    }

    private fun capture(name: String) {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(captureView.width, captureView.height, Bitmap.Config.ARGB_8888)
            captureView.draw(Canvas(bitmap))
        }
        val directory = File("build/outputs/settings-c-captures").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
