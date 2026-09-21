package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickZapPresentationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var layers: LivePlayerLayerState
    private lateinit var view: View
    private var playing by mutableStateOf(ChannelId(2))
    private var picks = 0
    private val channels = (1L..12L).map {
        Channel.create(id = ChannelId(it), number = it, name = if (it == 4L) "Dokumentation und Zeitgeschichte HD" else "Channel $it",
            icon = if (it == 5L) null else "imagecache/$it")
    }

    @Test fun peekOpensWithoutTuningAndDifferentPickKeepsFocusUntilPlayingPick() {
        content()
        compose.onNodeWithTag("player-info").assertIsFocused()
        compose.onNodeWithTag("player-channel-card-2").assertDoesNotExist()
        capture("en-font1-peek")
        key(Key.DirectionDown)
        card(2).assertIsFocused()
        assertEquals(0, picks)
        compose.onNodeWithTag("player-info").assertDoesNotExist()
        val focusedBounds = card(2).fetchSemanticsNode().boundsInRoot
        val progressBounds = compose.onNodeWithTag("player-channel-2-progress", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        // Card semantics describe its unscaled layout; descendants include the native focus transform.
        val piconBounds = compose.onNodeWithTag("player-channel-2-picon", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val scale = piconBounds.width / 100f
        val paintedBottom = focusedBounds.center.y + focusedBounds.height * scale / 2f
        val paintedLeft = focusedBounds.center.x - focusedBounds.width * scale / 2f
        assertEquals("progress stays embedded at the bottom", paintedBottom, progressBounds.bottom, .5f)
        assertEquals("progress stays full width", paintedLeft, progressBounds.left, .5f)
        assertTrue("progress remains a visible strip", progressBounds.height >= 2f)
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val pixel = bitmap.getPixel(
                (progressBounds.left + progressBounds.width * .1f).toInt(),
                (progressBounds.bottom - 1f).toInt(),
            )
            assertTrue("cyan progress is painted above the bottom edge, not covered by the gray ring",
                android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel) + 30)
            bitmap.recycle()
        }
        capture("en-font1-expanded")
        key(Key.DirectionRight)
        card(3).assertIsFocused()
        val before = card(3).fetchSemanticsNode().boundsInRoot
        key(Key.DirectionCenter)
        card(3).assertIsFocused()
        assertEquals(1, picks)
        assertEquals(before, card(3).fetchSemanticsNode().boundsInRoot)
        assertTrue(layers.channelDrawerOpen)
        key(Key.DirectionCenter)
        assertFalse(layers.channelDrawerOpen)
        compose.onNodeWithTag("player-info").assertIsFocused()
        assertEquals(1, picks)
    }

    @Test fun upAndBackRestoreControlsAndNextDownStillOpens() {
        content()
        for (close in listOf(Key.DirectionUp, Key.Back)) {
            key(Key.DirectionDown)
            card(2).assertIsFocused()
            key(close)
            compose.onNodeWithTag("player-info").assertIsFocused()
            assertFalse(layers.channelDrawerOpen)
        }
        assertEquals(0, picks)
    }

    @Test fun horizontalViewportReachesScreenEdgesButFocusedCardsStaySafe() {
        content()
        key(Key.DirectionDown)
        repeat(7) { key(Key.DirectionRight) }
        val focused = card(9).assertIsFocused().fetchSemanticsNode().boundsInRoot
        assertTrue("focused left safe: $focused", focused.left >= 48f)
        assertTrue("focused right safe: $focused", focused.right <= 912f)
        val shelf = compose.onNodeWithTag("player-channel-shelf").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, shelf.left, .5f)
        assertEquals(960f, shelf.right, .5f)
        assertTrue("bottom third: $focused", focused.top >= 360f)
        assertTrue("bottom focus overflow inside screen: $focused", focused.bottom <= 520f)
        capture("en-font1-scrolled")
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun largeLocalizedCardsStayCompactAndMissingEpgHasNoInventedProgress() {
        content(fontScale = 1.3f, bright = true)
        key(Key.DirectionDown)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        card(4).assertIsFocused()
        val bounds = card(4).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= 360f)
        assertTrue(bounds.bottom <= 520f)
        compose.onAllNodes(
            hasAnyAncestor(hasTestTag("player-channel-card-5")) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        capture("de-font1.3-expanded-bright")
    }

    @Test fun transitionKeepsTheOpeningPressFromActivatingCards() {
        content()
        compose.mainClock.autoAdvance = false
        key(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(96)
        capture("en-font1-opening")
        assertEquals(0, picks)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        card(2).assertIsFocused()
    }

    private fun content(fontScale: Float = 1f, bright: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            layers = rememberLivePlayerLayerState()
            val context = LocalContext.current
            val session = remember {
                FakeSessionObservation(SessionObservation.create(
                    sessionState = SessionState.Ready(ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
                    )),
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
                )).captureCurrentSession()
            }
            val loader = remember {
                ImageLoader.Builder(context)
                    .components { add(object : Mapper<AppArtworkSource, ByteArray> {
                        override fun map(data: AppArtworkSource, options: Options): ByteArray {
                            val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xFF79D1FF.toInt(); textSize = 64f; isFakeBoldText = true
                            }
                            Canvas(bitmap).drawText("TV ${data.selector.substringAfterLast('/')}", 12f, 68f, paint)
                            return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        }
                    }) }
                    .coroutineContext(Dispatchers.Main.immediate)
                    .fetcherCoroutineContext(Dispatchers.Main.immediate)
                    .decoderCoroutineContext(Dispatchers.Main.immediate)
                    .diskCache(null).build()
            }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(if (bright) Color(0xFFDDEAF0) else Color(0xFF24384A))
                        .onPreviewKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            when {
                                layers.revealingKeyCode == code -> {
                                    if (event.type == KeyEventType.KeyUp) layers.endOpeningKeyCycle(code)
                                    true
                                }
                                event.key == Key.Back && layers.channelDrawerOpen -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        layers.beginOpeningKeyCycle(code)
                                        layers.dismissChannelDrawer()
                                    }
                                    true
                                }
                                else -> false
                            }
                        }) {
                        OverlayControlsTv(
                            imageLoader = loader, currentSession = session, channelNumber = playing.value.toInt(), channelName = "Channel ${playing.value}",
                            piconPath = "imagecache/${playing.value}", nowEvent = event(playing), nextEvent = null, nowSec = 900,
                            controlsVisible = layers.controlsVisible, optionsOpen = false,
                            onOpenChannels = {
                                layers.beginOpeningKeyCycle(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
                                layers.openChannelDrawer()
                            },
                            onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = AppTimeshiftState(), timeshiftFeedback = null,
                            onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                            restoreChannelAction = layers.restoreChannelAction,
                            onChannelActionRestored = layers::onChannelActionRestored,
                            onActionFocused = layers::onActionFocused,
                            channelRailOpen = layers.channelDrawerOpen,
                            channelRailContent = {
                                ChannelDrawer(
                                    channels = channels, selectedId = playing, playingChannelId = playing,
                                    recordingChannelIds = setOf(ChannelId(4)), nowEvent = { if (it == ChannelId(5)) null else event(it) },
                                    imageLoader = loader, currentSession = session, active = layers.channelDrawerOpen, nowSec = 900,
                                    onFocusChannel = {}, onPickChannel = {
                                        if (it.id == playing) layers.dismissChannelDrawer() else {
                                            playing = it.id
                                            picks++
                                            layers.onChannelTuneRequested()
                                        }
                                    },
                                    onCloseDrawer = {
                                        if (it != null) layers.beginOpeningKeyCycle(it)
                                        layers.dismissChannelDrawer()
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun event(id: ChannelId) = EpgEvent.create(
        id = EventId(id.value), channelId = id, title = "A journey through the mountains",
        start = Instant.fromEpochSeconds(0), stop = Instant.fromEpochSeconds(3600),
    )
    private fun card(id: Int) = compose.onNodeWithTag("player-channel-card-$id")
    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val directory = File("../artifacts/quick-zap").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
