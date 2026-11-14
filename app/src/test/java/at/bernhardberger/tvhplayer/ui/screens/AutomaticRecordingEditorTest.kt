package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.testutil.testDvrMutationActions
import android.app.Application
import androidx.compose.ui.graphics.toArgb
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession
import coil3.EventListener
import coil3.request.ImageRequest
import coil3.request.transformations
import kotlinx.coroutines.Dispatchers
import at.bernhardberger.tvhplayer.R
import androidx.compose.runtime.remember
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrPriority
import at.bernhardberger.tvheadend.sdk.core.RecordingRuleChannel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.runtime.key
import at.bernhardberger.tvhplayer.core.resolveOccurrenceOn
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.core.AutomaticRecordingKind
import at.bernhardberger.tvhplayer.core.RecordingStartWindow
import at.bernhardberger.tvhplayer.core.newAutorecDraft
import at.bernhardberger.tvhplayer.core.readRecordingPattern
import at.bernhardberger.tvhplayer.core.toUpdateRequest
import at.bernhardberger.tvhplayer.core.newTimerDraft
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalTime
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleCreate
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class AutomaticRecordingEditorTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun newRecordingFlowRestoresItsKindAndClosesOnceAfterCreating() {
        val logoRequests = mutableListOf<String>()
        var focusedGround = 0
        images.install(FixtureArt.imageLoader(RuntimeEnvironment.getApplication(),
            mapOf(ArtworkId(1) to FixtureArt.picon("ridge-earth"))).newBuilder()
            .eventListener(object : EventListener() {
                override fun onStart(request: ImageRequest) {
                    logoRequests.addAll(request.transformations.map { it.cacheKey })
                }
            }).coroutineContext(Dispatchers.Main.immediate)
            .fetcherCoroutineContext(Dispatchers.Main.immediate)
            .decoderCoroutineContext(Dispatchers.Main.immediate).build())
        val session = automaticRecordingFakeSession()
        val current = requireNotNull(session.observation.value.currentSession)
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        var visible by mutableStateOf(true)
        var closes = 0
        session.dvrRepository.scriptCreateTimerecRule(
            DvrMutationResult.Confirmed(at.bernhardberger.tvheadend.sdk.core.TimerecRuleId("new")))
        compose.setContent {
            TVHeadendPlayerTheme {
                focusedGround = MaterialTheme.colorScheme.onSurface.toArgb()
                ProvideArtworkSession(automaticRecordingSession) {
                    if (visible) NewAutomaticRecordingHost(
                        current, session.observation, remember { AutorecPreviewLoader(session.epgRepository) },
                        remember { DvrMutationActions(session.dvrRepository) }, notices,
                        { automaticRecordingNow }, automaticRecordingZone, onClose = { closes++; visible = false },
                    )
                }
            }
        }
        // Check the kind level's initial focus and directional bounds.
        compose.onNodeWithTag("automatic-recording-new-keyword").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("automatic-recording-new-keyword").assertIsFocused()
        repeat(3) { compose.pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("automatic-recording-new-timer").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-new-timer").assertIsFocused()
        repeat(3) { compose.pressKey(Key.DirectionUp) }
        // Open Keyword, then leave without saving.
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-done").assertDoesNotExist()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-new-keyword").assertIsFocused()
        compose.runOnIdle { assertEquals(0, closes) }
        // Open the repeating timer, select a channel and inspect its days.
        repeat(2) { compose.pressKey(Key.DirectionDown) }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-date").assertDoesNotExist()
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        assertTrue("The focused channel logo is inked for the guided row's light fill",
            logoRequests.any { it.startsWith("logoInk(${Integer.toHexString(focusedGround)},") })
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        for (day in DayOfWeek.entries) compose.onNodeWithTag("timer-recording-day-${day.value}").assertIsOn()
        compose.pressKey(Key.Back)
        compose.pressKey(Key.Back)
        // Create once, then reopen the kind level and leave it with Back.
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("timer-recording-main").assertIsFocused().assertIsEnabled()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(1, closes)
            assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CREATE_TIMEREC_RULE })
            assertEquals(null, notices.state.value.candidate)
        }
        compose.onNodeWithTag("timer-recording-main").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("automatic-recording-new-keyword").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.runOnIdle { assertEquals(2, closes) }
        compose.onNodeWithTag("automatic-recording-new-keyword").assertDoesNotExist()
    }

    // Native graphics measures text, so the safe-bottom check below sees real row positions.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun editorConfirmationCardsKeepDraftsAndDispatchTheChosenActionOnce() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var automatic: AutomaticRecordingEditorState
        lateinit var timer: TimerRecordingEditorState
        var timerMode by mutableStateOf(false)
        var timerGeneration by mutableStateOf(0)
        var failedHost by mutableStateOf(true)
        var automaticOpener = ""
        var timerOpener = ""
        val storedRule = at.bernhardberger.tvheadend.sdk.core.AutorecRule.create(
            AutorecRuleId("automatic-recording"), title = "^Ridge Light$",
            seriesLinkUri = automaticRecordingSeries.seriesLinkUri)
        val session = at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession(
            automaticRecordingObservation(autorecRules = listOf(storedRule)))
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        session.dvrRepository.scriptDeleteAutorecRule(DvrMutationResult.Failed(
            at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure.ACCESS_DENIED, false))
        compose.setContent {
            val observation by session.observation.collectAsState()
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            automatic = remember { fixture.state(automaticRecordingSeries, editing = true) }
            timer = remember(timerGeneration) { fixture.timer(newTimerDraft(automaticRecordingNow, automaticRecordingZone,
                startingRepeating = true).copy(channelId = automaticRecordingChannels.first().id,
                name = "Stored timer", existingTimer = true)) }
            TVHeadendPlayerTheme {
                if (failedHost) AutomaticRecordingEditorHost(
                    requireNotNull(observation.currentSession), session.observation,
                    AutorecPreviewLoader(session.epgRepository), DvrMutationActions(session.dvrRepository),
                    notices, { automaticRecordingNow }, automaticRecordingZone, {}, rule = storedRule,
                ) else if (timerMode) TimerRecordingEditor(timer, channels = automaticRecordingChannels)
                else AutomaticRecordingEditor(automatic, channels = automaticRecordingChannels)
            }
        }
        compose.settleFrames(2)
        val lastBottom = compose.onAllNodes(hasTestTag("automatic-recording-more"))
            .fetchSemanticsNodes().maxOf { it.boundsInRoot.bottom }
        val screenBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val reserve = 35 * RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue("One-line edit fields fit at rest: last=$lastBottom, safe bottom=${screenBottom - reserve}",
            lastBottom > 0 && lastBottom <= screenBottom - reserve)
        compose.onNodeWithTag("automatic-recording-enabled")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-more")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-delete")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionRight)
        confirmTwice()
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("automatic-recording-delete").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-enabled").assertIsOff()
        compose.runOnIdle {
            assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_DELETE_AUTOREC_RULE })
            assertEquals(1, notices.state.value.pending.size)
            failedHost = false
        }
        compose.runOnIdle {
            automatic.updateDraft { it.copy(enabled = false, pattern = it.pattern.copy(words = "Unsaved title")) }
            automaticOpener = automatic.navigation.focusedRow
            automatic.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        confirmationButton("automatic-recording-keep").assertIsFocused()
        compose.onNode(hasTestTag("centred-dialog-card")).assertExists()
        compose.onNode(hasAnyAncestor(hasTestTag("centred-dialog-card")) and
            androidx.compose.ui.test.hasText(automatic.baseline.pattern.words, substring = true),
            useUnmergedTree = true).assertExists()
        compose.pressKey(Key.Back, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.runOnIdle { assertTrue(automatic.changed); assertEquals(0, fixture.deletions) }
        compose.onNodeWithTag("automatic-recording-$automaticOpener").assertIsFocused()
        compose.runOnIdle { automatic.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE }
        confirmationButton("automatic-recording-keep").assertIsFocused()
        compose.pressKey(Key.DirectionCenter, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.onNodeWithTag("automatic-recording-$automaticOpener").assertIsFocused()
        compose.runOnIdle { automatic.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE }
        compose.pressKey(Key.DirectionRight)
        confirmTwice()
        compose.runOnIdle {
            assertEquals(1, fixture.deletions)
            assertEquals(0, fixture.saves)
            assertTrue(automatic.closed)
            timerMode = true
        }
        compose.runOnIdle {
            timer.updateDraft { it.copy(name = "Late news") }
            timerOpener = timer.navigation.focusedRow
            timer.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        confirmationButton("timer-recording-keep").assertIsFocused()
        compose.onNode(hasAnyAncestor(hasTestTag("centred-dialog-card")) and
            androidx.compose.ui.test.hasText(timer.baseline.name, substring = true),
            useUnmergedTree = true).assertExists()
        compose.pressKey(Key.Back, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.onNodeWithTag("timer-recording-$timerOpener").assertIsFocused()
        compose.runOnIdle {
            assertEquals("Late news", timer.draft.name)
            timer.navigation.confirmation = AutomaticRecordingEditorConfirmation.DELETE
        }
        compose.pressKey(Key.DirectionRight)
        confirmTwice()
        compose.runOnIdle { assertEquals(2, fixture.deletions); assertTrue(timer.closed); timerGeneration++ }
        compose.runOnIdle {
            timer.updateDraft { it.copy(name = "Late news") }
            timer.updateDraft { it.copy(days = emptySet()) }
            timerOpener = timer.navigation.focusedRow
            timer.navigation.confirmation = AutomaticRecordingEditorConfirmation.LEAVE
        }
        compose.onNodeWithTag("timer-recording-keep-editing").assertIsFocused()
        compose.onNodeWithTag("timer-recording-leave-main").assertIsEnabled()
        compose.onNode(hasAnyAncestor(hasTestTag("centred-dialog-card")) and
            androidx.compose.ui.test.hasText(RuntimeEnvironment.getApplication().getString(
                at.bernhardberger.tvhplayer.R.string.timer_editor_repeat_required))).assertExists()
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("timer-recording-keep-editing").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-leave-main").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        timerOpener = "channel-time"
        compose.runOnIdle { assertTrue(fixture.timerSubmissions.isEmpty()) }
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-discard").assertIsFocused()
        compose.pressKey(Key.Back, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.onNodeWithTag("timer-recording-$timerOpener").assertIsFocused()
        compose.runOnIdle { timer.navigation.confirmation = AutomaticRecordingEditorConfirmation.LEAVE }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-$timerOpener").assertIsFocused()
        compose.runOnIdle {
            assertEquals("Late news", timer.draft.name)
            timer.updateDraft { it.copy(days = DayOfWeek.entries.toSet()) }
            timer.navigation.confirmation = AutomaticRecordingEditorConfirmation.LEAVE
        }
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-leave-main").assertIsFocused()
        confirmTwice()
        compose.runOnIdle { assertEquals(1, fixture.timerSubmissions.size); assertTrue(timer.closed) }
    }

    @Test fun ruleMenuSkipAndUnskipWaitForPublishedMetadata() {
        val entry = automaticRecordingEntry()
        var detailsRoute by mutableStateOf(false)
        val session = automaticRecordingFakeSession(listOf(entry))
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        session.dvrRepository.scriptUpdateEntry(DvrMutationResult.Confirmed(Unit))
        val dependencies = org.koin.dsl.module {
            single<at.bernhardberger.tvheadend.sdk.core.TvheadendSession> { session }
            single { notices }
        }
        compose.setContent {
            val observation by session.observation.collectAsState()
            org.koin.compose.KoinIsolatedContext(remember { org.koin.dsl.koinApplication { modules(dependencies) } }) {
                TVHeadendPlayerTheme {
                    key(detailsRoute) {
                    at.bernhardberger.tvhplayer.ui.screens.home.HomeRecordingActions(
                        entry, observation, contextMenu = true, nowSec = automaticRecordingNow.epochSeconds,
                        onPlay = { _, _ -> }, onWatchChannel = { _, _ -> }, onShowGuide = { _, _ -> },
                        onReturnToRecording = { _, _ -> false }, onClose = {},
                    )
                    }
                }
            }
        }
        compose.onNodeWithTag("browse-context-action-details").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("browse-context-action-skip").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_UPDATE_ENTRY })
        compose.onNodeWithTag("browse-context-action-skip").assertExists()
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = listOf(automaticRecordingEntry(enabled = false))))
            session.dvrRepository.scriptUpdateEntry(DvrMutationResult.Confirmed(Unit))
        }
        compose.onNodeWithTag("browse-context-action-unskip").assertIsFocused()
        compose.onNodeWithTag("browse-context-action-cancel").assertDoesNotExist()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(2, session.calls.count { it == FakeSessionCall.DVR_UPDATE_ENTRY })
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = listOf(automaticRecordingEntry())))
        }
        compose.onNodeWithTag("browse-context-action-skip").assertIsFocused()
        assertTrue(notices.state.value.pending.isEmpty())
        val rule = at.bernhardberger.tvheadend.sdk.core.AutorecRule.create(
            requireNotNull(entry.autorecRuleId), title = "^Ridge Light$",
            seriesLinkUri = automaticRecordingSeries.seriesLinkUri)
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = listOf(entry), autorecRules = listOf(rule)))
        }
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("browse-context-action-edit_rule").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-enabled")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-enabled").assertIsOff()
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = emptyList(), autorecRules = listOf(rule)))
        }
        compose.onNodeWithTag("automatic-recording-enabled").assertIsOff().assertIsFocused()
        compose.onNodeWithTag("automatic-recording-main").assertIsEnabled()
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = listOf(entry), autorecRules = listOf(rule)))
            detailsRoute = true
        }
        compose.onNodeWithTag("browse-context-action-details").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        // Edit is behind More on the details page.
        compose.onNodeWithTag("details-more")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("browse-context-action-edit_rule")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-enabled")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            session.publish(automaticRecordingObservation(entries = emptyList(), autorecRules = listOf(rule)))
        }
        compose.onNodeWithTag("automatic-recording-enabled").assertIsOff().assertIsFocused()
        compose.onNodeWithTag("automatic-recording-main").assertIsEnabled()
    }

    @Test fun ruleEditRechecksThePublishedEntryAtActivation() {
        val entry = automaticRecordingEntry()
        val rule = at.bernhardberger.tvheadend.sdk.core.AutorecRule.create(
            requireNotNull(entry.autorecRuleId), title = "^Ridge Light$")
        val session = automaticRecordingFakeSession(listOf(entry))
        var detailsRoute by mutableStateOf(false)
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        val dependencies = org.koin.dsl.module {
            single<at.bernhardberger.tvheadend.sdk.core.TvheadendSession> { session }
            single { notices }
        }
        session.publish(automaticRecordingObservation(entries = listOf(entry), autorecRules = listOf(rule)))
        compose.setContent {
            val observation by session.observation.collectAsState()
            org.koin.compose.KoinIsolatedContext(remember { org.koin.dsl.koinApplication { modules(dependencies) } }) {
                TVHeadendPlayerTheme {
                    key(detailsRoute) {
                        at.bernhardberger.tvhplayer.ui.screens.home.HomeRecordingActions(
                            entry, observation, contextMenu = !detailsRoute, nowSec = automaticRecordingNow.epochSeconds,
                            onPlay = { _, _ -> }, onWatchChannel = { _, _ -> }, onShowGuide = { _, _ -> },
                            onReturnToRecording = { _, _ -> false }, onClose = {})
                    }
                }
            }
        }
        val openedRoutes = mutableListOf<Boolean>()
        for (details in listOf(false, true)) {
            compose.runOnIdle {
                session.publish(automaticRecordingObservation(entries = listOf(entry), autorecRules = listOf(rule)))
                detailsRoute = details
            }
            // On the details page, Edit is behind More, in the same menu.
            if (details) {
                compose.onNodeWithTag("details-more").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
                compose.pressKey(Key.DirectionCenter)
            }
            val activate = requireNotNull(compose.onNodeWithTag("browse-context-action-edit_rule").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action)
            compose.runOnIdle {
                session.publish(automaticRecordingObservation(
                    entries = listOf(automaticRecordingEntry(state = at.bernhardberger.tvheadend.sdk.core.DvrEntryState.COMPLETED)),
                    autorecRules = listOf(rule)))
                activate()
            }
            if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("automatic-recording-matches"))
                    .fetchSemanticsNodes().isNotEmpty()) openedRoutes += details
        }
        assertEquals(emptyList<Boolean>(), openedRoutes)
    }

    @OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
    @Test fun programmeDetailsOpensRestoresAndCreatesThroughTheEditorHost() {
        val session = automaticRecordingFakeSession(emptyList())
        val repository = session.dvrRepository
        val requests = mutableListOf<AutorecRuleCreate>()
        repository.scriptCreateAutorecRule(DvrMutationResult.Confirmed(AutorecRuleId("created")))
        val mutations = testDvrMutationActions(
            repository::scheduleEntry, repository::stopEntry, repository::cancelEntry,
            repository::deleteEntry, repository::updateWatchState,
            createAutorecRule = { current, request ->
                requests += request
                repository.createAutorecRule(current, request)
            },
        )
        val notices = NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) })
        val settings = UiSettingsStore(InMemoryPreferencesDataStore())
        val openingObservation = session.observation.value
        compose.setContent {
            val observation by session.observation.collectAsState()
            val scope = rememberCoroutineScope()
            TVHeadendPlayerTheme {
                KoinIsolatedContext(remember {
                    koinApplication { modules(module { single<TvheadendSession> { session } }) }
                }) {
                    ProgrammeDetailsHost(
                        automaticRecordingEvents.first(), openingObservation, observation,
                        { session.observation.value }, { automaticRecordingNow.epochSeconds },
                        showContextMenu = false, onShowDetails = {}, mutations, notices, scope,
                        remember { mutableStateOf(null) }, onPlay = { _, _ -> }, onPlayRecording = null,
                        onClose = {}, settingsStore = settings, signedInUsername = null,
                    )
                }
            }
        }
        // Record series is the second way to record, behind Record….
        fun recordSeries() {
            compose.onNodeWithTag("details-record-choice").assertIsFocused()
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionDown)
            compose.onNodeWithTag("record-choice-record_series").assertIsFocused()
            compose.pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.pressKey(Key.DirectionRight)
        recordSeries()
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        assertTrue(requests.isEmpty())
        compose.pressKey(Key.Back)
        recordSeries()
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("details-record-choice").assertIsFocused()
        assertEquals(1, requests.size)
        assertEquals(automaticRecordingEvents.first().seriesLinkUri, requests.single().seriesLinkUri)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CREATE_AUTOREC_RULE })
        assertTrue(notices.state.value.pending.isEmpty())
        recordSeries()
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        val ready = session.observation.value
        compose.runOnIdle { session.publish(SessionObservation.create()) }
        compose.onNodeWithTag("automatic-recording-main").assertDoesNotExist()
        compose.runOnIdle { session.publish(ready) }
        compose.onNodeWithTag("automatic-recording-main").assertDoesNotExist()
    }

    @Test fun timerRepeatKeepsExistingTypeAndHidesDate() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: TimerRecordingEditorState
        var replacement by mutableStateOf<TimerRecordingEditorState?>(null)
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            val initial = remember { fixture.timer(newTimerDraft(automaticRecordingNow, automaticRecordingZone).copy(
                channelId = ChannelId(101), days = setOf(DayOfWeek.MONDAY), existingTimer = true,
            )) }
            state = replacement ?: initial
            TVHeadendPlayerTheme { TimerRecordingEditor(state, channels = automaticRecordingChannels) }
        }
        compose.onNodeWithTag("timer-recording-main").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-enabled").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-date").assertDoesNotExist()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-done").assertIsFocused()
        compose.onNodeWithTag("timer-recording-never").assertDoesNotExist()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOn()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOff()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-repeat").assertIsFocused()
        compose.onNodeWithTag("timer-recording-date").assertDoesNotExist()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-main").assertIsEnabled()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        compose.runOnIdle {
            assertTrue(state.draft.repeating)
            assertTrue(state.draft.days.isEmpty())
            assertTrue(fixture.timerSubmissions.isEmpty())
            replacement = fixture.timer()
        }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-date").assertExists()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("timer-recording-never").assertIsOn()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-day-1").assertIsOn()
        compose.onNodeWithTag("timer-recording-never").assertIsOff()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-never").assertIsOn()
        compose.onNodeWithTag("timer-recording-day-1").assertIsOff()
        compose.runOnIdle {
            assertTrue(state.draft.days.isEmpty())
            assertTrue(fixture.timerSubmissions.isEmpty())
            state.updateDraft { it.copy(channelId = ChannelId(101)) }
        }
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-hour-${state.draft.start.hour}").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("timer-recording-start").assertIsFocused()
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("timer-recording-main").assertIsFocused()
        compose.runOnIdle {
            fixture.timerNow = state.draft.resolveOccurrenceOn(state.draft.date, state.zone).stop
        }
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("timer-recording-main").assertIsEnabled()
        compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
        compose.onNodeWithText(org.robolectric.RuntimeEnvironment.getApplication().getString(
            at.bernhardberger.tvhplayer.R.string.timer_editor_time_passed,
        )).assertExists()
    }

    @Test fun timerHostRefusesMissingChannelWithoutLosingTheDraft() {
        val sessions = List(2) { automaticRecordingFakeSession(emptyList()) }
        val notices = List(2) { NoticeCenter({ compose.mainClock.currentTime }, { NoticeContext(1, null) }) }
        var repeating by mutableStateOf(false)
        var closes = 0
        compose.setContent {
            key(repeating) {
                val index = if (repeating) 1 else 0
                val session = sessions[index]
                val current = remember { session.captureCurrentSession() }
                TVHeadendPlayerTheme {
                    TimerRecordingEditorHost(
                        current, session.observation, remember { DvrMutationActions(session.dvrRepository) },
                        notices[index], now = { automaticRecordingNow }, onClose = { closes++ },
                        startingRepeating = repeating,
                    )
                }
            }
        }
        for (index in sessions.indices) {
            compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
            compose.onNodeWithTag("timer-recording-main")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
            compose.pressKey(Key.DirectionCenter)
            compose.onNodeWithTag("timer-recording-channel-time").assertIsFocused()
            compose.runOnIdle { assertEquals(0, notices[index].state.value.pending.size) }
            if (index == 1) {
                val sameAsChannel = RuntimeEnvironment.getApplication().getString(
                    R.string.timer_editor_name_default,
                )
                compose.onAllNodesWithText(sameAsChannel, useUnmergedTree = true).assertCountEquals(1)
            }
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.Back)
            compose.pressKey(Key.DirectionUp)
            compose.onNodeWithTag("timer-recording-main").assertIsFocused()
            compose.runOnIdle {
                sessions[index].publish(automaticRecordingObservation(emptyList(), emptyList()))
            }
            compose.pressKey(Key.DirectionCenter)
            compose.onNodeWithTag("timer-recording-main").assertIsFocused()
            compose.runOnIdle {
                assertEquals(0, closes)
                assertEquals(1, notices[index].state.value.pending.size)
                assertTrue(sessions[index].calls.none {
                    it == FakeSessionCall.DVR_SCHEDULE_ENTRY || it == FakeSessionCall.DVR_CREATE_TIMEREC_RULE
                })
            }
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionCenter)
            compose.onNodeWithTag("timer-recording-channel-101").assertIsFocused()
            if (index == 0) compose.runOnIdle { repeating = true }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun wordsEntryPreservesRawExpressionAndReturnsWithoutSubmitting() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: AutomaticRecordingEditorState
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            state = remember {
                fixture.state(newAutorecDraft(AutomaticRecordingKind.KEYWORD).copy(
                    pattern = readRecordingPattern("A|B"),
                ), editing = true)
            }
            TVHeadendPlayerTheme { AutomaticRecordingEditor(state) }
        }
        compose.onNodeWithTag("automatic-recording-main").assertIsEnabled().assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-enabled").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        compose.runOnIdle {
            state.updateDraft { it.copy(pattern = readRecordingPattern(
                "Ridge Light|Still Tide",
            )) }
        }
        compose.settleFrames(2)
        val belowWords = listOf("channel-time", "keep", "more", "enabled")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(
            R.string.autorec_editor_raw_value, state.draft.pattern.words,
        ), useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(2, layouts.single().lineCount)
        val twoLinePositions = belowWords.map {
            compose.onNodeWithTag("automatic-recording-$it").fetchSemanticsNode().boundsInRoot.top
        }
        compose.runOnIdle {
            state.updateDraft { it.copy(pattern = readRecordingPattern(automaticRecordingLongExpression)) }
        }
        compose.settleFrames(2)
        assertEquals("Long expressions must not move the fields below Words", twoLinePositions, belowWords.map {
            compose.onNodeWithTag("automatic-recording-$it").fetchSemanticsNode().boundsInRoot.top
        })
        layouts.clear()
        compose.onNodeWithText(automaticRecordingLongExpression, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Editor guidance is at most three lines", layouts.single().lineCount <= 3)
        assertTrue("Long guidance ends in an ellipsis", layouts.single().isLineEllipsized(2))
        compose.runOnIdle { state.updateDraft { it.copy(pattern = readRecordingPattern("A|B")) } }
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { state.updateDraft { it.copy(pattern = readRecordingPattern("Tide Watch")) } }
        compose.onAllNodesWithText("Tide Watch", useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle {
            state.updateDraft { it.copy(pattern = readRecordingPattern(automaticRecordingLongExpression)) }
        }
        compose.onNodeWithText(automaticRecordingLongExpression, useUnmergedTree = true).assertExists()
        compose.runOnIdle { state.updateDraft { it.copy(pattern = readRecordingPattern("A|B")) } }
        compose.onNodeWithTag("automatic-recording-episode-1")
            .assertTextContains(requireNotNull(automaticRecordingMatches.rows.first().title))
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").assertTextContains("A|B")
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").performTextReplacement("A|B$")
        compose.onNodeWithTag("automatic-recording-words-input").performImeAction()
        compose.onNodeWithTag("automatic-recording-words").assertIsFocused()
        compose.runOnIdle {
            assertTrue(state.draft.pattern.rawExpression)
            assertEquals("A|B$", state.draft.toUpdateRequest(state.baseline)?.title)
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-search-in").assertIsFocused()
        compose.runOnIdle { assertTrue(state.draft.fullText) }
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-words-input").performTextReplacement("discard this")
        compose.pressKey(Key.Back)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-words").assertIsFocused()
        compose.runOnIdle {
            assertEquals("A|B$", state.draft.pattern.words)
            assertTrue(fixture.submissions.isEmpty())
        }
        for (kind in listOf(AutomaticRecordingKind.KEYWORD, AutomaticRecordingKind.TITLE)) {
            for (fullText in listOf(false, true)) {
                compose.runOnIdle {
                    state.navigation.firstLevel("matches")
                    state.updateDraft { it.copy(kind = kind, pattern = readRecordingPattern(""), fullText = fullText) }
                }
                val help = if (kind == AutomaticRecordingKind.KEYWORD) R.string.automatic_recording_new_keyword_help
                    else R.string.automatic_recording_new_title_help
                compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(help)).assertExists()
                compose.onAllNodesWithText("“”", substring = true).assertCountEquals(0)
            }
        }
    }

    @Test fun levelOpeningReleaseTickAndBackRestoreFocusAndLeaveSafely() {
        lateinit var fixture: AutomaticRecordingEditorFixture
        lateinit var state: AutomaticRecordingEditorState
        compose.setContent {
            val scope = rememberCoroutineScope()
            fixture = remember { AutomaticRecordingEditorFixture(scope) }
            state = remember { fixture.state(automaticRecordingSeries.copy(days = setOf(DayOfWeek.SATURDAY))) }
            TVHeadendPlayerTheme { AutomaticRecordingEditor(state, channels = automaticRecordingChannels) }
        }
        // Open the upcoming episodes without submitting.
        compose.onNodeWithTag("automatic-recording-main").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-done").assertIsFocused()
        compose.runOnIdle {
            assertEquals(AutomaticRecordingEditorLevel.UPCOMING, state.navigation.page.level)
            assertTrue(fixture.submissions.isEmpty())
        }
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsOn()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsFocused()
        compose.onNodeWithTag("automatic-recording-episode-1").assertIsOff()
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        // Return from the changed episode and dismiss the safe leave choice.
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("automatic-recording-keep-editing").assertIsFocused()
        compose.pressKey(Key.Back, settleFrames = 1, frameMillis = PlayerMotion.PanelMs.toLong())
        compose.onNodeWithTag("automatic-recording-matches").assertIsFocused()
        // Inspect the stored channel and normalize day selections.
        compose.runOnIdle {
            state.updateDraft { it.copy(channel = RecordingRuleChannel.SpecificChannel(ChannelId(999))) }
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-channel-999").assertIsFocused()
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(DayOfWeek.entries.toSet(), state.draft.days)
            assertEquals(DayOfWeek.entries.toSet(), state.draft.toUpdateRequest(state.baseline)?.daysOfWeekMask)
        }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(DayOfWeek.entries.toSet(), state.draft.days) }
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOn()
        compose.pressKey(Key.DirectionLeft)
        compose.runOnIdle { state.updateDraft { it.copy(days = emptySet()) } }
        compose.onNodeWithTag("automatic-recording-days").assertTextContains("No day")
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-any-day").assertIsOff()
        compose.onNodeWithTag("automatic-recording-day-1").assertIsOff()
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
        // Reset More and confirm both time-window controls.
        verifyResetRestoresFirstMoreRow(state)
        verifyMinuteChoiceReturnsToWindow(state)
        // Discard the draft: leaving closes the editor but must not count as saved.
        compose.pressKey(Key.Back)
        compose.pressKey(Key.DirectionRight)
        compose.onNodeWithTag("automatic-recording-keep-editing").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("automatic-recording-discard").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(1, fixture.closes)
            assertEquals(0, fixture.saves)
            assertTrue(fixture.submissions.isEmpty())
        }
    }

    private fun confirmationButton(tag: String) =
        compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("centred-dialog-card")))

    private fun confirmTwice() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter); pressKey(Key.DirectionCenter) }
        compose.settleFrames(1, frameMillis = PlayerMotion.PanelMs.toLong())
    }

    private fun verifyResetRestoresFirstMoreRow(state: AutomaticRecordingEditorState) {
        compose.runOnIdle { state.updateDraft { it.copy(priority = DvrPriority.HIGH) } }
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        repeat(6) { compose.pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("automatic-recording-reset-options").assertIsFocused()
        compose.pressKey(Key.DirectionCenter)
        compose.onNodeWithTag("automatic-recording-duplicate").assertIsFocused()
        compose.runOnIdle { assertEquals(state.baseline.priority, state.draft.priority) }
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
    }

    private fun verifyMinuteChoiceReturnsToWindow(state: AutomaticRecordingEditorState) {
        compose.pressKey(Key.DirectionCenter)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionDown)
        compose.pressKey(Key.DirectionCenter)
        for (after in listOf(true, false)) {
            val original = compose.runOnIdle { state.draft.startWindow }
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionDown)
            val hour = if (after) 1 else 2
            compose.onNodeWithTag("automatic-recording-hour-$hour").assertIsFocused()
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.Back)
            compose.onNodeWithTag("automatic-recording-hour-$hour").assertIsFocused()
            compose.runOnIdle { assertEquals(original, state.draft.startWindow) }
            compose.pressKey(Key.DirectionCenter)
            compose.pressKey(Key.DirectionDown)
            compose.pressKey(Key.DirectionCenter)
            val time = LocalTime.of(hour, if (after) 5 else 10)
            compose.onNodeWithTag("automatic-recording-${if (after) "after" else "before"}")
                .assertIsFocused().assertTextContains(formatClock(time))
            compose.runOnIdle {
                assertEquals(if (after) RecordingStartWindow.After(time) else RecordingStartWindow.Before(time),
                    state.draft.startWindow)
            }
        }
        compose.pressKey(Key.DirectionLeft)
        compose.pressKey(Key.DirectionLeft)
        compose.onNodeWithTag("automatic-recording-channel-time").assertIsFocused()
    }
}
