package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlayFooterGradientRunout
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Live footer compaction evidence. Captures are composition only: 960×540 px,
 * density 1.0 (mdpi). Focus feel, overscan and readability over motion remain
 * physical-TV gates.
 *
 * `en-font1.0-…-live-normal-focus-pause` — locale en, font 1.0, pause focused.
 * `en-font1.0-…-behind-live-focus-go-live` — locale en, font 1.0, Go live focused.
 * `en-font1.0-…-seeking-preview-focus-seekbar` — locale en, font 1.0, seekbar focused.
 * `de-font1.3-…-error-go-live-peek` — locale de, font 1.3, Go live focused, peek visible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerFooterLayoutEvidenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private var defaultZone: TimeZone? = null

    @Before fun pinClock() {
        defaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreClock() {
        defaultZone?.let(TimeZone::setDefault)
    }

    @Test
    fun sharedChromeUses56RunoutAnd36BottomInset() {
        assertEquals(56.dp, TvOverlayFooterGradientRunout)
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerOverlayChrome(headerContent = {}) {
                    Box(Modifier.fillMaxWidth().height(20.dp))
                }
            }
        }
        val footer = bounds("player-footer")
        assertEquals(540f, footer.bottom, 1f)
        assertEquals(px(56.dp + 36.dp + 20.dp).toDouble(), footer.height.toDouble(), 1.0)
    }

    @Test
    fun recordingTimelineUsesSharedInlineEndpointRow() {
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerTimelineBlock(
                    progress = 0.4f,
                    tone = PlayerTimelineTone.INTERACTIVE,
                    leadingLabel = "0:12",
                    trailingLabel = "1:00",
                )
            }
        }
        val track = bounds("player-timeline-track")
        val labels = bounds("player-timeline-labels")
        assertEquals(labels.center.y, track.center.y, 1f)
        assertTrue(labels.left <= track.left + 1f)
        assertTrue(labels.right >= track.right - 1f)
    }

    @Test
    fun statusOverlayDoesNotMoveTimelineOrActionsAndGoLiveStaysAbove() {
        var behind by mutableStateOf(false)
        var feedback by mutableStateOf<String?>(null)
        var previewing by mutableStateOf(false)
        show(
            behind = { behind },
            feedback = { feedback },
            previewing = { previewing },
            fontScale = 1f,
        )
        compose.onNodeWithTag("player-pause").assertIsFocused()
        val track = bounds("player-timeline-track")
        val actions = bounds("player-actions")
        val seekbar = bounds("player-seekbar")
        assertEquals(px(56.dp).toDouble(), (seekbar.top - bounds("player-footer").top).toDouble(), 1.0)
        assertEquals(px(48.dp), bounds("player-pause").height, 1f)
        assertInline(track)

        compose.runOnIdle { behind = true }
        assertEquals(track, bounds("player-timeline-track"))
        assertEquals(actions, bounds("player-actions"))
        val goLive = bounds("player-go-live")
        assertTrue(goLive.bottom <= track.top + 1f)
        assertEquals(actions.right, goLive.right, 1f)
        assertTrue(
            "focus overflow above Go live",
            goLive.top - bounds("player-footer").top >= px(8.dp),
        )
        assertTrue(track.top - goLive.bottom >= px(4.dp))

        compose.runOnIdle { feedback = "Reached the available buffer limit" }
        assertEquals(track, bounds("player-timeline-track"))
        assertEquals(actions, bounds("player-actions"))
        assertEquals(goLive, bounds("player-go-live"))
        val message = bounds("player-window-title")
        assertTrue(message.right + px(8.dp) <= goLive.left)
        assertTrue(message.center.y >= goLive.top && message.center.y <= goLive.bottom)

        compose.runOnIdle { feedback = null; previewing = true }
        key(Key.DirectionUp)
        assertEquals(track.top, bounds("player-timeline-track").top, 1f)
        assertEquals(track.bottom, bounds("player-timeline-track").bottom, 1f)
        compose.onNodeWithTag("player-actions").assertDoesNotExist()
        compose.onNodeWithTag("player-go-live").assertDoesNotExist()
        val target = bounds("timeshift-preview-target")
        val progress = compose.onNodeWithTag("player-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        val resting = bounds("player-timeline-track")
        assertEquals(resting.left + resting.width * progress, target.center.x, 1.5f)
        assertTrue(target.left >= resting.left - 1f && target.right <= resting.right + 1f)
        assertTrue(target.bottom <= resting.top + 1f)
        assertTrue(bounds("player-timeline-status").contains(target.center))
        compose.onNodeWithTag("player-seekbar").assertExists()
    }

    @Test
    fun upFromTheTimelineReachesGoLiveWithoutMovingItIntoTheActionRow() {
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(
            behind = { true },
            feedback = { null },
            previewing = { false },
            fontScale = 1f,
            backdrop = { backdrop.value },
        )
        compose.onNodeWithTag("player-pause").assertIsFocused()
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-go-live").assertIsFocused()
        assertTrue(bounds("player-go-live").bottom <= bounds("player-timeline-track").top + 1f)
        assertTrue(bounds("player-go-live").bottom < bounds("player-actions").top)
        recordPair("en-font1.0-960x540-mdpi-d1", "behind-live-focus-go-live", "player-go-live", backdrop)
    }

    @Test
    fun captureLiveNormal() {
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(behind = { false }, feedback = { null }, previewing = { false }, fontScale = 1f, backdrop = { backdrop.value })
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.onNodeWithTag("player-live-status").assertDoesNotExist()
        compose.onNodeWithTag("player-clock-status").assertExists()
        compose.onNodeWithTag("player-go-live").assertDoesNotExist()
        assertInline(bounds("player-timeline-track"))
        recordPair("en-font1.0-960x540-mdpi-d1", "live-normal-focus-pause", "player-pause", backdrop)
    }

    @Test
    fun captureSeekingPreview() {
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(behind = { true }, feedback = { null }, previewing = { true }, fontScale = 1f, backdrop = { backdrop.value })
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertExists()
        compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-go-live").assertDoesNotExist()
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        recordPair("en-font1.0-960x540-mdpi-d1", "seeking-preview-focus-seekbar", "player-seekbar", backdrop, expectPeek = false)
    }

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun liveSharedFocusViewEvidenceAndPendingExit() = sharedFocusView(recording = false)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun recordingSharedFocusViewEvidenceAndPendingExit() = sharedFocusView(recording = true)

    private fun sharedFocusView(recording: Boolean) {
        var previewing by mutableStateOf(false)
        var standalone by mutableStateOf(false)
        var commits = 0
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(behind = { true }, feedback = { null }, previewing = { previewing }, fontScale = 1.3f,
            backdrop = { backdrop.value }, recording = recording, standalone = { standalone },
            onCommitSeek = { commits++ })
        val kind = if (recording) "recording" else "live"
        val timelineTag = if (recording) "recording-seekbar" else "player-seekbar"
        val actionsTag = if (recording) "recording-actions" else "player-actions"
        val resting = bounds("player-timeline-track")
        assertEquals(4f, bounds(actionsTag).top - bounds(timelineTag).bottom, 1f)
        assertEquals(504f, bounds(actionsTag).bottom, 1f)
        recordPair("de-font1.3-960x540-mdpi-d1", "$kind-resting", "player-pause", backdrop, !recording)
        key(Key.DirectionUp)
        compose.onNodeWithTag(timelineTag).assertIsFocused()
        recordPair("de-font1.3-960x540-mdpi-d1", "$kind-focused", timelineTag, backdrop, false)
        compose.runOnIdle { previewing = true }
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertExists()
        recordPair("de-font1.3-960x540-mdpi-d1", "$kind-seeking", timelineTag, backdrop, false)
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertTrue(previewing) // Owner deliberately leaves the dispatched seek pending.
        assertEquals(1, commits)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithTag(if (recording) "player-pause" else "player-go-live").assertIsFocused()
        assertTrue(previewing)
        assertEquals(2, commits)
        compose.runOnIdle { standalone = true }
        val previewTrack = bounds("player-timeline-track")
        assertEquals(resting.top, previewTrack.top, 1f)
        assertEquals(resting.bottom, previewTrack.bottom, 1f)
        assertEquals(resting.left, previewTrack.left, 1f)
        assertEquals(resting.right, previewTrack.right, 1f)
    }

    @Test fun pendingRecordingSeekCanOpenAndDismissMarkers() {
        var commits = 0
        show(behind = { true }, feedback = { null }, previewing = { true }, fontScale = 1.3f,
            recording = true, markers = listOf(0L, 900_000L, 2_700_000L), onCommitSeek = { commits++ })
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithTag("recording-marker-target").assertIsFocused()
        assertEquals(1, commits)
        key(Key.Back)
        compose.onNodeWithTag("recording-seekbar").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun recordingMarkerUsesMeasuredTrackAtNormalFont() = markerTrackGeometry(1f)

    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun recordingMarkerUsesMeasuredTrackAtLargeFont() = markerTrackGeometry(1.3f)

    private fun markerTrackGeometry(fontScale: Float) {
        val navigation = RecordingMarkerNavigation()
        val seeks = mutableListOf<Long>()
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(behind = { true }, feedback = { null }, previewing = { true }, fontScale = fontScale,
            recording = true, backdrop = { backdrop.value },
            markers = listOf(0L, 900_000L, 2_700_000L, 3_599_999L),
            markerNavigation = navigation, markerRevision = 42L, onSeekMarker = seeks::add)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        val track = bounds("player-timeline-track")
        assertTrue(track.left > bounds("recording-seekbar").left + 8f)
        assertTrue(track.right < bounds("recording-seekbar").right - 8f)
        fun assertMarker(fraction: Float) {
            compose.onNodeWithTag("recording-marker-target").assertIsFocused()
            assertEquals(42L, navigation.ownerRevision)
            val button = bounds("recording-marker-target")
            val label = bounds("recording-marker-overlay")
            val tick = bounds("recording-selected-marker")
            val position = track.left + track.width * fraction
            assertEquals(position.coerceIn(track.left + 0.5f, track.right - 0.5f), tick.center.x, 1f)
            assertEquals(position.coerceIn(track.left + button.width / 2f, track.right - button.width / 2f), label.center.x, 1f)
            assertTrue(label.left >= track.left && label.right <= track.right)
            assertTrue(button.left >= track.left - 1f && button.right <= track.right + 1f)
        }
        assertMarker(0.75f)
        recordPair(if (fontScale == 1f) "en-font1.0-960x540-mdpi-d1" else "de-font1.3-960x540-mdpi-d1",
            "recording-marker-selected-75", "recording-marker-target", backdrop, expectPeek = false)
        key(Key.DirectionLeft)
        assertMarker(0.25f)
        key(Key.DirectionLeft)
        assertMarker(0f)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        assertMarker(3_599_999f / 3_600_000f)
        key(Key.DirectionUp)
        assertMarker(3_599_999f / 3_600_000f)
        key(Key.Back)
        compose.onNodeWithTag("recording-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        assertMarker(0.75f)
        key(Key.DirectionDown)
        compose.onNodeWithTag("recording-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionCenter)
        assertEquals(listOf(2_700_000L), seeks)
        compose.onNodeWithTag("recording-seekbar").assertIsFocused()
    }

    @Test fun livePreviewDismissalRestoresChromeWithoutMovingTimelineFocus() = previewDismissal(false)

    @Test fun recordingPreviewDismissalRestoresChromeWithoutMovingTimelineFocus() = previewDismissal(true)

    private fun previewDismissal(recording: Boolean) {
        var preview by mutableStateOf(true)
        show(behind = { true }, feedback = { null }, previewing = { preview }, fontScale = 1.3f, recording = recording)
        key(Key.DirectionUp)
        val timeline = if (recording) "recording-seekbar" else "player-seekbar"
        val header = if (recording) "recording-title" else "player-programme-title"
        compose.onNodeWithTag(timeline).assertIsFocused()
        compose.onNodeWithTag(header).assertExists()
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        // Both ancestor Back callbacks synchronously clear the presented preview.
        compose.runOnIdle { preview = false }
        compose.onNodeWithTag(timeline).assertIsFocused()
        compose.onNodeWithTag(header).assertExists()
        compose.onNodeWithTag("player-pause").assertExists()
        compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertDoesNotExist()
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun hidingControlsTransfersFocusBeforeExitFadeCompletes() {
        var visible by mutableStateOf(true)
        compose.setContent {
            val rootFocus = remember { FocusRequester() }
            val actionFocus = remember { FocusRequester() }
            PlayerRootFocusEffect(if (visible) PlayerForegroundLayer.CONTROLS else PlayerForegroundLayer.NONE, rootFocus)
            androidx.compose.runtime.LaunchedEffect(Unit) { actionFocus.requestFocus() }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().testTag("back-root")
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Back) {
                            if (event.type == KeyEventType.KeyDown) visible = false
                            true
                        } else false
                    }.focusRequester(rootFocus).focusable()) {
                    PlayerControlsLayer(visible = visible, modalVisible = false) {
                        androidx.tv.material3.Button(onClick = {},
                            modifier = Modifier.testTag("departing-action").focusRequester(actionFocus)) {
                            androidx.tv.material3.Text("Pause")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("departing-action").assertIsFocused()
        compose.mainClock.autoAdvance = false
        key(Key.Back)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("back-root").assertIsFocused()
        compose.mainClock.advanceTimeBy(500L)
        compose.onNodeWithTag("departing-action").assertDoesNotExist()
        compose.onNodeWithTag("back-root").assertIsFocused()
    }

    @Test fun chromeAlphaAnimatesAndPendingPreviewDoesNotOverrideDestination() {
        var focused by mutableStateOf(false)
        var preview by mutableStateOf(false)
        lateinit var alpha: androidx.compose.runtime.State<Float>
        compose.setContent { alpha = rememberPlayerChromeAlpha(focused, preview) }
        assertEquals(1f, alpha.value, 0.001f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { focused = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        assertTrue("intermediate alpha=${alpha.value}", alpha.value > 0.55f && alpha.value < 1f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0.55f, alpha.value, 0.001f)
        compose.runOnIdle { preview = true }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(0f, alpha.value, 0.001f)
        compose.runOnIdle { focused = false }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        assertTrue(alpha.value > 0f && alpha.value < 1f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(1f, alpha.value, 0.001f)
    }

    @Test fun idlePauseIsTransparentButNativeFocusAndPressStillPaint() {
        show(behind = { false }, feedback = { null }, previewing = { false }, fontScale = 1f)
        val pause = bounds("player-pause")
        fun sample(x: Float, y: Float): Int = draw().let { bitmap ->
            bitmap.getPixel(x.toInt(), y.toInt()).also { bitmap.recycle() }
        }
        val focused = sample(pause.center.x, pause.top + 8f)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        val pressed = sample(pause.center.x, pause.top + 8f)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        key(Key.DirectionRight)
        compose.onNodeWithTag("player-stop").assertIsFocused()
        val idle = sample(pause.center.x, pause.top + 8f)
        assertEquals(sample(pause.left - 8f, pause.top + 8f), idle)
        assertTrue(focused != idle)
        assertTrue(pressed != idle)
    }

    @Test fun endpointTimesAnimateFromTertiaryToFullForActiveAndPreview() {
        var tone by mutableStateOf(PlayerTimelineTone.INTERACTIVE)
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    PlayerTimelineBlock(progress = 0.5f, tone = tone,
                        leadingLabel = "0:30", trailingLabel = "1:00",
                        leadingLabelTestTag = "endpoint", trailingLabelTestTag = "end")
                }
            }
        }
        fun brightness(): Long {
            val rect = bounds("endpoint")
            val bitmap = draw()
            var sum = 0L
            for (y in rect.top.toInt() until rect.bottom.toInt()) {
                for (x in rect.left.toInt() until rect.right.toInt()) sum += android.graphics.Color.red(bitmap.getPixel(x, y))
            }
            bitmap.recycle()
            return sum
        }
        val resting = brightness()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { tone = PlayerTimelineTone.ACTIVE }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val intermediate = brightness()
        compose.mainClock.advanceTimeBy(240)
        val active = brightness()
        assertTrue("brightness resting=$resting intermediate=$intermediate active=$active", resting < intermediate && intermediate < active)
        compose.runOnIdle { tone = PlayerTimelineTone.PREVIEW }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(active, brightness())
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun germanLargeTextKeepsFeedbackDistinctFromGoLiveAndPeeksQuickZap() {
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(
            behind = { true },
            feedback = { "Diese Position ist nicht mehr im Puffer." },
            previewing = { false },
            fontScale = 1.3f,
            error = true,
            backdrop = { backdrop.value },
        )
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-go-live").assertIsFocused()
        compose.onNodeWithText("Zu Live").assertExists()
        val feedback = compose.onNodeWithText("Diese Position ist nicht mehr im Puffer.")
        feedback.assertExists()
        val message = feedback.fetchSemanticsNode().boundsInRoot
        val goLive = bounds("player-go-live")
        val track = bounds("player-timeline-track")
        assertTrue(message.right + px(8.dp) <= goLive.left)
        assertTrue(goLive.bottom <= track.top + 1f)
        assertTrue(goLive.top >= 0f && goLive.bottom <= 540f)
        assertTrue(message.top >= 0f && message.bottom <= 540f)
        val layouts = mutableListOf<TextLayoutResult>()
        feedback.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(!layouts.single().isLineEllipsized(0))
        assertEquals(1, layouts.single().lineCount)
        assertInline(track)
        recordPair("de-font1.3-960x540-mdpi-d1", "error-go-live-peek", "player-go-live", backdrop)
    }

    @Test
    fun recordingPauseChangesSemanticsNotEndpointTextOrBounds() {
        var paused by mutableStateOf(false)
        val backdrop = mutableStateOf(Backdrop.TEXTURED)
        show({ false }, { null }, { false }, 1f, recording = true,
            paused = { paused }, backdrop = { backdrop.value })
        val labels = compose.onNodeWithTag("player-timeline-labels", useUnmergedTree = true)
        val text = compose.onNodeWithText("0:30:00", useUnmergedTree = true)
        val original = text.fetchSemanticsNode().boundsInRoot
        val track = bounds("player-timeline-track")
        recordPair("en-font1.0-960x540-mdpi-d1", "recording-playing-endpoints", "player-pause", backdrop, false)
        compose.runOnIdle { paused = true }
        assertEquals(original, text.fetchSemanticsNode().boundsInRoot)
        assertEquals(track, bounds("player-timeline-track"))
        assertTrue(labels.fetchSemanticsNode().boundsInRoot.width > 0)
        val description = compose.onNodeWithTag("recording-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description.contains("Paused"))
        compose.onNodeWithText("Paused", substring = true).assertDoesNotExist()
        recordPair("en-font1.0-960x540-mdpi-d1", "recording-paused-endpoints", "player-pause", backdrop, false)
    }

    @Test
    fun unknownTimingPauseKeepsVisibleLabelAndBoundsButAnnouncesPause() {
        var paused by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = at.bernhardberger.tvhplayer.core.SeekbarRange(
                        at.bernhardberger.tvhplayer.core.SeekbarDomain.RECORDING,
                        0, 60_000, 0, positionKnown = false,
                    ),
                    onSeekTo = {}, paused = paused, modifier = Modifier.testTag("unknown-seekbar"),
                )
            }
        }
        val label = compose.onNodeWithText("Playback timing unavailable", useUnmergedTree = true)
        val before = label.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { paused = true }
        assertEquals(before, label.fetchSemanticsNode().boundsInRoot)
        assertTrue(compose.onNodeWithTag("unknown-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single().contains("Paused"))
    }

    @Test
    fun fallbackTimeshiftEndpointsAreNumericAndDoNotShowNegativeZero() {
        assertEquals("0:00", timeshiftEndpointLabel(true, 800))
        assertEquals("0:00", timeshiftEndpointLabel(false, 999))
        assertEquals("−0:30", timeshiftEndpointLabel(false, 30_000))
    }

    @Test
    fun passiveClockUsesCommittedPositionNotOptimisticSeekTarget() {
        var committedBehind by mutableStateOf(false)
        var selectedBehind by mutableStateOf(true)
        var known by mutableStateOf(true)
        show({ selectedBehind }, { null }, { true }, 1f, committedBehind = { committedBehind },
            paused = { true }, timingKnown = { known })
        compose.onNodeWithText("Live", useUnmergedTree = true).assertExists()
        compose.runOnIdle { committedBehind = true; selectedBehind = false }
        compose.onNodeWithText("30s behind live", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-live-status").assertDoesNotExist()
        compose.runOnIdle { known = false }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
    }

    @Test fun missingEpgSampledGeometryDoesNotJumpWhenServerReportsLive() = missingEpgAxis("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun missingEpgAxisGermanLargeText() = missingEpgAxis("de", 1.3f)

    private fun missingEpgAxis(locale: String, fontScale: Float) {
        var paused by mutableStateOf(false)
        var preview by mutableStateOf(false)
        var standalone by mutableStateOf(false)
        var pinned by mutableStateOf(false)
        var known by mutableStateOf(true)
        val sample = AppTimeshiftState(available = true, timingKnown = true,
            bufferStartMs = 0, positionMs = 60_000, liveEdgeMs = 90_000, serverBehindLiveMs = 0)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) { TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    if (standalone) TimeshiftSeekPreview(
                        state = sample.copy(timingKnown = known,
                            bufferStartMs = if (pinned) 30_000 else 0,
                            displayLiveEdgeMs = if (pinned) 120_000 else null),
                        decision = TimeshiftSeekDecision(if (pinned) 0 else 60_000, 0, false),
                    ) else PlayerOverlayChrome(headerContent = {}) {
                        PlaybackSeekbar(
                            range = at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange(sample).let {
                                if (pinned) it.copy(startMs = 30_000, displayStartMs = 0, displayEndMs = 120_000, positionKnown = known)
                                else it.copy(positionKnown = known)
                            },
                            timeshiftPosition = at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation(sample),
                            paused = paused, previewing = preview, onSeekTo = {},
                            modifier = Modifier.testTag("sampled-seekbar"),
                        )
                    }
                }
            } }
        }
        fun assertSample() {
            val node = compose.onNodeWithTag("sampled-seekbar").fetchSemanticsNode()
            assertEquals(2f / 3, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.00001f)
            assertTrue(node.config[SemanticsProperties.ContentDescription].joinToString().contains("0:30"))
            assertEquals(2f / 3, bounds("player-timeline-fill").width / bounds("player-timeline-track").width, 0.005f)
        }
        assertSample()
        compose.onNodeWithText("−1:30").assertExists()
        compose.onNodeWithText("0:00").assertExists()
        capture("$locale-font$fontScale-missing-epg-playing-sample60-history90", focus = "none")
        compose.runOnIdle { paused = true }
        assertSample()
        capture("$locale-font$fontScale-missing-epg-paused-sample60-history90", focus = "none")
        compose.runOnIdle { preview = true }
        assertSample()
        compose.onNodeWithText("−0:30", useUnmergedTree = true).assertExists()
        capture("$locale-font$fontScale-missing-epg-full-preview", focus = "none")
        compose.runOnIdle { standalone = true }
        assertEquals(2f / 3, bounds("player-timeline-fill").width / bounds("player-timeline-track").width, 0.005f)
        compose.onNodeWithTag("timeshift-seek-preview").assert(androidx.compose.ui.test.SemanticsMatcher("sampled thirty seconds behind") {
            it.config[SemanticsProperties.ContentDescription].joinToString().contains("0:30")
        })
        compose.onNodeWithText("−1:30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("0:00", useUnmergedTree = true).assertExists()
        capture("$locale-font$fontScale-missing-epg-standalone-preview", focus = "none")
        compose.runOnIdle { standalone = false; pinned = true }
        compose.onNodeWithText("−2:00").assertExists()
        compose.onNodeWithText("0:00").assertExists()
        compose.onNodeWithText("−1:00", useUnmergedTree = true).assertExists()
        assertEquals(0.5f, compose.onNodeWithTag("sampled-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        compose.runOnIdle { standalone = true }
        val oldest = compose.onNodeWithTag("timeshift-preview-buffer-start", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("−2:00", oldest.config[SemanticsProperties.Text].single().text)
        compose.onNodeWithText("0:00", useUnmergedTree = true).assertExists()
        compose.runOnIdle { standalone = false }
        compose.runOnIdle { known = false }
        compose.onNodeWithText("−2:00").assertDoesNotExist()
        compose.onNodeWithText("0:00").assertDoesNotExist()
        compose.runOnIdle { standalone = true }
        compose.onNodeWithTag("timeshift-preview-buffer-start", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("timeshift-preview-position", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun dismissedPausedPreviewWithUnknownCommittedTimingDoesNotBorrowBroadcastAxis() {
        var preview by mutableStateOf(true)
        show({ false }, { null }, { preview }, 1f, paused = { true }, timingKnown = { false },
            programmeWindowsAvailable = false)
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        val track = bounds("player-timeline-track")
        val slot = bounds("player-seekbar")
        compose.runOnIdle { preview = false }
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.onNodeWithTag("player-programme-title").assertDoesNotExist()
        compose.onNodeWithText("00:00").assertDoesNotExist()
        compose.onNodeWithText("01:00").assertDoesNotExist()
        compose.onNodeWithTag("player-timeline-track", useUnmergedTree = true)
            .assert(androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.onNodeWithTag("player-seekbar")
            .assert(androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(slot, bounds("player-seekbar"))
        assertEquals(track.top, bounds("player-timeline-track").top, 0f)
        assertEquals(track.bottom, bounds("player-timeline-track").bottom, 0f)
        compose.onNodeWithTag("player-seekbar").assert(androidx.compose.ui.test.SemanticsMatcher("paused timing unavailable") {
            it.config[SemanticsProperties.ContentDescription].joinToString().contains("Paused", ignoreCase = true)
        })
        capture("en-font1.0-960x540-mdpi-d1-paused-dismissed-unknown", focus = "player-seekbar")
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun nonTimeshiftLiveStillHasInformationalScheduleAxis() {
        show({ false }, { null }, { false }, 1f, programmeWindowsAvailable = false, timeshiftAvailable = false)
        compose.onNodeWithTag("player-schedule-progress").assertExists()
        compose.onNodeWithText("00:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("01:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun focusGrowthSurvivesIntermediateActionFadeAndSettledFrame() {
        show({ false }, { null }, { false }, 1f)
        val nominalActions = bounds("player-actions")
        val track = bounds("player-timeline-track")
        key(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.mainClock.autoAdvance = false
        key(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val mid = draw()
        val button = bounds("player-pause")
        val x = button.center.x.toInt()
        val y = nominalActions.top.toInt() - 1
        val midOverflow = android.graphics.Color.red(mid.getPixel(x, y))
        val midInterior = android.graphics.Color.red(mid.getPixel(x, nominalActions.top.toInt() + 7))
        capture("en-font1.0-960x540-mdpi-d1-white-focus-midfade-96ms")
        compose.mainClock.advanceTimeBy(400)
        val settled = draw()
        val settledOverflow = android.graphics.Color.red(settled.getPixel(x, y))
        val settledInterior = android.graphics.Color.red(settled.getPixel(x, nominalActions.top.toInt() + 7))
        assertTrue("mid-fade overflow cropped: $midOverflow", midOverflow > 90)
        assertTrue("settled overflow cropped: $settledOverflow", settledOverflow > 150)
        assertTrue("capture must be between alpha .55 and 1: $midInterior / $settledInterior",
            midInterior in 91 until settledInterior)
        assertEquals(nominalActions, bounds("player-actions"))
        assertEquals(track, bounds("player-timeline-track"))
        capture("en-font1.0-960x540-mdpi-d1-white-focus-settled")
        mid.recycle(); settled.recycle()
    }

    @Test
    @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun clockStatusIsTruthfulAndDoesNotOverlapTitleAtLargeFont() {
        var behind by mutableStateOf(false)
        var known by mutableStateOf(true)
        show({ behind }, { null }, { false }, 1.3f, timingKnown = { known })
        compose.onNodeWithText("Live", useUnmergedTree = true).assertExists()
        compose.runOnIdle { behind = true }
        val status = compose.onNodeWithTag("player-clock-status")
        val description = status.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(description.contains("30Sek hinter Live"))
        assertTrue(bounds("player-clock-status").top >= bounds("player-clock").bottom)
        assertTrue(bounds("player-clock-status").left >= bounds("player-programme-title").right)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("30Sek hinter Live", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(!layouts.single().isLineEllipsized(0))
        capture("de-font1.3-960x540-mdpi-d1-white-clock-behind")
        compose.runOnIdle { known = false }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
    }

    @Test fun statusTagsEnglish() = captureStatusTags("en", 1f)
    @Test fun recordingStatusEnglish() = captureRecordingStatus("en", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun recordingStatusGerman() = captureRecordingStatus("de", 1.3f)

    private fun captureRecordingStatus(locale: String, scale: Float) {
        var growing by mutableStateOf(true)
        var paused by mutableStateOf(false)
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show({ false }, { null }, { false }, scale, recording = true, paused = { paused },
            growing = { growing }, backdrop = { backdrop.value }, title = "A journey through the mountains — Eine Reise durch die Berge und ihre Geschichte")
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.onNodeWithContentDescription(if (locale == "de") "Aufnahme läuft. Wiedergabe" else "Recording in progress. Playing").assertExists()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        assertTrue(bounds("player-clock-status").left >= bounds("recording-title").right)
        recordPair("$locale-font$scale-status", "growing-playing", "player-pause", backdrop, false)
        compose.runOnIdle { paused = true }
        compose.onNodeWithContentDescription(if (locale == "de") "Aufnahme läuft. Pausiert" else "Recording in progress. Paused").assertExists()
        recordPair("$locale-font$scale-status", "growing-paused", "player-pause", backdrop, false)
        compose.runOnIdle { growing = false }
        compose.onNodeWithTag("player-clock-status").assertDoesNotExist()
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        recordPair("$locale-font$scale-status", "completed", "player-pause", backdrop, false)
    }
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun statusTagsGerman() = captureStatusTags("de", 1.3f)

    private fun captureStatusTags(locale: String, scale: Float) {
        var behind by mutableStateOf(false)
        var paused by mutableStateOf(false)
        var recordingNow by mutableStateOf(true)
        var backdrop by mutableStateOf(Backdrop.WHITE)
        show({ behind }, { null }, { false }, scale, paused = { paused },
             recordingNow = { recordingNow }, backdrop = { backdrop },
             title = "A journey through the mountains — Eine Reise durch die Berge und ihre Geschichte")
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.onNodeWithTag("player-recording-now").assertExists()
        assertEquals(bounds("player-recording-now").center.y, bounds("player-clock-status").center.y, 1f)
        capture("$locale-font$scale-status-live-recording-bright")
        compose.runOnIdle { behind = true; paused = true; backdrop = Backdrop.TEXTURED }
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertTrue(bounds("player-clock-status").left >= bounds("player-programme-title").right)
        assertEquals(bounds("player-recording-now").center.y, bounds("player-clock-status").center.y, 1f)
        capture("$locale-font$scale-status-paused-behind-recording-dark")
        compose.runOnIdle { recordingNow = false }
        compose.onNodeWithTag("player-recording-now").assertDoesNotExist()
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    private fun show(
        behind: () -> Boolean,
        feedback: () -> String?,
        previewing: () -> Boolean,
        fontScale: Float,
        error: Boolean = false,
        backdrop: () -> Backdrop = { Backdrop.WHITE },
        recording: Boolean = false,
        standalone: () -> Boolean = { false },
        onCommitSeek: () -> Unit = {},
        markers: List<Long> = emptyList(),
        markerNavigation: RecordingMarkerNavigation = RecordingMarkerNavigation(),
        markerRevision: Long = 0L,
        onSeekMarker: (Long) -> Unit = {},
        paused: () -> Boolean = { false },
        timingKnown: () -> Boolean = { true },
        committedBehind: () -> Boolean = behind,
        programmeWindowsAvailable: Boolean = true,
        timeshiftAvailable: Boolean = true,
        recordingNow: () -> Boolean = { false },
        growing: () -> Boolean = { false },
        title: String = "Zeit im Bild",
    ) {
        val programme = EpgEvent.create(
            id = EventId(1),
            channelId = ChannelId(1),
            title = title,
            start = Instant.fromEpochSeconds(0),
            stop = Instant.fromEpochSeconds(3_600),
        )
        val window = ProgrammeWindow(programme, Instant.fromEpochSeconds(1_800), 0.5f, 0.2f, 0.8f, 0.8f, true)
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            val context = LocalContext.current
            val session = remember {
                FakeSessionObservation(SessionObservation.create(
                    sessionState = SessionState.Ready(ServerCapabilities.create(
                        streaming = CapabilityAccess.ALLOWED,
                        dvrWrite = CapabilityAccess.ALLOWED,
                    )),
                    channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
                    epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
                    dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
                )).captureCurrentSession()
            }
            val loader = remember { zapImageLoader(context) }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize()) {
                        if (backdrop() == Backdrop.TEXTURED) {
                            DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.White))
                        }
                        if (standalone()) {
                            if (recording) RecordingSeekPreview(
                                targetMs = 1_800_000, originMs = 1_700_000, durationMs = 3_600_000,
                                growing = false, modifier = Modifier.align(Alignment.BottomCenter),
                            ) else TimeshiftSeekPreview(
                                state = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0),
                                decision = TimeshiftSeekDecision(-30_000, -30_000, false),
                                programmeWindow = window, modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        } else if (recording) RecordingOverlayControls(
                            imageLoader = loader, piconPath = null, title = title,
                            subtitle = "Eine Reise durch die Berge", channelName = "Documentary",
                            positionMs = 1_800_000, durationMs = 3_600_000, growing = growing(), nowSec = 1_800,
                             canSeek = true, controlsVisible = true, optionsOpen = false,
                             paused = paused(),
                            onTogglePlayPause = {}, onSeek = {}, onStopPlayback = {}, onUserInteraction = {},
                            onOpenOptions = {}, onOpenInfo = {}, previewing = previewing(),
                            onCommitSeek = onCommitSeek, markers = markers,
                            markerNavigation = markerNavigation, markerRevision = markerRevision, onSeekMarker = onSeekMarker,
                        ) else OverlayControlsTv(
                            imageLoader = loader,
                            currentSession = session,
                            channelNumber = 1,
                            channelName = "Documentary",
                            piconPath = null,
                            nowEvent = programme,
                            nextEvent = null,
                             nowSec = 1_800,
                            channelRecordingNow = recordingNow(),
                            controlsVisible = true,
                            optionsOpen = false,
                            onOpenChannels = {},
                            onStopPlayback = {},
                            onUserInteraction = {},
                            onOpenOptions = {},
                            timeshiftState = AppTimeshiftState(
                                available = timeshiftAvailable,
                                bufferStartMs = -600_000,
                                positionMs = if (behind()) -30_000 else 0,
                                 liveEdgeMs = 0,
                                 timingKnown = timingKnown(),
                            ),
                             timeshiftFeedback = feedback(),
                            paused = paused(),
                             committedTimeshiftState = AppTimeshiftState(
                                 available = timeshiftAvailable, bufferStartMs = -600_000,
                                 positionMs = if (committedBehind()) -30_000 else 0,
                                 liveEdgeMs = 0, timingKnown = timingKnown(),
                             ),
                            timeshiftFeedbackIsError = error && feedback() != null,
                            onToggleTimeshiftPause = {},
                            onSeekTimeshift = {},
                            onGoLive = {},
                            onCommitSeek = onCommitSeek,
                            programmeWindow = window.takeIf { programmeWindowsAvailable },
                            committedWindow = window.takeIf { programmeWindowsAvailable },
                            previewing = previewing(),
                            channelsAvailable = true,
                            channelRailContent = {
                                ChannelDrawer(
                                    channels = channels,
                                    selectedId = ChannelId(1),
                                    playingChannelId = ChannelId(1),
                                    recordingChannelIds = setOf(ChannelId(4)),
                                    nowEvent = { id ->
                                        if (id == ChannelId(5)) null else EpgEvent.create(
                                            id = EventId(id.value),
                                            channelId = id,
                                            title = "A journey through the mountains",
                                            start = Instant.fromEpochSeconds(0),
                                            stop = Instant.fromEpochSeconds(3_600),
                                        )
                                    },
                                    imageLoader = loader,
                                    currentSession = session,
                                    active = false,
                                    nowSec = 1_800,
                                    onFocusChannel = {},
                                    onPickChannel = {},
                                    onCloseDrawer = {},
                                )
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInline(track: androidx.compose.ui.geometry.Rect) {
        val start = bounds("player-window-start")
        val end = bounds("player-window-end")
        assertTrue(start.center.y >= track.top - 1f && start.center.y <= track.bottom + 1f)
        assertTrue(end.center.y >= track.top - 1f && end.center.y <= track.bottom + 1f)
        assertTrue(start.right <= track.left + 1f)
        assertTrue(end.left >= track.right - 1f)
        assertTrue(bounds("player-timeline-labels").bottom <= track.bottom + px(8.dp))
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun px(value: androidx.compose.ui.unit.Dp) = with(compose.density) { value.toPx() }

    private fun recordPair(
        prefix: String,
        state: String,
        focusedTag: String,
        backdrop: androidx.compose.runtime.MutableState<Backdrop>,
        expectPeek: Boolean = true,
    ) {
        for (next in Backdrop.entries) {
            compose.runOnIdle { backdrop.value = next }
            compose.waitForIdle()
            val peek = measurePeek(focusedTag)
            val name = "$prefix-${next.fileName}-$state"
            capture(name)
            val note = buildString {
                appendLine("file=$name.png")
                appendLine("canvas=960x540")
                appendLine("density=1.0")
                appendLine("locale=${if (prefix.startsWith("de")) "de" else "en"}")
                appendLine("fontScale=${if (prefix.contains("font1.3")) "1.3" else "1.0"}")
                appendLine("backdrop=${next.fileName}")
                appendLine("focus=$focusedTag")
                appendLine("focusedBottomPx=${peek.focusedBottom}")
                appendLine("trayVisualTopPx=${peek.trayVisualTop}")
                appendLine("cardPeekTopPx=${peek.cardPeekTop}")
                appendLine("layoutClearancePx=${peek.clearance}")
            }
            for (directory in captureDirectories()) {
                File(directory, "$name.txt").writeText(note)
            }
            if (expectPeek) assertTrue("production card peek missing in $name: $note", peek.cardPeekTop != null)
        }
    }

    private fun measurePeek(focusedTag: String): PeekMeasure {
        val bitmap = draw()
        val trayVisualTop = bitmap.height - 36
        var cardTop: Int? = null
        for (y in trayVisualTop until bitmap.height) {
            var cardPixels = 0
            for (x in 48 until bitmap.width - 48 step 2) {
                val pixel = bitmap.getPixel(x, y)
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val card = kotlin.math.abs(red - 12) <= 2 &&
                    kotlin.math.abs(green - 15) <= 2 &&
                    kotlin.math.abs(blue - 16) <= 2
                val picon = blue > 180 && green > 150 && red in 70..160
                if (card || picon) cardPixels++
            }
            if (cardPixels > 12) {
                cardTop = y
                break
            }
        }
        bitmap.recycle()
        val focusedBottom = bounds(focusedTag).bottom
        return PeekMeasure(
            focusedBottom = focusedBottom,
            trayVisualTop = trayVisualTop,
            cardPeekTop = cardTop,
            clearance = cardTop?.minus(focusedBottom),
        )
    }

    private fun capture(name: String, focus: String = "player-pause") {
        val bitmap = draw()
        for (directory in captureDirectories()) {
            File(directory, "$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(directory, "$name.txt").writeText(
                "file=$name.png\ncanvas=${bitmap.width}x${bitmap.height}\ndensity=1.0\n" +
                    "locale=${if (name.startsWith("de")) "de" else "en"}\n" +
                    "fontScale=${if (name.contains("font1.3")) "1.3" else "1.0"}\n" +
                    "fixture=PlayerFooterLayoutEvidenceTest; production composables, offline fake state\n" +
                    "focus=$focus\n",
            )
        }
        bitmap.recycle()
    }

    private fun captureDirectories() = listOf(
        File("build/outputs/player-footer-captures").apply { mkdirs() },
        File("/tmp/opencode/player-footer").apply { mkdirs() },
    )

    private fun zapImageLoader(context: android.content.Context) = ImageLoader.Builder(context)
        .components {
            add(object : Mapper<AppArtworkSource, ByteArray> {
                override fun map(data: AppArtworkSource, options: Options): ByteArray {
                    val bitmap = Bitmap.createBitmap(200, 90, Bitmap.Config.ARGB_8888)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF79D1FF.toInt()
                        textSize = 64f
                        isFakeBoldText = true
                    }
                    Canvas(bitmap).drawText("TV ${data.selector.substringAfterLast('/')}", 12f, 68f, paint)
                    return ByteArrayOutputStream().also {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }.toByteArray()
                }
            })
        }
        .coroutineContext(Dispatchers.Main.immediate)
        .fetcherCoroutineContext(Dispatchers.Main.immediate)
        .decoderCoroutineContext(Dispatchers.Main.immediate)
        .diskCache(null)
        .build()

    private fun draw(): Bitmap {
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private val channels = (1L..12L).map {
        Channel.create(
            id = ChannelId(it),
            number = it,
            name = if (it == 4L) "Dokumentation und Zeitgeschichte HD" else "Channel $it",
            icon = if (it == 5L) null else "imagecache/$it",
        )
    }

    private enum class Backdrop(val fileName: String) { WHITE("white"), TEXTURED("backdrop") }

    private data class PeekMeasure(
        val focusedBottom: Float,
        val trayVisualTop: Int,
        val cardPeekTop: Int?,
        val clearance: Float?,
    )
}
