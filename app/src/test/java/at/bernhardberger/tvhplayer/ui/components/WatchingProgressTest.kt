package at.bernhardberger.tvhplayer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.testutil.FixtureArt
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.VisualCapture
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.player.NowPlayingStrip
import at.bernhardberger.tvhplayer.ui.screens.ProgrammeContextMenu
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadline
import at.bernhardberger.tvhplayer.ui.screens.guide.GuideHeadlineProgramme
import at.bernhardberger.tvhplayer.ui.screens.recordings.RecordingListRow
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
class WatchingProgressTest {
    @get:Rule val images = ImageLoaderRule()
    @get:Rule val compose = createComposeRule()
    private val locale = Locale.getDefault()
    private val zone = TimeZone.getDefault()
    private lateinit var view: View
    private var viewerColor = 0
    private var backgroundColor = 0
    private val start = Instant.parse("2026-09-15T19:00:00Z")
    private val now = start.epochSeconds + 1800
    private val channel = Channel.create(ChannelId(1), name = "Ridge Earth HD", number = 101, icon = ArtworkId(1))
    private val event = EpgEvent.create(EventId(1), channel.id, start, start + 7200.seconds,
        title = "Ridge Light", subtitle = "Beyond the Arctic Circle", image = ArtworkId(2),
        summary = "A winter expedition follows the changing light across the ridge.")
    private val recording = DvrEntry.create(DvrEntryId(1), channelId = channel.id, title = event.title,
        channelName = channel.name, start = start, stop = event.stop, state = DvrEntryState.COMPLETED,
        image = event.image, playPosition = 60.seconds)
    private val observation = FakeSessionObservation(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).observation.value
    private val position = mutableStateOf<RecordingViewerPosition?>(RecordingViewerPosition(recording.id, 1800000, 7200000))
    private val indicator = mutableStateOf(ChannelPlaybackIndicator.PLAYING)
    private val target = mutableStateOf<AppPlaybackTarget?>(AppPlaybackTarget.Live(channel.id))

