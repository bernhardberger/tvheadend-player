package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screen side of a media-session Stop: the runtime has already stopped (and filtered
 * out Stops overtaken by a newer playback request, see SessionPlaybackPlayerTest), so the
 * showing player only closes, once, through the same close-once guard as all its other
 * exits. The remote Stop key without an active target is the player screen's own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionStopCloseTest {
    @get:Rule val compose = createComposeRule()

    private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var closes = 0
    private var playbackStops = 0
    private var playerShowing by mutableStateOf(true)
    private lateinit var playerClose: PlayerClose
    private lateinit var stopButton: () -> Unit

    /** A player screen's close wiring: route close, Stop button path, session Stop. */
    private fun showPlayer() {
        compose.setContent {
            if (playerShowing) {
                val scope = rememberCoroutineScope()
                val close = rememberPlayerClose {
                    closes++
                    // The route keeps the popped screen composed during its exit transition.
                }
                playerClose = close
                stopButton = {
                    scope.launch {
                        stopPlaybackAndClose(stopPlayback = { playbackStops++ }, closePlayer = close::close)
                    }
                }
                CloseOnSessionStop(stops, close)
            }
        }
        compose.waitForIdle()
    }

    private fun emitStop() {
        stops.tryEmit(Unit)
        compose.waitForIdle()
    }

    @Test fun sessionStopClosesTheShowingPlayerOnceWithoutStoppingAgain() {
        showPlayer()
        emitStop()
        assertEquals(1, closes)
        assertEquals(0, playbackStops)
        // Still composed while fading out: a second Stop does not navigate again.
        emitStop()
        assertEquals(1, closes)
        // Browse with warm video: nobody collects, so a later Stop navigates nowhere.
        playerShowing = false
        compose.waitForIdle()
        emitStop()
        assertEquals(1, closes)
        assertEquals(0, stops.subscriptionCount.value)
    }

    @Test fun sessionStopAfterTheScreenRequestedItsCloseDoesNothing() {
        showPlayer()
        compose.runOnIdle { playerClose.close() } // Back, Close on an overlay, or a close key
        emitStop()
        assertEquals(1, closes)
    }

    @Test fun stopButtonStopsThenClosesAndASessionStopAfterwardsDoesNothing() {
        showPlayer()
        compose.runOnIdle { stopButton() }
        compose.waitForIdle()
        assertEquals(1, playbackStops)
        assertEquals(1, closes)
        emitStop()
        assertEquals(1, closes)
    }

    @Test fun stopKeyWithoutATargetStopsAndClosesAndConsumesItsWholeCycle() {
        val passed = showKeyPlayer(hasActiveTarget = false)
        press(AndroidKeyEvent.ACTION_DOWN)
        press(AndroidKeyEvent.ACTION_DOWN, repeat = 1)
        press(AndroidKeyEvent.ACTION_DOWN, repeat = 2)
        press(AndroidKeyEvent.ACTION_UP)
        assertEquals(1, playbackStops)
        assertEquals(1, closes)
        assertEquals(emptyList<KeyEventType>(), passed)
    }

    @Test fun stopKeyWithAnActiveTargetIsLeftToTheMediaSession() {
        val passed = showKeyPlayer(hasActiveTarget = true)
        press(AndroidKeyEvent.ACTION_DOWN)
        press(AndroidKeyEvent.ACTION_UP)
        assertEquals(0, playbackStops)
        assertEquals(0, closes)
        assertEquals(listOf(KeyEventType.KeyDown, KeyEventType.KeyUp), passed)
    }

    /**
     * The live screen's key order over its recovery overlay: the Stop-key helper, then the
     * layer state's key-cycle suppression and recovery routing. Returns the key events that
     * were left unhandled (they would reach the Activity and then the media session).
     */
    private fun showKeyPlayer(hasActiveTarget: Boolean): List<KeyEventType> {
        val passed = mutableListOf<KeyEventType>()
        compose.setContent {
            val state = rememberLivePlayerLayerState()
            val scope = rememberCoroutineScope()
            val close = rememberPlayerClose { closes++ }
            val stopAndClose: () -> Unit = {
                scope.launch { stopPlaybackAndClose(stopPlayback = { playbackStops++ }, closePlayer = close::close) }
            }
            val focus = remember { FocusRequester() }
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (handlePlayerStopKeyWithoutTarget(event, hasActiveTarget, state::beginOpeningKeyCycle,
                                stopAndClose)
                        ) {
                            return@onPreviewKeyEvent true
                        }
                        state.handleOverlayKey(
                            event = event,
                            keyContext = PlayerKeyContext(PlayerSurface.LIVE, controlsVisible = false,
                                seekbarFocused = false, timeshiftAvailable = false),
                            foregroundLayer = PlayerForegroundLayer.RECOVERY,
                            quickListAvailable = false,
                            onBack = {},
                            onOptionsOpened = {},
                        ) ?: false
                    },
            ) {
                Box(
                    Modifier
                        .testTag("player")
                        .focusRequester(focus)
                        .onKeyEvent { passed += it.type; false }
                        .focusable(),
                )
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        compose.waitForIdle()
        return passed
    }

    private fun press(action: Int, repeat: Int = 0) {
        val now = SystemClock.uptimeMillis()
        compose.onNodeWithTag("player").performKeyPress(
            KeyEvent(AndroidKeyEvent(now, now, action, AndroidKeyEvent.KEYCODE_MEDIA_STOP, repeat)),
        )
        compose.waitForIdle()
    }

    @Test fun openingAPlayerEntersOncePerEntryAndStaysOpen() {
        var entries = 0
        var closes = 0
        var recompositions by mutableStateOf(0)
        var entry: Long? = null
        showEntry = true
        compose.setContent {
            if (showEntry) {
                val close = rememberPlayerClose { closes++ }
                entry = rememberPlayerEntry({ entries++; entries.toLong() }, close)
                recompositions.toString()
            }
        }
        compose.waitForIdle()
        assertEquals(1, entries)
        assertEquals(1L, entry)
        recompositions++
        compose.waitForIdle()
        assertEquals(1, entries)
        showEntry = false
        compose.waitForIdle()
        showEntry = true
        compose.waitForIdle()
        assertEquals(2, entries)
        assertEquals(0, closes)
    }

    /**
     * R1 at the screen: the entry is decided while the screen first composes, before any
     * effect, so a screen entered after a user stop closes once and its gated playback
     * effects (start, retune, restore) never run; an ungated effect shows the order.
     */
    @Test fun enteringAfterAUserStopClosesBeforeAnyPlaybackEffectRuns() {
        val events = mutableListOf<String>()
        compose.setContent {
            val close = rememberPlayerClose { events += "close" }
            val entry = rememberPlayerEntry({ events += "enter"; null }, close)
            LaunchedEffect(Unit) { events += "effect" }
            if (entry != null) {
                LaunchedEffect(Unit) { events += "playback" }
            }
            LaunchedEffect(Unit) { if (entry != null) events += "retune" }
        }
        compose.waitForIdle()
        assertEquals(listOf("enter", "close", "effect"), events)
    }

    private var showEntry by mutableStateOf(true)

    /**
     * The screens need Koin, the runtime and a player, so no test composes them. This
     * whitespace-insensitive check guards that every exit of both screens goes through the
     * close-once guard (the route's `onClose` is used only to create it), that each collects
     * the session Stop into that guard, that each routes the Stop key through the helper,
     * that each notes viewing intent on entry, and that every live channel selection (each
     * new live request token) notes intent before any zap settle delay.
     */
    @Test fun bothPlayerScreensWireTheirCloseStopKeyAndViewingIntent() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, ".git").exists() }
        for ((name, runtime) in listOf("VideoPlayerScreen.kt" to "playbackRuntime", "RecordingPlayerScreen.kt" to "session")) {
            val source = File(root, "app/src/main/java/at/bernhardberger/tvhplayer/ui/player/$name").readText()
                .filterNot(Char::isWhitespace)
            val routeCloseUses = Regex("""(?<![.\w])onClose(?![\w=])""").findAll(source).map { it.value }.count()
            assertEquals(name, 2, routeCloseUses) // the parameter and rememberPlayerClose(onClose)
            assertEquals(name, 1, source.split("rememberPlayerClose(onClose)").size - 1)
            assertEquals(name, 1, source.split("CloseOnSessionStop($runtime.sessionStops,playerClose)").size - 1)
            assertEquals(name, 1, source.split("closePlayer=playerClose::close").size - 1)
            assertEquals(name, 1, source.split("handlePlayerStopKeyWithoutTarget(").size - 1)
            val entry = "valscreenEntry=rememberPlayerEntry($runtime::enterPlayerScreen,playerClose)"
            assertEquals(name, 1, source.split(entry).size - 1)
            // The entry comes first: before every effect and every playback command of the screen.
            val entryAt = source.indexOf(entry)
            for (effect in listOf("LaunchedEffect(", "DisposableEffect(", "RecordingPlaybackRouteRestorationEffect(", "$runtime.play", "videoPlayerViewModel.play")) {
                val at = source.indexOf(effect)
                assertTrue("$name $effect", at < 0 || at > entryAt)
            }
        }
        val recording = File(root, "app/src/main/java/at/bernhardberger/tvhplayer/ui/player/RecordingPlayerScreen.kt").readText()
            .filterNot(Char::isWhitespace)
        assertEquals(1, recording.split("if(screenEntry!=null&&routeSelection!=null){RecordingPlaybackRouteRestorationEffect(").size - 1)
        assertEquals(1, recording.split("RecordingPlaybackRouteRestorationEffect(").size - 2) // call + declaration
        val live = File(root, "app/src/main/java/at/bernhardberger/tvhplayer/ui/player/VideoPlayerScreen.kt").readText()
            .lines().joinToString("") { it.substringBefore("//") }.filterNot(Char::isWhitespace)
        val selections = live.split("liveRequestToken+=1L")
        assertTrue(selections.size > 1)
        for (afterSelection in selections.drop(1)) {
            // Same block as the token bump, so it runs synchronously with the selection.
            assertTrue(afterSelection.substringBefore("}").contains("liveIntent=playbackRuntime.notePlaybackIntent()"))
        }
        // The live start and the reconnect retry are gated on the entry; the delayed start
        // carries the selection's intent and is withdrawn (no failure) after a later user stop.
        assertEquals(2, live.split("if(screenEntry==null)return@LaunchedEffect").size - 1 + live.split("||screenEntry==null)return@LaunchedEffect").size - 1)
        assertEquals(1, live.split("valrequestIntent=liveIntent").size - 1)
        assertEquals(1, live.split("videoPlayerViewModel.playChannel(playbackSelection,requestIntent)").size - 1)
        assertEquals(1, live.split("withdrawn={requestIntent!=null&&playbackRuntime.isPlaybackIntentStopped(requestIntent)}").size - 1)
        // Launch requests (appliance entry, startup autoplay) note intent through their
        // hook: ApplianceLaunchRequestsTest and StartupAutoplayIntentTest.
        // Automatic stops (rejected start, connection lost) record no user stop.
        assertEquals(2, live.split("videoPlayerViewModel.stopAfterLoss()").size - 1)
        assertFalse(live.contains("videoPlayerViewModel.stop()"))
    }
}
