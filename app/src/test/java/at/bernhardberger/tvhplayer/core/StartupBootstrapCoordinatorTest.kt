package at.bernhardberger.tvhplayer.core

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
        val coordinator = StartupBootstrapCoordinator(
            applianceLaunchRequests = requests,
            loadServerSettings = { throw cancellation },
            loadUiSettings = { UiSettings() },
            loadSimpleTvSettings = { SimpleTvSettings() },
            startSimpleTvSession = {},
        )

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.bootstrap() }
        }

        assertSame(cancellation, thrown)
        assertEquals(MainStartupState.ResolvingLocal, coordinator.state.value)
        assertEquals(ApplianceLaunchState.Idle, requests.state.value)
    }

    private fun fixture(
        server: ServerSettings = configuredServer,
        autoStartPlayback: Boolean = false,
        simpleTvEnabled: Boolean = false,
        onSimpleTvStart: () -> Unit = {},
    ): Fixture = Fixture(
        server = server,
        autoStartPlayback = autoStartPlayback,
        simpleTvEnabled = simpleTvEnabled,
        onSimpleTvStart = onSimpleTvStart,
    )

    private class Fixture(
        server: ServerSettings,
        autoStartPlayback: Boolean,
        simpleTvEnabled: Boolean,
        private val onSimpleTvStart: () -> Unit,
    ) {
        val requests = ApplianceLaunchRequests()
        var serverReads = 0
        var uiReads = 0
        var simpleTvReads = 0
        var simpleTvStarts = 0

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
        )
    }

    private companion object {
        val configuredServer = ServerSettings(host = "tvh.invalid")
    }
}
