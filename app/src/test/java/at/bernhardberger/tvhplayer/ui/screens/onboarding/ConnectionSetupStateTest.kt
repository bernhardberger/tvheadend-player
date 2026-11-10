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
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSetupStateTest {
    private val session = FakeTvheadendSession()
    private val profiles = FakeProfiles()
    private val form = ConnectionFormState()
    private val discovery = MutableStateFlow(discovering())

    @Test fun anonymousConfirmationSurvivesARealFormRestorationAndStillPrecedesThePlaybackStop() = runTest {
        form.updateHost(HOST)
        val events = mutableListOf<String>()
        val saver = ConnectionSetupState.saver(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, {}, {}, interruptsPlayback = { true }, beforeTest = { events += "stop" })
        val state = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, step = SetupStep.SIGN_IN, interruptsPlayback = { true }, beforeTest = { events += "stop" })
        state.connect()
        assertTrue(state.confirmingSwitch)
        assertTrue(session.calls.isEmpty())
        val snapshot = with(saver) { object : SaverScope { override fun canBeSaved(value: Any) = true }.save(state)!! }
        val saveScope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val formSnapshot = with(at.bernhardberger.tvhplayer.settings.ConnectionFormStateSaver) { saveScope.save(form)!! }
        val restoredForm = at.bernhardberger.tvhplayer.settings.ConnectionFormStateSaver.restore(formSnapshot)!!
        assertNotSame(form, restoredForm)
        val restored = ConnectionSetupState.saver(restoredForm, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, {}, {}, interruptsPlayback = { true }, beforeTest = { events += "stop" }).restore(snapshot)!!
        assertTrue(restored.confirmingSwitch)
        restored.keepWatching()
        runCurrent()
        assertTrue(events.isEmpty())
        assertEquals(SignInFocus.CONNECT, restored.signInFocus)
        restored.connect()
        restored.confirmSwitch()
        runCurrent()
        assertEquals(listOf("stop"), events)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
    }

    @Test fun passwordConfirmationReturnsToAccountAfterARealCredentialFreeFormRestoration() = runTest {
        form.updateHost(HOST)
        form.updateUsername(USERNAME)
        form.updatePassword(PASSWORD)
        var stopped = false
        val state = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, step = SetupStep.SIGN_IN, interruptsPlayback = { true }, beforeTest = { stopped = true })
        state.connect()
        assertTrue(state.confirmingSwitch)
        val saveScope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saver = ConnectionSetupState.saver(form, session, profiles, { discovery }, backgroundScope, SetupMode.ADD, {}, {})
        val snapshot = with(saver) { saveScope.save(state)!! }
        val formSnapshot = with(at.bernhardberger.tvhplayer.settings.ConnectionFormStateSaver) { saveScope.save(form)!! }
        val restoredForm = at.bernhardberger.tvhplayer.settings.ConnectionFormStateSaver.restore(formSnapshot)!!
        assertNotSame(form, restoredForm)
        assertTrue(restoredForm.username.isEmpty())
        assertTrue(restoredForm.password.isEmpty())
        val restored = ConnectionSetupState.saver(restoredForm, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, {}, {}, interruptsPlayback = { true }, beforeTest = { stopped = true }).restore(snapshot)!!
        restored.loadSaved()
        runCurrent()
        assertFalse(restored.confirmingSwitch)
        assertEquals(SetupStep.SIGN_IN, restored.step)
        assertEquals(SignInFocus.USERNAME, restored.signInFocus)
        assertFalse(stopped)
        assertTrue(session.calls.isEmpty())
    }

    @Test fun duplicateEditNeverAsksStopsOrConnects() = runTest {
        form.updateHost(HOST)
        var lookedUp = 0
        var stopped = 0
        val state = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.EDIT, step = SetupStep.SIGN_IN, existingProfile = { _, _, _ -> lookedUp++; true },
            interruptsPlayback = { true }, beforeTest = { stopped++ })
        state.connect()
        runCurrent()
        assertEquals(1, lookedUp)
        assertFalse(state.confirmingSwitch)
        assertEquals(0, stopped)
        assertTrue(session.calls.isEmpty())
    }

    @Test fun aNewSetupInstanceWaitsForTheClosedInstancesCleanup() = runTest {
        form.updateHost(HOST)
        val lock = Mutex()
        val gate = CompletableDeferred<Unit>()
        val firstScope = CoroutineScope(coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        val first = ConnectionSetupState(form, session, profiles, { discovery }, firstScope,
            SetupMode.ADD, restoreSaved = { gate.await(); session.publish(ready()) }, sessionLock = lock)
        first.connect()
        runCurrent()
        firstScope.cancel()
        runCurrent()
        val calls = session.calls.toList()
        val second = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, sessionLock = lock)
        second.connect()
        runCurrent()
        assertEquals(calls, session.calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(calls + listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        assertEquals(SetupStep.CONNECTING, second.step)
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, second.step)
    }

    @Test fun anOutcomeAfterTheSavedConnectionGenerationChangedCannotCompleteOrCleanUpTheTest() = variants { cancelled -> runTest {
        form.updateHost(HOST)
        var generation = 1L
        var restored = 0
        val state = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, restoreSaved = { restored++ }, connectionGeneration = { generation })
        state.connect()
        runCurrent()
        val calls = session.calls.toList()
        generation++
        if (cancelled) {
            assertTrue(state.back())
            runCurrent()
            assertEquals(SetupStep.SIGN_IN, state.step)
        } else {
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(SetupFailure.OTHER, state.failure)
        }
        assertEquals(calls, session.calls)
        assertEquals(0, restored)
    } }

    @Test fun cancelledAndFailedAddsWithNoneInUseLeaveTheSessionDisconnected() = runTest {
        form.updateHost(HOST)
        var restores = 0
        fun add() = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope,
            SetupMode.ADD, restoreSaved = { restores++; session.disconnect() })
        val cancelled = add()
        cancelled.connect()
        runCurrent()
        cancelled.back()
        runCurrent()
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        val failed = add()
        failed.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.SERVER_UNREACHABLE)))
        runCurrent()
        assertEquals(SetupStep.FAILED, failed.step)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertEquals(2, restores)
        assertNull(profiles.saved)
    }

    @Test
    fun aSearchNeverValidatesTheAddress() = runTest {
        val state = setup()
        state.openSearch()
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
        assertTrue(state.back())
        assertFalse(state.hostError)
        state.continueFromAddress()
        assertTrue(state.hostError)
    }

    @Test
    fun staleSearchCannotStartOrClearTheCurrentSearch() = runTest {
        val state = setup()
        state.openSearch()
        val firstRequest = state.searchRequest
        val first = backgroundScope.launch { state.search(firstRequest) }
        runCurrent()
        state.findServer()
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
    fun openingTheEditorWaitsOnContinueAndSearchesOnlyWhenAsked() = runTest {
        val state = edit()
        val savedHost = form.host
        val savedPort = form.port
        assertEquals(0, state.searchRequest)
        assertFalse(state.searching)
        assertEquals(AddressFocus.CONTINUE, state.addressFocus)
        state.openSearch()
        assertEquals(SetupStep.FIND_SERVER, state.step)
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

        val firstFocus = state.focusRequest
        state.continueFromAddress()
        assertTrue(state.focusRequest > firstFocus)
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
        listOf(true, false).forEach { missingHost ->
            form.updateHost(if (missingHost) "" else HOST)
            form.updatePort(if (missingHost) "9982" else "0")
            val focus = state.focusRequest
            state.connect()
            assertEquals(SetupStep.ADDRESS, state.step)
            assertEquals(missingHost, state.hostError)
            assertEquals(!missingHost, state.portError)
            assertEquals(if (missingHost) AddressFocus.HOST else AddressFocus.PORT, state.addressFocus)
            assertTrue(state.focusRequest > focus)
            form.updateHost(HOST)
            form.updatePort("9982")
            state.continueFromAddress()
        }
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun aHalfFilledSignInNeverReachesTheSession() = runTest {
        val state = signIn(username = USERNAME, password = "")
        val focus = state.focusRequest
        state.connect()
        runCurrent()
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertTrue(state.signInError)
        assertEquals(SignInFocus.PASSWORD, state.signInFocus)
        assertTrue(state.focusRequest > focus)
        assertEquals(emptyList<FakeSessionCall>(), session.calls)
        form.updateUsername("")
        form.updatePassword(PASSWORD)
        val usernameFocus = state.focusRequest
        state.connect()
        assertEquals(SignInFocus.USERNAME, state.signInFocus)
        assertTrue(state.focusRequest > usernameFocus)
        assertTrue(state.signInError)
        assertTrue(session.calls.isEmpty())
    }

    @Test
    fun theFirstRunFlowEndingDuringTheSaveKeepsTheConnection() = runTest {
        val saving = CompletableDeferred<Unit>()
        profiles.pendingSave = saving
        // The app root leaves first-run as soon as the profile publishes, cancelling this scope
        // while the save still clears old artwork and before submit returns.
        val composition = CoroutineScope(coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        form.updateHost(HOST)
        form.updateUsername(USERNAME)
        form.updatePassword(PASSWORD)
        val state = ConnectionSetupState(
            form, session, profiles, { discovery }, composition, step = SetupStep.SIGN_IN,
        )
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)

        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        composition.cancel()
        runCurrent()

        assertEquals(
            "The saved profile keeps the connection the test made",
            listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT),
            session.calls,
        )
    }

    @Test
    fun theFirstRunFlowEndingBeforeTheOwnerTookTheSaveDropsTheConnection() = runTest {
        profiles.pendingAdmission = CompletableDeferred()
        val composition = CoroutineScope(coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        form.updateHost(HOST)
        form.updateUsername(USERNAME)
        form.updatePassword(PASSWORD)
        val state = ConnectionSetupState(
            form, session, profiles, { discovery }, composition, step = SetupStep.SIGN_IN,
        )
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()

        state.save()
        runCurrent()
        composition.cancel()
        runCurrent()

        assertNull(profiles.saved)
        assertEquals(
            "Nothing was saved, so nothing may keep the tested connection",
            listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT),
            session.calls,
        )
    }

    @Test
    fun theFirstRunFlowEndingMidSaveWaitsForTheOutcome() = runTest {
        val composition = CoroutineScope(coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        form.updateHost(HOST)
        form.updateUsername(USERNAME)
        form.updatePassword(PASSWORD)
        val state = ConnectionSetupState(
            form, session, profiles, { discovery }, composition, step = SetupStep.SIGN_IN,
        )
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()

        profiles.settings.value = null
        composition.cancel()
        runCurrent()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)

        profiles.settings.value = ServerSettings(host = HOST)
        runCurrent()
        assertEquals(
            "The save landed, so the connection stays",
            listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT),
            session.calls,
        )
    }

    @Test
    fun aSaveThatFailsStillLetsBackDropTheTestedConnection() = variants { failed -> runTest {
        if (failed) profiles.saveFailure = IOException("Fake store failure")
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()

        state.save()
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        if (failed) assertNull(profiles.saved) else assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)

        assertTrue(state.back())
        runCurrent()
        assertEquals(
            if (failed) listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT)
            else listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT),
            session.calls,
        )
    } }

    @Test
    fun aFailedTestDisconnectsAndOffersItsOwnRecovery() = variants { network -> runTest {
        val state = signIn(username = USERNAME, password = PASSWORD)
        state.connect()
        runCurrent()
        session.publish(SessionObservation.create(sessionState = SessionState.Unavailable(
            if (network) SessionFailure.NETWORK_UNAVAILABLE else SessionFailure.AUTHENTICATION_REJECTED)))
        runCurrent()
        assertEquals(SetupStep.FAILED, state.step)
        assertEquals(if (network) SetupFailure.NETWORK else SetupFailure.SIGN_IN_REJECTED, state.failure)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())

        if (network) {
            assertEquals(listOf(SetupAction.TRY_AGAIN), state.failure!!.actionsFor(SetupMode.FIRST_RUN))
        } else {
        state.onAction(SetupAction.CHANGE_SIGN_IN)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(SignInFocus.PASSWORD, state.signInFocus)
        assertEquals(USERNAME, form.username)
        }
        assertNull(profiles.saved)
    } }

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
    fun discoveryDoesNotStealFocusAndChoosingAServerAdvancesToAccount() = runTest {
        val state = setup()
        state.openSearch()
        assertEquals(SetupStep.FIND_SERVER, state.step)
        assertTrue(state.searching)
        assertNull("The search action holds focus until a server answers", state.findFocus)
        backgroundScope.launch { state.search(state.searchRequest) }
        runCurrent()
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        discovery.value = discovering(LIVING_ROOM, OFFICE)
        runCurrent()
        assertEquals(listOf(LIVING_ROOM, OFFICE), state.servers)
        assertNull("Results don't take focus", state.findFocus)

        val focus = state.focusRequest
        state.chooseServer(OFFICE)
        assertTrue(state.focusRequest > focus)
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
    fun aSearchThatFindsNothingEndsAfterItsWindowAndCanRetryInPlace() = variants { unavailable -> runTest {
        if (unavailable) discovery.value = TvheadendDiscoveryState.Unavailable(TvheadendDiscoveryFailure.PERMISSION_DENIED)
        val state = setup()
        state.openSearch()
        backgroundScope.launch { state.search(state.searchRequest) }
        if (unavailable) runCurrent() else {
        advanceTimeBy(DiscoveryWindow - 1.milliseconds)
        assertTrue(state.searching)
        advanceTimeBy(2.milliseconds)
        }
        assertFalse(state.searching)
        assertEquals(emptyList<DiscoveredTvheadendServer>(), state.servers)

        val request = state.searchRequest
        state.findServer()
        assertEquals(SetupStep.FIND_SERVER, state.step)
        assertTrue("An empty search starts again", state.searching)
        assertEquals(request + 1, state.searchRequest)
        assertNull(state.findFocus)
    } }

    @Test
    fun theSearchScreenLeavesForTheAddressFieldOrItsAction() = runTest {
        val state = setup()
        state.openSearch()
        state.enterAddressManually()
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.HOST, state.addressFocus)
        state.openSearch()
        assertTrue(state.back())
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.SEARCH, state.addressFocus)
    }

    @Test
    fun aRecreatedSearchScreenStartsItsOwnSearch() = runTest {
        val state = ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope, SetupMode.EDIT, step = SetupStep.FIND_SERVER)
        assertEquals(1, state.searchRequest)
        assertTrue(state.searching)
    }

    @Test
    fun aServerThatLeavesHandsFocusOnButAMovedFocusStays() = runTest {
        val state = setup()
        state.openSearch()
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
        assertNull("Focus on the search action stays there", state.findFocus)
    }

    @Test
    fun editingUsesTheSameWizardAndDoesNotSaveDrafts() = runTest {
        val state = edit()
        assertFalse(state.offersName)
        assertEquals(SetupStep.ADDRESS, state.step)
        assertFalse(state.back())
        form.updateHost(OFFICE.host)
        state.continueFromAddress()
        assertEquals(SetupStep.SIGN_IN, state.step)
        form.updatePassword("")
        val focus = state.focusRequest
        state.continueFromSignIn()
        assertTrue(state.focusRequest > focus)
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
    fun editSavesAPassedTestOnlyOnTheFinalAction() = variants { editing -> runTest {
        val state = if (editing) edit() else signIn(USERNAME, PASSWORD)
        state.connect()
        assertEquals(SetupStep.CONNECTING, state.step)
        runCurrent()
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        assertNull("The current connection stays saved during the test", profiles.saved)
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        runCurrent()
        assertEquals(SetupStep.CONNECTING, state.step)
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        assertNull(profiles.saved)
        assertEquals(listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT), session.calls)
        state.save()
        runCurrent()
        assertEquals("$HOST:9982:$USERNAME", profiles.saved)
        assertEquals(if (editing) 1 else 0, leaves)
        assertEquals("The passed connection is the app's now", 0, restores)
        assertEquals("", form.password)
    } }

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
    fun editBackCancelsTheTestAndRestoresTheSavedConnection() = variants { editing -> runTest {
        val state = if (editing) edit() else signIn(USERNAME, PASSWORD)
        state.connect()
        runCurrent()
        assertTrue(state.back())
        assertEquals(SetupStep.SIGN_IN, state.step)
        runCurrent()
        assertEquals(if (editing) 1 else 0, restores)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertNull(profiles.saved)
        assertEquals(PASSWORD, form.password)
        assertEquals(SignInFocus.CONNECT, state.signInFocus)
        session.publish(ready())
        runCurrent()
        assertEquals("A cancelled test never finishes", SetupStep.SIGN_IN, state.step)
        assertTrue(state.back())
        assertEquals(SetupStep.ADDRESS, state.step)
        assertEquals(AddressFocus.CONTINUE, state.addressFocus)
        assertEquals(HOST, form.host)
        assertEquals(PASSWORD, form.password)
        if (editing) assertFalse(state.back()) else {
            assertTrue(state.back())
            assertEquals(SetupStep.WELCOME, state.step)
            assertFalse("Back on Welcome belongs to the caller", state.back())
        }
    } }

    @Test
    fun editBackFromSuccessRestoresTheSavedConnectionWithoutSaving() = variants { editing -> runTest {
        val state = if (editing) edit() else signIn(USERNAME, PASSWORD)
        state.connect()
        runCurrent()
        session.publish(ready())
        runCurrent()
        assertEquals(SetupStep.DONE, state.step)
        assertNull(profiles.saved)
        assertTrue(state.back())
        runCurrent()
        assertEquals(if (editing) 1 else 0, restores)
        assertEquals(SetupStep.SIGN_IN, state.step)
        assertEquals(FakeSessionCall.DISCONNECT, session.calls.last())
        assertNull(profiles.saved)
        assertEquals(0, leaves)
    } }

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
    fun aFailureCancelledWhileItRestoresLeavesTheNextTestRunning() = variants { retry -> runTest {
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
        if (retry) {
        state.connect()
        runCurrent()
        val firstTest = listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT)
        assertEquals("The next test waits for the restore", firstTest, session.calls)
        }

        restoring.complete(Unit)
        runCurrent()
        assertEquals(1, restores)
        assertNull(state.failure)
        if (retry) {
        val firstTest = listOf(FakeSessionCall.DISCONNECT, FakeSessionCall.CONNECT, FakeSessionCall.DISCONNECT)
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
        } else assertEquals("Back dismissed the test before its failure showed", SetupStep.SIGN_IN, state.step)
    } }

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
    fun clearingSavedCredentialsOnlyChangesTheDraft() = runTest {
        val state = edit()
        state.loadSaved()
        val focus = state.focusRequest
        state.clearSavedPassword()
        assertTrue(state.focusRequest > focus)
        assertEquals(SignInFocus.USERNAME, state.signInFocus)
        assertEquals("", form.password)
        assertEquals("", form.username)
        assertFalse(form.hasSavedPassword)
        assertNull(profiles.saved)
        assertTrue(session.calls.isEmpty())
    }

    private fun TestScope.setup(step: SetupStep = SetupStep.ADDRESS) =
        ConnectionSetupState(form, session, profiles, { discovery }, backgroundScope, step = step)

    private fun variants(scenario: ConnectionSetupStateTest.(Boolean) -> Unit) {
        listOf(false, true).forEach { ConnectionSetupStateTest().scenario(it) }
    }

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
    val settings = MutableStateFlow<ServerSettings?>(ServerSettings())
    override val serverSettings: Flow<ServerSettings> = settings.filterNotNull()
    var saved: String? = null
    var pendingAdmission: CompletableDeferred<Unit>? = null
    var pendingSave: CompletableDeferred<Unit>? = null
    var saveFailure: Exception? = null

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
        pendingAdmission?.await()
        saveFailure?.let { throw it }
        saved = "$host:$htspPort:$username"
        settings.value = ServerSettings(host = host, htspPort = htspPort)
        pendingSave?.await()
    }

    override suspend fun clearProfile() = Unit
}

private val LIVING_ROOM = DiscoveredTvheadendServer("Living room", "192.0.2.10", 9982)
private val OFFICE = DiscoveredTvheadendServer("Office", "192.0.2.20", 9983)

private const val HOST = "setup.invalid"
private const val USERNAME = "setup-user"
private const val PASSWORD = "fake password"
