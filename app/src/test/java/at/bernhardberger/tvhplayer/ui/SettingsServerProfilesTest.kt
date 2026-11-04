package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.components.SideRail
import org.junit.experimental.categories.Category
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Connection over a real profile owner and the SDK's fake store: where focus lands,
 * when switching asks first, a duplicate add, and removing. Hosts and users are fictional.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w960dp-h540dp-land-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsServerProfilesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val session = FakeTvheadendSession()
    private var ids = 0
    private val store = FakeServerProfileStore(idSource = {
        ServerProfileId.from("00000000-0000-4000-8000-%012d".format(++ids))
    })
    private lateinit var owner: AppProfileOwner
    private lateinit var notices: NoticeCenter
    private lateinit var navigation: DepthNavigationState
    private var interruption = PlaybackInterruption.NONE
    private val unreadable = mutableSetOf<ServerProfileId>()
    /** How many session calls had happened at each playback stop. */
    private val stopsAt = mutableListOf<Int>()

    @After fun after() = scope.cancel()

    private fun id(index: Int) = ServerProfileId.from("00000000-0000-4000-8000-%012d".format(index))
    private fun row(index: Int) = "settings-row-${serverProfileRowId(id(index))}"

    /** Living room (anna, in use unless [active] says otherwise), kids, and an anonymous box. */
    private fun stored(active: Int? = 1) = runBlocking {
        store.addPassword("tvheadend.home.arpa", 9982, "anna", "fixture-secret", "Living room", activate = active == 1)
        store.addPassword("tvheadend.home.arpa", 9982, "kids", "fixture-secret", null, activate = active == 2)
        store.addAnonymous("192.168.1.20", 9982, null, activate = active == 3)
    }

    private var presentation by mutableStateOf(AppConnectionPresentation(ConnectionUiState.Ready))
    private var isCurrent by mutableStateOf(true)
    private lateinit var view: View
    private var retained = 0
    private var used = 0
    private var duplicate: ServerProfileId? = null

    private fun settings(rail: Boolean = false, enter: Boolean = true, picker: Boolean = false) {
        owner = AppProfileOwner(session, store, PlayerSettingsStore(InMemoryPreferencesDataStore()), Dispatchers.IO,
            readProfileForEditing = { if (it in unreadable) ServerProfileEditReadResult.Unavailable else ServerProfileEditReadResult.Missing },
            noticeCenter = { notices })
        notices = NoticeCenter({ 0L }) { NoticeContext(owner.configurationGeneration.value, null) }
        scope.launch { owner.run() }
        compose.waitUntil(10_000) { owner.profiles.value != null && owner.serverProfile.value != null }
        val dependencies = module {
            single<TvheadendSession> { session }
            single { notices }
            single { owner }
        }
        compose.leaveTouchModeBeforeComposing()
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    if (picker) {
                        if (rail) SideRail(AppDestination.SWITCH_SERVER, {}, {},
                            availableDestinations = setOf(AppDestination.CHANNELS, AppDestination.SWITCH_SERVER, AppDestination.SETTINGS)) { padding, drawer ->
                            at.bernhardberger.tvhplayer.ui.screens.ServerSwitcherScreen(!drawer, isCurrent,
                                onUsed = { used++ }, onExisting = { duplicate = it }, owner = owner,
                                interruption = { interruption }, stopPlaying = {}, contentPadding = padding, drawerOpen = drawer)
                        } else
                        at.bernhardberger.tvhplayer.ui.screens.ServerSwitcherScreen(true, isCurrent,
                            onUsed = { used++ }, onExisting = { duplicate = it }, onRetained = { retained++ }, owner = owner,
                            interruption = { interruption }, stopPlaying = { stopsAt.add(session.calls.size) })
                    } else
                    if (rail) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SideRail(currentRoute = AppDestination.SETTINGS, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                                Settings(padding, !drawerActive)
                            }
                        }
                    } else Settings()
                }
            }
        }
        compose.waitForIdle()
        if (picker) return
        if (rail) {
            // As SettingsConnectionDepthTest settles focus on the page beside the rail.
            press(Key.DirectionDown)
            press(Key.DirectionUp)
        }
        compose.onNodeWithText("Connection").assertIsFocused()
        if (enter) press(Key.DirectionCenter)
    }

    @Composable
    private fun Settings(contentPadding: PaddingValues = TvFullScreenPadding, initialFocusEnabled: Boolean = true) {
        navigation = rememberDepthNavigationState(SETTINGS_ROOT, SettingsSection.CONNECTION.name)
        val servers = rememberServerSettingsState(navigation, owner, { interruption }, { stopsAt += session.calls.size })
        val connection = settingsConnectionLevels(presentation, {}, servers.actions, owner)
        servers.FocusPending(connection.firstOrNull())
        val profiles by owner.profiles.collectAsStateWithLifecycle()
        SettingsScreenNavigation(navigation,
            listOf(settingsRootLevel(applianceBuild = false,
                connectionStatus = connectionRootStatus(profiles, connectionOverviewStatus(presentation, owner).title))) + connection,
            initialFocusEnabled = initialFocusEnabled, contentPadding = contentPadding,
            setup = servers.setupContent(), onCloseSetup = servers::closeSetup, dialog = servers.dialogContent(),
            isCurrent = isCurrent, onCloseDialog = servers::closeDialogs)
    }

    private val activeId get() = (owner.profiles.value as? ServerProfileListResult.Available)?.activeId

    private fun pickerRow(index: Int) = "server-picker-${id(index).value}"

    @Test fun pickerSetupInsideTheRailKeepsTypedInputAndHidesBackgroundSemantics() {
        stored(); settings(picker = true, rail = true)
        compose.onNodeWithTag("nav-switch-server").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        type("setup-host", "new.example.invalid")
        press(Key.DirectionUp); press(Key.DirectionDown)
        compose.onNodeWithTag("setup-host").assertTextContains("new.example.invalid")
        compose.onNodeWithTag("nav-settings").assertDoesNotExist()
        compose.onNodeWithTag(pickerRow(1)).assertDoesNotExist()
        press(Key.Back)
        compose.onNodeWithTag("server-picker-add").assertIsFocused()
    }

    @Test fun pickerRetainsTheInUseServerWithoutAReconnectAndReturnsToOrigin() {
        stored(); settings(picker = true)
        compose.onNodeWithTag(pickerRow(1)).assertIsFocused()
        val calls = session.calls.toList()
        press(Key.DirectionCenter)
        assertEquals(1, retained); assertEquals(0, used); assertEquals(calls, session.calls)
    }

    @Test fun pickerWithNoneInUseFocusesTheFirstServerAndCanSelectIt() {
        stored(active = null); settings(picker = true)
        compose.onNodeWithTag(pickerRow(1)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { used == 1 }
        assertEquals(id(1), activeId)
        assertEquals(0, retained)
    }

    @Test fun askAtStartupIsOffByDefaultAndTheSettingsSwitchPersistsTheChoice() {
        stored(); settings()
        compose.onNodeWithTag("settings-row-ask-at-startup").assertIsOff().performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { runBlocking { owner.askAtStartup.first() } }
        compose.onNodeWithTag("settings-row-ask-at-startup").assertIsOn()
        runBlocking { owner.setShowInSwitcher(id(2), false); owner.setShowInSwitcher(id(3), false) }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-row-ask-at-startup").assertDoesNotExist()
    }

    @Test fun pickerFailedSelectionStaysOnThePageAndPostsTheTypedFailure() {
        stored(); settings(picker = true)
        runBlocking { store.scriptMutationUnavailable() }
        press(Key.DirectionRight); press(Key.DirectionCenter)
        compose.waitUntil(10_000) { notices.state.value.pending.any { it.notice == Notice.ServerProfileWriteFailed } }
        compose.onNodeWithTag(pickerRow(2)).assertIsFocused()
        assertEquals(id(1), activeId)
        assertEquals(0, used)
    }

    @Test fun pickerSwitchesAndReturnsToChannelsAndHiddenProfilesAreNotChoices() {
        stored(); settings(picker = true)
        runBlocking { owner.setShowInSwitcher(id(3), false) }
        compose.waitForIdle()
        compose.onNodeWithTag(pickerRow(3)).assertDoesNotExist()
        press(Key.DirectionRight); press(Key.DirectionCenter)
        compose.waitUntil(10_000) { used == 1 }
        assertEquals(id(2), activeId)
    }

    @Test fun pickerConfirmsOnlyWhilePlayingAndBackRestoresTheChosenAvatar() {
        stored(); interruption = PlaybackInterruption.RECORDING; settings(picker = true)
        press(Key.DirectionRight); press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag(pickerRow(2)).assertIsFocused()
        assertEquals(0, used); assertEquals(0, stopsAt.size)
        press(Key.DirectionDown)
        compose.onNodeWithTag("server-picker-add").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag(pickerRow(2)).assertIsFocused()
    }

    @Test fun pickerUnreadablePasswordAndDuplicateAddReuseSettingsFlows() {
        stored()
        runBlocking { store.scriptCredentialsUnavailable(id(2)) }
        unreadable.add(id(2)); settings(picker = true)
        press(Key.DirectionRight); press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-password").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag(pickerRow(2)).assertIsFocused()
        press(Key.DirectionDown); press(Key.DirectionCenter)
        type("setup-host", "tvheadend.home.arpa")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        type("setup-username", "anna"); type("setup-password", "fixture-secret")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { duplicate != null }
        assertEquals(id(1), duplicate)
    }

    @Test fun theListOpensOnTheServerInUseInStoredOrder() {
        stored(active = 2)
        settings()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        compose.onNodeWithText("No server in use. Choose one to watch TV again.").assertDoesNotExist()
        // Stored order, and the row in use keeps its place.
        val tops = listOf(row(1), row(2), row(3), "settings-row-$ADD_SERVER", "settings-row-$CONNECTION_STATUS")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithTag(row(2)).assert(hasText("In use", substring = true))
        compose.onNodeWithTag(row(1)).assert(hasText("kids on tvheadend.home.arpa", substring = true).not())
        compose.onNodeWithTag(row(1)).assert(hasText("anna on tvheadend.home.arpa", substring = true))
        compose.onNodeWithTag(row(3)).assert(hasText("No sign-in", substring = true))
        // Every row is reachable by D-pad, and Back returns to the root's Connection row.
        repeat(2) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings-row-$CONNECTION_STATUS").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
    }

    @Test fun withNoServerInUseTheListSaysSoAndFocusesTheFirstServer() {
        stored(active = null)
        settings()
        compose.onNodeWithText("No server in use. Choose one to watch TV again.").assertIsDisplayed()
        compose.onNodeWithTag(row(1)).assertIsFocused()
        compose.onNodeWithTag("settings-row-$CONNECTION_STATUS").assert(hasText("No server in use", substring = true))
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
    }

    @Test fun usingAServerWhileNothingPlaysSwitchesAtOnceAndFocusesItsRow() {
        stored(active = 1)
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // The level of a server not in use starts on "Use this server".
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        compose.onNodeWithText("Sign in as kids").assertIsDisplayed()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.waitUntil(10_000) { activeId == id(2) }
        compose.waitForIdle()
        assertEquals(SettingsSection.CONNECTION.name, navigation.stack.active.levelId)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        compose.onNodeWithTag(row(2)).assert(hasText("In use", substring = true))
    }

    @Test fun usingAServerWhileLiveTvPlaysAsksAndKeepWatchingComesFirst() {
        stored(active = 1)
        interruption = PlaybackInterruption.LIVE
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        compose.onNodeWithText("Switch to kids on tvheadend.home.arpa?").assertIsDisplayed()
        compose.onNodeWithText("Live TV stops", substring = true).assertIsDisplayed()
        press(Key.DirectionCenter)
        // Keeping returns focus to the row that asked; nothing switched.
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(id(1), activeId)
        assertTrue(stopsAt.isEmpty())
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        compose.onNodeWithTag(SWITCH_CONFIRM).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { activeId == id(2) }
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        // Switching stops what played; nothing retunes it on the new server.
        assertEquals(1, stopsAt.size)
    }

    @Test fun editingAServerNotInUseWhileLiveTvPlaysStopsPlaybackBeforeTheTestWithoutAsking() {
        stored(active = 1)
        unreadable += id(3) // Prefills the anonymous box's address.
        interruption = PlaybackInterruption.LIVE
        settings()
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        assertEquals(serverProfileRowId(id(3)), navigation.stack.active.levelId)
        compose.onNodeWithTag("settings-row-$EDIT_CONNECTION").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-continue").assertIsFocused()
        press(Key.DirectionCenter)
        val beforeTest = session.calls.size
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { session.calls.size > beforeTest }
        compose.waitForIdle()
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        // Stopped once, before the test connection replaced the session; the server in use stays.
        assertEquals(listOf(beforeTest), stopsAt)
        assertEquals(id(1), activeId)
    }

    @Test fun leavingSettingsClosesSwitchAndRemoveQuestionsWithoutReopeningThem() {
        stored()
        interruption = PlaybackInterruption.LIVE
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        compose.runOnIdle { isCurrent = false }
        compose.waitForIdle()
        compose.runOnIdle { isCurrent = true }
        compose.waitForIdle()
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        assertEquals(id(1), activeId)
        compose.onNodeWithTag("settings-row-$SERVER_REMOVE").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(REMOVE_KEEP).assertIsFocused()
        compose.runOnIdle { isCurrent = false }
        compose.waitForIdle()
        compose.runOnIdle { isCurrent = true }
        compose.waitForIdle()
        compose.onNodeWithTag(REMOVE_KEEP).assertDoesNotExist()
        assertEquals(3, (owner.profiles.value as ServerProfileListResult.Available).profiles.size)
    }

    @Test fun backDismissesTheSwitchQuestion() {
        stored(active = 1)
        interruption = PlaybackInterruption.RECORDING
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithText("The recording stops", substring = true).assertIsDisplayed()
        press(Key.Back)
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(id(1), activeId)
    }

    @Test fun addingAServerThatIsStoredOpensItsLevelWithTheNotice() {
        stored(active = 1)
        settings()
        val startup = session.calls.toList()
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        press(Key.DirectionCenter)
        // Adding starts at the Server step, with no Welcome.
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.onNodeWithTag("setup-host").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNode(hasSetTextAction()).performTextReplacement("192.168.1.20")
        press(Key.Back)
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-username").assertExists()
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.waitForIdle()
        compose.onNodeWithTag("setup-continue").assertDoesNotExist()
        assertEquals(serverProfileRowId(id(3)), navigation.stack.active.levelId)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(Notice.ServerProfileAlreadyExists, notices.state.value.candidate?.notice)
        // Nothing was added or tested.
        assertEquals(3, (owner.profiles.value as ServerProfileListResult.Available).profiles.size)
        assertEquals(startup, session.calls)
    }

    @Test fun editingToADuplicateOpensThatServersLevelWithoutStoppingOrTesting() {
        stored(active = 1)
        interruption = PlaybackInterruption.LIVE
        settings()
        val startup = session.calls.toList()
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        type("setup-host", "192.168.1.20")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        assertTrue(stopsAt.isEmpty())
        assertEquals(startup, session.calls)
        assertEquals(serverProfileRowId(id(3)), navigation.stack.active.levelId)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(Notice.ServerProfileAlreadyExists, notices.state.value.candidate?.notice)
    }

    @Test fun backOnTheServerStepOfAddingReturnsToAddServer() {
        stored(active = 1)
        settings()
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag("setup-find-server").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
    }

    @Test fun removingTheServerInUseAsksThenLeavesNoneInUse() {
        stored(active = 1)
        settings()
        press(Key.DirectionCenter)
        // The level of the server in use has no "Use this server".
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_REMOVE").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(REMOVE_KEEP).assertIsFocused()
        compose.onNodeWithText("Remove Living room?").assertIsDisplayed()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(REMOVE_KEEP).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_REMOVE").assertIsFocused()
        assertEquals(id(1), activeId)
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        compose.onNodeWithTag(REMOVE_CONFIRM).assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.size == 2 }
        compose.waitForIdle()
        assertNull(activeId)
        assertEquals(SettingsSection.CONNECTION.name, navigation.stack.active.levelId)
        compose.onNodeWithText("No server in use. Choose one to watch TV again.").assertIsDisplayed()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        assertEquals(Notice.ServerProfileRemoved(id(1), "Living room"), notices.state.value.candidate?.notice)
    }

    @Test fun anUnreadablePasswordOffersEnterPasswordWithTheUserFilledIn() {
        stored(active = 1)
        store.scriptCredentialsUnavailable(id(2))
        unreadable += id(2)
        settings()
        press(Key.DirectionDown)
        compose.onNodeWithTag(row(2)).assert(hasText("Password needed", substring = true))
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_PASSWORD").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithText("Enter the password again").assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("setup-password") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("setup-username").assert(hasText("kids", substring = true))
        press(Key.Back)
        compose.onNodeWithText("Enter the password again").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_PASSWORD").assertIsFocused()
    }

    // Captures of frames 01-12 (mockups-profiles-1) and 08/09 (mockups-profiles-switcher-3): English,
    // font scale 1.0, 960x540 dp, fictional servers, Settings beside the rail as on the TV.

    /** The scene of frame 09: Cabin's password can't be read on this TV. */
    private fun frameScene() = runBlocking {
        store.addPassword("tvheadend.home.arpa", 9982, "anna", "fixture-secret", "Living room", activate = true)
        store.addPassword("tvheadend.home.arpa", 9982, "kids", "fixture-secret", null, activate = false)
        store.addAnonymous("192.168.1.20", 9982, null, activate = false)
        store.addPassword("cabin.home.arpa", 9982, "anna", "fixture-secret", "Cabin", activate = false)
        store.scriptCredentialsUnavailable(id(4))
        unreadable += id(4)
    }

    @Test @Category(VisualCapture::class) fun captureListUseAndSwitch() {
        frameScene()
        settings(rail = true, enter = false)
        capture("01-settings-root-active-profile", "Connection")
        press(Key.DirectionCenter)
        compose.onNodeWithTag(row(1)).assertIsFocused()
        capture("02-connection-profile-list", "Living room")
        capture("switcher3-08-connection-list-in-use-text", "Living room")
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag(row(4)).assertIsFocused()
        capture("09-password-unreadable-list", "Cabin")
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        capture("switcher3-09-connection-list-with-avatars", "Add server")
        repeat(3) { press(Key.DirectionUp) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        capture("03-profile-use-this-server", "Use this server")
        interruption = PlaybackInterruption.LIVE
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        capture("04-switch-confirm-while-playing", "Keep watching")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { activeId == id(2) }
        presentation = AppConnectionPresentation(ConnectionUiState.Connecting)
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        capture("05-switched-connecting", "kids on tvheadend.home.arpa")
    }

    @Test @Category(VisualCapture::class) fun captureAddAndName() {
        runBlocking { store.addPassword("tvheadend.home.arpa", 9982, "anna", "fixture-secret", "Living room", activate = true) }
        settings(rail = true)
        repeat(1) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        capture("06-add-server-step", "Find servers on my network")
        type("setup-host", "tvheadend.home.arpa")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        type("setup-username", "kids")
        type("setup-password", "fixture-secret")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.runOnIdle { session.publish(ready()) }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("setup-start-watching") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("setup-add-name").assertIsDisplayed()
        capture("07-add-connected-offer-name", "Start watching")
        press(Key.DirectionRight)
        compose.onNodeWithTag("setup-add-name").assertIsFocused()
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(SERVER_NAME_FIELD)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        type(SERVER_NAME_FIELD, "Kids")
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        capture("08-name-step", "Name (optional)")
        compose.onNodeWithTag(SERVER_NAME_SAVE).performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) {
            (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.any { it.label == "Kids" } == true
        }
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        capture("08b-named-server-in-list", "Kids")
    }

    @Test @Category(VisualCapture::class) fun captureEnterPasswordAgain() {
        frameScene()
        settings(rail = true)
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_PASSWORD").assertIsFocused()
        capture("09b-password-unreadable-level", "Enter password")
        press(Key.DirectionCenter)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("setup-password") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        capture("10-enter-password-again", "Password")
    }

    @Test @Category(VisualCapture::class) fun captureRemoveTheServerInUse() {
        frameScene()
        settings(rail = true)
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_REMOVE").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(REMOVE_KEEP).assertIsFocused()
        capture("11-remove-active-confirm", "Keep")
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.waitUntil(10_000) { (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.size == 3 }
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        capture("12-after-remove-none-active", "kids on tvheadend.home.arpa")
    }

    @Test @Category(VisualCapture::class) fun captureLongName() {
        runBlocking {
            store.addPassword("recordings-and-live-tv.basement.home.arpa", 9983, "grandparents", "fixture-secret",
                "Grandparents’ living room downstairs with the big satellite dish", activate = true)
            store.addPassword("recordings-and-live-tv.basement.home.arpa", 9983, "a-very-long-user-name-for-the-kids", "fixture-secret",
                null, activate = false)
        }
        settings(rail = true)
        capture("long-name-list", "Grandparents’ living room downstairs with the big satellite dish")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        capture("long-name-level", "Use this server")
    }

    private fun ready() = at.bernhardberger.tvheadend.sdk.core.SessionObservation.create(
        sessionState = at.bernhardberger.tvheadend.sdk.core.SessionState.Ready(at.bernhardberger.tvheadend.sdk.core.ServerCapabilities.create(
            streaming = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
            dvrWrite = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED)),
    )

    /** Enters the field, replaces its text and leaves it with Back, as the remote does. */
    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        press(Key.DirectionCenter)
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        press(Key.Back)
    }

    private fun capture(name: String, focus: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/server-profile-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.txt").writeText("canvas=960x540dp; locale=en; fontScale=1.0; focus=$focus; " +
                "Settings beside the SideRail; fictional servers and users; no notice host composed")
            bitmap.recycle()
        }
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
