package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.tagExists
import at.bernhardberger.tvhplayer.testutil.tagBounds
import at.bernhardberger.tvhplayer.testutil.pressKey
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
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
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import coil3.ImageLoader
import coil3.map.Mapper
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.time.Instant
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerFooterLayoutTest {
    @get:Rule val images = ImageLoaderRule { zapImageLoader(ApplicationProvider.getApplicationContext<Application>()) }
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
        val track = compose.tagBounds("player-timeline-track")
        val labels = compose.tagBounds("player-timeline-labels")
        assertEquals(labels.center.y, track.center.y, 1f)
        assertTrue(labels.left <= track.left + 1f)
        assertTrue(labels.right >= track.right - 1f)
    }

    @Test
    fun statusOverlayDoesNotMoveTimelineOrActionsOrTheBarRowsEnds() {
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
        val track = compose.tagBounds("player-timeline-track")
        val actions = compose.tagBounds("player-actions")
        assertInline(track)
        val end = compose.tagBounds("player-end-clock")
        val distance = compose.tagBounds("player-live-state")
        compose.onNodeWithTag("player-distance", useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle { behind = true }
        assertEquals(track, compose.tagBounds("player-timeline-track"))
        assertEquals(actions, compose.tagBounds("player-actions"))
        assertEquals("the end clock keeps its place as playback falls behind", end, compose.tagBounds("player-end-clock"))
        assertEquals("the distance's box keeps its place as the distance appears", distance, compose.tagBounds("player-live-state"))
        compose.onNodeWithTag("player-distance", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-go-live").assertDoesNotExist()

        compose.runOnIdle { feedback = "Reached the available buffer limit" }
        assertEquals(track, compose.tagBounds("player-timeline-track"))
        assertEquals(actions, compose.tagBounds("player-actions"))
        assertEquals(end, compose.tagBounds("player-end-clock"))
        assertEquals(distance, compose.tagBounds("player-live-state"))
        val message = compose.tagBounds("player-window-title")
        assertTrue("feedback ends before the distance: $message vs $distance", message.right <= distance.left)
        assertTrue("feedback paints above the bar row", message.bottom <= track.top + 1f)

        compose.runOnIdle { feedback = null; previewing = true }
        compose.pressKey(Key.DirectionUp)
        assertEquals(track.top, compose.tagBounds("player-timeline-track").top, 1f)
        assertEquals(track.bottom, compose.tagBounds("player-timeline-track").bottom, 1f)
        compose.onNodeWithTag("player-actions").assertDoesNotExist()
        assertEquals("the end clock keeps its room during a step", end, compose.tagBounds("player-end-clock"))
        assertEquals("the distance keeps its place during a step", distance, compose.tagBounds("player-live-state"))
        val target = compose.tagBounds("timeshift-preview-target")
        val progress = compose.onNodeWithTag("player-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        val resting = compose.tagBounds("player-timeline-track")
        assertEquals(resting.left + resting.width * progress, target.center.x, 1.5f)
        assertTrue(target.left >= resting.left - 1f && target.right <= resting.right + 1f)
        assertTrue(target.bottom <= resting.top + 1f)
        assertTrue(compose.tagBounds("player-timeline-status").contains(target.center))
        compose.onNodeWithTag("player-seekbar").assertExists()
    }

    @Test
    fun theBarRowKeepsTheLeftLabelTheBarAndTheEndClockApartWithTheDistanceAboveTheEnd() {
        show(behind = { true }, feedback = { null }, previewing = { false }, fontScale = 1f)
        val track = compose.tagBounds("player-timeline-track")
        val start = compose.tagBounds("player-window-start")
        val end = compose.tagBounds("player-end-clock")
        val labels = compose.tagBounds("player-timeline-labels")
        assertFalse("the controls have no state cell", compose.tagExists("player-state"))
        assertEquals("the left label leads the row", labels.left, start.left, 1f)
        assertTrue("the left label sits before the bar", start.right <= track.left + 1f)
        assertTrue("the end clock does not overlap the bar: $end vs $track", end.left >= track.right - 1f)
        assertTrue("the end clock ends the row", end.right <= labels.right + 1f && end.right >= labels.right - 1f)
        for (cell in listOf(start, end)) {
            assertTrue("centred on the bar", cell.center.y >= track.top - 1f && cell.center.y <= track.bottom + 1f)
        }
        compose.onNodeWithTag("player-distance", useUnmergedTree = true).assertExists()
        val distance = compose.tagBounds("player-distance")
        assertEquals("the distance's right edge is the end clock's", end.right, distance.right, 0.5f)
        assertTrue("the distance sits above the bar row: $distance vs $labels", distance.bottom <= labels.top)
        compose.onNodeWithTag("player-end-tag-slot", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLengthShorterThanItsBoxEndsTheRowAndLeavesItsRoomBesideTheBar() {
        show(behind = { false }, feedback = { null }, previewing = { false }, fontScale = 1f, recording = true, recordingDurationMs = 2_700_000)
        val track = compose.tagBounds("player-timeline-track")
        val end = compose.tagBounds("player-end-clock")
        val labels = compose.tagBounds("player-timeline-labels")
        val box = px(requireNotNull(endpointWidths).trailing)
        assertEquals("45:00", compose.onNodeWithTag("player-end-clock", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text })
        assertTrue("the length is shorter than its box: $end vs $box", end.width < box - 1f)
        assertEquals("the end box starts one label gap after the bar", track.right + px(8.dp), labels.right - box, 1f)
        assertEquals("the length ends the row", labels.right, end.right, 1f)
        assertTrue("its spare room lies beside the bar: $end vs $track", end.left > track.right + px(8.dp) + 1f)
    }

    @Test fun seekProgressKeepsStateLiveRegionsSeparate() = assertIndependentAnnouncements(timeshift = true)

    @Test fun scheduleProgressKeepsStateLiveRegionsSeparate() = assertIndependentAnnouncements(timeshift = false)

    private fun assertIndependentAnnouncements(timeshift: Boolean) {
        show({ false }, { null }, { false }, 1f, timeshiftAvailable = timeshift)
        val progressTag = if (timeshift) "player-seekbar" else "player-schedule-progress"
        val descriptions = compose.onNodeWithTag(progressTag).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
        assertEquals(1, descriptions.size)
        assertTrue(descriptions.none { it == "Playing" || it == "Live" })
        assertFalse("the controls have no state cell", compose.tagExists("player-state"))
        val config = compose.onNodeWithTag("player-live-state").fetchSemanticsNode().config
        assertEquals(listOf("Live"), config[SemanticsProperties.ContentDescription])
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun upFromTheTimelineStaysAtTheTopEdgeBehindLive() {
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(
            behind = { true },
            feedback = { null },
            previewing = { false },
            fontScale = 1f,
            backdrop = { backdrop.value },
        )
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        // Artwork is passive; Up cannot move focus out of the timeline.
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.onNodeWithTag("player-go-live").assertDoesNotExist()
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
        val timelineTag = "player-seekbar"
        val resting = compose.tagBounds("player-timeline-track")
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag(timelineTag).assertIsFocused()
        compose.runOnIdle { previewing = true }
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertExists()
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertTrue(previewing) // Owner deliberately leaves the dispatched seek pending.
        assertEquals(1, commits)
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        // Without markers above it, Up stays on the timeline and commits the pending preview.
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        assertTrue(previewing)
        assertEquals(2, commits)
        compose.runOnIdle { standalone = true }
        val previewTrack = compose.tagBounds("player-timeline-track")
        // A quick step plays in the Banner, which rests one action row lower than the controls.
        val drop = with(compose.density) { PlayerBannerDrop.toPx() }
        assertEquals(resting.top + drop, previewTrack.top, 1f)
        assertEquals(resting.bottom + drop, previewTrack.bottom, 1f)
        // The Banner keeps the state cell's room before the bar; the controls hand it to the bar.
        assertEquals(resting.left + compose.tagBounds("player-state").width + px(8.dp), previewTrack.left, 1f)
        assertEquals(resting.right, previewTrack.right, 1f)
    }

    @Test fun pendingRecordingSeekCanOpenAndDismissMarkers() {
        var commits = 0
        show(behind = { true }, feedback = { null }, previewing = { true }, fontScale = 1.3f,
            recording = true, marks = chapterMarks(900_000L, 2_700_000L), onCommitSeek = { commits++ })
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("recording-marker-target").assertIsFocused()
        assertEquals(1, commits)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.DirectionDown)
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
            marks = chapterMarks(900_000L, 2_700_000L, 3_599_999L),
            markerNavigation = navigation, markerRevision = 42L, onSeekMarker = seeks::add)
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionUp)
        val track = compose.tagBounds("player-timeline-track")
        assertTrue(track.left > compose.tagBounds("player-timeline-labels").left + 8f)
        assertTrue(track.right < compose.tagBounds("player-timeline-labels").right - 8f)
        assertEquals(track, compose.tagBounds("player-seekbar"))
        fun assertMarker(fraction: Float) {
            compose.onNodeWithTag("recording-marker-target").assertIsFocused()
            assertEquals(42L, navigation.ownerRevision)
            val button = compose.tagBounds("recording-marker-target")
            val label = compose.tagBounds("recording-marker-overlay")
            val tick = compose.tagBounds("recording-selected-marker")
            val position = track.left + track.width * fraction
            assertEquals(position.coerceIn(track.left + 0.5f, track.right - 0.5f), tick.center.x, 1f)
            // The pill centres on its tick and may reach past the track over the endpoint labels,
            // but never past the screen's horizontal safe margins.
            val margin = px(TvGridHorizontalMargin)
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val low = minOf(track.left, margin)
            val high = maxOf(track.right - button.width, screen.right - margin - button.width)
            val expectedLeft = (position - button.width / 2f).coerceIn(low, maxOf(low, high))
            assertEquals(expectedLeft + button.width / 2f, label.center.x, 1f)
            assertTrue(button.left >= margin.coerceAtMost(track.left) - 1f)
            assertTrue(button.right <= maxOf(track.right, screen.right - margin) + 1f)
        }
        assertMarker(0.25f) // midpoint entry chooses the earlier of the two equally distant marks
        compose.pressKey(Key.DirectionRight)
        assertMarker(0.75f)
        compose.pressKey(Key.DirectionLeft)
        assertMarker(0.25f)
        compose.pressKey(Key.DirectionLeft)
        assertMarker(0f)
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionRight)
        compose.pressKey(Key.DirectionRight)
        assertMarker(3_599_999f / 3_600_000f)
        // Up above the markers closes them and returns to the timeline; artwork stays passive.
        compose.pressKey(Key.DirectionUp)
        assertFalse(navigation.open)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        assertMarker(0.25f)
        compose.pressKey(Key.Back)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        assertMarker(0.25f)
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.pressKey(Key.DirectionUp)
        compose.pressKey(Key.DirectionCenter)
        assertEquals(listOf(900_000L), seeks)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
    }

    @Test fun livePreviewDismissalRestoresChromeWithoutMovingTimelineFocus() = previewDismissal(false)

    @Test fun recordingPreviewDismissalRestoresChromeWithoutMovingTimelineFocus() = previewDismissal(true)

    private fun previewDismissal(recording: Boolean) {
        var preview by mutableStateOf(true)
        show(behind = { true }, feedback = { null }, previewing = { preview }, fontScale = 1.3f, recording = recording)
        compose.pressKey(Key.DirectionUp)
        val timeline = "player-seekbar"
        compose.onNodeWithTag(timeline).assertIsFocused()
        compose.onNodeWithTag("player-pause").assertDoesNotExist()
        // Both ancestor Back callbacks synchronously clear the presented preview.
        compose.runOnIdle { preview = false }
        compose.onNodeWithTag(timeline).assertIsFocused()
        compose.onNodeWithTag("player-pause").assertExists()
        compose.onNodeWithTag("timeshift-preview-target", useUnmergedTree = true).assertDoesNotExist()
        compose.pressKey(Key.DirectionDown)
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
        compose.pressKey(Key.Back)
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
        assertEquals(1f, alpha.value, 0.001f)
        compose.mainClock.advanceTimeBy(240)
        assertEquals(1f, alpha.value, 0.001f)
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
        val pause = compose.tagBounds("player-pause")
        fun sample(x: Float, y: Float): Int = draw().let { bitmap ->
            bitmap.getPixel(x.toInt(), y.toInt()).also { bitmap.recycle() }
        }
        val focused = sample(pause.center.x, pause.top + 8f)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.waitForIdle()
        val pressed = sample(pause.center.x, pause.top + 8f)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.pressKey(Key.DirectionRight)
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
            val rect = compose.tagBounds("endpoint")
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
    fun germanLargeTextKeepsFeedbackClearOfTheBarRowAndPeeksQuickZap() {
        val feedbackText = ApplicationProvider.getApplicationContext<Application>().getString(R.string.timeshift_target_expired)
        val backdrop = mutableStateOf(Backdrop.WHITE)
        show(
            behind = { true },
            feedback = { feedbackText },
            previewing = { false },
            fontScale = 1.3f,
            error = true,
            backdrop = { backdrop.value },
        )
        compose.onNodeWithTag("player-pause").assertIsFocused()
        val feedback = compose.onNodeWithText(feedbackText)
        feedback.assertExists()
        val message = feedback.fetchSemanticsNode().boundsInRoot
        val track = compose.tagBounds("player-timeline-track")
        val end = compose.tagBounds("player-end-clock")
        val distance = compose.tagBounds("player-distance")
        assertTrue("message $message track $track", message.bottom <= track.top + 1f)
        assertTrue("the end clock keeps clear of the bar: $end $track", end.left >= track.right - 1f)
        assertTrue("the message ends before the distance: $message $distance", message.right <= distance.left)
        assertTrue(message.top >= 0f && message.bottom <= 540f)
        val layouts = mutableListOf<TextLayoutResult>()
        feedback.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("feedback ellipsized", !layouts.single().isLineEllipsized(0))
        assertEquals(1, layouts.single().lineCount)
        assertInline(track)
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
        val track = compose.tagBounds("player-timeline-track")
        compose.runOnIdle { paused = true }
        assertEquals(original, text.fetchSemanticsNode().boundsInRoot)
        assertEquals(track, compose.tagBounds("player-timeline-track"))
        assertTrue(labels.fetchSemanticsNode().boundsInRoot.width > 0)
        val description = compose.onNodeWithTag("player-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description.contains("Paused"))
        compose.onNodeWithText("Paused", substring = true).assertDoesNotExist()
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

    @Test fun missingEpgServerLivePinsPlayingButNotPauseOrPreview() = missingEpgAxis(1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun missingEpgAxisGermanLargeText() = missingEpgAxis(1.3f)

    private fun missingEpgAxis(fontScale: Float) {
        var paused by mutableStateOf(false)
        var preview by mutableStateOf(false)
        var settled by mutableStateOf(false)
        var standalone by mutableStateOf(false)
        var pinned by mutableStateOf(false)
        var known by mutableStateOf(true)
        val sample = AppTimeshiftState(available = true, timingKnown = true,
            bufferStartMs = 0, positionMs = 60_000, liveEdgeMs = 90_000, serverBehindLiveMs = 0)
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) { TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    if (standalone) QuickStepBanner(
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
                            paused = paused, previewing = preview, onSeekTo = {}, stepDeltaMs = -10_000L, stepSettled = settled,
                            modifier = Modifier.testTag("sampled-seekbar"),
                        )
                    }
                }
            } }
        }
        fun assertSample(atLive: Boolean = false) {
            val fraction = if (atLive) 1f else 2f / 3
            val node = compose.onNodeWithTag("sampled-seekbar").fetchSemanticsNode()
            assertEquals(fraction, node.config[SemanticsProperties.ProgressBarRangeInfo].current, 0.00001f)
            assertTrue(node.config[SemanticsProperties.ContentDescription].joinToString().contains(if (atLive) "Live" else "0:30"))
            assertEquals(fraction, compose.tagBounds("player-timeline-fill").width / compose.tagBounds("player-timeline-track").width, 0.005f)
        }
        assertSample(atLive = true)
        compose.onNodeWithText("−1:30").assertExists()
        compose.onNodeWithText("0:00").assertExists()
        compose.runOnIdle { paused = true }
        assertSample()
        compose.runOnIdle { preview = true }
        assertSample()
        compose.onNodeWithText("−0:30", useUnmergedTree = true).assertExists()
        compose.runOnIdle { standalone = true }
        assertEquals(2f / 3, compose.tagBounds("player-timeline-fill").width / compose.tagBounds("player-timeline-track").width, 0.005f)
        // The step is spoken once it has been dispatched.
        compose.runOnIdle { settled = true }
        compose.waitForIdle()
        compose.onNodeWithTag("timeshift-seek-preview").assert(androidx.compose.ui.test.SemanticsMatcher("sampled thirty seconds behind") {
            it.config[SemanticsProperties.ContentDescription].joinToString().contains("0:30")
        })
        // The Banner step's bar row: the buffer's start clock (90 s before 00:30:00), no end label.
        assertEquals("00:28", compose.onNodeWithTag("timeshift-preview-buffer-start", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        compose.onNodeWithTag("timeshift-preview-position", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { standalone = false; pinned = true }
        compose.onNodeWithText("−2:00").assertExists()
        compose.onNodeWithText("0:00").assertExists()
        // The readout measures against the verified live edge (90 s), not the extrapolated axis end (120 s).
        compose.onNodeWithText("−0:30", useUnmergedTree = true).assertExists()
        assertEquals(0.5f, compose.onNodeWithTag("sampled-seekbar").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        compose.runOnIdle { standalone = true }
        val oldest = compose.onNodeWithTag("timeshift-preview-buffer-start", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("00:28", oldest.config[SemanticsProperties.Text].single().text)
        compose.runOnIdle { standalone = false }
        compose.runOnIdle { known = false }
        compose.onNodeWithText("−2:00").assertDoesNotExist()
        compose.onNodeWithText("0:00").assertDoesNotExist()
        compose.runOnIdle { standalone = true }
        compose.onNodeWithTag("timeshift-preview-buffer-start", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("timeshift-preview-position", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun dismissedPausedPreviewWithoutKnownTimingDoesNotBorrowBroadcastAxis() {
        var preview by mutableStateOf(true)
        show({ false }, { null }, { preview }, 1f, paused = { true }, timingKnown = { false },
            programmeWindowsAvailable = false)
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        val track = compose.tagBounds("player-timeline-track")
        val slot = compose.tagBounds("player-timeline-labels")
        compose.runOnIdle { preview = false }
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.onNodeWithText("00:00").assertDoesNotExist()
        compose.onNodeWithText("01:00").assertDoesNotExist()
        compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-distance", useUnmergedTree = true).assertDoesNotExist()
        assertFalse("unknown is not a claim of Live", compose.onNodeWithTag("player-live-state", useUnmergedTree = true)
            .fetchSemanticsNode().config.contains(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("player-timeline-track", useUnmergedTree = true)
            .assert(androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        compose.onNodeWithTag("player-seekbar")
            .assert(androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        assertEquals(slot, compose.tagBounds("player-timeline-labels"))
        assertEquals(track.top, compose.tagBounds("player-timeline-track").top, 0f)
        assertEquals(track.bottom, compose.tagBounds("player-timeline-track").bottom, 0f)
        compose.onNodeWithTag("player-seekbar").assert(androidx.compose.ui.test.SemanticsMatcher("paused timing unavailable") {
            it.config[SemanticsProperties.ContentDescription].joinToString().contains("Paused", ignoreCase = true)
        })
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }

    @Test fun nonTimeshiftLiveStillHasInformationalScheduleAxis() {
        show({ false }, { null }, { false }, 1f, programmeWindowsAvailable = false, timeshiftAvailable = false)
        compose.onNodeWithTag("player-schedule-progress").assertExists()
        compose.onNodeWithText("00:00", useUnmergedTree = true).assertExists()
        // Without timeshift playback is at the live edge: no distance is drawn, the state is announced.
        compose.onNodeWithTag("player-distance", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(listOf("Live"), compose.onNodeWithTag("player-live-state", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription])
        assertEquals("01:00", compose.onNodeWithTag("player-end-clock", useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString { it.text })
    }

    @Test
    fun focusGrowthSurvivesSeekbarReturnAndSettledFrame() {
        show({ false }, { null }, { false }, 1f)
        val nominalActions = compose.tagBounds("player-actions")
        val track = compose.tagBounds("player-timeline-track")
        compose.pressKey(Key.DirectionUp)
        compose.onNodeWithTag("player-seekbar").assertIsFocused()
        compose.mainClock.autoAdvance = false
        compose.pressKey(Key.DirectionDown)
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val mid = draw()
        val button = compose.tagBounds("player-pause")
        val x = button.center.x.toInt()
        val y = nominalActions.top.toInt() - 1
        val midOverflow = android.graphics.Color.red(mid.getPixel(x, y))
        val midInterior = android.graphics.Color.red(mid.getPixel(x, nominalActions.top.toInt() + 7))
        compose.mainClock.advanceTimeBy(400)
        val settled = draw()
        val settledOverflow = android.graphics.Color.red(settled.getPixel(x, y))
        val settledInterior = android.graphics.Color.red(settled.getPixel(x, nominalActions.top.toInt() + 7))
        assertTrue("mid-fade overflow cropped: $midOverflow", midOverflow > 90)
        assertTrue("settled overflow cropped: $settledOverflow", settledOverflow > 150)
        assertEquals("the action row stays fully opaque across seekbar focus", settledInterior, midInterior)
        assertEquals(nominalActions, compose.tagBounds("player-actions"))
        assertEquals(track, compose.tagBounds("player-timeline-track"))
        mid.recycle(); settled.recycle()
    }

    /** The label boxes of the bar row [show] composed. */
    private var endpointWidths: TimelineEndpointWidths? = null

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
        marks: List<at.bernhardberger.tvhplayer.core.RecordingMark> = emptyList(),
        markerNavigation: RecordingMarkerNavigation = RecordingMarkerNavigation(),
        markerRevision: Long = 0L,
        onSeekMarker: (Long) -> Unit = {},
        paused: () -> Boolean = { false },
        timingKnown: () -> Boolean = { true },
        committedBehind: () -> Boolean = behind,
        programmeWindowsAvailable: Boolean = true,
        timeshiftAvailable: Boolean = true,
        growing: () -> Boolean = { false },
        title: String = "Zeit im Bild",
        recordingDurationMs: Long = 3_600_000,
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
            val backCycle = remember { at.bernhardberger.tvhplayer.core.PlayerBackKeyCycle() }
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
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TVHeadendPlayerTheme {
                    endpointWidths = rememberTimelineEndpointWidths(if (recording) TimelineKind.RECORDING else TimelineKind.LIVE)
                    Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                        // Marker Back belongs to the recording root, not the chrome itself.
                        recording && backCycle.intercept(event, false,
                            onBack = { markerNavigation.dismiss() }, stopAndClose = {})
                    }) {
                        if (backdrop() == Backdrop.TEXTURED) {
                            DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.White))
                        }
                        if (standalone()) {
                            if (recording) RecordingChromeFixture(
                                mode = PlayerChromeMode.BANNER_STEP, positionMs = 1_700_000,
                                targetMs = 1_800_000, originMs = 1_700_000,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) else QuickStepBanner(
                                state = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = -30_000, liveEdgeMs = 0),
                                decision = TimeshiftSeekDecision(-30_000, -30_000, false),
                                programmeWindow = window, modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        } else if (recording) RecordingChromeFixture(
                            positionMs = 1_800_000, durationMs = recordingDurationMs, targetMs = 1_800_000L.takeIf { previewing() },
                            growing = growing(), paused = paused(), info = fixtureRecordingInfo(title, growing(), durationMs = recordingDurationMs),
                            onCommitSeek = onCommitSeek, marks = marks,
                            markerNavigation = markerNavigation, markerRevision = markerRevision, onSeekMarker = onSeekMarker,
                        ) else ProvideArtworkSession(session) { PlayerChrome(
                            mode = PlayerChromeMode.CONTROLS,
                            content = PlayerChromeContent("", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "Documentary", programme, null, false, 1_800, "")),
                            timeline = PlayerChromeTimeline.Live(
                                timeshift = AppTimeshiftState(
                                    available = timeshiftAvailable,
                                    bufferStartMs = -600_000,
                                    positionMs = if (behind()) -30_000 else 0,
                                    liveEdgeMs = 0,
                                    timingKnown = timingKnown(),
                                ),
                                nowSec = 1_800,
                                programme = programme,
                                committedTimeshift = AppTimeshiftState(
                                    available = timeshiftAvailable, bufferStartMs = -600_000,
                                    positionMs = if (committedBehind()) -30_000 else 0,
                                    liveEdgeMs = 0, timingKnown = timingKnown(),
                                ),
                                committedWindow = window.takeIf { programmeWindowsAvailable },
                                programmeWindow = window.takeIf { programmeWindowsAvailable },
                                previewing = previewing(),
                                feedback = feedback(),
                                feedbackIsError = error && feedback() != null,
                            ),
                            actions = PlayerChromeActions(active = true, paused = paused()),
                            onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                            onCommitSeek = onCommitSeek,
                            controlsDecoration = { _, controls -> QuickZapPresentation(
                              expanded = false,
                              preview = {},
                              controls = controls,
                              channelContent = {
                                ProvideArtworkSession(session) { ChannelDrawer(
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
                                    active = false,
                                    nowSec = 1_800,
                                    onFocusChannel = {},
                                    onPickChannel = {},
                                    onCloseDrawer = {},
                                ) }
                              },
                            ) },
                        ) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInline(track: androidx.compose.ui.geometry.Rect) {
        val start = compose.tagBounds("player-window-start")
        val end = compose.tagBounds("player-end-clock")
        assertTrue(start.center.y >= track.top - 1f && start.center.y <= track.bottom + 1f)
        assertTrue(end.center.y >= track.top - 1f && end.center.y <= track.bottom + 1f)
        assertTrue(start.right <= track.left + 1f)
        assertTrue(end.left >= track.right - 1f)
        assertTrue(compose.tagBounds("player-timeline-labels").bottom <= track.bottom + px(8.dp))
    }

    private fun px(value: androidx.compose.ui.unit.Dp) = with(compose.density) { value.toPx() }

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
                    Canvas(bitmap).drawText("TV ${data.id.value}", 12f, 68f, paint)
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
            icon = if (it == 5L) null else ChannelIcon.ImageCache(ArtworkId(it.toInt())),
        )
    }

    private enum class Backdrop { WHITE, TEXTURED }
}

/** A live quick step on the hidden player, as the screen composes it: the passive Banner's preview timeline. */
@androidx.compose.runtime.Composable
private fun QuickStepBanner(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
    settled: Boolean = true,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    PlayerChrome(
        mode = PlayerChromeMode.BANNER_STEP,
        content = PlayerChromeContent("", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "Documentary", programmeWindow?.event, null, false, 1_800, "")),
        timeline = PlayerChromeTimeline.Live(state, nowSec = 1_800, step = decision, programmeWindow = programmeWindow, stepSettled = settled),
        actions = PlayerChromeActions(active = false),
        onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
        modifier = modifier,
    )
}
