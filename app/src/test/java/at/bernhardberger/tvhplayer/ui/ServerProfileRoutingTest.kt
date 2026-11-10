@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
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
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class ServerProfileRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Saved channels, so Home shows its rows and the rail at once.
    private val session = FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Ready(at.bernhardberger.tvheadend.sdk.core.ServerCapabilities.create(
            streaming = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED,
            dvrWrite = at.bernhardberger.tvheadend.sdk.core.CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Name 1", number = 1)))),
    )).apply { scriptLivePlaybackSuccess() }
    private val store = FakeServerProfileStore()
    private var removalGate: CompletableDeferred<Unit>? = null
    private var connectionGate: CompletableDeferred<Unit>? = null
    private var trialStarted = false
    private val launchRequests = ApplianceLaunchRequests()
    private lateinit var player: ExoPlayer
    private lateinit var owner: AppProfileOwner
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var view: android.view.View

    @After fun after() {
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    private fun app(startup: ServerSettings) {
        val settings = PlayerSettingsStore(InMemoryPreferencesDataStore())
        val gatedStore = object : ServerProfileStore by store {
            override suspend fun remove(id: ServerProfileId): ServerProfileWriteResult {
                removalGate?.await()
                return store.remove(id)
            }
        }
        val initialObservation = session.observation.value
        val connectingSession = object : TvheadendSession by session {
            override suspend fun disconnect() {
                session.disconnect()
                session.publish(SessionObservation.create(sessionState = SessionState.Disconnected))
            }
            override suspend fun connect(profile: at.bernhardberger.tvheadend.sdk.core.ServerProfile): at.bernhardberger.tvheadend.sdk.core.SessionCommandResult {
                connectionGate?.let { connectionGate = null; trialStarted = true; it.await() }
                val result = session.connect(profile)
                session.publish(initialObservation)
                return result
            }
        }
        owner = AppProfileOwner(connectingSession, gatedStore, settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { owner.run() }
        compose.waitUntil(10_000) { owner.profiles.value != null && owner.serverProfile.value != null }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(owner.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, owner, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = notices)
        val dependencies = module {
            single<TvheadendSession> { session }
            single { runtime }
            single { owner }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryPreferencesDataStore()) }
            single { UiSettingsStore(InMemoryPreferencesDataStore()) }
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
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    view = androidx.compose.ui.platform.LocalView.current
                    val profile by owner.serverProfile.collectAsStateWithLifecycle()
                    val profiles by owner.profiles.collectAsStateWithLifecycle()
                    AppRoot(
                        startupState = MainStartupState.Ready(startup, autoStartPlayback = launchRequests.state.value != ApplianceLaunchState.Idle),
                        runtimeServerSettings = profile?.toServerSettings(),
                        applianceLaunchRequests = launchRequests,
                        onPlayerVisibilityChanged = {},
                        onRequestExit = {},
                        serversStored = profiles.serversStored(),
                    )
                }
            }
        }
        settle()
    }

    private fun storedWithNoneInUse() = runBlocking {
        store.addAnonymous("living.example.invalid", 9982, "Living room", activate = false)
        store.addAnonymous("cabin.example.invalid", 9982, null, activate = false)
    }

    @Test fun productionSettingsBackCancelsThePendingConnectionRatherThanLeavingTheList() {
        storedWithNoneInUse()
        app(ServerSettings())
        val first = runBlocking { (store.profiles() as ServerProfileListResult.Available).profiles.first().id }
        click("settings-row-${SettingsSection.CONNECTION.name}")
        connectionGate = CompletableDeferred()
        click("settings-row-${serverProfileRowId(first)}")
        click("settings-row-${at.bernhardberger.tvhplayer.ui.screens.settings.SERVER_USE}")
        compose.waitUntil(10_000) { trialStarted }
        compose.onRoot().performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Back) }
        compose.waitUntil(10_000) { !owner.connectionChanges.isLocked }
        settle()
        compose.onNodeWithTag("settings-row-${serverProfileRowId(first)}").assertIsFocused()
        assertNull((owner.profiles.value as ServerProfileListResult.Available).activeId)
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
    }

    private fun assertSettingsConnection() {
        compose.onNodeWithTag("setup-welcome-continue").assertDoesNotExist()
        compose.onNodeWithTag("settings-row-${SettingsSection.CONNECTION.name}").assertIsFocused()
        compose.onNode(hasText("No connection in use. Choose one to watch TV again."), useUnmergedTree = true).assertExists()
        assertNull(runtime.activeTarget.value)
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
        compose.onNodeWithText("No connection in use. Choose one to watch TV again.").assertExists()
    }

    private fun click(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    private fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
            compose.runOnIdle {
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                bitmap.recycle()
            }
        }
    }

}
