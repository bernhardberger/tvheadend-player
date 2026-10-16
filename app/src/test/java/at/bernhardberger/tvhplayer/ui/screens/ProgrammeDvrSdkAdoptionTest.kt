package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.notifications.NoticeFormatter
import coil3.ImageLoader
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

@OptIn(ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgrammeDvrSdkAdoptionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(1000),
        Instant.fromEpochSeconds(3000), title = "Ridge Light")

    @Test fun detailsStopsRecordingRatherThanCompletedEntry() = stop(details = true)
    @Test fun guideMenuStopsRecordingRatherThanCompletedEntry() = stop(details = false)
    @Test fun differentProfilesNameSelectedProfile() = stop(details = true, otherProfile = "two")
    @Test fun sameProfileNamesSelectedOwner() = stop(details = true, otherProfile = "one")
    @Test fun singleActiveKeepsExistingConfirmationText() = stop(details = true, unchangedText = true)

    @Test fun recordRestoresFocusBeforeFailureAndGuardsReopenedMenu() = deferredMutation(null, reopen = true)
    @Test fun confirmedStopRestoresFocusBeforeFailure() = deferredMutation(DvrEntryState.RECORDING)
    @Test fun confirmedCancelRestoresFocusBeforeFailure() = deferredMutation(DvrEntryState.SCHEDULED)
    @Test fun detailsBackDoesNotCancelRecord() = deferredMutation(null, details = true)
    @Test fun staleStopDoesNotDisableDetailsBack() = deferredMutation(DvrEntryState.RECORDING, details = true, stale = true)
    @Test fun staleCancelRestoresMenuOrigin() = deferredMutation(DvrEntryState.SCHEDULED, stale = true)

    @Test fun detailsClockUpdatesOnlyAtMinuteBoundaries() {
        var seconds = 119L
        var displayed = 0L
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val clock = at.bernhardberger.tvhplayer.ui.common.rememberDetailsEpochSeconds { seconds }
            SideEffect { displayed = clock }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(119L, displayed)
        seconds = 120
        compose.mainClock.advanceTimeBy(1_100)
        compose.waitForIdle()
        assertEquals(120L, displayed)
        seconds = 125
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(120L, displayed)
        seconds = 180
        compose.mainClock.advanceTimeBy(55_100)
        compose.waitForIdle()
        assertEquals(180L, displayed)
    }

    private fun deferredMutation(state: DvrEntryState?, details: Boolean = false, reopen: Boolean = false,
        stale: Boolean = false) {
        fun observation(entryState: DvrEntryState?) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Ridge Earth")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entryState?.let {
                listOf(DvrEntry.create(DvrEntryId(2), eventId = event.id, state = it))
            }.orEmpty())),
        )
        val session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession(observation(state))
        val opening = session.observation.value
        val release = CompletableDeferred<Unit>()
        var started = 0
        var cancelled = false
        suspend fun result(): DvrMutationResult.Failed {
            started++
            try { release.await() } catch (exception: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw exception
            }
            return DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, false, null)
        }
        val actions = DvrMutationActions(scheduleEntry = { _, _ -> result() }, stopEntry = { _, _ -> result() },
            cancelEntry = { _, _ -> result() }, deleteEntry = { _, _ -> error("unexpected delete") })
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, opening.currentSession?.generationIdentity) }
        val loader = ImageLoader.Builder(context).build()
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            val scope = rememberCoroutineScope()
            val inFlight = remember { mutableStateOf<DvrMutationAction?>(null) }
            var opened by remember { mutableStateOf(true) }
            val origin = remember { FocusRequester() }
            TVHeadendPlayerTheme {
                androidx.tv.material3.Button(onClick = { opened = true }, modifier = Modifier.focusRequester(origin)) {
                    androidx.tv.material3.Text("Origin card")
                }
                if (opened) ProgrammeDetailsHost(event, opening, opening, { session.observation.value }, { 2000L },
                    fromGuide = true, showContextMenu = !details, onShowDetails = {}, imageLoader = loader,
                    mutations = actions, notices = notices, mutationScope = scope, mutationInFlight = inFlight,
                    onPlay = { _, _ -> }, onPlayRecording = {}, onClose = { opened = false },
                    returnFocusRequester = origin, settingsStore = UiSettingsStore(context))
            }
        }
        val actionText = when (state) {
            DvrEntryState.RECORDING -> "Stop recording"
            DvrEntryState.SCHEDULED -> "Cancel recording"
            else -> "Record"
        }
        if (stale) compose.runOnIdle { session.publish(observation(DvrEntryState.COMPLETED)) }
        compose.onNodeWithText(actionText).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        if (!stale && state != null) {
            compose.onNodeWithText(actionText).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        }
        if (details) {
            compose.runOnIdle { back.onBackPressed() }
            compose.waitForIdle()
        }
        compose.onNodeWithText("Origin card").assertIsFocused()
        if (!stale) compose.waitUntil(5_000) { compose.waitForIdle(); started == 1 }
        assertEquals(if (stale) 0 else 1, started)
        assertFalse(cancelled)
        if (reopen) {
            compose.onNodeWithText("Origin card").performKeyInput { pressKey(Key.DirectionCenter) }
            compose.onNodeWithText("Record").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
            assertEquals(1, started)
        }
        compose.runOnIdle { release.complete(Unit) }
        compose.waitForIdle()
        if (!stale) compose.waitUntil(5_000) { compose.waitForIdle(); notices.state.value.candidate != null }
        assertFalse(cancelled)
        loader.shutdown()
    }

    private fun stop(details: Boolean, otherProfile: String? = null, unchangedText: Boolean = false) {
        val selected = DvrEntry.create(DvrEntryId(2), eventId = event.id, state = DvrEntryState.RECORDING,
            configId = DvrConfigId("one"), owner = "Fixture owner", creator = "Fixture creator")
        val other = DvrEntry.create(DvrEntryId(1), eventId = event.id,
            state = if (otherProfile == null) DvrEntryState.COMPLETED else DvrEntryState.SCHEDULED,
            configId = otherProfile?.let(::DvrConfigId))
        val observation = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(listOf(Channel.create(ChannelId(1), name = "Ridge Earth")))),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(listOf(event))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(listOf(other, selected))),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(listOf(
                DvrConfiguration(DvrConfigId("one"), "One", ""), DvrConfiguration(DvrConfigId("two"), "Two", ""))),
        )
        var stopped: DvrEntryId? = null
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> error("unexpected schedule") },
            stopEntry = { _, id -> stopped = id; DvrMutationResult.Confirmed(Unit) },
            cancelEntry = { _, _ -> error("unexpected cancel") },
            deleteEntry = { _, _ -> error("unexpected delete") },
        )
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        val loader = ImageLoader.Builder(context).build()
        compose.setContent {
            TVHeadendPlayerTheme {
                ProgrammeDetailsHost(event, observation, observation, { observation }, { 2000L },
                    fromGuide = true, showContextMenu = !details, onShowDetails = {}, imageLoader = loader,
                    mutations = actions, notices = notices, onPlay = { _, _ -> }, onPlayRecording = {},
                    mutationScope = androidx.compose.runtime.rememberCoroutineScope(),
                    mutationInFlight = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) },
                    onClose = {}, settingsStore = UiSettingsStore(context))
            }
        }
        compose.onNodeWithText("Stop recording").requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        if (otherProfile == "one") {
            compose.onNodeWithText("Recording owner: Fixture owner", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Fixture creator", substring = true).assertDoesNotExist()
        } else if (otherProfile != null) {
            compose.onNodeWithText("Recording profile: One", substring = true).assertIsDisplayed()
        }
        if (unchangedText) compose.onNodeWithText(context.getString(R.string.stop_recording_confirm_message, "Ridge Light")).assertIsDisplayed()
        compose.onNodeWithText("Keep recording").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Stop recording", ignoreCase = false).requestFocus().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(selected.id, stopped) }
        loader.shutdown()
    }

    @Test fun rejectedServerMessageReachesNoticeWithoutEnteringDiagnostics() = rejected("No free adapter")
    @Test fun rejectedWithoutServerMessageKeepsExistingText() = rejected(null)
    @Test fun aServerMessageOnAnotherFailureKeepsTheAppText() = rejected("No free adapter", TvheadendFailure.TIMEOUT)

    private fun rejected(message: String?, failure: TvheadendFailure = TvheadendFailure.SERVER_REJECTED) = runTest {
        val observation = SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()))
        val action = DvrMutationAction.Stop(requireNotNull(observation.currentSession), DvrEntryId(2))
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> error("unexpected schedule") },
            stopEntry = { _, _ -> DvrMutationResult.Failed(failure, false, message) },
            cancelEntry = { _, _ -> error("unexpected cancel") }, deleteEntry = { _, _ -> error("unexpected delete") },
        )
        val notices = NoticeCenter({ 0L }) { NoticeContext(0, observation.currentSession?.generationIdentity) }
        notices.postDvrFailure(action, actions.execute(action), notices.context())
        val notice = requireNotNull(notices.state.value.candidate).notice
        if (failure != TvheadendFailure.SERVER_REJECTED) {
            assertFalse(NoticeFormatter(context).format(notice).detail.orEmpty().contains(message.orEmpty()))
            return@runTest
        }
        val generic = context.getString(R.string.recording_action_rejected)
        assertEquals(if (message == null) generic else "$generic $message", NoticeFormatter(context).format(notice).detail)
        if (message != null) assertFalse(notice.toString().contains(message))
    }
}
