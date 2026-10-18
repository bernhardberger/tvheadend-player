package at.bernhardberger.tvhplayer.ui

import android.app.Application
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CacheStatistics
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvhplayer.settings.AppLanguage
import at.bernhardberger.tvhplayer.settings.ClockFormat
import at.bernhardberger.tvhplayer.settings.UiSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
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

    @Test fun recordingsLevelNeedsAProfileChoiceOrRecordingSpace() {
        val profile = at.bernhardberger.tvheadend.sdk.core.DvrConfiguration(
            at.bernhardberger.tvheadend.sdk.core.DvrConfigId("default"), "", "")
        compose.setContent {
            TVHeadendPlayerTheme {
                assertTrue(settingsRecordingLevels(listOf(profile), null) {}.isEmpty())
                val spaceOnly = settingsRecordingLevels(listOf(profile), null, "412 GB free of 2 TB") {}
                assertTrue(spaceOnly.single().rows.map { it.item.id } == listOf(RECORDING_SPACE_ROW))
                assertFalse(settingsRootLevel(false, showRecordings = false).rows.any { it.item.id == RECORDINGS_LEVEL })
                assertTrue(settingsRootLevel(false, showRecordings = true).rows.any { it.item.id == RECORDINGS_LEVEL })
            }
        }
    }

    @Test fun aOneProfileServerOpensRecordingsOnTheReadOnlySpaceRow() {
        val profiles = listOf(DvrConfiguration(DvrConfigId("default"), "", ""))
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState(SETTINGS_ROOT, RECORDINGS_LEVEL),
                    listOf(settingsRootLevel(false, showRecordings = true)) +
                        settingsRecordingLevels(profiles, null, "412 GB free of 2 TB") {})
            }
        }
        compose.onNodeWithText("Recordings").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recording space", substring = true).assertIsFocused()
        compose.onNodeWithText("412 GB free of 2 TB", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Recording profile").assertCountEquals(0)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recording space", substring = true).assertIsFocused()
    }

    @Test fun recordingSpaceFollowsTheProfileChoice() {
        val profiles = listOf(DvrConfiguration(DvrConfigId("default"), "", ""),
            DvrConfiguration(DvrConfigId("archive"), "Archive", ""))
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState(SETTINGS_ROOT, RECORDINGS_LEVEL),
                    listOf(settingsRootLevel(false, showRecordings = true)) +
                        settingsRecordingLevels(profiles, null, "412 GB free of 2 TB") {})
            }
        }
        compose.onNodeWithText("Recordings").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recording profile", substring = true).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Recording space", substring = true).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Recording profile", substring = true).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Default profile").assertIsFocused()
    }

    @Test fun recordingProfileSelectionPersistsAndRestoresFocus() {
        val default = at.bernhardberger.tvheadend.sdk.core.DvrConfigId("default")
        val archive = at.bernhardberger.tvheadend.sdk.core.DvrConfigId("archive")
        val profiles = listOf(at.bernhardberger.tvheadend.sdk.core.DvrConfiguration(default, "", ""),
            at.bernhardberger.tvheadend.sdk.core.DvrConfiguration(archive, "Archive", ""))
        val store = UiSettingsStore(RuntimeEnvironment.getApplication())
        runBlocking { store.setRecordingProfile(at.bernhardberger.tvheadend.sdk.core.DvrConfigId("removed")) }
        compose.setContent {
            TVHeadendPlayerTheme {
                val settings by store.settings.collectAsState(initial = UiSettings())
                val scope = rememberCoroutineScope()
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, RECORDINGS_LEVEL)
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(false, showRecordings = true)) +
                    settingsRecordingLevels(profiles, settings.recordingProfileId) { id ->
                        scope.launch { store.setRecordingProfile(id); navigation.pop() }
                    })
            }
        }
        compose.onNodeWithText("Recordings").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recording profile").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Default profile").assertIsFocused().assertIsSelected()
        press(Key.DirectionDown)
        compose.onNodeWithText("Archive").assertIsFocused().assertIsNotSelected()
        assertEquals("removed", runBlocking { store.settings.first().recordingProfileId?.value })
        press(Key.DirectionCenter)
        compose.waitUntil { runBlocking { store.settings.first().recordingProfileId == archive } }
        compose.onNodeWithText("Recording profile").assertIsFocused()
        assertEquals(archive, runBlocking { UiSettingsStore(RuntimeEnvironment.getApplication()).settings.first().recordingProfileId })
        press(Key.DirectionCenter)
        compose.onNodeWithText("Archive").assertIsFocused().assertIsSelected()
        press(Key.Back)
        compose.onNodeWithText("Recording profile").assertIsFocused()
    }

    @Test fun recordingProfileBelowFoldKeepsFocusInListOnOkEntry() =
        recordingProfileBelowFold(Key.DirectionCenter, delayedPreference = false)

    @Test fun recordingProfileBelowFoldKeepsFocusInListOnRightEntry() =
        recordingProfileBelowFold(Key.DirectionRight, delayedPreference = false)

    @Test fun recordingProfileLoadedAfterEntryFocusesSelectionWithoutDrawerHandoff() =
        recordingProfileBelowFold(Key.DirectionRight, delayedPreference = true)

    @Test fun recordingProfilesLoadedAfterEntryFocusSelectionWithoutDrawerHandoff() =
        recordingProfileBelowFold(Key.DirectionRight, delayedPreference = false, delayedProfiles = true)

    @Test fun recordingProfileLatePreferenceDoesNotOverrideViewerNavigation() {
        val profiles = (0..29).map { index ->
            DvrConfiguration(DvrConfigId("profile-$index"), "Profile $index", "")
        }
        var preferred by mutableStateOf<DvrConfigId?>(null)
        compose.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(rememberDepthNavigationState(RECORDING_PROFILE_LEVEL),
                    settingsRecordingLevels(profiles, preferred) {})
            }
        }
        compose.onNodeWithText("Profile 0").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Profile 1").assertIsFocused()
        compose.runOnIdle { preferred = profiles[25].id }
        compose.onNodeWithText("Profile 1").assertIsFocused().assertIsNotSelected()
    }

    private fun recordingProfileBelowFold(entryKey: Key, delayedPreference: Boolean, delayedProfiles: Boolean = false) {
        val allProfiles = (0..29).map { index ->
            DvrConfiguration(DvrConfigId("profile-$index"), "Profile $index", "")
        }
        var profiles by mutableStateOf(if (delayedProfiles) allProfiles.take(2) else allProfiles)
        var preferred by mutableStateOf(if (delayedPreference) null else allProfiles[25].id)
        compose.setContent {
            TVHeadendPlayerTheme {
                val navigation = rememberDepthNavigationState(RECORDINGS_LEVEL)
                SideRail(currentRoute = AppDestination.SETTINGS, showEpgMenu = true,
                    onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                    SettingsScreenNavigation(navigation,
                        settingsRecordingLevels(profiles, preferred) { id ->
                            preferred = id
                            navigation.pop()
                        }, initialFocusEnabled = !drawerActive, contentPadding = padding)
                }
            }
        }
        compose.onNodeWithText("Recording profile").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(entryKey) }
        assertProfileListKeepsFocusEveryFrame()
        if (delayedPreference || delayedProfiles) {
            compose.onNodeWithText("Profile 0").assertIsFocused()
            compose.runOnIdle { preferred = allProfiles[25].id; profiles = allProfiles }
            assertProfileListKeepsFocusEveryFrame()
        }
        compose.onNodeWithText("Profile 25").assertIsFocused().assertIsSelected().assertIsDisplayed()
        compose.mainClock.autoAdvance = true
        press(Key.DirectionDown)
        compose.onNodeWithText("Profile 26").assertIsFocused().assertIsNotSelected()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Recording profile").assertIsFocused()
        assertEquals(profiles[26].id, preferred)
        press(entryKey)
        compose.onNodeWithText("Profile 26").assertIsFocused().assertIsSelected()
        press(Key.Back)
        compose.onNodeWithText("Recording profile").assertIsFocused()
        // A cached preview must also track a selection changed while its parent is active.
        compose.runOnIdle { preferred = profiles[2].id }
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(entryKey) }
        assertProfileListKeepsFocusEveryFrame()
        compose.onNodeWithText("Profile 2").assertIsFocused().assertIsSelected()
    }

    private fun assertProfileListKeepsFocusEveryFrame() {
        repeat(75) { frame ->
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onNodeWithTag("nav-settings").assertIsNotFocused()
            val focused = compose.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag("depth-active")))
                .fetchSemanticsNodes()
            assertEquals("Profile list lost focus at frame $frame", 1, focused.size)
        }
    }

    @Test fun playRootHasFourSectionsAndApplianceAddsOnlyTheLastSection() {
        lateinit var play: DepthLevel
        lateinit var appliance: DepthLevel
        compose.setContent {
            TVHeadendPlayerTheme {
                play = settingsRootLevel(applianceBuild = false)
                appliance = settingsRootLevel(applianceBuild = true)
            }
        }
        compose.runOnIdle {
            val sections = listOf(SettingsSection.GENERAL, SettingsSection.CHANNEL_TAGS,
                SettingsSection.CONNECTION, SettingsSection.PLAYER).map { it.name }
            assertEquals(sections, play.rows.map { it.item.id })
            assertEquals(sections + SettingsSection.APPLIANCE.name, appliance.rows.map { it.item.id })
        }
    }

    @Test fun playerAutoplayIsFirstPersistsAndRestoresFocusInBothBuilds() {
        val store = UiSettingsStore(RuntimeEnvironment.getApplication())
        runBlocking { store.setAutoStartPlayback(false) }
        var applianceBuild by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                val settings by store.settings.collectAsState(initial = UiSettings())
                val scope = rememberCoroutineScope()
                val navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.PLAYER.name)
                val player = settingsPlayerLevel(SettingsPlayerUiState(autoStartPlayback = settings.autoStartPlayback),
                    {}, {}, {}, onAutoStartPlaybackChanged = { enabled ->
                        scope.launch { store.setAutoStartPlayback(enabled) }
                    })
                SettingsScreenNavigation(navigation, listOf(settingsRootLevel(applianceBuild), player))
            }
        }
        val autoplay = "Start playback when the app opens"
        for (appliance in listOf(false, true)) {
            compose.runOnIdle { applianceBuild = appliance }
            press(Key.DirectionCenter)
            compose.onNodeWithText(autoplay).assertIsFocused().assertIsOff()
            assertFalse(runBlocking { store.settings.first().autoStartPlayback })
            press(Key.DirectionCenter)
            compose.waitUntil { runBlocking { store.settings.first().autoStartPlayback } }
            compose.onNodeWithText(autoplay).assertIsFocused().assertIsOn()
            press(Key.Back)
            compose.onNodeWithText("Player").assertIsFocused()
            press(Key.DirectionCenter)
            compose.onNodeWithText(autoplay).assertIsFocused().assertIsOn()
            press(Key.DirectionCenter)
            compose.waitUntil { !runBlocking { store.settings.first().autoStartPlayback } }
            compose.onNodeWithText(autoplay).assertIsFocused().assertIsOff()
            press(Key.Back)
        }
    }

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
        var clockFormat by mutableStateOf(ClockFormat.SYSTEM)
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
                            UiSettings(showEpgMenu = showGuide, clockFormat = clockFormat), language, CacheStatistics.EMPTY,
                            CacheClearState.IDLE,
                            { languageCalls++; navigation.pop(); language = it }, { showGuide = !showGuide }, { clearCalls++ },
                            onSelectClockFormat = { navigation.pop(); clockFormat = it }),
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
        compose.onNodeWithText("Clock format").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Follow system").assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Clock format").assertIsFocused()
        compose.onNodeWithText("12-hour (8:15 PM)").assertExists()
        assertEquals(ClockFormat.TWELVE_HOUR, clockFormat)
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
                        SettingsScreenNavigation(navigation, listOf(settingsRootLevel(applianceBuild = true),
                            settingsChannelTagsLevel(emptyList(), ChannelScopeVisibility(), loaded, failed,
                                { retries++ }, { toggles++ }),
                            settingsPlayerLevel(SettingsPlayerUiState(profiles = StreamProfilesResult.Failed(TvheadendFailure.ACCESS_DENIED)),
                                { settingsActions++ }, { settingsActions++ }, { settingsActions++ }),
                            settingsApplianceLevel(false, { settingsActions++ }),
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
