package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettings
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupBootstrapCoordinatorTest {
    @Test
    fun initialState_isResolvingLocal() {
        val fixture = fixture()

        assertEquals(MainStartupState.ResolvingLocal, fixture.coordinator.state.value)
        assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
    }

    @Test
    fun configuredAutoplay_readsSnapshotsOnceAndRequestsOnce() = runBlocking {
        val fixture = fixture(autoStartPlayback = true)

        fixture.coordinator.bootstrap()
        val pending = fixture.requests.state.value
        fixture.coordinator.bootstrap()

        assertEquals(1, fixture.serverReads)
        assertEquals(1, fixture.uiReads)
        assertEquals(1, fixture.simpleTvReads)
        assertEquals(0, fixture.simpleTvStarts)
        assertEquals(pending, fixture.requests.state.value)
        assertTrue(pending is ApplianceLaunchState.Pending)
        assertEquals(
            MainStartupState.Ready(
                server = configuredServer,
                autoStartPlayback = true,
                startSimpleTv = false,
            ),
            fixture.coordinator.state.value,
        )
    }

    @Test
    fun restoredPendingAndEnabledAutoplay_coalesceAsOneGeneration() = runBlocking {
        val requests = ApplianceLaunchRequests(restoredRequestId = 52L)
        val fixture = fixture(
            autoStartPlayback = true,
            requests = requests,
        )

        fixture.coordinator.bootstrap()

        val pending = requests.state.value as ApplianceLaunchState.Pending
        assertEquals(52L, pending.request.id)
        assertEquals(1, fixture.serverReads)
        assertEquals(1, fixture.uiReads)
        assertEquals(1, fixture.simpleTvReads)
    }

    @Test
    fun restoredExplicitPending_survivesDisabledAutoplay() = runBlocking {
        val requests = ApplianceLaunchRequests(restoredRequestId = 61L)
        val fixture = fixture(
            autoStartPlayback = false,
            requests = requests,
        )

        fixture.coordinator.bootstrap()

        assertEquals(
            ApplianceLaunchState.Pending(ApplianceLaunchRequest(61L)),
            requests.state.value,
        )
    }

    @Test
    fun configuredWithAutoplayDisabled_doesNotRequestPlayback() = runBlocking {
        val fixture = fixture(autoStartPlayback = false)

        fixture.coordinator.bootstrap()

        assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
        assertEquals(
            MainStartupState.Ready(
                server = configuredServer,
                autoStartPlayback = false,
                startSimpleTv = false,
            ),
            fixture.coordinator.state.value,
        )
    }

    @Test
    fun simpleTv_startsBeforeReadyAndCreatesOneRequest() = runBlocking {
        lateinit var fixture: Fixture
        fixture = fixture(
            autoStartPlayback = false,
            simpleTvEnabled = true,
            onSimpleTvStart = {
                assertEquals(
                    MainStartupState.ResolvingLocal,
                    fixture.coordinator.state.value,
                )
                assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
            },
        )

        fixture.coordinator.bootstrap()

        assertEquals(1, fixture.simpleTvStarts)
        assertTrue(fixture.requests.state.value is ApplianceLaunchState.Pending)
        assertEquals(
            MainStartupState.Ready(
                server = configuredServer,
                autoStartPlayback = false,
                startSimpleTv = true,
            ),
            fixture.coordinator.state.value,
        )
    }

    @Test
    fun successfulBootstrap_marksRequestCreationHandledBeforeReady() = runBlocking {
        lateinit var fixture: Fixture
        fixture = fixture(
            autoStartPlayback = true,
            onBootstrapHandled = {
                assertEquals(
                    MainStartupState.ResolvingLocal,
                    fixture.coordinator.state.value,
                )
                assertTrue(fixture.requests.state.value is ApplianceLaunchState.Pending)
            },
        )

        fixture.coordinator.bootstrap()

        assertEquals(1, fixture.bootstrapHandledCalls)
        assertTrue(fixture.coordinator.state.value is MainStartupState.Ready)
    }

    @Test
    fun handledSimpleTvRestoration_restartsSessionBeforeReadyWithoutCreatingRequest() =
        runBlocking {
            lateinit var fixture: Fixture
            fixture = fixture(
                simpleTvEnabled = true,
                createStartupRequest = false,
                onSimpleTvStart = {
                    assertEquals(
                        MainStartupState.ResolvingLocal,
                        fixture.coordinator.state.value,
                    )
                    assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
                },
            )

            fixture.coordinator.bootstrap()

            assertEquals(1, fixture.simpleTvStarts)
            assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
            assertEquals(
                MainStartupState.Ready(
                    server = configuredServer,
                    autoStartPlayback = false,
                    startSimpleTv = true,
                ),
                fixture.coordinator.state.value,
            )
        }

    @Test
    fun unconfiguredServer_neitherStartsSimpleTvNorRequestsPlayback() = runBlocking {
        val server = ServerSettings(host = "")
        val fixture = fixture(
            server = server,
            autoStartPlayback = true,
            simpleTvEnabled = true,
        )

        fixture.coordinator.bootstrap()

        assertEquals(0, fixture.simpleTvStarts)
        assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
        assertEquals(
            MainStartupState.Ready(
                server = server,
                autoStartPlayback = false,
                startSimpleTv = false,
            ),
            fixture.coordinator.state.value,
        )
    }

    @Test
    fun runtimeServerUpdate_changesPresentationWithoutRerunningStartup() = runBlocking {
        val initialServer = ServerSettings(host = "")
        val updatedServer = ServerSettings(host = "configured.invalid")
        val fixture = fixture(
            server = initialServer,
            autoStartPlayback = true,
            simpleTvEnabled = true,
        )
        fixture.coordinator.bootstrap()
        val ready = fixture.coordinator.state.value as MainStartupState.Ready

        assertEquals(initialServer, ready.serverSettingsForRuntime(runtimeServer = null))
        assertEquals(updatedServer, ready.serverSettingsForRuntime(updatedServer))
        assertEquals(initialServer, ready.server)
        assertEquals(false, ready.autoStartPlayback)
        assertEquals(false, ready.startSimpleTv)
        assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
        assertEquals(1, fixture.serverReads)
        assertEquals(1, fixture.uiReads)
        assertEquals(1, fixture.simpleTvReads)
        assertEquals(0, fixture.simpleTvStarts)
    }

    @Test
    fun repeatedBootstrapAfterCancellation_doesNotRearmStartup() = runBlocking {
        val fixture = fixture(autoStartPlayback = true)
        fixture.coordinator.bootstrap()
        val pending = fixture.requests.state.value as ApplianceLaunchState.Pending
        assertTrue(fixture.requests.cancel(pending))

        fixture.coordinator.bootstrap()

        assertEquals(ApplianceLaunchState.Idle, fixture.requests.state.value)
        assertEquals(1, fixture.serverReads)
        assertEquals(1, fixture.uiReads)
        assertEquals(1, fixture.simpleTvReads)
    }

    @Test
    fun handledBootstrapWithNoRetainedRequest_doesNotRearmAfterCancelOrCommit() = runBlocking {
        suspend fun restoreAfterTerminal(completePlayer: Boolean): ApplianceLaunchState {
            var retainedRequestId: Long? = null
            var bootstrapHandled = false
            val originalRequests = ApplianceLaunchRequests(
                onRetainedRequestIdChanged = { retainedRequestId = it },
            )
            val original = fixture(
                autoStartPlayback = true,
                requests = originalRequests,
                onBootstrapHandled = { bootstrapHandled = true },
            )
            original.coordinator.bootstrap()
            val pending = originalRequests.state.value as ApplianceLaunchState.Pending
            if (completePlayer) {
                val target = requireNotNull(
                    originalRequests.resolve(
                        request = pending.request,
                        readiness = CurrentChannelReadiness.Ready(
                            listOf(
                                ChannelUi(
                                    id = 20,
                                    name = "Twenty",
                                    number = null,
                                    icon = null,
                                )
                            ),
                        ),
                        persistedId = 20,
                    )
                )
                assertTrue(
                    originalRequests.completePlayerVisibility(
                        target = target,
                        channelId = target.channelId,
                        serviceId = target.serviceId,
                        channelName = target.channelName,
                    )
                )
            } else {
                assertTrue(originalRequests.cancel(pending))
            }
            assertTrue(bootstrapHandled)
            assertEquals(null, retainedRequestId)

            val restoredRequests = ApplianceLaunchRequests(
                restoredRequestId = retainedRequestId,
                onRetainedRequestIdChanged = { retainedRequestId = it },
            )
            val restored = fixture(
                autoStartPlayback = true,
                requests = restoredRequests,
                createStartupRequest = !bootstrapHandled,
            )
            restored.coordinator.bootstrap()
            return restoredRequests.state.value
        }

        assertEquals(
            ApplianceLaunchState.Idle,
            restoreAfterTerminal(completePlayer = false),
        )
        assertEquals(
            ApplianceLaunchState.Idle,
            restoreAfterTerminal(completePlayer = true),
        )
    }

    @Test
    fun concurrentBootstrapCalls_serializeOneStartupSequence() = runBlocking {
        val firstReadStarted = CompletableDeferred<Unit>()
        val allowFirstRead = CompletableDeferred<Unit>()
        val requests = ApplianceLaunchRequests()
        var serverReads = 0
        var uiReads = 0
        var simpleTvReads = 0
        var simpleTvStarts = 0
        val coordinator = StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = {
                serverReads += 1
                firstReadStarted.complete(Unit)
                allowFirstRead.await()
                configuredServer
            },
            loadUiSettings = {
                uiReads += 1
                UiSettings(autoStartPlayback = false)
            },
            loadSimpleTvSettings = {
                simpleTvReads += 1
                SimpleTvSettings(enabled = true)
            },
            startSimpleTvSession = { simpleTvStarts += 1 },
        )

        val first = launch { coordinator.bootstrap() }
        firstReadStarted.await()
        val second = launch { coordinator.bootstrap() }
        allowFirstRead.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, serverReads)
        assertEquals(1, uiReads)
        assertEquals(1, simpleTvReads)
        assertEquals(1, simpleTvStarts)
        assertTrue(requests.state.value is ApplianceLaunchState.Pending)
    }

    @Test
    fun retainedCoordinator_keepsTheOwnedRequestInstanceAndState() = runBlocking {
        val fixture = fixture(autoStartPlayback = true)
        fixture.coordinator.bootstrap()
        val pending = fixture.requests.state.value

        assertSame(fixture.requests, fixture.coordinator.applianceLaunchRequests)
        assertEquals(pending, fixture.coordinator.applianceLaunchRequests.state.value)
        assertEquals(pending, fixture.requests.state.value)
    }

    @Test
    fun bootstrapCancellation_propagatesWithoutPublishingReady() {
        val cancellation = CancellationException("cancel bootstrap")
        val requests = ApplianceLaunchRequests()
        var bootstrapHandledCalls = 0
        val coordinator = StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = { throw cancellation },
            loadUiSettings = { UiSettings() },
            loadSimpleTvSettings = { SimpleTvSettings() },
            startSimpleTvSession = {},
            onStartupRequestCreationHandled = { bootstrapHandledCalls += 1 },
        )

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.bootstrap() }
        }

        assertSame(cancellation, thrown)
        assertEquals(MainStartupState.ResolvingLocal, coordinator.state.value)
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        assertEquals(0, bootstrapHandledCalls)
    }

    @Test
    fun bootstrapFailure_doesNotMarkRequestCreationHandledOrPublishReady() {
        val failure = IllegalStateException("settings unavailable")
        val requests = ApplianceLaunchRequests()
        var bootstrapHandledCalls = 0
        val coordinator = StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = { configuredServer },
            loadUiSettings = { throw failure },
            loadSimpleTvSettings = { SimpleTvSettings() },
            startSimpleTvSession = {},
            onStartupRequestCreationHandled = { bootstrapHandledCalls += 1 },
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.bootstrap() }
        }

        assertSame(failure, thrown)
        assertEquals(MainStartupState.ResolvingLocal, coordinator.state.value)
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        assertEquals(0, bootstrapHandledCalls)
    }

    private fun fixture(
        server: ServerSettings = configuredServer,
        autoStartPlayback: Boolean = false,
        simpleTvEnabled: Boolean = false,
        onSimpleTvStart: () -> Unit = {},
        requests: ApplianceLaunchRequests = ApplianceLaunchRequests(),
        createStartupRequest: Boolean = true,
        onBootstrapHandled: () -> Unit = {},
    ): Fixture = Fixture(
        server = server,
        autoStartPlayback = autoStartPlayback,
        simpleTvEnabled = simpleTvEnabled,
        onSimpleTvStart = onSimpleTvStart,
        requests = requests,
        createStartupRequest = createStartupRequest,
        onBootstrapHandled = onBootstrapHandled,
    )

    private class Fixture(
        server: ServerSettings,
        autoStartPlayback: Boolean,
        simpleTvEnabled: Boolean,
        private val onSimpleTvStart: () -> Unit,
        val requests: ApplianceLaunchRequests,
        createStartupRequest: Boolean,
        private val onBootstrapHandled: () -> Unit,
    ) {
        var serverReads = 0
        var uiReads = 0
        var simpleTvReads = 0
        var simpleTvStarts = 0
        var bootstrapHandledCalls = 0

        val coordinator = StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = {
                serverReads += 1
                server
            },
            loadUiSettings = {
                uiReads += 1
                UiSettings(autoStartPlayback = autoStartPlayback)
            },
            loadSimpleTvSettings = {
                simpleTvReads += 1
                SimpleTvSettings(enabled = simpleTvEnabled)
            },
            startSimpleTvSession = {
                simpleTvStarts += 1
                onSimpleTvStart()
            },
            createStartupRequest = createStartupRequest,
            onStartupRequestCreationHandled = {
                bootstrapHandledCalls += 1
                onBootstrapHandled()
            },
        )
    }

    private companion object {
        val configuredServer = ServerSettings(host = "tvh.invalid")
    }
}
