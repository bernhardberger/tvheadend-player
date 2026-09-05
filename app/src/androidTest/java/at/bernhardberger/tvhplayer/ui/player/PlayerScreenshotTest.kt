package at.bernhardberger.tvhplayer.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
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
class PlayerScreenshotTest(private val scenario: String) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun captureProductionChrome() {
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            val context = LocalContext.current
            val imageLoader = remember { ImageLoader.Builder(context).build() }
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
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
                    } else if (scenario == "info") {
                        LiveProgrammeInfoOverlay(
                            event = programme(), channelIdentity = "1 Documentary HD", channelName = "Documentary HD",
                            recordingScheduled = false, canRecord = true, recordingState = LiveInfoRecordingState.Idle,
                            confirmationVisible = false, restoreRecordFocus = false, onRecord = {},
                            onRecordingActivate = {}, onRecordingDismiss = {}, onClose = {},
                        )
                    } else if (scenario == "shelf") {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            ChannelDrawer(
                                channels = List(15) { Channel.create(id = ChannelId(it + 1L), name = "Documentary ${it + 1}") },
                                selectedId = ChannelId(2), playingChannelId = ChannelId(12), recordingChannelIds = setOf(ChannelId(12)),
                                nowEvent = { programme() }, nextEvent = { null }, imageLoader = imageLoader,
                                onFocusChannel = {}, onPickChannel = {}, onCloseDrawer = {},
                            )
                        }
                    } else if (scenario.startsWith("recording")) {
                        RecordingOverlayControls(
                            imageLoader = imageLoader, piconPath = null,
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
                            piconPath = null,
                            nowEvent = EpgEvent.create(id = EventId(1), channelId = ChannelId(1),
                                start = Instant.fromEpochSeconds(1_783_018_800L),
                                stop = Instant.fromEpochSeconds(1_783_022_400L), title = "A journey through the Alps"),
                            nextEvent = EpgEvent.create(id = EventId(2), channelId = ChannelId(1),
                                start = Instant.fromEpochSeconds(1_783_022_400L),
                                stop = Instant.fromEpochSeconds(1_783_024_200L), title = "The world beneath the ice"),
                            nowSec = 1_783_020_600L, controlsVisible = true, optionsOpen = false,
                            onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = remember(scenario) {
                                if (scenario == "live") AppTimeshiftState() else {
                                    val fixture = TimeshiftTestFixture(7_200.seconds)
                                    fixture.updateHistory(3_000.seconds, 3_600.seconds)
                                    fixture.state.value.toAppPresentation(fixture.playbackPosition(
                                        when (scenario) {
                                            "timing-unavailable" -> null
                                            "paused" -> 3_300.seconds
                                            else -> 3_600.seconds
                                        },
                                    ))
                                }
                            },
                            timeshiftFeedback = null, onToggleTimeshiftPause = {}, onSeekTimeshift = {}, onGoLive = {},
                            paused = scenario == "paused",
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
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
        File(directory, "$scenario.png").outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = listOf("live", "timeshift-live", "paused", "timing-unavailable", "recording", "recording-unknown", "settings", "settings-audio", "info", "shelf")

        private fun programme() = EpgEvent.create(id = EventId(1), channelId = ChannelId(1),
            start = Instant.fromEpochSeconds(1_783_018_800L), stop = Instant.fromEpochSeconds(1_783_022_400L),
            title = "A journey through the Alps", description = "Explore the high mountains, their wildlife and the people who live in this extraordinary landscape.")
    }
}
