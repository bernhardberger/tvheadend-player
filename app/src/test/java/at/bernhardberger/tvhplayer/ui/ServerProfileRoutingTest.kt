@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testutil.leaveTouchModeBeforeComposing
import at.bernhardberger.tvhplayer.ui.screens.settings.REMOVE_CONFIRM
import at.bernhardberger.tvhplayer.ui.screens.settings.SERVER_REMOVE
import at.bernhardberger.tvhplayer.ui.screens.settings.noServerInUse
import at.bernhardberger.tvhplayer.ui.screens.settings.serversStored
import at.bernhardberger.tvheadend.sdk.core.ServerProfileStore
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult
import kotlinx.coroutines.CompletableDeferred
import at.bernhardberger.tvhplayer.ui.screens.settings.serverProfileRowId
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the app goes from the stored servers, wired as MainActivity wires AppRoot: with servers
 * but none in use it opens Settings and never Welcome, launch and appliance entries open Settings
 * instead of playing, and removing the last server returns to Welcome. Hosts are fictional.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ServerProfileRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Saved channels, so Home shows its rows and the rail at once.
    private val session = FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Connecting,
        channelState = ChannelRepositoryState.Stale(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Name 1", number = 1)))),
    )).apply { scriptLivePlaybackSuccess() }
    private val store = FakeServerProfileStore()
    private var removalGate: CompletableDeferred<Unit>? = null
    private var selectionGate: CompletableDeferred<Unit>? = null
    private var selectionStarted = false
    private var selectionThrows = false
    private lateinit var startupVm: at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel
    private lateinit var appContent: @Composable () -> Unit
    private val startupModels = androidx.lifecycle.ViewModelStore()
    private val launchRequests = ApplianceLaunchRequests()
    private lateinit var player: ExoPlayer
    private lateinit var owner: AppProfileOwner
    private lateinit var runtime: AppPlaybackRuntime

    @After fun after() {
        startupModels.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    private fun app(startup: ServerSettings, picker: Boolean = false) {
        val settings = PlayerSettingsStore(InMemoryData())
        val gatedStore = object : ServerProfileStore by store {
            override suspend fun setActive(id: ServerProfileId): ServerProfileWriteResult {
                selectionStarted = true
                selectionGate?.await()
                if (selectionThrows) error("Synthetic selection failure")
                return store.setActive(id)
            }
            override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult {
                removalGate?.await()
                return store.remove(id)
            }
        }
        owner = AppProfileOwner(session, gatedStore, settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { owner.run() }
        compose.waitUntil(10_000) { owner.profiles.value != null && owner.serverProfile.value != null }
        if (picker) {
            runBlocking { owner.setAskAtStartup(true) }
            startupVm = at.bernhardberger.tvhplayer.viewmodels.MainStartupViewModel(owner,
                UiSettingsStore(InMemoryData()), androidx.lifecycle.SavedStateHandle(), applianceBuild = false)
            startupModels.put("startup", startupVm)
        }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, owner, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(owner.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val dependencies = module {
            single<TvheadendSession> { session }
            single { runtime }
            single { owner }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryData()) }
            single { UiSettingsStore(InMemoryData()) }
            single { LastPlayedChannelStore(context) }
            single { ChannelSelectionStore() }
            single { GuidePositionStore() }
            single { notices }
            single { ConnectionNoticeSource(session.observation, owner.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get()) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) {} }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        compose.leaveTouchModeBeforeComposing()
        appContent = {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    val profile by owner.serverProfile.collectAsStateWithLifecycle()
                    val profiles by owner.profiles.collectAsStateWithLifecycle()
                    val pickerVisible = if (picker) startupVm.startupPickerVisible.collectAsStateWithLifecycle().value else false
                    if (pickerVisible) at.bernhardberger.tvhplayer.ui.screens.StartupServerPicker(
                        startupVm::finishStartupPicker, startupVm::openStartupProfile,
                        startupVm.startupSelectionPending.collectAsStateWithLifecycle().value,
                        onSelect = { startupVm.selectStartupServer(it, notices) }) else
                    AppRoot(
                        startupState = if (picker) startupVm.state.collectAsStateWithLifecycle().value else MainStartupState.Ready(startup, autoStartPlayback = launchRequests.state.value != ApplianceLaunchState.Idle),
                        runtimeServerSettings = profile?.toServerSettings(),
                        applianceLaunchRequests = launchRequests,
                        onPlayerVisibilityChanged = {},
                        onRequestExit = {},
                        serversStored = profiles.serversStored(),
                        initialSettingsProfileId = if (picker) startupVm.startupSettingsProfileId else null,
                    )
                }
            }
        }
        compose.setContent(appContent)
        settle()
    }

    private fun storedWithNoneInUse() = runBlocking {
        store.addAnonymous("living.example.invalid", 9982, "Living room", activate = false)
        store.addAnonymous("cabin.example.invalid", 9982, null, activate = false)
    }

    private fun coldPicker(): Pair<ServerProfileId, ServerProfileId> {
        val ids = runBlocking {
            val first = store.addAnonymous("living.example.invalid", 9982, "Living room", true) as ServerProfileWriteResult.Applied
            val second = store.addAnonymous("cabin.example.invalid", 9982, "Cabin", false) as ServerProfileWriteResult.Applied
            first.id to second.id
        }
        app(ServerSettings(host = "living.example.invalid"), picker = true)
        compose.waitUntil(10_000) { startupVm.startupPickerVisible.value }
        settle()
        return ids
    }

    @Test fun startupBackWaitsForTheAdmittedSelectionBeforeBootstrap() {
        val (_, second) = coldPicker()
        selectionGate = CompletableDeferred()
        click("server-picker-${second.value}")
        compose.waitUntil(10_000) { selectionStarted }
        press(androidx.compose.ui.input.key.Key.Back)
        assertEquals(true, startupVm.startupPickerVisible.value)
        assertEquals(MainStartupState.ResolvingLocal, startupVm.state.value)
        assertNull(runtime.activeTarget.value)
        selectionGate!!.complete(Unit)
        compose.waitUntil(10_000) { startupVm.state.value is MainStartupState.Ready }
        assertEquals("cabin.example.invalid", (startupVm.state.value as MainStartupState.Ready).server.host)
    }

    @Test fun recreatedStartupPickerBackWaitsForTheRetainedSelection() {
        val (_, second) = coldPicker()
        selectionGate = CompletableDeferred()
        click("server-picker-${second.value}")
        compose.waitUntil(10_000) { selectionStarted }
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity -> activity.setContent(content = appContent) }
        settle()
        press(androidx.compose.ui.input.key.Key.Back)
        assertEquals(true, startupVm.startupSelectionPending.value)
        assertEquals(true, startupVm.startupPickerVisible.value)
        assertEquals(MainStartupState.ResolvingLocal, startupVm.state.value)
        selectionGate!!.complete(Unit)
        compose.waitUntil(10_000) { startupVm.state.value is MainStartupState.Ready }
        assertEquals("cabin.example.invalid", (startupVm.state.value as MainStartupState.Ready).server.host)
    }

    @Test fun pendingStartupSelectionBlocksAddAndDefersDuplicateCompletion() {
        val (first, second) = coldPicker()
        selectionGate = CompletableDeferred()
        click("server-picker-${second.value}")
        compose.waitUntil(10_000) { selectionStarted }
        // Even a semantic click cannot start setup while the retained decision is pending.
        compose.onNodeWithTag("server-picker-add").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithTag("setup-host").assertDoesNotExist()
        // All completion paths are fenced too, including a duplicate callback already in flight.
        compose.runOnIdle { startupVm.openStartupProfile(first.value) }
        assertEquals(true, startupVm.startupPickerVisible.value)
        assertEquals(MainStartupState.ResolvingLocal, startupVm.state.value)
        selectionGate!!.complete(Unit)
        compose.waitUntil(10_000) { startupVm.state.value is MainStartupState.Ready }
        assertEquals("cabin.example.invalid", (startupVm.state.value as MainStartupState.Ready).server.host)
        assertEquals(first.value, startupVm.startupSettingsProfileId)
        assertEquals(false, (startupVm.state.value as MainStartupState.Ready).autoStartPlayback)
    }

    @Test fun startupSelectionExceptionIsShownWhileThePickerRemainsOpen() {
        val (_, second) = coldPicker()
        selectionThrows = true
        click("server-picker-${second.value}")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("app-notice").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("The server could not be selected. Try again.").assertIsDisplayed()
        assertEquals(true, startupVm.startupPickerVisible.value)
        press(androidx.compose.ui.input.key.Key.Back)
        compose.waitUntil(10_000) { !startupVm.startupPickerVisible.value }
    }

    @Test fun startupDuplicateAddReachesVisibleSettingsInsteadOfWaitingForHome() {
        val (first, _) = coldPicker()
        click("server-picker-add")
        compose.onNodeWithTag("setup-host").performSemanticsAction(SemanticsActions.RequestFocus)
        press(androidx.compose.ui.input.key.Key.DirectionCenter)
        compose.onNodeWithTag("setup-host").performTextReplacement("living.example.invalid")
        press(androidx.compose.ui.input.key.Key.Back)
        click("setup-continue")
        click("setup-continue")
        compose.waitUntil(10_000) { !startupVm.startupPickerVisible.value }
        settle()
        compose.onNodeWithTag("settings-row-$SERVER_REMOVE").assertIsDisplayed()
        assertEquals(first.value, startupVm.startupSettingsProfileId)
        assertNull(runtime.activeTarget.value)
    }

    @Test fun switchingAwayFromAHiddenInUseServerKeepsTheDestinationUntilChannels() {
        val (first, second) = runBlocking {
            val a = store.addAnonymous("living.example.invalid", 9982, "Living room", true) as ServerProfileWriteResult.Applied
            val b = store.addAnonymous("cabin.example.invalid", 9982, "Cabin", false) as ServerProfileWriteResult.Applied
            a.id to b.id
        }
        app(ServerSettings(host = "living.example.invalid"))
        runBlocking { owner.setShowInSwitcher(first, false) }
        click("nav-switch-server")
        press(androidx.compose.ui.input.key.Key.DirectionRight)
        click("server-picker-${second.value}")
        compose.waitUntil(10_000) { (owner.profiles.value as? ServerProfileListResult.Available)?.activeId == second }
        settle()
        compose.onNodeWithTag("nav-channels").assertIsSelected()
        compose.onNodeWithText("Which server do you want to use?").assertDoesNotExist()
    }

    private fun assertSettingsConnection() {
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-${SettingsSection.CONNECTION.name}").assertIsFocused()
        compose.onNode(hasText("No server in use. Choose one to watch TV again."), useUnmergedTree = true).assertExists()
        assertNull(runtime.activeTarget.value)
    }

    @Test fun serversWithNoneInUseOpenSettingsConnectionNotWelcome() {
        storedWithNoneInUse()
        app(ServerSettings())
        assertSettingsConnection()
    }

    @Test fun launchAndApplianceEntriesWithNoneInUseOpenSettingsConnectionInsteadOfPlaying() {
        storedWithNoneInUse()
        // Launch as the appliance does: a pending startup request that would autoplay.
        launchRequests.request()
        app(ServerSettings())
        compose.waitUntil(10_000) { launchRequests.state.value == ApplianceLaunchState.Idle }
        settle()
        assertSettingsConnection()
        // HOME, GUIDE or wake later in the same process.
        compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.runOnIdle { launchRequests.request() }
        compose.waitUntil(10_000) { launchRequests.state.value == ApplianceLaunchState.Idle }
        settle()
        assertSettingsConnection()
    }

    @Test fun removingTheLastServerReturnsToWelcome() {
        val id = runBlocking {
            (store.addAnonymous("living.example.invalid", 9982, "Living room", activate = true)
                as at.bernhardberger.tvheadend.sdk.core.ServerProfileWriteResult.Applied).id
        }
        app(ServerSettings(host = "living.example.invalid"))
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("nav-settings")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        click("settings-row-${SettingsSection.CONNECTION.name}")
        click("settings-row-${serverProfileRowId(id)}")
        click("settings-row-$SERVER_REMOVE")
        click(REMOVE_CONFIRM)
        compose.waitUntil(10_000) { (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.isEmpty() == true }
        settle()
        compose.onNodeWithTag("setup-welcome-continue").assertIsFocused()
        assertEquals(false, owner.profiles.value.noServerInUse())
    }

    @Test fun removingTheActiveServerNeverShowsWelcomeWhileOtherServersRemain() {
        val id = runBlocking {
            val first = store.addAnonymous("living.example.invalid", 9982, "Living room", activate = true) as ServerProfileWriteResult.Applied
            store.addAnonymous("cabin.example.invalid", 9982, null, activate = false)
            first.id
        }
        app(ServerSettings(host = "living.example.invalid"))
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("nav-settings")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        click("settings-row-${SettingsSection.CONNECTION.name}")
        click("settings-row-${serverProfileRowId(id)}")
        removalGate = CompletableDeferred()
        click("settings-row-$SERVER_REMOVE")
        click(REMOVE_CONFIRM)
        compose.waitUntil(10_000) { owner.serverProfile.value == at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult.Missing }
        settle()
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").assertExists()
        compose.runOnIdle { removalGate!!.complete(Unit) }
        compose.waitUntil(10_000) { (owner.profiles.value as? ServerProfileListResult.Available)?.profiles?.size == 1 }
        settle()
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithText("No server in use. Choose one to watch TV again.").assertExists()
    }

    private fun click(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun press(key: androidx.compose.ui.input.key.Key) {
        compose.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        settle()
    }

    private fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
