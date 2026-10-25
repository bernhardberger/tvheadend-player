package at.bernhardberger.tvhplayer.ui.screens.onboarding

import at.bernhardberger.tvheadend.sdk.android.DiscoveredTvheadendServer
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryFailure
import at.bernhardberger.tvheadend.sdk.android.TvheadendDiscoveryState
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.settings.ConnectionFormState
import at.bernhardberger.tvhplayer.settings.ConnectionProfileEditor
import at.bernhardberger.tvhplayer.settings.CredentialEditLease
import at.bernhardberger.tvhplayer.settings.ServerSettings
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    fun addressFocusesTheFirstInvalidFieldBeforeGoingOn() = runTest {
        val state = setup()
        state.enterAddress()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.HOST, state.addressFocus)
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
        assertEquals(SignInFocus.CONNECT, state.signInFocus)
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
        assertFalse("Back on the first step belongs to the caller", state.back())
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
    fun aFoundServerSkipsTheAddressAndBackReturnsToIt() = runTest {
        val state = setup()
        state.findServer()
        assertEquals(SetupStep.FIND, state.step)
        assertTrue(state.searching)
        assertNull("The address entry holds focus until a server answers", state.findFocus)
        backgroundScope.launch { state.search() }
        runCurrent()
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        discovery.value = discovering(LIVING_ROOM, OFFICE)
        runCurrent()
        assertEquals(listOf(LIVING_ROOM, OFFICE), state.servers)
        assertEquals("The first find takes focus", LIVING_ROOM, state.findFocus)

        state.chooseServer(OFFICE)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(OFFICE.host, form.host)
        assertEquals("9983", form.port)
        assertEquals(SignInFocus.USERNAME, state.signInFocus)

        assertTrue(state.back())
        assertEquals(SetupStep.FIND, state.step)
        assertEquals(OFFICE, state.findFocus)
        assertTrue(state.back())
        assertEquals(SetupStep.WELCOME, state.step)
        assertEquals(WelcomeFocus.FIND, state.welcomeFocus)
    }

    @Test
    fun aSearchThatFindsNothingEndsAfterItsWindowAndStartsAgainOnReturn() = runTest {
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search() }
        advanceTimeBy(DiscoveryWindow - 1.milliseconds)
        assertTrue(state.searching)
        advanceTimeBy(2.milliseconds)
        assertFalse(state.searching)
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        state.enterAddress()
        assertEquals(SetupStep.ADDRESS, state.step)
        val request = state.searchRequest
        assertTrue(state.back())
        assertEquals(SetupStep.FIND, state.step)
        assertTrue("An empty search starts again", state.searching)
        assertEquals(request + 1, state.searchRequest)
        assertNull(state.findFocus)
    }

    @Test
    fun anUnavailableSearchEndsAtOnce() = runTest {
        discovery.value = TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.PERMISSION_DENIED)
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search() }
        runCurrent()
        assertFalse(state.searching)
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)
    }

    @Test
    fun aServerThatLeavesHandsFocusOnButAMovedFocusStays() = runTest {
        val state = setup()
        state.findServer()
        backgroundScope.launch { state.search() }
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
    fun theAddressAfterASearchGoesBackToTheSearch() = runTest {
        val state = setup()
        state.enterAddress()
        assertTrue(state.back())
        assertEquals(SetupStep.WELCOME, state.step)
        assertEquals(WelcomeFocus.TYPE, state.welcomeFocus)

        state.findServer()
        state.enterAddress()
        assertEquals(ServerChoice.TYPED_AFTER_SEARCH, state.serverChoice)
        assertTrue(state.back())
        assertEquals(SetupStep.FIND, state.step)
    }

    private fun TestScope.setup(step: SetupStep = SetupStep.WELCOME) =
        ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope, step)

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
        credentialLease: CredentialEditLease,
    ) {
        saved = "$host:$htspPort:$username"
        credentialLease.release()
    }

    override suspend fun clearProfile() = Unit
}

private val LIVING_ROOM = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val OFFICE = DiscoveredTvheadendServer("Office", "192.0.2.20", 9983)

private const val HOST = "setup.invalid"
private const val USERNAME = "setup-user"
private const val PASSWORD = "fake password"