    @Before fun prepare() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        images.install(FixtureArt.imageLoader(ApplicationProvider.getApplicationContext(), mapOf(
            ArtworkId(1) to FixtureArt.picon("ridge-earth"), ArtworkId(2) to FixtureArt.art("ridge-light"))))
    }
    @After fun restore() { Locale.setDefault(locale); TimeZone.setDefault(zone) }

    @Test fun watchingUsesViewerColorAndOtherItemsUseAmbientColor() {
        assertEquals(Color.Yellow, progressColor(true, Color.Cyan, Color.Yellow))
        assertEquals(Color.Cyan, progressColor(false, Color.Cyan, Color.Yellow))
    }

    @Test fun recordingUsesTheSamePlayingPausedAndStoppedPolicyAsLive() {
        val item = AppPlaybackTarget.Recording(recording.id)
        assertEquals(ChannelPlaybackIndicator.PLAYING, playbackIndicator(item, item, AppPlaybackState.Playing, true))
        assertEquals(ChannelPlaybackIndicator.PAUSED, playbackIndicator(item, item, AppPlaybackState.Buffering, false))
        assertEquals(ChannelPlaybackIndicator.NONE, playbackIndicator(item, item, AppPlaybackState.Idle, true))
        assertEquals(ChannelPlaybackIndicator.NONE, playbackIndicator(item, AppPlaybackTarget.Live(channel.id), AppPlaybackState.Playing, true))
        assertEquals(null, RecordingViewerPosition(recording.id, 0, -1).fraction)
        assertEquals(1f, RecordingViewerPosition(recording.id, 9000, 1000).fraction)
    }

    @Test fun guideHeadlineMarksOnlyThePlayingProgramme() {
        show("guide")
        marker().assertExists()
        val bounds = marker().fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertTrue("The passive headline's marker must have a visible neutral tint",
            (bounds.top.toInt() until bounds.bottom.toInt()).any { y ->
                (bounds.left.toInt() until bounds.right.toInt()).any { x ->
                    android.graphics.Color.red(bitmap.getPixel(x, y)) > 100
                }
            })
        bitmap.recycle()
        compose.runOnIdle { target.value = AppPlaybackTarget.Live(ChannelId(2)) }
        marker().assertDoesNotExist()
    }
    @Test fun programmeDetailsReplacePlayIconAndRetainPauseMark() {
        show("programme")
        marker().assertExists()
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { indicator.value = ChannelPlaybackIndicator.PAUSED }
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
    }
    @Test fun contextMenuMarksThePlayingProgramme() { show("menu"); marker().assertExists() }
    @Test fun nowPlayingStripKeepsTheSharedMark() { show("zap"); marker().assertExists() }
    @Test fun pausedZapKeepsOrangeProgressWithoutALogoPlate() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
        val bar = progressBar().fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        // Inside the logo's horizontal padding, above the progress strip, not the rounded corner.
        assertEquals(backgroundColor, bitmap.getPixel(bar.left.toInt() + 2, bar.top.toInt() - 15))
        bitmap.recycle()
    }
    @Test fun zapWithoutLogoKeepsChannelNumberFallback() {
        show("zap", picon = null)
        compose.onNodeWithText("101", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun recordingRowMarksPlaybackRatherThanSelection() {
        show("row")
        marker().assertExists()
        compose.runOnIdle { target.value = null }
        marker().assertDoesNotExist()
    }
    @Test fun playingRecordingDetailsFollowPositionAndIgnoreOtherRecordings() {
        show("recording")
        marker().assertExists()
        progress(.25f)
        compose.runOnIdle { position.value = RecordingViewerPosition(recording.id, 3600000, 7200000) }
        progress(.5f)
        compose.runOnIdle { indicator.value = ChannelPlaybackIndicator.PAUSED }
        progress(.5f)
        compose.onNodeWithTag("channel-paused-indicator", useUnmergedTree = true).assertExists()
        assertOrangeProgress()
        compose.runOnIdle { position.value = RecordingViewerPosition(recording.id, 1800000, 7200000) }
        progress(.25f)
        assertOrangeProgress()
        compose.runOnIdle { position.value = RecordingViewerPosition(DvrEntryId(99), 5400000, 7200000) }
        compose.onNodeWithTag("details-progress", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test @Category(VisualCapture::class) fun guidePlayingCapture() = captureSurface("guide")
    @Test @Category(VisualCapture::class) fun programmePlayingCapture() = captureSurface("programme", "details-watch")
    @Test @Category(VisualCapture::class) fun menuPlayingCapture() = captureSurface("menu")
    @Test @Category(VisualCapture::class) fun zapPlayingCapture() = captureSurface("zap")
    @Test @Category(VisualCapture::class) fun recordingRowPlayingCapture() = captureSurface("row", "recording-list-entry-1")
    @Test @Category(VisualCapture::class) fun recordingDetailsPlayingCapture() = captureSurface("recording", "details-resume")
    @Test @Category(VisualCapture::class) fun pausedZapCapture() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("zap")
        save("zap-paused")
    }
    @Test @Category(VisualCapture::class) fun pausedRecordingCapture() {
        indicator.value = ChannelPlaybackIndicator.PAUSED
        show("recording")
        save("recording-paused")
    }

    private fun marker() = compose.onNodeWithTag("channel-playing-indicator", useUnmergedTree = true)
    private fun progress(value: Float) = compose.onNodeWithTag("details-progress", useUnmergedTree = true)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)))
    private fun progressBar() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
        useUnmergedTree = true)
    private fun assertOrangeProgress() {
        val bounds = progressBar().fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        assertEquals(viewerColor, bitmap.getPixel(bounds.left.toInt() + 5, bounds.center.y.toInt()))
        bitmap.recycle()
    }

    private fun show(surface: String, picon: ArtworkId? = channel.icon) {
        if (surface == "recording" || surface == "row") target.value = AppPlaybackTarget.Recording(recording.id)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f),
                LocalWatchingTarget provides target.value,
                LocalWatchingChannel provides (target.value as? AppPlaybackTarget.Live)?.channelId,
                LocalWatchingIndicator provides indicator.value, LocalRecordingPosition provides position) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    viewerColor = MaterialTheme.colorScheme.tertiary.toArgb()
                    backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                    ProvideArtworkSession(observation.currentSession) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (surface) {
                                "guide" -> GuideHeadline({ GuideHeadlineProgramme(channel, event) }, observation, null, false,
                                    { now }, Modifier.padding(48.dp))
                                "programme", "recording" -> ProgrammeDetailsPage(event.takeIf { surface == "programme" },
                                    recording.takeIf { surface == "recording" }, channel, now,
                                    listOf(if (surface == "recording") BrowseContextAction.RESUME else BrowseContextAction.WATCH,
                                        BrowseContextAction.SHOW_GUIDE), {}, {})
                                "menu" -> ProgrammeContextMenu(event, channel, null, true, false, now, false,
                                    observation.currentSession, observation, false, { _, _ -> }, {})
                                "zap" -> NowPlayingStrip("101", channel.name.orEmpty(), picon, event, now,
                                    Modifier.padding(48.dp), indicator.value)
                                "row" -> Column(Modifier.padding(48.dp).width(480.dp)) {
                                    RecordingListRow(recording, channel.icon, 60.seconds, false, onClick = {})
                                    RecordingListRow(DvrEntry.create(DvrEntryId(2), title = "Other recording"), null, null, true, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun captureSurface(surface: String, focusTag: String? = null) {
        show(surface)
        if (focusTag != null) compose.onNodeWithTag(focusTag).performSemanticsAction(SemanticsActions.RequestFocus)
        save("$surface-${if (surface == "guide" || surface == "zap") "passive" else "focused"}")
        if (focusTag != null) {
            compose.onNodeWithTag(focusTag).performKeyInput { pressKey(if (surface == "row") Key.DirectionDown else Key.DirectionRight) }
            compose.onNodeWithTag(focusTag).assertIsNotFocused()
            save("$surface-unfocused")
        }
    }

    private fun save(name: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/now-playing").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
