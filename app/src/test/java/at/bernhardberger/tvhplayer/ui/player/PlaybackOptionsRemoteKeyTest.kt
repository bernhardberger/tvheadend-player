package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.pressKey
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import at.bernhardberger.tvhplayer.core.PlaybackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.RecordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The options keys driven through [LivePlayerLayerState.openOptionsForKey] and the real
 * [PlaybackOptionsSheet], with the player root's key counting, key-cycle suppression and
 * Back routing as in VideoPlayerScreen, over a player whose tracks follow its track
 * selection parameters. Time is virtual: the main clock only moves when a test says so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalTestApi::class)
class PlaybackOptionsRemoteKeyTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var layerState: LivePlayerLayerState
    private lateinit var player: FakeTrackPlayer
    private var automaticAudioRequests = 0
    private var targetEpoch by mutableStateOf(1L)
    private var targetAvailable by mutableStateOf(true)
    private var optionsOpened = 0
    private var recovery by mutableStateOf(false)
    private val quickAudioQueue = mutableListOf<Pair<Long, TrackSelectionOverride?>>()

    @Before
    fun televisionFeature() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test
    fun audioKeyOpensOnlyTheAudioListOnTheCheckedRowAndItsKeyUpChangesNothing() {
        show()

        keyDown(Key.MediaAudioTrack)
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        assertTrue(layerState.optionsQuickList)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        compose.onNodeWithTag("playback-options-title").assertTextEquals("Audio")
        compose.onNodeWithTag("playback-options-header-back").assertDoesNotExist()
        compose.onNodeWithTag("playback-options-audio").assertDoesNotExist()

        keyUp(Key.MediaAudioTrack)
        assertNull(layerState.revealingKeyCode)
        advance(1_000)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        assertEquals(0, player.selectionChanges)

        // No way up to a root: Up on the first row stays in the list.
        press(Key.DirectionUp)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
    }

    @Test
    fun automaticRowNamesThePlayingTrackInTheShortListAndTheFullMenu() {
        show()

        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(AUTOMATIC_SUPPORT, useUnmergedTree = true).assertTextEquals("Playing: $MAIN_SUMMARY")

        press(Key.Menu)
        assertEquals(PlaybackOptionsPage.ROOT, layerState.optionsPage)
        assertFalse(layerState.optionsQuickList)
        compose.onNodeWithTag("playback-options-audio").assertIsFocused()
        press(Key.DirectionRight)
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        compose.onNodeWithTag(AUTOMATIC_SUPPORT, useUnmergedTree = true).assertTextEquals("Playing: $MAIN_SUMMARY")
        compose.onNodeWithTag("playback-options-header-back").assertExists()
    }

    @Test
    fun focusAppliesARowOnlyAfterItRestsAndFastMovesApplyOnlyTheLast() {
        show()
        press(Key.MediaAudioTrack)

        press(Key.DirectionDown)
        compose.onNodeWithTag(MAIN_ROW).assertIsFocused()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS - 100)
        assertEquals(0, player.selectionChanges)
        advance(150)
        assertEquals(listOf("main"), player.audioOverrides())
        assertEquals(1, player.selectionChanges)

        press(Key.DirectionDown)
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithTag(THIRD_ROW).assertIsFocused()
        assertEquals(1, player.selectionChanges)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(listOf("third"), player.audioOverrides())
        assertEquals(2, player.selectionChanges)
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
    }

    @Test
    fun theListKeyAgainMovesDownOneRowAndWraps() {
        show()
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()

        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(MAIN_ROW).assertIsFocused()
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(SECOND_ROW).assertIsFocused()
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(THIRD_ROW).assertIsFocused()
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        assertTrue(layerState.optionsQuickList)
        // Every stop was passed quickly; focus is back on what plays.
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun okAppliesTheFocusedRowAtOnceAndCloses() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        assertEquals(0, player.selectionChanges)

        press(Key.DirectionCenter)
        assertNull(layerState.optionsPage)
        assertFalse(layerState.optionsQuickList)
        assertEquals(listOf("second"), player.audioOverrides())
        assertEquals(1, player.selectionChanges)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS)
        assertEquals(1, player.selectionChanges)
    }

    @Test
    fun backPutsBackAutomaticAudioAndCloses() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(listOf("main"), player.audioOverrides())

        press(Key.Back)
        assertNull(layerState.optionsPage)
        assertEquals(1, automaticAudioRequests)
        assertEquals(emptyList<String>(), player.audioOverrides())
        assertTrue(player.audioAutomatic)
    }

    @Test
    fun backPutsBackAManualAudioTrackExactly() {
        show(start = { it.withOverride("second") })
        val start = player.parameters
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(SECOND_ROW).assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        // Resting on Automatic previews it the way picking it does.
        assertTrue(player.audioAutomatic)
        assertEquals(1, automaticAudioRequests)

        press(Key.Back)
        assertNull(layerState.optionsPage)
        assertEquals(1, automaticAudioRequests)
        assertEquals(start, player.parameters)
        assertEquals(listOf("second"), player.audioOverrides())
    }

    @Test
    fun backWithAnUnsettledRowAppliesNothing() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        press(Key.Back)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS)
        assertNull(layerState.optionsPage)
        assertEquals(0, player.selectionChanges)
        assertEquals(0, automaticAudioRequests)
    }

    @Test
    fun theListClosesFiveSecondsAfterTheLastKeyAndKeepsWhatPlays() {
        show()
        press(Key.MediaAudioTrack)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS - 1_000)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS - 1_000)
        // Nine seconds after opening, four after the last key: still open.
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        assertEquals(listOf("main"), player.audioOverrides())

        advance(1_100)
        assertNull(layerState.optionsPage)
        assertEquals(listOf("main"), player.audioOverrides())
        assertEquals(0, automaticAudioRequests)
    }

    @Test
    fun closingByTheOtherListKeyAppliesAPendingRowAtOnce() {
        show()
        press(Key.MediaAudioTrack)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS - 200)
        press(Key.DirectionDown)
        // The row has not rested yet; switching lists closes this one and applies it.
        press(Key.Captions)
        assertEquals(PlaybackOptionsPage.SUBTITLES, layerState.optionsPage)
        assertEquals(listOf("main"), player.audioOverrides())
        assertEquals(1, player.selectionChanges)
    }

    @Test
    fun captionsKeyPreviewsSubtitlesAndBackRestoresTheAutomaticOffState() {
        show()
        val start = player.parameters
        keyDown(Key.Captions)
        assertEquals(PlaybackOptionsPage.SUBTITLES, layerState.optionsPage)
        compose.onNodeWithTag(OFF_ROW).assertIsFocused()
        compose.onNodeWithTag("playback-options-title").assertTextEquals("Subtitles")
        compose.onNodeWithTag("playback-options-header-back").assertDoesNotExist()
        keyUp(Key.Captions)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(0, player.selectionChanges)

        press(Key.DirectionDown)
        compose.onNodeWithTag(SUBTITLE_ROW).assertIsFocused()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS - 100)
        assertEquals(0, player.selectionChanges)
        advance(150)
        assertEquals(listOf("dvb"), player.textOverrides())

        press(Key.Back)
        assertNull(layerState.optionsPage)
        // Off without a disabled text type, exactly as before; not a forced Off.
        assertEquals(start, player.parameters)
    }

    @Test
    fun backPutsBackAManualSubtitleTrackAfterPreviewingOff() {
        show(start = { it.withOverride("dvb") })
        val start = player.parameters
        press(Key.Captions)
        compose.onNodeWithTag(SUBTITLE_ROW).assertIsFocused()
        press(Key.DirectionUp)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertTrue(C.TRACK_TYPE_TEXT in player.parameters.disabledTrackTypes)

        press(Key.Back)
        assertEquals(start, player.parameters)
        assertEquals(listOf("dvb"), player.textOverrides())
    }

    @Test
    fun theSameSubtitleKeyMovesDownAndWraps() {
        show()
        press(Key.Captions)
        press(Key.Captions)
        compose.onNodeWithTag(SUBTITLE_ROW).assertIsFocused()
        press(Key.Captions)
        compose.onNodeWithTag(OFF_ROW).assertIsFocused()
    }

    @Test
    fun captionsKeyOnAChannelWithoutSubtitlesShowsOnlyTheCheckedOffRowAndWhy() {
        show(withSubtitles = false)

        press(Key.Captions)
        assertEquals(PlaybackOptionsPage.SUBTITLES, layerState.optionsPage)
        compose.onNodeWithTag(OFF_ROW).assertIsFocused()
        compose.onNodeWithTag(OFF_SUPPORT, useUnmergedTree = true).assertTextEquals("No subtitles on this channel")
        compose.onNodeWithTag("playback-options-track-empty").assertDoesNotExist()
        press(Key.DirectionDown)
        compose.onNodeWithTag(OFF_ROW).assertIsFocused()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun switchingListsKeepsWhatPlaysAndBackUndoesOnlyTheOpenList() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        press(Key.Captions)
        assertEquals(PlaybackOptionsPage.SUBTITLES, layerState.optionsPage)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(listOf("dvb"), player.textOverrides())

        press(Key.Back)
        assertNull(layerState.optionsPage)
        assertEquals(emptyList<String>(), player.textOverrides())
        assertEquals(listOf("main"), player.audioOverrides())
        assertEquals(0, automaticAudioRequests)
    }

    @Test
    fun menuOpensTheFullRootWhereFocusAppliesNothing() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.Menu)
        assertEquals(PlaybackOptionsPage.ROOT, layerState.optionsPage)
        assertFalse(layerState.optionsQuickList)
        compose.onNodeWithTag("playback-options-audio").assertIsFocused()

        press(Key.DirectionRight)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS + 1_000)
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        assertEquals(0, player.selectionChanges)

        press(Key.Back)
        assertEquals(PlaybackOptionsPage.ROOT, layerState.optionsPage)
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun recordingShortListNamesTheRecordingAndBackRestores() {
        showRecording()
        press(Key.Captions)
        compose.onNodeWithTag(OFF_ROW).assertIsFocused()
        compose.onNodeWithTag(OFF_SUPPORT, useUnmergedTree = true).assertTextEquals("No subtitles in this recording")

        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        compose.onNodeWithTag("playback-options-header-back").assertDoesNotExist()
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(listOf("main"), player.audioOverrides())

        press(Key.Back)
        compose.onNodeWithTag("playback-options-overlay").assertDoesNotExist()
        assertEquals(emptyList<String>(), player.audioOverrides())
        assertEquals(1, automaticAudioRequests)
    }

    @Test
    fun successorManualChoiceSurvivesBackOnThePredecessorsAutomaticList() {
        show()
        press(Key.MediaAudioTrack)
        compose.runOnIdle {
            targetEpoch++
            player.parameters = with(player) { parameters.withOverride("second") }
            // Back can race recomposition, while the predecessor's restore is still bound.
            layerState.quickList.restoreStart()
        }
        frames()
        assertNull(layerState.optionsPage)
        assertEquals(listOf("second"), player.audioOverrides())
        assertEquals(0, automaticAudioRequests)
    }

    @Test
    fun predecessorPendingRowNeverAppliesOnSuccessorSettleOrDisposal() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        compose.runOnIdle {
            targetEpoch++
            player.parameters = with(player) { parameters.withOverride("second") }
            player.selectionChanges = 0
        }
        frames()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 100)
        assertNull(layerState.optionsPage)
        assertEquals(listOf("second"), player.audioOverrides())
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun sameChannelNewEpochAndUnavailableTargetDismissWithoutApplying() {
        show()
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        // Track groups and channel are deliberately unchanged: epoch alone must retire the list.
        compose.runOnIdle { targetEpoch++ }
        frames()
        assertNull(layerState.optionsPage)
        assertEquals(0, player.selectionChanges)
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        compose.runOnIdle { targetAvailable = false }
        frames()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 100)
        assertNull(layerState.optionsPage)
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun fullAudioPageBecomesAFreshQuickListOnTheCheckedRow() {
        show()
        press(Key.Menu)
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        compose.onNodeWithTag(MAIN_ROW).assertIsFocused()
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(AUTOMATIC_ROW).assertIsFocused()
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 100)
        assertEquals(0, player.selectionChanges)
        press(Key.Back)
        assertNull(layerState.optionsPage)
    }

    @Test
    fun realOverlayHandlerKeepsRecoveryPrecedenceAndLeftBackAndOpeningCallback() {
        show()
        compose.runOnIdle { recovery = true }
        frames()
        press(Key.MediaAudioTrack)
        assertNull(layerState.optionsPage)
        assertEquals(0, optionsOpened)
        compose.runOnIdle { recovery = false; targetAvailable = false }
        frames()
        press(Key.MediaAudioTrack)
        assertNull(layerState.optionsPage)
        assertEquals(0, optionsOpened)
        compose.runOnIdle { targetAvailable = true; layerState.openInfo() }
        frames()
        press(Key.MediaAudioTrack)
        assertEquals(PlaybackOptionsPage.AUDIO, layerState.optionsPage)
        assertFalse(layerState.infoOpen)
        assertEquals(1, optionsOpened)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 100)
        press(Key.DirectionLeft)
        assertNull(layerState.optionsPage)
        assertTrue(player.audioOverrides().isEmpty())
    }

    @Test
    fun backOnAnUntouchedAutomaticListQueuesNothing() {
        show(queueAudio = true)
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        press(Key.DirectionUp)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)

        press(Key.Back)
        assertNull(layerState.optionsPage)
        assertTrue(quickAudioQueue.isEmpty())
        assertEquals(0, automaticAudioRequests)
        assertEquals(0, player.selectionChanges)
    }

    @Test
    fun backAfterAPreviewQueuesTheOpeningChoiceForTheOpeningTarget() {
        show(queueAudio = true, start = { it.withOverride("second") })
        press(Key.MediaAudioTrack)
        compose.onNodeWithTag(SECOND_ROW).assertIsFocused()
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        assertEquals(listOf("third"), player.audioOverrides())

        press(Key.Back)
        assertEquals(listOf(1L to "third", 1L to "second"), queuedAudio())
        assertEquals(listOf("second"), player.audioOverrides())

        quickAudioQueue.clear()
        compose.runOnIdle { player.clearAudioOverrides() }
        press(Key.MediaAudioTrack)
        press(Key.DirectionDown)
        advance(PLAYBACK_QUICK_LIST_SETTLE_MS + 50)
        press(Key.Back)
        assertEquals(listOf(1L to "main", 1L to null), queuedAudio())
        assertTrue(player.audioOverrides().isEmpty())
    }

    private fun queuedAudio() = quickAudioQueue.map { (epoch, override) -> epoch to override?.mediaTrackGroup?.id }

    private fun show(
        withSubtitles: Boolean = true,
        queueAudio: Boolean = false,
        start: FakeTrackPlayer.(TrackSelectionParameters) -> TrackSelectionParameters = { it },
    ) {
        val fake = FakeTrackPlayer(withSubtitles)
        fake.parameters = fake.start(fake.parameters)
        fake.selectionChanges = 0
        player = fake
        compose.setContent {
            TVHeadendPlayerTheme {
                val state = rememberLivePlayerLayerState()
                layerState = state
                val rootFocus = remember { FocusRequester() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val foregroundLayer = if (recovery) PlayerForegroundLayer.RECOVERY
                                else playerForegroundLayer(state.foregroundContext())
                            state.handleOverlayKey(
                                event = event,
                                foregroundLayer = foregroundLayer,
                                quickListAvailable = targetAvailable,
                                keyContext = PlayerKeyContext(
                                    surface = PlayerSurface.LIVE,
                                    controlsVisible = state.controlsVisible,
                                    seekbarFocused = false,
                                    timeshiftAvailable = true,
                                    optionsOpen = state.optionsPage != null,
                                    infoOpen = state.infoOpen,
                                    confirmationOpen = foregroundLayer == PlayerForegroundLayer.CONFIRMATION,
                                ),
                                onBack = {
                                    when (playerBackAction(PlayerSurface.LIVE, foregroundLayer)) {
                                        PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST -> {
                                            state.quickList.restoreStart()
                                            state.closeQuickList()
                                        }
                                        PlayerBackAction.RETURN_TO_OPTIONS_ROOT ->
                                            state.showOptionsPage(PlaybackOptionsPage.ROOT)
                                        PlayerBackAction.CLOSE_OPTIONS -> state.closeOptions()
                                        else -> Unit
                                    }
                                },
                                onOptionsOpened = { optionsOpened++ },
                            ) ?: false
                        }
                        .focusRequester(rootFocus)
                        .focusable(),
                ) {
                    state.optionsPage?.let { page ->
                        PlaybackOptionsSheet(
                            page = page,
                            player = fake.player,
                            tracksResolving = false,
                            aspectRatio = AspectRatioMode.FIT,
                            statsVisible = false,
                            onPageChange = state::showOptionsPage,
                            onAspectRatioChange = {},
                            onStatsVisibleChange = {},
                            audioAutomatic = fake.audioAutomatic,
                            onAutomaticAudio = {
                                automaticAudioRequests++
                                fake.clearAudioOverrides()
                            },
                            quickList = state.quickList.takeIf { state.optionsQuickList },
                            onQuickListClose = state::closeQuickList,
                            quickListTargetEpoch = targetEpoch,
                            quickListAvailable = targetAvailable,
                            isQuickListTargetCurrent = { it == targetEpoch && targetAvailable },
                            // As the runtime's queue: record the command, then apply it.
                            onQuickAudioSelection = if (queueAudio) { epoch, override ->
                                quickAudioQueue += epoch to override
                                fake.parameters = fake.parameters.buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                    .apply { override?.let(::addOverride) }
                                    .build()
                            } else null,
                        )
                    }
                }
                LaunchedEffect(Unit) { rootFocus.requestFocus() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun recordingControlsReturnToExactOriginAcrossListsAndEveryCloseKind() {
        showRecording(controlsVisible = true)
        for ((origin, close) in listOf(
            "player-stop" to Key.Back,
            "player-info" to Key.DirectionCenter,
            "player-settings" to null,
            "recording-seekbar" to Key.Back,
        )) {
            compose.onNodeWithTag(origin).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            frames()
            compose.onNodeWithTag(origin).assertIsFocused()
            press(Key.MediaAudioTrack)
            press(Key.Captions)
            if (close == null) {
                advance(PLAYBACK_QUICK_LIST_AUTO_CLOSE_MS + 100)
                frames()
            } else press(close)
            compose.onNodeWithTag(origin).assertIsFocused()
        }
    }

    /** Real recording controls are removed/recomposed exactly as in the screen. */
    private fun showRecording(controlsVisible: Boolean = false) {
        val loader = coil3.ImageLoader.Builder(androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>())
            .diskCache(null).build()
        val fake = FakeTrackPlayer(withSubtitles = false)
        fake.selectionChanges = 0
        player = fake
        compose.setContent {
            TVHeadendPlayerTheme {
                var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
                var optionsQuickList by remember { mutableStateOf(false) }
                var revealingKeyCode by remember { mutableStateOf<Int?>(null) }
                val quickList = remember { PlaybackQuickListSignals() }
                val rootFocus = remember { FocusRequester() }
                var lastControl by remember { mutableStateOf("player-pause") }
                var returnControl by remember { mutableStateOf<String?>(null) }
                var restoreControl by remember { mutableStateOf<String?>(null) }
                fun closeQuickList() {
                    restoreControl = returnControl.takeIf { controlsVisible }
                    returnControl = null
                    optionsQuickList = false
                    optionsPage = null
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val keyCode = event.nativeKeyEvent.keyCode
                            if (event.type == KeyEventType.KeyDown && optionsQuickList) quickList.onKeyDown()
                            if (revealingKeyCode == keyCode) {
                                if (event.type == KeyEventType.KeyUp) revealingKeyCode = null
                                return@onPreviewKeyEvent true
                            }
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (event.key == Key.Back) {
                                revealingKeyCode = keyCode
                                if (optionsQuickList) {
                                    quickList.restoreStart()
                                    closeQuickList()
                                }
                                return@onPreviewKeyEvent true
                            }
                            val action = recordingPlaybackKeyAction(controlsVisible = false, keyCode = keyCode)
                            if (action != RecordingPlaybackKeyAction.OPEN_OPTIONS) return@onPreviewKeyEvent false
                            revealingKeyCode = keyCode
                            when (
                                val outcome = playbackOptionsKeyOutcome(
                                    keyCode,
                                    optionsPage.takeIf { optionsQuickList },
                                )
                            ) {
                                PlaybackOptionsKeyOutcome.OpenMenu -> {
                                    optionsQuickList = false
                                    optionsPage = PlaybackOptionsPage.ROOT
                                }
                                PlaybackOptionsKeyOutcome.MoveDown -> quickList.moveDown()
                                is PlaybackOptionsKeyOutcome.OpenQuickList -> {
                                    if (!optionsQuickList) returnControl = lastControl.takeIf { controlsVisible }
                                    optionsQuickList = true
                                    optionsPage = outcome.page
                                }
                                null -> Unit
                            }
                            true
                        }
                        .focusRequester(rootFocus)
                        .focusable(),
                ) {
                    PlayerControlsLayer(visible = controlsVisible, modalVisible = optionsPage != null) {
                        RecordingOverlayControls(
                            imageLoader = loader,
                            piconPath = null, title = "Recording", subtitle = null, channelName = null,
                            positionMs = 10_000, durationMs = 100_000, growing = false, nowSec = 0,
                            canSeek = true, controlsVisible = controlsVisible, optionsOpen = optionsPage != null,
                            onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                            onOpenOptions = {}, onOpenInfo = {},
                            restoreQuickListControl = restoreControl,
                            onQuickListFocusRestored = { restoreControl = null },
                            onControlFocused = { lastControl = it },
                        )
                    }
                    optionsPage?.let { page ->
                        PlaybackOptionsSheet(
                            page = page,
                            player = fake.player,
                            tracksResolving = false,
                            aspectRatio = AspectRatioMode.FIT,
                            statsVisible = false,
                            onPageChange = { optionsPage = it },
                            onAspectRatioChange = {},
                            onStatsVisibleChange = {},
                            audioAutomatic = fake.audioAutomatic,
                            onAutomaticAudio = {
                                automaticAudioRequests++
                                fake.clearAudioOverrides()
                            },
                            quickList = quickList.takeIf { optionsQuickList },
                            onQuickListClose = ::closeQuickList,
                            recording = true,
                        )
                    }
                }
                LaunchedEffect(Unit) { if (!controlsVisible) rootFocus.requestFocus() }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun frames() {
        repeat(4) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis, ignoreFrameDuration = true)
        compose.waitForIdle()
    }

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        frames()
    }

    private fun keyDown(key: Key) {
        compose.onRoot().performKeyInput { keyDown(key) }
        frames()
    }

    private fun keyUp(key: Key) {
        compose.onRoot().performKeyInput { keyUp(key) }
        frames()
    }

    /** Tracks follow the parameters: an override selects its track, a disabled type none. */
    private class FakeTrackPlayer(withSubtitles: Boolean) {
        private val groups = listOfNotNull(
            TrackSpec(group("main", MimeTypes.AUDIO_MPEG_L2, "de", 2), defaultSelected = true),
            TrackSpec(group("second", MimeTypes.AUDIO_AC3, "en", 6), defaultSelected = false),
            TrackSpec(group("third", MimeTypes.AUDIO_AAC, "fr", 2), defaultSelected = false),
            TrackSpec(group("dvb", MimeTypes.APPLICATION_DVBSUBS, "de", null), defaultSelected = false)
                .takeIf { withSubtitles },
        )
        private val listeners = mutableListOf<Player.Listener>()
        var selectionChanges = 0
        var audioAutomatic by mutableStateOf(true)
            private set
        var parameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT
            set(value) {
                field = value
                selectionChanges++
                audioAutomatic = value.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO }
                val tracks = tracks()
                listeners.toList().forEach {
                    it.onTrackSelectionParametersChanged(value)
                    it.onTracksChanged(tracks)
                }
            }

        val player: Player = java.lang.reflect.Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getCurrentTracks" -> tracks()
                "getTrackSelectionParameters" -> parameters
                "setTrackSelectionParameters" -> {
                    parameters = args!![0] as TrackSelectionParameters
                    Unit
                }
                "addListener" -> {
                    listeners += args!![0] as Player.Listener
                    Unit
                }
                "removeListener" -> {
                    listeners -= args!![0] as Player.Listener
                    Unit
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "QuickListTestPlayer"
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player

        fun TrackSelectionParameters.withOverride(id: String): TrackSelectionParameters {
            val spec = groups.single { it.group.id == id }
            return buildUpon()
                .clearOverridesOfType(spec.group.type)
                .addOverride(TrackSelectionOverride(spec.group, 0))
                .build()
        }

        fun clearAudioOverrides() {
            parameters = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        }

        fun audioOverrides(): List<String> = overrides(C.TRACK_TYPE_AUDIO)

        fun textOverrides(): List<String> = overrides(C.TRACK_TYPE_TEXT)

        private fun overrides(type: Int) =
            parameters.overrides.values.filter { it.type == type }.map { it.mediaTrackGroup.id }

        private fun tracks() = Tracks(
            groups.map { spec ->
                val type = spec.group.type
                val typeOverride = parameters.overrides.values.firstOrNull { it.type == type }
                val selected = when {
                    type in parameters.disabledTrackTypes -> false
                    typeOverride != null -> typeOverride.mediaTrackGroup == spec.group
                    else -> spec.defaultSelected
                }
                Tracks.Group(spec.group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))
            },
        )
    }

    private class TrackSpec(val group: TrackGroup, val defaultSelected: Boolean)

    private companion object {
        // Track keys are "<track type>:<group id>:<format id>".
        const val AUTOMATIC_ROW = "playback-options-track-automatic"
        const val AUTOMATIC_SUPPORT = "playback-options-track-support-automatic"
        const val MAIN_ROW = "playback-options-track-1:main:main"
        const val SECOND_ROW = "playback-options-track-1:second:second"
        const val THIRD_ROW = "playback-options-track-1:third:third"
        const val SUBTITLE_ROW = "playback-options-track-3:dvb:dvb"
        const val OFF_ROW = "playback-options-subtitles-off"
        const val OFF_SUPPORT = "playback-options-subtitles-off-support"
        const val MAIN_SUMMARY = "German · Stereo"

        fun group(id: String, mime: String, language: String, channels: Int?) = TrackGroup(
            id,
            Format.Builder()
                .setId(id)
                .setSampleMimeType(mime)
                .setLanguage(language)
                .apply { if (channels != null) setChannelCount(channels) }
                .build(),
        )
    }
}
