package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionCommandResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSetupStateTest {
    private val session = FakeTvheadendSession()
    private val profiles = FakeProfiles()
    private val form = ConnectionFormState()
    private val discovery = MutableStateFlow(discovering())

    @Test
    fun automaticAndManualSearchNeverValidateTheAddress() = runTest {
        val state = setup()
        state.onServerOpened()
        assertEquals(1, state.searchRequest)
        assertFalse(state.hostError)
        backgroundScope.launch { state.search(state.searchRequest) }
        runCurrent()
        discovery.value = discovering(LIVING_ROOM)
        runCurrent()
        assertFalse(state.hostError)
        discovery.value = discovering()
        advanceTimeBy(DiscoveryWindow + 1.milliseconds)
        runCurrent()
        assertFalse(state.searching)
        assertFalse(state.hostError)
        state.findServer()
        assertFalse(state.hostError)
        state.continueFromAddress()
        assertTrue(state.hostError)
    }

    @Test
    fun staleSearchCannotStartOrClearTheCurrentSearch() = runTest {
        val state = setup()
        state.onServerOpened()
        val firstRequest = state.searchRequest
        val first = backgroundScope.launch { state.search(firstRequest) }
        runCurrent()
        state.onServerOpened()
        val currentRequest = state.searchRequest
        first.cancel()
        runCurrent()
        assertTrue(state.searching)
        // The stale effect must return without collecting discovery or changing the busy state.
        state.search(firstRequest)
        assertTrue(state.searching)
        assertTrue(state.servers.isEmpty())
        val current = backgroundScope.launch { state.search(currentRequest) }
        discovery.value = discovering(LIVING_ROOM)
        runCurrent()
        assertEquals(listOf(LIVING_ROOM), state.servers)
        current.cancel()
        runCurrent()
        assertFalse(state.searching)
    }

    @Test
    fun openingTheEditorStartsDiscoveryWithoutChangingTheSavedFields() = runTest {
        val state = edit()
        val savedHost = form.host
        val savedPort = form.port
        state.onServerOpened()
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
        assertEquals(savedHost, form.host)
        assertEquals(savedPort, form.port)
    }

    @Test
    fun addressFocusesTheFirstInvalidFieldBeforeGoingOn() = runTest {
        val state = setup()
        state.enterAddress()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.SEARCH, state.addressFocus)
        assertFalse("Errors wait for the first attempt", state.hostError)

        state.continueFromAddress()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertTrue(state.hostError)
        assertEquals(AddressFocus.HOST, state.addressFocus)

        form.updateHost(HOST)
        form.updatePort("0")
        state.continueFromAddress()
        assertTrue(state.portError)
        assertEquals(AddressFocus.PORT, state.addressFocus)

        form.updatePort("9982")
        state.continueFromAddress()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SignInFocus.USERNAME, state.signInFocus)
    }

    @Test
    fun aHalfFilledSignInNeverReachesTheSession() = runTest {
        val state = signIn(username = USERNAME, password = "")
        state.connect()
        runCurrent()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertTrue(state.signInError)
        assertEquals(SignInFocus.PASSWORD, state.signInFocus)
        assertEquals(emptyList<FakeSessionCall>(), session.calls)
    }

    @Test
    fun aPassedTestStaysConnectedUntilStartWatchingSavesIt() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        assertEquals(SetupStep.CONNECTING, state.step)
        runCurrent()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        assertNull("Nothing is saved before the test passes", profiles.saved)

        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        assertEquals(SetupStep.CONNECTING, state.step)
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)

        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        assertEquals("", form.password)
    }

    @Test
    fun aFailedTestDisconnectsAndOffersItsOwnRecovery() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AUTHENTICATION_REJECTED)))
        runCurrent()
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(SetupFailure.SIGN_IN_REJECTED, state.failure)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())

        state.onAction(SetupAction.CHANGE_SIGN_IN)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SignInFocus.PASSWORD, state.signInFocus)
        assertEquals(USERNAME, form.username)
        assertNull(profiles.saved)
    }

    @Test
    fun continueAnywaySavesAnUnreachableServer() = runTest {
        val state = signIn(username = "", password = "")
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertEquals(SetupFailure.UNREACHABLE, state.failure)

        state.onAction(SetupAction.CONTINUE_ANYWAY)
        runCurrent()
        assertEquals("$HOST:9982", profiles.saved)
    }

    @Test
    fun backCancelsTheTestAndKeepsWhatWasEntered() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()

        assertTrue(state.back())
        runCurrent()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SignInFocus.CONNECT, state.signInFocus)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        session.publish(ready())
        runCurrent()
        assertEquals("A cancelled test never finishes", SetupStep.SIGN_IN, state.step)

        assertTrue(state.back())
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.CONTINUE, state.addressFocus)
        assertEquals(HOST, form.host)
        assertEquals(PASSWORD, form.password)
        assertTrue(state.back())
        assertEquals(SetupStep.WELCOME, state.step)
        assertFalse("Back on Welcome belongs to the caller", state.back())
    }

    @Test
    fun backFromDoneDropsTheTestedConnection() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)

        assertTrue(state.back())
        runCurrent()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertNull(profiles.saved)
    }

    @Test
    fun discoveryDoesNotStealFocusAndChoosingAServerAdvancesToAccount() = runTest {
        val state = setup()
        state.findServer()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertTrue(state.searching)
        assertNull("The address entry holds focus until a server answers", state.findFocus)
        backgroundScope.launch { state.search(state.searchRequest) }
        runCurrent()
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        discovery.value = discovering(LIVING_ROOM, OFFICE)
        runCurrent()
        assertEquals(listOf(LIVING_ROOM, OFFICE), state.servers)
        assertNull("Typing is not interrupted by discovery", state.findFocus)

        state.chooseServer(OFFICE)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(OFFICE.host, form.host)
        assertEquals("9983", form.port)
        assertEquals(SignInFocus.USERNAME, state.signInFocus)
        assertTrue(state.back())
        assertEquals(SetupStep.ADDRESS, state.step)
        assertTrue(state.back())
        assertEquals(SetupStep.WELCOME, state.step)
        assertFalse(state.back())
    }

    @Test
    fun aSearchThatFindsNothingEndsAfterItsWindowAndCanRetryInPlace() = runTest {
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search(state.searchRequest) }
        advanceTimeBy(DiscoveryWindow - 1.milliseconds)
        assertTrue(state.searching)
        advanceTimeBy(2.milliseconds)
        assertFalse(state.searching)
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        state.enterAddress()
        assertEquals(SetupStep.ADDRESS, state.step)
        val request = state.searchRequest
        state.findServer()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertTrue("An empty search starts again", state.searching)
        assertEquals(request + 1, state.searchRequest)
        assertNull(state.findFocus)
    }

    @Test
    fun anUnavailableSearchEndsAtOnce() = runTest {
        discovery.value = TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.PERMISSION_DENIED)
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search(state.searchRequest) }
        runCurrent()
        assertFalse(state.searching)
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)
    }

    @Test
    fun aServerThatLeavesHandsFocusOnButAMovedFocusStays() = runTest {
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search(state.searchRequest) }
        discovery.value = discovering(LIVING_ROOM, OFFICE)
        runCurrent()
        state.onFindFocused(OFFICE)

        discovery.value = discovering(LIVING_ROOM)
        runCurrent()
        assertEquals(LIVING_ROOM, state.findFocus)

        state.onFindFocused(null)
        discovery.value = discovering(LIVING_ROOM, OFFICE)
        runCurrent()
        assertNull("Focus on the address entry stays there", state.findFocus)
    }

    @Test
    fun editingUsesTheSameWizardAndDoesNotSaveDrafts() = runTest {
        val state = edit()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertFalse(state.back())
        form.updateHost(OFFICE.host)
        state.continueFromAddress()
        assertEquals(SetupStep.SIGN_IN, state.step)
        form.updatePassword("")
        state.continueFromSignIn()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SignInFocus.PASSWORD, state.signInFocus)
        form.updatePassword(PASSWORD)
        state.continueFromSignIn()
        assertEquals(SetupStep.CONNECTING, state.step)
        assertEquals(USERNAME, form.username)
        runCurrent()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        assertNull(profiles.saved)
    }

    @Test
    fun editSavesAPassedTestOnlyOnTheFinalAction() = runTest {
        val state = edit()
        state.connect()
        assertEquals(SetupStep.CONNECTING, state.step)
        runCurrent()
        assertNull("The current connection stays saved during the test", profiles.saved)
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        assertNull(profiles.saved)
        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        assertEquals(1, leaves)
        assertEquals("The passed connection is the app's now", 0, restores)
        assertEquals("", form.password)
    }

    @Test
    fun editRestoresTheSavedConnectionAfterAFailedTestAndCanSaveAnyway() = runTest {
        val state = edit()
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(1, restores)
        assertNull(profiles.saved)

        state.onAction(SetupAction.SAVE_ANYWAY)
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        assertEquals(1, leaves)
    }

    @Test
    fun editBackCancelsTheTestAndRestoresTheSavedConnection() = runTest {
        val state = edit()
        state.connect()
        runCurrent()
        assertTrue(state.back())
        assertEquals(SetupStep.SIGN_IN, state.step)
        runCurrent()
        assertEquals(1, restores)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertNull(profiles.saved)
        assertEquals(PASSWORD, form.password)
    }

    @Test
    fun editBackFromSuccessRestoresTheSavedConnectionWithoutSaving() = runTest {
        val state = edit()
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        assertNull(profiles.saved)
        assertTrue(state.back())
        runCurrent()
        assertEquals(1, restores)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertNull(profiles.saved)
        assertEquals(0, leaves)
    }

    @Test
    fun aCancelledQueuedTestNeverLetsTheNextOneSkipUnfinishedCleanup() = runTest {
        val restoring = CompletableDeferred<Unit>()
        val state = edit(
            restoreSaved = {
                restores++
                restoring.await()
                // The saved connection is back, as a real restore would publish it.
                session.publish(ready())
            },
        )
        state.connect()
        runCurrent()
        assertTrue("The first test is cancelled and cleans up", state.back())
        runCurrent()
        val firstTest = listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT)
        assertEquals(firstTest, session.calls)
        assertEquals(1, restores)

        state.connect()
        runCurrent()
        assertTrue("The second test is cancelled while it waits", state.back())
        runCurrent()
        state.connect()
        runCurrent()
        assertEquals("Nothing starts while the first test still restores", firstTest, session.calls)

        restoring.complete(Unit)
        runCurrent()
        assertEquals(firstTest + listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        assertEquals("The restored connection's Ready is not this test's", SetupStep.CONNECTING, state.step)
        assertNull(profiles.saved)

        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AUTHENTICATION_REJECTED)))
        runCurrent()
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(SetupFailure.SIGN_IN_REJECTED, state.failure)
        assertNull(profiles.saved)
    }

    @Test
    fun aFailureCancelledWhileItRestoresNeverShows() = runTest {
        val restoring = CompletableDeferred<Unit>()
        val state = edit(restoreSaved = { restores++; restoring.await() })
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertEquals("The failure waits for the saved connection", 1, restores)
        assertEquals(SetupStep.CONNECTING, state.step)

        assertTrue(state.back())
        runCurrent()
        restoring.complete(Unit)
        runCurrent()
        assertEquals("Back dismissed the test before its failure showed", SetupStep.SIGN_IN, state.step)
        assertNull(state.failure)
        assertEquals("The finished restore is not repeated", 1, restores)
    }

    @Test
    fun aFailureCancelledWhileItRestoresLeavesTheNextTestRunning() = runTest {
        val restoring = CompletableDeferred<Unit>()
        val state = edit(restoreSaved = { restores++; restoring.await() })
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertTrue(state.back())
        runCurrent()
        state.connect()
        runCurrent()
        val firstTest = listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT)
        assertEquals("The next test waits for the restore", firstTest, session.calls)

        restoring.complete(Unit)
        runCurrent()
        assertEquals("The old failure does not replace the running test", SetupStep.CONNECTING, state.step)
        assertNull(state.failure)
        assertEquals(1, restores)
        assertEquals(firstTest + listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)

        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        session.publish(ready())
        runCurrent()
        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
    }

    @Test
    fun aReadyFromBeforeTheTestNeverSavesIt() = runTest {
        session.publish(ready())
        val state = edit()
        state.connect()
        runCurrent()
        assertEquals(SetupStep.CONNECTING, state.step)
        assertNull("The saved connection's Ready is not this test's", profiles.saved)

        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        assertNull(profiles.saved)
        session.publish(ready())
        runCurrent()
        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
    }

    @Test
    fun tryAgainWaitsForItsOwnOutcomeInsteadOfTheLastFailure() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertEquals(SetupFailure.UNREACHABLE, state.failure)

        state.onAction(SetupAction.TRY_AGAIN)
        runCurrent()
        assertEquals("The last failure is not this attempt's", SetupStep.CONNECTING, state.step)
        session.publish(SessionObservation.create(sessionState = SessionState.Synchronizing))
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
    }

    @Test
    fun aConnectionThatIsAlreadySelectedIsTestedAfreshOnceThenFails() = runTest {
        session.scriptConnect(SessionCommandResult.NO_CHANGE)
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        assertEquals(
            listOf(
                FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT,
                FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT,
                FakeSessionCall.DISCONNECT,
            ),
            session.calls,
        )
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(SetupFailure.OTHER, state.failure)
    }

    @Test
    fun noNetworkOffersOnlyTryAgain() = runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.NETWORK_UNAVAILABLE)))
        runCurrent()
        assertEquals(SetupFailure.NETWORK, state.failure)
        assertEquals(listOf(SetupAction.TRY_AGAIN), state.failure!!.actionsFor(SetupMode.FIRST_RUN))
    }

    @Test
    fun clearingSavedCredentialsOnlyChangesTheDraft() = runTest {
        val state = edit()
        state.loadSaved()
        state.clearSavedPassword()
        assertEquals("", form.password)
        assertEquals("", form.username)
        assertFalse(form.hasSavedPassword)
        assertNull(profiles.saved)
        assertTrue(session.calls.isEmpty())
    }

    private fun TestScope.setup(step: SetupStep = SetupStep.ADDRESS) =
        ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope, step = step)

    private var restores = 0
    private var leaves = 0

    private fun TestScope.edit(
        restoreSaved: suspend () -> Unit = { restores++ },
        profiles: ConnectionProfileEditor = this@ConnectionSetupStateTest.profiles,
    ): ConnectionSetupState {
        form.updateHost(HOST)
        form.updateUsername(USERNAME)
        form.updatePassword(PASSWORD)
        return ConnectionSetupState(
            form, session, profiles, { discovery }, backgroundScope, SetupMode.EDIT,
            restoreSaved = restoreSaved,
            onSaved = { leaves++ },
        )
    }

    private fun TestScope.signIn(username: String, password: String): ConnectionSetupState {
        form.updateHost(HOST)
        form.updateUsername(username)
        form.updatePassword(password)
        return setup(SetupStep.SIGN_IN)
    }

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED),
        ),
    )
}

private class FakeProfiles : ConnectionProfileEditor {
    override val serverSettings: Flow<ServerSettings> = emptyFlow()
    var saved: String? = null

    override suspend fun loadServerForEditing(applyAvailable: (String, Int, String, String) -> Unit) = Unit

    override suspend fun saveServer(host: String, htspPort: Int) {
        saved = "$host:$htspPort"
    }

    override suspend fun savePasswordServer(
        host: String,
        htspPort: Int,
        username: String,
        password: String,
    ) {
        saved = "$host:$htspPort:$username"
    }

    override suspend fun clearProfile() = Unit
}

private val LIVING_ROOM = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val OFFICE = DiscoveredTvheadendServer("Office", "192.0.2.20", 9983)

private const val HOST = "setup.invalid"
private const val USERNAME = "setup-user"
private const val PASSWORD = "fake password"
