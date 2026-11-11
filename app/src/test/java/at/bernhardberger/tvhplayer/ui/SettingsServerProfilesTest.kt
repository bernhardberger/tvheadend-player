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
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.ui.components.SERVER_AVATAR_IN_USE_TAG
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.rememberDepthNavigationState
import at.bernhardberger.tvhplayer.ui.screens.SETTINGS_ROOT
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreenNavigation
import at.bernhardberger.tvhplayer.ui.screens.settingsRootLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.*
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = dispatcher)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
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

    @After fun after() {
        scope.cancel()
        scheduler.runCurrent()
    }

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
    private var settingsComposed by mutableStateOf(true)
    private lateinit var view: View
    private var connectionGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var trialStarted = false
    private var rejectNextConnection = false
    private var unreachableNextConnection = false
    private var persistenceGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var persistenceStarted = false
    private var wentHome = 0
    private var restorationGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var restoring = false

    private fun settings(rail: Boolean = false, enter: Boolean = true) {
        val connectedSession = object : TvheadendSession by session {
            override suspend fun disconnect() {
                restorationGate?.let { restoring = true; it.await() }
                session.disconnect()
                session.publish(at.bernhardberger.tvheadend.sdk.core.SessionObservation.create(
                    sessionState = at.bernhardberger.tvheadend.sdk.core.SessionState.Disconnected))
            }
            override suspend fun connect(profile: at.bernhardberger.tvheadend.sdk.core.ServerProfile): at.bernhardberger.tvheadend.sdk.core.SessionCommandResult {
                connectionGate?.let { gate -> connectionGate = null; trialStarted = true; gate.await() }
                val result = session.connect(profile)
                if (unreachableNextConnection) {
                    unreachableNextConnection = false
                    session.publish(at.bernhardberger.tvheadend.sdk.core.SessionObservation.create(
                        sessionState = at.bernhardberger.tvheadend.sdk.core.SessionState.Unavailable(
                            at.bernhardberger.tvheadend.sdk.core.SessionFailure.TRANSPORT_UNAVAILABLE)))
                } else if (rejectNextConnection) {
                    rejectNextConnection = false
                    session.publish(at.bernhardberger.tvheadend.sdk.core.SessionObservation.create(
                        sessionState = at.bernhardberger.tvheadend.sdk.core.SessionState.Unavailable(
                            at.bernhardberger.tvheadend.sdk.core.SessionFailure.AUTHENTICATION_REJECTED)))
                } else session.publish(ready())
                return result
            }
        }
        val gatedStore = object : at.bernhardberger.tvheadend.sdk.core.ServerProfileStore by store {
            override suspend fun setActive(id: ServerProfileId): at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult {
                persistenceGate?.let { persistenceStarted = true; it.await() }
                return store.setActive(id)
            }
        }
        owner = AppProfileOwner(connectedSession, gatedStore, PlayerSettingsStore(InMemoryPreferencesDataStore()), dispatcher,
            readProfileForEditing = { if (it in unreadable) ServerProfileEditReadResult.Unavailable else ServerProfileEditReadResult.Missing },
            noticeCenter = { notices })
        notices = NoticeCenter({ 0L }) { NoticeContext(owner.configurationGeneration.value, null) }
        scope.launch { owner.run() }
        scheduler.runCurrent()
        assertTrue(owner.profiles.value != null && owner.serverProfile.value != null)
        val dependencies = module {
            single<TvheadendSession> { connectedSession }
            single { notices }
            single { owner }
            single<CoroutineScope>(qualifier = org.koin.core.qualifier.named("application")) { scope }
        }
        compose.leaveTouchModeBeforeComposing()
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    if (rail) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            SideRail(currentRoute = AppDestination.SETTINGS, onRootBack = {}, onNavigate = {}) { padding, drawerActive ->
                                Settings(padding, !drawerActive)
                            }
                        }
                    } else if (settingsComposed) SettingsSetupFrame { Settings() }
                    at.bernhardberger.tvhplayer.ui.notifications.AppShellNoticeHost(notices, owner, session)
                }
            }
        }
        compose.waitForIdle()
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
        val connection = settingsConnectionLevels(presentation, {}, servers.actions, owner, servers.switchingNow)
        servers.FocusPending(connection.firstOrNull())
        val profiles by owner.profiles.collectAsStateWithLifecycle()
        SettingsScreenNavigation(navigation,
            listOf(settingsRootLevel(
                connectionStatus = connectionRootStatus(profiles, connectionOverviewStatus(presentation, owner)))) + connection,
            initialFocusEnabled = initialFocusEnabled, contentPadding = contentPadding,
            setup = servers.setupContent(isCurrent && initialFocusEnabled) { wentHome++ },
            onCloseSetup = servers::closeSetup, dialog = servers.dialogContent(),
            isCurrent = isCurrent, onCloseDialog = servers::closeDialogs)
    }

    private val activeId get() = (owner.profiles.value as? ServerProfileListResult.Available)?.activeId

    @Test fun switchBusyStateSurvivesSettingsReentryButExplicitCancelClearsIt() = kotlinx.coroutines.test.runTest {
        val stored = FakeServerProfileStore()
        val first = (stored.addAnonymous("first.invalid", activate = true) as at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult.Applied).id
        stored.addAnonymous("second.invalid")
        val profiles = AppProfileOwner(FakeTvheadendSession(), stored, PlayerSettingsStore(InMemoryPreferencesDataStore()),
            kotlinx.coroutines.test.StandardTestDispatcher(testScheduler), { ServerProfileEditReadResult.Missing })
        backgroundScope.launch { profiles.run() }
        runCurrent()
        val state = ServerSettingsState(
            DepthNavigationState(at.bernhardberger.tvhplayer.ui.components.depth.DepthStack(listOf(
                at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame(SettingsSection.CONNECTION.name)))),
            profiles, { PlaybackInterruption.NONE }, {}, backgroundScope, backgroundScope, { null },
            mutableStateOf(null), mutableStateOf(null), mutableStateOf(null), mutableStateOf(null), mutableStateOf(false))
        val target = (profiles.profiles.value as ServerProfileListResult.Available).profiles.last()
        state.actions.onUse(target)
        assertTrue(state.switchingNow)
        state.cancelSwitch()
        runCurrent()
        assertTrue(!state.switchingNow)
        assertEquals(first, (profiles.profiles.value as ServerProfileListResult.Available).activeId)
        assertTrue(!profiles.connectionChanges.isLocked)
        state.actions.onUse(target)
        runCurrent()
        assertTrue(profiles.switchInProgress.value)
        state.closeSetup()
        val setup = mutableStateOf<String?>(null)
        val confirmation = mutableStateOf<String?>(null)
        val removal = mutableStateOf<String?>(null)
        val reentered = ServerSettingsState(
            DepthNavigationState(at.bernhardberger.tvhplayer.ui.components.depth.DepthStack(listOf(
                at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame(SettingsSection.CONNECTION.name)))),
            profiles, { PlaybackInterruption.NONE }, {}, backgroundScope, backgroundScope, { null },
            setup, confirmation, removal, mutableStateOf(null), mutableStateOf(false))
        reentered.actions.onEnterPassword(target)
        reentered.actions.onSetUp(target)
        reentered.actions.onName(target)
        reentered.actions.onRemove(target)
        reentered.actions.onAdd()
        reentered.actions.onUse(target)
        assertNull(setup.value)
        assertNull(confirmation.value)
        assertNull(removal.value)
        assertTrue(!reentered.switchingNow)
        state.cancelSwitch()
        runCurrent()
        assertTrue(!profiles.switchInProgress.value)
    }

    private fun startSwitchToNext(gated: Boolean = true) {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        if (!gated) {
            compose.runOnIdle { gate.complete(Unit) }
            settle()
        }
    }

    /** Back on a finished switch page is "Back to Settings": it returns to the connection's own level. */
    private fun closeSwitchPage() {
        press(Key.Back)
        compose.mainClock.advanceTimeBy(1_000)
        settle()
    }

    /** One more Back leaves that level for the list, focused on the connection. */
    private fun closeSwitchPageToList() {
        closeSwitchPage()
        press(Key.Back)
    }

    private fun assertOnLevelOf(number: Int) {
        assertEquals(serverProfileRowId(id(number)), navigation.stack.active.levelId)
    }

    @Test fun anAttemptRunsOnAPageThatBlocksSettingsAndCancelKeepsThePreviousConnection() {
        stored(); settings()
        startSwitchToNext()
        assertTrue(trialStarted)
        compose.onNodeWithTag(SWITCH_CANCEL).assertIsFocused()
        compose.onNodeWithText("Connecting to", substring = true).assertExists()
        // Nothing behind the dialog takes focus, and the attempt carries on.
        listOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach { press(it) }
        compose.onNodeWithTag(SWITCH_CANCEL).assertIsFocused()
        assertTrue(owner.connectionChanges.isLocked)
        press(Key.DirectionCenter)
        settle()
        assertTrue(!owner.connectionChanges.isLocked)
        assertEquals(id(1), activeId)
        compose.onNodeWithTag(SWITCH_CANCEL).assertDoesNotExist()
        // Cancelling is silent and lands on the connection's level, where Use is one press away again.
        assertNull(notices.state.value.candidate)
        assertOnLevelOf(2)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        // A fresh attempt can follow the cancelled one.
        trialStarted = false
        connectionGate = kotlinx.coroutines.CompletableDeferred()
        press(Key.DirectionCenter)
        settle()
        assertTrue(trialStarted)
        compose.onNodeWithTag(SWITCH_CANCEL).assertIsFocused()
    }

    @Test fun theConnectingPageNamesTheTargetAndLeavesNoRowToSayConnecting() {
        stored(); settings()
        startSwitchToNext()
        assertTrue(trialStarted)
        compose.runOnIdle { presentation = AppConnectionPresentation(ConnectionUiState.Connecting) }
        settle()
        compose.onNodeWithText("Connecting to kids@tvheadend.home.arpa").assertExists()
        compose.onNodeWithTag(row(1)).assertDoesNotExist()
    }

    @Test fun leavingSettingsDuringAnAttemptDoesNotCancelIt() {
        stored(); settings()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        assertTrue(trialStarted)
        compose.runOnIdle { settingsComposed = false }
        settle()
        assertTrue(owner.connectionChanges.isLocked)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(id(2), activeId)
        assertEquals(listOf(Notice.ServerSwitched(id(2), "kids@tvheadend.home.arpa")),
            notices.state.value.let { listOfNotNull(it.active?.notice) + it.pending }.map { it.notice })
        compose.runOnIdle { settingsComposed = true }
        settle()
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
        assertEquals(1L, notices.state.value.candidate?.id)
    }

    @Test fun aFailedSwitchAwayFromSettingsPostsItsOutcomeOnceWithoutReplayingTheDialog() {
        stored(); settings()
        unreachableNextConnection = true
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        compose.runOnIdle { isCurrent = false }
        settle()
        compose.onNodeWithTag(SWITCH_CANCEL).assertDoesNotExist()
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(id(1), activeId)
        assertEquals(listOf(Notice.ServerConnectionFailed("kids@tvheadend.home.arpa")),
            notices.state.value.let { listOfNotNull(it.active?.notice) + it.pending }.map { it.notice })
        compose.runOnIdle { isCurrent = true }
        settle()
        compose.onNodeWithTag(SWITCH_TRY_AGAIN).assertDoesNotExist()
        assertEquals(1L, notices.state.value.candidate?.id)
    }

    @Test fun aSuccessfulSwitchSaysSoOnItsPageAndGoToHomeLeavesSettings() {
        stored(); settings()
        // No gate: even an outcome before the page's first composition belongs to the requested page.
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        assertEquals(id(2), activeId)
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
        compose.onNodeWithTag("server-switch-page").assertExists()
        compose.onNodeWithTag(SWITCH_BACK).assertExists()
        // The page is the message: no notice repeats it.
        assertNull(notices.state.value.candidate)
        press(Key.DirectionCenter)
        assertEquals(1, wentHome)
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
    }

    /** In the app Settings sits beside the SideRail, where the page must still take the Back key. */
    @Test fun backOnTheSuccessPageReturnsToTheConnectionsLevelAndOnceMoreToTheListInUse() {
        stored(); settings(rail = true)
        startSwitchToNext(gated = false)
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
        closeSwitchPage()
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
        assertEquals(0, wentHome)
        assertOnLevelOf(2)
        press(Key.Back)
        assertEquals(SettingsSection.CONNECTION.name, navigation.stack.active.levelId)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        assertInUse(row(2))
    }

    @Test fun backToSettingsOnTheSuccessPageDoesTheSameAsBack() {
        stored(); settings()
        startSwitchToNext(gated = false)
        press(Key.DirectionRight)
        compose.onNodeWithTag(SWITCH_BACK).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
        assertEquals(0, wentHome)
        assertOnLevelOf(2)
    }

    /** The buttons arrive with the end of the success animation, and focus lands on them then, not before. */
    @Test fun theSuccessButtonsAndTheirFocusWaitForTheRevealToFinish() {
        stored(); settings()
        press(Key.DirectionDown); press(Key.DirectionCenter)
        compose.mainClock.autoAdvance = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        // The clock is held: step frames so the connecting page is up before the outcome arrives.
        repeat(3) { scheduler.runCurrent(); compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag(SWITCH_CANCEL).assertExists()
        compose.runOnIdle { gate.complete(Unit) }
        repeat(5) { scheduler.runCurrent(); compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("server-switch-page").assertExists()
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
        compose.onNodeWithTag(SWITCH_CANCEL).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithTag(SWITCH_HOME).assertDoesNotExist()
        compose.onNodeWithTag("server-switch-reveal").assertIsFocused()
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("server-switch-reveal").assertIsFocused()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
    }

    /** With animators off (reduced motion) the finished page is there at once, buttons and focus included. */
    @Test fun withAnimatorsOffTheSuccessPageShowsItsFinalStateAtOnce() {
        var backEnabled by mutableStateOf(false)
        var outerBack = 0
        var pageBack = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                androidx.activity.compose.BackHandler { outerBack++ }
                SwitchPage(SwitchProgress.Finished(fixtureServer(2, "tvheadend.home.arpa", "kids"), Notice.ServerSwitched(id(2), "kids")), null,
                    SwitchPageActions({}, {}, { pageBack++ }, {}, {}), motionEnabled = false, backEnabled = backEnabled)
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, outerBack)
        assertEquals(0, pageBack)
        compose.runOnIdle { backEnabled = true }
        compose.settleFrames(2, 16)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, pageBack)
    }

    @Test fun backDuringPersistenceFromNoneActiveShowsTheCompletedSelectionAndRestoresFocus() {
        stored(active = null); settings()
        persistenceGate = kotlinx.coroutines.CompletableDeferred()
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        assertTrue(persistenceStarted)
        press(Key.Back)
        persistenceGate!!.complete(Unit)
        settle()
        assertEquals(id(2), activeId)
        assertTrue(!owner.connectionChanges.isLocked)
        assertOnLevelOf(2)
        press(Key.Back)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        assertInUse(row(2))
        // No busy/cancelled latch remains: Back navigates normally again.
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
    }

    @Test fun aFailedSwitchSaysWhyOnItsPageAndBackReturnsToTheConnectionsLevelOnThePreviousConnection() {
        stored(); settings()
        press(Key.DirectionDown); press(Key.DirectionCenter)
        listOf(false, true).forEach { rejected ->
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            connectionGate = gate
            unreachableNextConnection = !rejected
            rejectNextConnection = rejected
            press(Key.DirectionCenter)
            compose.runOnIdle { gate.complete(Unit) }
            settle()
            assertTrue(!owner.connectionChanges.isLocked)
            // The likelier fix comes first: a rejected sign-in asks for the account, an unreachable server for another go.
            compose.onNodeWithTag(if (rejected) SWITCH_EDIT else SWITCH_TRY_AGAIN).assertIsFocused()
            compose.onNodeWithText(if (rejected) "Sign-in failed" else "Can’t reach the server").assertExists()
            compose.onNodeWithTag(SWITCH_BACK).assertExists()
            assertNull(notices.state.value.candidate)
            assertEquals(id(1), activeId)
            // The connection that stays in use is named, with its avatar.
            compose.onNodeWithText(if (rejected) "Couldn’t sign in" else "didn’t respond", substring = true).assertExists()
            if (!rejected) compose.onNodeWithText("Living room is still in use").assertExists()
            closeSwitchPage()
            compose.onNodeWithTag(SWITCH_BACK).assertDoesNotExist()
            assertOnLevelOf(2)
            compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
            assertEquals(id(1), activeId)
        }
    }

    @Test fun tryAgainOnAFailedPageRerunsTheSameSwitch() {
        stored(); settings()
        unreachableNextConnection = true
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        compose.onNodeWithTag(SWITCH_TRY_AGAIN).assertIsFocused()
        assertEquals(id(1), activeId)
        press(Key.DirectionCenter)
        settle()
        assertEquals(id(2), activeId)
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
    }

    @Test fun editConnectionOnAFailedPageOpensTheEditFlowOfThatConnection() {
        stored(); settings()
        rejectNextConnection = true
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        compose.onNodeWithTag(SWITCH_EDIT).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_EDIT).assertDoesNotExist()
        compose.onNodeWithTag("setup-host").assertExists()
        assertEquals(id(1), activeId)
    }

    @Test fun backCancelsAnAttemptAndKeepsSettingsOnThePreviousConnection() {
        stored(); settings()
        press(Key.DirectionDown); press(Key.DirectionCenter)
        listOf(false, true).forEach { dispatcher ->
        trialStarted = false
        connectionGate = kotlinx.coroutines.CompletableDeferred()
        press(Key.DirectionCenter)
        settle()
        assertTrue(trialStarted)
        assertEquals(id(1), activeId)
        compose.onNodeWithTag(SWITCH_CANCEL).assertIsFocused()
        if (dispatcher) compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        else press(Key.Back)
        settle()
        assertTrue(!owner.connectionChanges.isLocked)
        assertEquals(id(1), activeId)
        assertNull(notices.state.value.candidate)
        assertOnLevelOf(2)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        }
        val trial = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = trial
        unreachableNextConnection = true
        press(Key.DirectionCenter)
        val restore = kotlinx.coroutines.CompletableDeferred<Unit>()
        restorationGate = restore
        compose.runOnIdle { trial.complete(Unit) }
        settle()
        assertTrue(restoring)
        press(Key.Back)
        compose.runOnIdle { restore.complete(Unit) }
        settle()
        compose.onNodeWithTag("server-switch-page").assertDoesNotExist()
        assertEquals(id(1), activeId)
        assertNull(notices.state.value.candidate)
        assertTrue(!owner.connectionChanges.isLocked)
    }

    @Test fun cancellingBeforeHandoverRestoresTheInUseConnectionsDegradedWarning() {
        stored(); settings()
        lateinit var connection: at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
        compose.runOnIdle {
            connection = at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel(session, owner,
                at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource(session.observation,
                    owner.configurationGeneration, notices, owner.switchInProgress))
        }
        settle()
        assertEquals(ConnectionUiState.Ready, connection.uiState.value.connection)
        compose.runOnIdle {
            session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(
                at.bernhardberger.tvheadend.sdk.core.SessionFailure.AUTHENTICATION_REJECTED)))
        }
        settle()
        assertTrue(connection.uiState.value.degraded)
        compose.runOnIdle { session.publish(SessionObservation.create(sessionState = SessionState.Connecting)) }
        settle()
        assertEquals(ConnectionUiState.Connecting, connection.uiState.value.connection)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val attempt = scope.launch { owner.switchActive(id(2)) { gate.await() } }
        settle()
        assertTrue(owner.switchInProgress.value)
        assertTrue(!connection.uiState.value.degraded)
        attempt.cancel()
        settle()
        assertTrue(attempt.isCompleted)
        assertEquals(ConnectionUiState.Connecting, connection.uiState.value.connection)
        assertTrue(connection.uiState.value.degraded)
        compose.runOnIdle { session.publish(ready()) }
        settle()
        assertEquals(ConnectionUiState.Ready, connection.uiState.value.connection)
        assertTrue(!connection.uiState.value.degraded)
    }

    @Test fun attemptHidesRetryAndRefreshesTheUnmovedRowsAndPreviewAfterFallback() {
        stored(); settings()
        lateinit var connection: at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
        compose.runOnIdle {
            connection = at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel(session, owner,
                at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource(session.observation,
                    owner.configurationGeneration, notices, owner.switchInProgress))
        }
        settle()
        assertEquals(ConnectionUiState.Ready, connection.uiState.value.connection)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        rejectNextConnection = true
        press(Key.DirectionDown); press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        assertTrue(trialStarted)
        compose.runOnIdle {
            presentation = AppConnectionPresentation(ConnectionUiState.Reconnecting, degraded = true)
            session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(
                at.bernhardberger.tvheadend.sdk.core.SessionFailure.AUTHENTICATION_REJECTED)))
        }
        settle()
        assertEquals(ConnectionUiState.Connecting, connection.uiState.value.connection)
        assertTrue(!connection.uiState.value.degraded)
        compose.onNodeWithText("Try again").assertDoesNotExist()
        compose.onNodeWithTag(SWITCH_CANCEL).assertExists()
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertTrue(!owner.connectionChanges.isLocked)
        assertEquals(ConnectionUiState.Ready, connection.uiState.value.connection)
        assertTrue(!connection.uiState.value.degraded)
        closeSwitchPageToList()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        // No focus move: the same preview must now expose the restored profile's actions.
        compose.onNodeWithText("Use this connection", useUnmergedTree = true).assertExists()
        assertEquals(id(1), activeId)
    }

    /**
     * Opening a connection's level hands the list's focus on row by row; the Status row's pass used
     * to scroll the list, leaving the connection in use partly under the top fade (looking dimmed)
     * until focus moved over it.
     */
    @Test fun theConnectionInUseLooksTheSameAfterAFailedAttemptWithoutAFocusPass() {
        stored(); settings()
        press(Key.DirectionDown)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        val before = rowPixels(row(1))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        rejectNextConnection = true
        press(Key.DirectionCenter); press(Key.DirectionCenter)
        settle()
        assertTrue(trialStarted)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertTrue(!owner.connectionChanges.isLocked)
        closeSwitchPageToList()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        assertEquals(id(1), activeId)
        assertTrue("the connection in use keeps its enabled look", before.contentEquals(rowPixels(row(1))))
        compose.onNodeWithTag(row(1)).assertIsEnabled()
    }

    private fun rowPixels(tag: String): IntArray {
        compose.mainClock.advanceTimeBy(1_000)
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        lateinit var pixels: IntArray
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = bounds.width.toInt(); val height = bounds.height.toInt()
            pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
            bitmap.recycle()
        }
        return pixels
    }

    @Test fun theListOpensOnTheServerInUseInStoredOrder() {
        stored(active = 2)
        runBlocking { store.addAnonymous("tvh-demo.example.org", 19982, null, activate = false) }
        settings()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        compose.onNodeWithText("No connection in use. Choose one to watch TV again.").assertDoesNotExist()
        // Stored order, and the row in use keeps its place.
        val tops = listOf(row(1), row(2), row(3), row(4), "settings-row-$ADD_SERVER")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
        assertInUse(row(2))
        // A labeled server's second line is its account on the address; an unlabeled one is titled by
        // its account over the address, and an anonymous one by its address over "No sign-in".
        compose.onNodeWithTag(row(1)).assert(hasText("anna@tvheadend.home.arpa", substring = true))
        compose.onNodeWithTag(row(2)).assert(hasText("kids")).assert(hasText("tvheadend.home.arpa"))
        compose.onNodeWithTag(row(3)).assert(hasText("No sign-in", substring = true))
        compose.onNodeWithTag(row(4)).assert(hasText("tvh-demo.example.org:19982"))
        // Every row is reachable by D-pad, and Back returns to the root's Connection row.
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        // The status of the connection in use shows on its own row, not on a row of its own.
        compose.onNodeWithTag("settings-row-connection-status").assertDoesNotExist()
        press(Key.Back)
        compose.onNodeWithText("Connection").assertIsFocused()
    }

    @Test fun withNoServerInUseTheListSaysSoAndFocusesTheFirstServer() {
        stored(active = null)
        settings()
        compose.onNodeWithText("No connection in use. Choose one to watch TV again.").assertIsDisplayed()
        compose.onNodeWithTag(row(1)).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
    }

    /** The connection in use says so with the avatar badge and for TalkBack, not with a line of text. */
    private fun assertInUse(rowTag: String) {
        compose.onAllNodesWithTag(SERVER_AVATAR_IN_USE_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onNode(hasTestTag(rowTag) and hasAnyDescendant(hasTestTag(SERVER_AVATAR_IN_USE_TAG)), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag(rowTag).assert(hasContentDescription("In use")).assert(hasText("In use", substring = true).not())
    }

    @Test fun theConnectionInUseWearsTheBadgeAndShowsItsProblemInPlaceOfInUse() {
        stored(active = 1)
        settings()
        assertInUse(row(1))
        val failing = AppConnectionPresentation(ConnectionUiState.Error(
            at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
            at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition.EXPLICIT_RETRY))
        compose.runOnIdle { presentation = failing }
        compose.waitForIdle()
        val title = at.bernhardberger.tvhplayer.ui.common.AppErrorText(compose.activity)
            .title(requireNotNull(connectionOverviewFailure(owner.serverProfile.value, failing)))
        compose.onNodeWithTag(row(1)).assert(hasText(title, substring = true))
        compose.onNodeWithTag(row(2)).assert(hasText(title, substring = true).not())
        compose.runOnIdle { presentation = AppConnectionPresentation(ConnectionUiState.Connecting) }
        compose.waitForIdle()
        compose.onNodeWithTag(row(1)).assert(hasText("Connecting…", substring = true))
        assertInUse(row(1))
    }

    @Test fun theServerInUseHasNoUseActionAndOpensOnEdit() {
        stored(active = 1)
        settings()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$EDIT_CONNECTION").assertIsFocused()
        assertEquals(id(1), activeId)
        assertEquals(serverProfileRowId(id(1)), navigation.stack.active.levelId)
        // Connecting does not introduce an action on the connection already in use.
        compose.runOnIdle { presentation = AppConnectionPresentation(ConnectionUiState.Connecting) }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$EDIT_CONNECTION").assertIsFocused()
    }

    @Test fun theRootConnectionRowDropsConnectedForItsIconAndKeepsOtherWords() {
        stored()
        settings(enter = false)
        val root = compose.onNodeWithTag("settings-row-${SettingsSection.CONNECTION.name}")
        root.assert(hasText("Living room", substring = true))
            .assert(hasText("Connected", substring = true).not())
            .assert(hasContentDescription("Connected"))
        compose.runOnIdle { presentation = AppConnectionPresentation(ConnectionUiState.Connecting) }
        compose.waitForIdle()
        root.assert(hasText("Connecting…", substring = true)).assert(hasContentDescription("Connected").not())
    }

    @Test fun usingAServerWhileNothingPlaysSwitchesAtOnceAndFocusesItsRow() {
        stored(active = 1)
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        // The level of a server not in use starts on "Use this server".
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        compose.onNodeWithText("Sign in as kids").assertIsDisplayed()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(id(2), activeId)
        closeSwitchPageToList()
        assertEquals(SettingsSection.CONNECTION.name, navigation.stack.active.levelId)
        compose.onNodeWithTag(row(2)).assertIsFocused()
        assertInUse(row(2))
    }

    @Test fun usingAServerWhileLiveTvPlaysAsksAndKeepWatchingComesFirst() {
        stored(active = 1)
        interruption = PlaybackInterruption.LIVE
        settings()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        compose.onNodeWithText("Switch to kids@tvheadend.home.arpa?").assertIsDisplayed()
        compose.onNodeWithText("Live TV stops", substring = true).assertIsDisplayed()
        press(Key.DirectionCenter)
        // Keeping returns focus to the row that asked; nothing switched.
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(id(1), activeId)
        assertTrue(stopsAt.isEmpty())
        compose.runOnIdle { interruption = PlaybackInterruption.RECORDING }
        press(Key.DirectionCenter)
        compose.onNodeWithText("The recording stops", substring = true).assertIsDisplayed()
        press(Key.Back)
        compose.onNodeWithTag(SWITCH_KEEP).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        assertEquals(id(1), activeId)
        assertTrue(stopsAt.isEmpty())
        compose.runOnIdle { interruption = PlaybackInterruption.LIVE }
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        compose.onNodeWithTag(SWITCH_CONFIRM).assertIsFocused()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(id(2), activeId)
        closeSwitchPageToList()
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
        settle()
        assertTrue(session.calls.size > beforeTest)
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
        settle()
        assertEquals(2, (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.size)
        compose.waitForIdle()
        assertNull(activeId)
        assertEquals(SettingsSection.CONNECTION.name, navigation.stack.active.levelId)
        compose.onNodeWithText("No connection in use. Choose one to watch TV again.").assertIsDisplayed()
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
        compose.onNodeWithTag("settings-row-$SERVER_PASSWORD").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("Enter the password again").assertIsDisplayed()
        settle()
        compose.onNodeWithTag("setup-password").assertIsFocused()
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
        val failing = AppConnectionPresentation(ConnectionUiState.Error(
            at.bernhardberger.tvhplayer.data.ConnectionFailureKind.UNREACHABLE,
            at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition.EXPLICIT_RETRY), degraded = true)
        presentation = failing
        compose.waitForIdle()
        capture("06-settings-root-not-connected", "Connection")
        presentation = AppConnectionPresentation(ConnectionUiState.Ready)
        compose.waitForIdle()
        press(Key.DirectionCenter)
        compose.onNodeWithTag(row(1)).assertIsFocused()
        capture("02-connection-profile-list", "Living room")
        capture("switcher3-08-connection-list-in-use-text", "Living room")
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$EDIT_CONNECTION").assertIsFocused()
        capture("10-connection-in-use-level", "Edit connection")
        press(Key.Back)
        compose.onNodeWithTag(row(1)).assertIsFocused()
        press(Key.DirectionDown)
        capture("07-connection-list-in-use-unfocused", "Kids")
        presentation = failing
        compose.waitForIdle()
        capture("08-connection-list-in-use-cant-connect", "Kids")
        presentation = AppConnectionPresentation(ConnectionUiState.Ready)
        compose.waitForIdle()
        press(Key.DirectionUp)
        repeat(3) { press(Key.DirectionDown) }
        compose.onNodeWithTag(row(4)).assertIsFocused()
        capture("09-password-unreadable-list", "Cabin")
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_PASSWORD").assertIsFocused()
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertDoesNotExist()
        capture("18-password-needed-level", "Enter password")
        press(Key.Back)
        press(Key.DirectionDown)
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        capture("connection-list-with-avatars", "Add connection")
        repeat(3) { press(Key.DirectionUp) }
        press(Key.DirectionCenter)
        compose.onNodeWithTag("settings-row-$SERVER_USE").assertIsFocused()
        capture("03-profile-use-this-connection", "Use this connection")
        interruption = PlaybackInterruption.LIVE
        press(Key.DirectionCenter)
        compose.onNodeWithTag(SWITCH_KEEP).assertIsFocused()
        capture("04-switch-confirm-while-playing", "Keep watching")
        press(Key.DirectionRight)
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        compose.runOnIdle { gate.complete(Unit) }
        settle()
        assertEquals(id(2), activeId)
        closeSwitchPageToList()
        presentation = AppConnectionPresentation(ConnectionUiState.Connecting)
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        capture("05-switched-connecting", "kids@tvheadend.home.arpa")
    }

    /** The page of a switch the user started, while it connects, for each way it ends and across the success reveal. */
    @Test @Category(VisualCapture::class) fun captureSwitchPages() {
        stored(); settings()
        startSwitchToNext()
        compose.onNodeWithTag(SWITCH_CANCEL).assertIsFocused()
        capture("20-switch-page-connecting", "Cancel")
        press(Key.DirectionCenter)
        listOf("21-switch-page-cant-reach", "22-switch-page-sign-in-failed").forEachIndexed { index, name ->
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            connectionGate = gate
            unreachableNextConnection = index == 0
            rejectNextConnection = index == 1
            press(Key.DirectionCenter)
            compose.runOnIdle { gate.complete(Unit) }
            settle()
            compose.onNodeWithTag(if (index == 0) SWITCH_TRY_AGAIN else SWITCH_EDIT).assertIsFocused()
            capture(name, if (index == 0) "Try again" else "Edit connection")
            press(Key.Back)
        }
        compose.mainClock.autoAdvance = false
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        repeat(3) { scheduler.runCurrent(); compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { gate.complete(Unit) }
        // The outcome lands through the owner's coroutines and a frame; the clock is held, so step both.
        repeat(5) { scheduler.runCurrent(); compose.mainClock.advanceTimeByFrame() }
        var elapsed = 0
        listOf(100, 250, 400, 600).forEachIndexed { index, at ->
            compose.mainClock.advanceTimeBy((at - elapsed).toLong())
            elapsed = at
            capture("23-switch-page-connected-${index + 1}-${at}ms",
                if (at < 600) "hero" else "Go to Home", settleBeforeCapture = false)
        }
        compose.onNodeWithTag(SWITCH_HOME).assertIsFocused()
    }

    /** A household's real shapes with fictional values: many accounts on one box, custom ports, a long host. */
    @Test @Category(VisualCapture::class) fun captureDefaultConnectionNames() {
        runBlocking {
            store.addPassword("192.0.2.15", 9982, "maria", "fixture-secret", null, activate = true)
            store.addPassword("198.51.100.2", 9982, "reviewer_5f3a9c21", "fixture-secret", null, activate = false)
            store.addPassword("192.0.2.15", 9982, "tom", "fixture-secret", null, activate = false)
            store.addPassword("192.0.2.15", 19982, "lab", "fixture-secret", null, activate = false)
            store.addPassword("192.0.2.15", 29982, "lab", "fixture-secret", null, activate = false)
            store.addPassword("tvheadend.home.arpa", 9982, "anna", "fixture-secret", "Living room", activate = false)
            store.addAnonymous("tvh-demo.example.org", 19982, null, activate = false)
        }
        settings(rail = true, enter = false)
        capture("11-names-settings-root", "Connection")
        press(Key.DirectionCenter)
        compose.onNodeWithTag(row(1)).assertIsFocused()
        capture("12-names-list-in-use-focused", "maria")
        press(Key.DirectionDown)
        capture("13-names-list-in-use-resting", "reviewer")
        repeat(4) { press(Key.DirectionDown) }
        capture("14-names-list-labeled-focused", "Living room")
        press(Key.DirectionDown)
        capture("15-names-list-anonymous-long-host", "tvh-demo.example.org")
        press(Key.DirectionCenter)
        capture("16-names-anonymous-level", "Use this connection")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        connectionGate = gate
        press(Key.DirectionCenter)
        presentation = AppConnectionPresentation(ConnectionUiState.Connecting)
        settle()
        compose.runOnIdle { gate.complete(Unit) }
        presentation = AppConnectionPresentation(ConnectionUiState.Ready)
        settle()
        capture("17-names-connected-page", "Go to Home")
        closeSwitchPage()
        compose.onNodeWithTag("settings-row-$EDIT_CONNECTION").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionDown); press(Key.DirectionCenter)
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        capture("19-name-screen", "Name field")
    }

    @Test fun addAndNameReturnsToTheNamedConnection() {
        runBlocking { store.addPassword("tvheadend.home.arpa", 9982, "anna", "fixture-secret", "Living room", activate = true) }
        settings(rail = true)
        repeat(1) { press(Key.DirectionDown) }
        compose.onNodeWithTag("settings-row-$ADD_SERVER").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithTag("setup-find-server").assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)
        type("setup-host", "tvheadend.home.arpa")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        type("setup-username", "kids")
        type("setup-password", "fixture-secret")
        compose.onNodeWithTag("setup-continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        compose.runOnIdle { session.publish(ready()) }
        settle()
        compose.onNodeWithTag("setup-start-watching").assertIsFocused()
        compose.onNodeWithTag("setup-add-name").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_000)
        press(Key.DirectionRight)
        compose.onNodeWithTag("setup-add-name").assertIsFocused()
        press(Key.DirectionCenter)
        settle()
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertExists()
        compose.waitForIdle()
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        type(SERVER_NAME_FIELD, "Kids")
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag(SERVER_NAME_SAVE).performSemanticsAction(SemanticsActions.RequestFocus)
        press(Key.DirectionCenter)
        settle()
        assertTrue((owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.any { it.label == "Kids" } == true)
        compose.waitForIdle()
        compose.onNodeWithTag(row(2)).assertIsFocused()
        compose.mainClock.advanceTimeBy(1_000)
    }

    /**
     * The keyboard's own Back hides it without the app seeing the key. That ends the edit: the next
     * Down leaves the field for Save, and a Back after it leaves the page instead of the field.
     */
    @Test fun nameFieldLeavesEditingWhenTheKeyboardHidesItself() {
        stored(); settings()
        press(Key.DirectionCenter); press(Key.DirectionDown); press(Key.DirectionCenter)
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(hasSetTextAction()).assertExists()
        showKeyboard(true)
        showKeyboard(false)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        press(Key.DirectionDown)
        compose.onNodeWithTag(SERVER_NAME_SAVE).assertIsFocused()
        press(Key.Back)
        compose.onNodeWithTag(SERVER_NAME_FIELD).assertDoesNotExist()
        compose.onNodeWithTag("settings-row-$SERVER_NAME").assertIsFocused()
    }

    /** Robolectric has no keyboard: the window reports the insets the keyboard would. */
    private fun showKeyboard(visible: Boolean) {
        compose.runOnIdle {
            ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, if (visible) 300 else 0))
                .setVisible(WindowInsetsCompat.Type.ime(), visible).build())
        }
        settle()
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

    private fun capture(name: String, focus: String, settleBeforeCapture: Boolean = true) {
        if (settleBeforeCapture) compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/server-profile-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.txt").writeText("canvas=960x540dp; locale=en; fontScale=1.0; focus=$focus; " +
                "Settings beside the SideRail; fictional servers and users; production notice host")
            bitmap.recycle()
        }
    }

    private fun press(key: Key) {
        compose.pressKey(key)
        settle()
    }

    private fun settle() {
        scheduler.runCurrent()
        compose.waitForIdle()
    }
}
