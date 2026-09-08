package at.bernhardberger.tvhplayer.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import coil3.map.Mapper
import coil3.request.Options
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.testing.testSessionObservation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import kotlin.time.Duration.Companion.seconds
import coil3.ImageLoader
import java.io.File
import kotlin.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Offline production chrome only; these captures do not establish live video or SDK timing. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class PlayerScreenshotTest(private val scenario: String, private val dark: Boolean) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun captureProductionChrome() {
        lateinit var inputModeManager: InputModeManager
        var infoOpen by mutableStateOf(false)
        var restoreInfo by mutableStateOf(false)
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            val context = LocalContext.current
            val currentSession = remember { FakeSessionObservation(testSessionObservation()).captureCurrentSession() }
            val imageLoader = remember {
                ImageLoader.Builder(context).components {
                    add(object : Mapper<AppArtworkSource, Bitmap> {
                        override fun map(data: AppArtworkSource, options: Options): Bitmap {
                            val id = data.selector.substringAfterLast('/').toInt()
                            val width = if (id % 2 == 0) 240 else 96
                            return Bitmap.createBitmap(width, 96, Bitmap.Config.ARGB_8888).apply {
                                Canvas(this).apply {
                                    drawColor(android.graphics.Color.rgb(20, 74, 112))
                                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = android.graphics.Color.WHITE
                                        textAlign = Paint.Align.CENTER
                                        textSize = if (width > 100) 48f else 34f
                                    }
                                    drawText(if (width > 100) "DOC $id" else "TV", width / 2f, 62f, paint)
                                }
                            }
                        }
                    })
                }.build()
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val large = scenario in listOf("long", "shelf-long")
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current).apply {
                setLocale(if (large) java.util.Locale.GERMAN else java.util.Locale.US)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if (large) 1.5f else 1f),
                androidx.compose.ui.platform.LocalConfiguration provides configuration,
                LocalContext provides context.createConfigurationContext(configuration),
            ) {
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
                    if (dark) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)))
                    if (scenario.startsWith("settings")) {
                        PlaybackOptionsSheetContent(
                            page = if (scenario == "settings") PlaybackOptionsPage.ROOT else PlaybackOptionsPage.AUDIO,
                            audioTracks = listOf(PlaybackOptionTrack("de", "Deutsch", "Dolby Digital 5.1", true),
                                PlaybackOptionTrack("en", "English", "Stereo")),
                            subtitleTracks = listOf(PlaybackOptionTrack("de", "Deutsch")),
                            tracksResolving = false, aspectRatio = AspectRatioMode.FIT, statsVisible = false,
                            onPageChange = {}, onAudioTrackSelected = {}, onSubtitleTrackSelected = {},
                            onAspectRatioChange = {}, onStatsVisibleChange = {},
                        )
                    } else if (scenario.startsWith("info") || infoOpen) {
                        LiveProgrammeInfoOverlay(
                            event = if (scenario == "info-missing") null else programme(long = scenario.startsWith("info-long")), channelIdentity = "1 Documentary HD", channelName = "Documentary HD",
                            recordingScheduled = false, canRecord = true, recordingState = LiveInfoRecordingState.Idle,
                            confirmationVisible = false, restoreRecordFocus = false, onRecord = {},
                            onRecordingActivate = {}, onRecordingDismiss = {}, onClose = { infoOpen = false; restoreInfo = true },
                        )
                    } else if (scenario == "recording-info") {
                        val readingFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { readingFocus.requestFocus() }
                        PlaybackOptionsOverlayFrame(paneTitle = "Info", panelTag = "recording-info-panel") {
                            PlayerInfoReadingContent(title = programme(true).title.orEmpty(), subtitle = "Documentary HD / The high mountains",
                                body = programme(true).description, readingFocus = readingFocus,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
                                androidx.tv.material3.OutlinedButton(onClick = {}, modifier = Modifier.align(Alignment.End)) { androidx.tv.material3.Text("Close info") }
                            }
                        }
                    } else if (scenario.startsWith("seek")) {
                        val history = if (scenario == "seek-shallow") 60_000L else 5_400_000L
                        TimeshiftSeekPreview(
                            state = AppTimeshiftState(available = true, bufferStartMs = -history,
                                positionMs = -30_000L, liveEdgeMs = 0L),
                            decision = TimeshiftSeekDecision(if (scenario == "seek-live") 0L else -history / 2, -30_000L, false),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else if (scenario.startsWith("shelf")) {
                        PlayerOverlayChrome(
                            footerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            headerContent = { modifier ->
                                PlayerIdentityHeader(imageLoader, "imagecache/12", "12 Documentary 12", "", null,
                                    "21:30", null, modifier = modifier, currentSession = currentSession, compact = true)
                            },
                        ) {
                            ChannelDrawer(
                                channels = if (scenario == "shelf-empty") emptyList() else List(15) {
                                    Channel.create(id = ChannelId(it + 1L), icon = "imagecache/${it + 1}", name = if (scenario == "shelf-long") "Dokumentation und Zeitgeschichte ${it + 1}" else "Documentary ${it + 1}")
                                },
                                selectedId = ChannelId(2), playingChannelId = ChannelId(12), recordingChannelIds = setOf(ChannelId(12)),
                             nowEvent = { if (scenario == "shelf-missing") null else programme(long = scenario == "shelf-long") }, nextEvent = { if (scenario == "shelf-missing") null else EpgEvent.create(id = EventId(2), channelId = ChannelId(1),
                                 start = Instant.fromEpochSeconds(1_783_022_400L), stop = Instant.fromEpochSeconds(1_783_024_200L),
                                 title = "The world beneath the ice") }, imageLoader = imageLoader,
                                 onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {}, currentSession = currentSession,
                            )
                        }
                    } else if (scenario.startsWith("recording")) {
                        RecordingOverlayControls(
                            imageLoader = imageLoader, piconPath = "imagecache/13", currentSession = currentSession,
                            title = "A journey through the Alps", subtitle = "The high mountains",
                            channelName = "Documentary HD", positionMs = 1_200_000L,
                            durationMs = if (scenario == "recording-unknown") androidx.media3.common.C.TIME_UNSET else 5_400_000L,
                            growing = scenario == "recording-unknown", nowSec = 1_783_020_600L,
                            canSeek = scenario != "recording-unknown", controlsVisible = true,
                            optionsOpen = false, onTogglePlayPause = {}, onSeek = {},
                            onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {}, onOpenInfo = {},
                        )
                    } else {
                        OverlayControlsTv(
                            imageLoader = imageLoader, channelNumber = 1, channelName = "Documentary HD",
                            piconPath = "imagecache/12", currentSession = currentSession,
                            nowEvent = if (scenario == "missing") null else programme(long = scenario == "long"),
                            nextEvent = EpgEvent.create(id = EventId(2), channelId = ChannelId(1),
                                start = Instant.fromEpochSeconds(1_783_022_400L),
                                stop = Instant.fromEpochSeconds(1_783_024_200L), title = "The world beneath the ice"),
                            nowSec = 1_783_020_600L, controlsVisible = true, optionsOpen = false,
                            onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            onOpenInfo = { infoOpen = true }, restoreInfoFocus = restoreInfo,
                            onInfoFocusRestored = { restoreInfo = false },
                            timeshiftState = remember(scenario) {
                                if (scenario in listOf("live", "long", "missing", "return-info")) AppTimeshiftState() else {
                                    val fixture = TimeshiftTestFixture(7_200.seconds)
                                    fixture.updateHistory(if (scenario.endsWith("deep")) 0.seconds else 3_000.seconds, 3_600.seconds)
                                    fixture.state.value.toAppPresentation(fixture.playbackPosition(
                                        when (scenario) {
                                            "timing-unavailable" -> null
                                            "paused", "paused-deep" -> 3_300.seconds
                                            else -> 3_600.seconds
                                        },
                                    ))
                                }
                            },
                            timeshiftFeedback = null, onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                            paused = scenario.startsWith("paused"),
                        )
                    }
                }
            }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        if (scenario == "shelf-empty") composeRule.onNodeWithTag("player-shelf-close").assertIsFocused()
        if (scenario == "return-info") {
            composeRule.onNodeWithTag("player-info").requestFocus().performKeyInput { pressKey(Key.Enter) }
            composeRule.onNodeWithTag("player-info-reading").assertIsFocused()
            composeRule.onNodeWithTag("live-info-close").requestFocus().performKeyInput { pressKey(Key.Enter) }
            composeRule.onNodeWithTag("player-info").assertIsFocused()
        }
        if (scenario == "info-long-end") {
            repeat(80) { composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
            composeRule.onNodeWithTag("live-info-record").assertIsFocused()
        }
        if (scenario == "shelf-browse") {
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            composeRule.onNodeWithTag("player-channel-card-13").assertIsFocused()
        }
        if (scenario.startsWith("shelf") && scenario != "shelf-empty") {
            val id = if (scenario == "shelf-browse") 13 else 12
            val card = composeRule.onNodeWithTag("player-channel-card-$id").assertIsFocused().fetchSemanticsNode().boundsInRoot
            for (part in listOf("now", "next")) {
                val programme = composeRule.onNodeWithTag("player-channel-$id-$part", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertTrue(programme.left >= card.left && programme.right <= card.right)
                assertTrue(programme.top >= card.top && programme.bottom <= card.bottom)
                if (scenario == "shelf-long") {
                    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                    composeRule.onNodeWithTag("player-channel-$id-$part", useUnmergedTree = true)
                        .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    val layout = layouts.single()
                    assertTrue("$part: line bottom ${layout.getLineBottom(layout.lineCount - 1)} must fit text height ${layout.size.height}",
                        layout.getLineBottom(layout.lineCount - 1) <= layout.size.height)
                    if (part == "now") assertTrue("Long Now title must retain two lines", layout.lineCount == 2)
                }
            }
        }
        if (scenario == "live") {
            composeRule.onNodeWithText("A journey through the Alps").assertExists()
            composeRule.onNodeWithTag("player-next-programme").assertExists()
            composeRule.onNodeWithTag("player-info").requestFocus()
            composeRule.onNodeWithTag("player-info").assertIsFocused()
        }
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "player-captures")
        assertTrue(directory.isDirectory || directory.mkdirs())
        File(directory, "$scenario-${if (dark) "dark" else "bright"}.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-dark={1}")
        fun scenarios() = listOf("live", "timeshift-live", "timeshift-live-deep", "paused", "paused-deep", "timing-unavailable",
            "seek-shallow", "seek-deep", "seek-live", "long", "missing", "recording", "recording-unknown", "recording-info",
            "settings", "settings-audio", "info", "info-long", "info-long-end", "info-missing", "shelf", "shelf-browse", "shelf-long", "shelf-missing", "shelf-empty", "return-info")
            .flatMap { scenario -> listOf(false, true).map { dark -> arrayOf<Any>(scenario, dark) } }

        private fun programme(long: Boolean = false) = EpgEvent.create(id = EventId(1), channelId = ChannelId(1),
            start = Instant.fromEpochSeconds(1_783_018_800L), stop = Instant.fromEpochSeconds(1_783_022_400L),
            title = if (long) "A journey through the Alps: the extraordinary landscapes, wildlife and people of the high mountains across Europe" else "A journey through the Alps",
            description = "Explore the high mountains, their wildlife and the people who live in this extraordinary landscape. ".repeat(if (long) 24 else 1))
    }
}
